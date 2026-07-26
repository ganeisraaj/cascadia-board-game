package service.network

import edu.udo.cs.sopra.ntf.ChatMessage
import edu.udo.cs.sopra.ntf.GameConfigMessage
import edu.udo.cs.sopra.ntf.GameInitMessage
import edu.udo.cs.sopra.ntf.NetPlayer
import edu.udo.cs.sopra.ntf.NetWildlife
import edu.udo.cs.sopra.ntf.PlaceMessage
import edu.udo.cs.sopra.ntf.RotationMessage
import edu.udo.cs.sopra.ntf.SelectHabitatTileMessage
import edu.udo.cs.sopra.ntf.SelectMessage
import edu.udo.cs.sopra.ntf.SelectWildlifeMessage
import edu.udo.cs.sopra.ntf.UseNatureTokenMessage
import edu.udo.cs.sopra.ntf.WipeWildlifeMessage
import entity.CascadiaGame
import entity.Coordinate
import entity.GameState
import entity.HabitatTile
import entity.HabitatTileType
import entity.ScoringCard
import entity.User
import entity.UserBoard
import entity.UserType
import entity.WildLifeToken
import entity.WildLifeTokenType
import service.AbstractRefreshingService
import service.ConnectionState
import service.RootService
import tools.aqua.bgw.util.Stack

/**
 * Handles all communication with the game server.
 *
 * @property rootService Reference to the application's root service.
 */
class NetworkService(val rootService: RootService) : AbstractRefreshingService() {

    var client: CascadiaNetworkClient? = null
        private set

    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    val players = mutableListOf<User>()

    /**
     * Connects to server and creates a new game session.
     *
     * @param secret Server secret.
     * @param name Player name.
     * @param sessionID identifier of the hosted session (to be used by guest on join)
     *
     * @throws IllegalStateException if already connected to another game or connection attempt fails
     */
    fun hostGame(secret: String, name: String, sessionID: String?) {
        if (!connect(secret, name)) {
            error("Connection failed")
        }
        updateConnectionState(ConnectionState.CONNECTED)

        if (sessionID.isNullOrBlank()) {
            client?.createGame(GAME_ID, "Welcome!")
        } else {
            client?.createGame(GAME_ID, sessionID, "Welcome!")
        }

        updateConnectionState(ConnectionState.WAITING_FOR_HOST_CONFIRMATION)
    }

    /**
     * Handles a successful game hosting request by updating the connection state,
     * adding the local player, and refreshing the application state.
     *
     * @param localUsername Name of the local player hosting the game.
     */
    fun handleHostGameSuccess(localUsername: String) {
        updateConnectionState(ConnectionState.WAITING_FOR_GUEST)
        onPlayerJoined(localUsername, true)
        onAllRefreshables { refreshAfterHostSuccessful() }
    }

    /**
     * Updates the current connection state and notifies all refreshable components
     * about the state change.
     *
     * @param newState The new connection state to apply.
     */
    fun updateConnectionState(newState: ConnectionState) {
        this.connectionState = newState
        onAllRefreshables { refreshAfterConnectionStateChanged(newState) }
    }

    /**
     * Disconnects the [client] from the server, nulls it and updates the
     * [connectionState] to [ConnectionState.DISCONNECTED]. Can safely be called
     * even if no connection is currently active.
     */
    fun disconnect() {
        client?.apply {
            if (sessionID != null) leaveGame("Goodbye!")
            if (isOpen) disconnect()
        }
        client = null
        updateConnectionState(ConnectionState.DISCONNECTED)
        players.clear()
    }

    /**
     * Connects to server and joins a game session as guest player.
     *
     * @param secret Server secret.
     * @param name Player name.
     * @param sessionID identifier of the joined session (as defined by host on create)
     *
     * @throws IllegalStateException if already connected to another game or connection attempt fails
     */

    fun joinGame(secret: String, name: String, sessionID: String) {
        if (!connect(secret, name)) {
            error("Connection failed")
        }
        updateConnectionState(ConnectionState.CONNECTED)
        client?.joinGame(sessionID, "Hello!")

        updateConnectionState(ConnectionState.WAITING_FOR_JOIN_CONFIRMATION)
    }

    /**
     * Handles a successful join request by updating the connection state,
     * adding all opponents and the local player, and refreshing the application state.
     *
     * @param localUsername Name of the local player joining the game.
     * @param opponents List of usernames of the already connected players.
     */
    fun handleJoinGameSuccess(localUsername: String, opponents: List<String>) {
        updateConnectionState(ConnectionState.WAITING_FOR_INIT)

        for (username in opponents) {
            onPlayerJoined(username, false)
        }

        onPlayerJoined(localUsername, true)

        onAllRefreshables { refreshAfterJoinSuccessful() }
    }

    /**
     * Connects to server, sets the [NetworkService.client] if successful and returns `true` on success.
     *
     * @param secret Network secret. Must not be blank (i.e. empty or only whitespaces)
     * @param name Player name. Must not be blank
     *
     * @throws IllegalArgumentException if secret or name is blank
     * @throws IllegalStateException if already connected to another game
     */
    fun connect(secret: String, name: String): Boolean {
        require(connectionState == ConnectionState.DISCONNECTED && client == null)
        { "already connected to another game" }

        require(secret.isNotBlank()) { "server secret must be given" }
        require(name.isNotBlank()) { "player name must be given" }

        val newClient =
            CascadiaNetworkClient(
                playerName = name,
                host = SERVER_ADDRESS,
                secret = secret,
                networkService = this
            )

        return if (newClient.connect()) {
            this.client = newClient
            true
        } else {
            false
        }
    }

    /**
     * set up the game using [service.GameService.startNewGame] and send the game init message
     * to the guest player. [connectionState] needs to be [ConnectionState.WAITING_FOR_GUEST].
     * This method should be called from the [CascadiaNetworkClient] when the guest joined notification
     *
     * The message mirrors the exact state that [service.GameService.startNewGame] produced
     * (display + remaining supply for tiles/wildlife, and the starter-tile block per player)
     * so that the guest can reconstruct an identical game via [startNewJoinedGame].
     */
    fun startNewHostedGame(users: List<User>, scoringCards: List<ScoringCard>) {
        check(connectionState == ConnectionState.WAITING_FOR_GUEST)

        rootService.gameService.startNewGame(users, scoringCards)

        val game = checkNotNull(rootService.currentGame)
        val allTilesForHost = parseTilesFromCSV(loadCsvLines("csv/tiles.csv"))
        val starterBlocks = parseStarterTilesFromCSV(loadCsvLines("csv/start_tiles.csv"))

        // already shuffled habitatTiles
        val fullTileOrder = (0..3).map { index ->
            game.displayedHabitatTiles[index]
        } + game.habitatTileCollection.peekAll()

        val tileStackIndices = fullTileOrder.map { tile ->

            checkNotNull(tile)

            allTilesForHost.indexOfFirst {
                it.keyStone == tile.keyStone &&
                        it.edges == tile.edges &&
                        it.availableWildLifeToken == tile.availableWildLifeToken
            }.also {
                require(it != -1) { "Tile $tile could not be found." }
            }
        }

        // already shuffled wildLifeTokens
        val fullWildlifeOrder = (0..3).map { index ->
            game.displayedWildLifeToken[index]
        } + game.wildLifeCollection.peekAll()

        val wildlifeNetBag = fullWildlifeOrder.map { token -> NetWildlife.valueOf(checkNotNull(token).type.name) }

        val message = GameInitMessage(
            tileStack = tileStackIndices,
            scoringCards = scoringCards.map { it.isTypeB },
            players = users.map { user ->
                NetPlayer(
                    name = user.name,
                    startingTileID = findAssignedStarterBlockId(user.board, starterBlocks)
                )
            },
            wildlifeBag = wildlifeNetBag
        )

        client?.sendGameActionMessage(message)
        //updateConnectionState(ConnectionState.WAITING_FOR_PLAYER_TURN)
    }


    /**
     * Initializes the entity structure with the data given by the NetCascadiaGameInitMessage sent by the host.
     * [connectionState] needs to be [ConnectionState.WAITING_FOR_INIT].
     * This method should be called from the [CascadiaNetworkClient] when the host sends the init message.
     */
    private fun loadCsvLines(fileName: String): List<String> {
        val resourceStream = this::class.java.getResourceAsStream("/$fileName")
            ?: Thread.currentThread().contextClassLoader.getResourceAsStream(fileName)
        return resourceStream?.bufferedReader()?.readLines() ?: emptyList()
    }

    /**
     * Parses habitat tiles from CSV-formatted lines.
     *
     * @param csvLines List of CSV lines containing habitat tile data.
     * @return Mutable list of parsed habitat tiles.
     */
    private fun parseTilesFromCSV(csvLines: List<String>): MutableList<HabitatTile> {
        val parsedTiles = mutableListOf<HabitatTile>()
        csvLines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("id") || line.isEmpty() || line.startsWith("-")) return@forEach

            val parts = line.split(";")
            if (parts.size < 4) return@forEach

            val edgeTypes = parts[1].trim().map { charToHabitatType(it) }.toMutableList()
            val allowedAnimals = parts[2].trim().map { charToWildLifeType(it) }
            val isKeystone = parts[3].trim().equals("yes", ignoreCase = true)

            parsedTiles.add(
                HabitatTile(
                    keyStone = isKeystone,
                    rotation = 0,
                    edges = edgeTypes,
                    availableWildLifeToken = allowedAnimals,
                    placedWildLifeToken = null
                )
            )
        }
        return parsedTiles
    }

    /**
     * Parses starter habitat tiles from CSV-formatted lines and groups them by ID.
     *
     * @param csvLines List of CSV lines containing starter tile data.
     * @return Map containing starter tile IDs as keys and corresponding tiles as values.
     */
    private fun parseStarterTilesFromCSV(csvLines: List<String>): Map<Int, List<HabitatTile>> {
        val allStarters = mutableListOf<Pair<Int, HabitatTile>>()
        csvLines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("id") || line.isEmpty() || line.startsWith("-")) return@forEach

            val parts = line.split(";")
            if (parts.size < 4) return@forEach

            val id = parts[0].trim().toInt()
            val edgeTypes = parts[1].trim().map { charToHabitatType(it) }.toMutableList()
            val allowedAnimals = parts[2].trim().map { charToWildLifeType(it) }
            val isKeystone = parts[3].trim().equals("yes", ignoreCase = true)

            allStarters.add(
                Pair(
                    id, HabitatTile(
                        keyStone = isKeystone,
                        rotation = 0,
                        edges = edgeTypes,
                        availableWildLifeToken = allowedAnimals,
                        placedWildLifeToken = null
                    )
                )
            )
        }

        return allStarters.sortedBy { it.first }
            .groupBy(keySelector = { it.first / 10 }, valueTransform = { it.second })
    }

    /**
     * Sets up the initial habitat tiles on a player's board.
     *
     * @param board The player's board where the starter tiles are placed.
     * @param starterTiles List of three tiles forming the starter block.
     * @throws IllegalArgumentException If the starter block does not contain exactly three tiles.
     */
    private fun setupPlayerStarterTiles(board: UserBoard, starterTiles: List<HabitatTile>) {
        board.placedHabitatTiles.clear()
        require(starterTiles.size == 3) { "A starter block must consist of exactly 3 tiles." }

        board.placedHabitatTiles[Coordinate(0, 0)] = starterTiles[0]
        board.placedHabitatTiles[Coordinate(0, 1)] = starterTiles[1]
        board.placedHabitatTiles[Coordinate(-1, 1)] = starterTiles[2]
    }

    /**
     * Finds the ID of the starter block assigned to a player's board.
     *
     * @param board The player's board containing the placed starter tiles.
     * @param starterBlocks Map of starter block IDs and their corresponding tiles.
     * @return The ID of the matching starter block.
     * @throws IllegalArgumentException If required starter tiles are missing.
     * @throws IllegalStateException If no matching starter block can be found.
     */
    private fun findAssignedStarterBlockId(
        board: UserBoard,
        starterBlocks: Map<Int, List<HabitatTile>>
    ): Int {
        val tile0 = board.placedHabitatTiles[Coordinate(0, 0)]
        val tile1 = board.placedHabitatTiles[Coordinate(0, 1)]
        val tile2 = board.placedHabitatTiles[Coordinate(-1, 1)]
        requireNotNull(tile0) { "Board has no tile at (0,0)" }
        requireNotNull(tile1) { "Board has no tile at (0,1)" }
        requireNotNull(tile2) { "Board has no tile at (-1,1)" }

        fun matches(a: HabitatTile, b: HabitatTile) =
            a.keyStone == b.keyStone && a.edges == b.edges && a.availableWildLifeToken == b.availableWildLifeToken

        for ((blockId, tiles) in starterBlocks) {
            if (tiles.size == 3 && tiles.indices.all
                { matches(tiles[it], listOf(tile0, tile1, tile2)[it]) }
            ) {
                return blockId
            }
        }
        error("Could not determine starter block for the given board")
    }

    /**
     * Converts a character representation into a habitat tile type.
     *
     * @param char Character representing a habitat type.
     * @return The corresponding habitat tile type.
     * @throws IllegalArgumentException If the character does not represent a valid habitat type.
     */
    private fun charToHabitatType(char: Char): HabitatTileType {
        return when (char) {
            'M' -> HabitatTileType.MOUNTAINS
            'F' -> HabitatTileType.FORESTS
            'P' -> HabitatTileType.PRAIRIES
            'W' -> HabitatTileType.WETLANDS
            'R' -> HabitatTileType.RIVERS
            else -> throw IllegalArgumentException("Invalid habitat character: $char")
        }
    }

    /**
     * Converts a character representation into a wildlife token type.
     *
     * @param char Character representing a wildlife token type.
     * @return The corresponding wildlife token type.
     * @throws IllegalArgumentException If the character does not represent a valid wildlife type.
     */
    private fun charToWildLifeType(char: Char): WildLifeTokenType {
        return when (char) {
            'B' -> WildLifeTokenType.BEAR
            'E' -> WildLifeTokenType.ELK
            'S' -> WildLifeTokenType.SALMON
            'H' -> WildLifeTokenType.HAWK
            'F' -> WildLifeTokenType.FOX
            else -> throw IllegalArgumentException("Invalid wildlife character: $char")
        }
    }

    /**
     * Reconstructs the game exactly as the host set it up:
     * - player order follows [message.players] (not a hardcoded local-first order), so
     *   `currentUser` refers to the same physical player on both sides,
     * - habitat/wildlife display and supply are split at index 4, matching what the host
     *   actually serialized in [startNewHostedGame],
     * - each player's starter-tile board is set up from [edu.udo.cs.sopra.ntf.NetPlayer.startingTileID].
     */
    fun startNewJoinedGame(
        message: GameInitMessage,
        userName: String,
        otherUserName: String
    ) {

        // Validate network state
        check(connectionState == ConnectionState.WAITING_FOR_INIT) { "Not in correct state to start." }

        // Reconstruct players in the exact order the host used, so both sides agree on
        // turn order (index 0 == whoever the host listed first), instead of always
        // putting the local (guest) player at index 0.
        val users = message.players.map { netPlayer ->
            when (netPlayer.name) {
                userName -> User(name = userName)
                otherUserName -> User(name = otherUserName, type = UserType.ONLINE_PLAYER)
                else -> error("Unknown player '${netPlayer.name}' in GameInitMessage")
            }
        }

        val tileCsvLines = loadCsvLines("csv/tiles.csv")
        val allTiles = parseTilesFromCSV(tileCsvLines)
        val tileStack = Stack<HabitatTile>().apply {
            message.tileStack.drop(4).forEach { tileIndex ->
                val originalTile = allTiles[tileIndex]
                push(originalTile.deepCopy())
            }
        }

        val tokenStack = Stack<WildLifeToken>().apply {
            message.wildlifeBag.drop(4).forEach { netToken ->
                push(WildLifeToken(type = WildLifeTokenType.valueOf(netToken.name)))
            }
        }

        val displayedTokens = message.wildlifeBag.take(4).mapIndexed { index, netToken ->
            index to WildLifeToken(type = WildLifeTokenType.valueOf(netToken.name))
        }.toMap().toMutableMap()

        //displayedTiles
        val displayedTiles = message.tileStack.take(4).mapIndexed { i, tileIndex ->
            i to allTiles[tileIndex].deepCopy()
        }.toMap().toMutableMap()

        //  Reconstruct the ScoringCards
        val reconstructedScoringCards = message.scoringCards.mapIndexed { index, isB ->
            ScoringCard(wildLife = WildLifeTokenType.entries[index], isTypeB = isB)
        }
        val game = CascadiaGame(
            userList = users,
            scoringCards = reconstructedScoringCards,
            displayedHabitatTiles = displayedTiles,
            displayedWildLifeToken = displayedTokens,
            currentUser = 0,
        ).apply {
            habitatTileCollection = tileStack
            wildLifeCollection = tokenStack
            state = GameState.WAIT_FOR_TURN
        }

        val starterBlocks = parseStarterTilesFromCSV(loadCsvLines("csv/start_tiles.csv"))
        message.players.forEachIndexed { index, netPlayer ->
            val blockTiles = starterBlocks[netPlayer.startingTileID]
                ?: error("Unknown starter block ${netPlayer.startingTileID}")
            setupPlayerStarterTiles(game.userList[index].board, blockTiles.map { it.deepCopy() })
        }

        //  Update global state and notify UI
        rootService.currentGame = game
        updateConnectionState(ConnectionState.WAITING_FOR_PLAYER_TURN)

        onAllRefreshables { refreshAfterGameStart() }

    }

    /**
     * Sends a message to wipe selected wildlife tokens and updates the game state.
     *
     * @param natureTokenRemaining Number of nature tokens remaining after the action.
     * @param wipedIndices Indices of the wildlife tokens to be removed.
     * @param newBag Updated wildlife bag after wiping tokens.
     */
    fun sendWipeWildlife(natureTokenRemaining: Int, wipedIndices: List<Int>, newBag: List<NetWildlife>) {
        check(connectionState == ConnectionState.SELECTING) {
            "not placing or selecting"
        }
        val message = WipeWildlifeMessage(
            usedNatureToken = true,
            natureTokenAmount = natureTokenRemaining,
            wipedWildlifeIndices = wipedIndices,
            wildlifeBag = newBag
        )
        client?.sendGameActionMessage(message)
    }

    /**
     * Handles a received wildlife wipe message and synchronizes the local game state.
     *
     * @param message Contains the updated nature token amount, wiped token indices,
     * and replacement wildlife tokens.
     */
    fun onWipeWildlifeReceived(message: WipeWildlifeMessage) {
        val game = rootService.currentGame ?: return

        // 1. Nature Token Stand des aktuellen Spielers auf dem Gast-Client anpassen
        game.userList[game.currentUser].natureToken = message.natureTokenAmount

        // 2. Auslage synchronisieren
        // Der Host schickt die Indizes der gelöschten Tiere und die neuen Tiere im Bag
        message.wipedWildlifeIndices.forEachIndexed { listIndex, marketIndex ->
            val newNetToken = message.wildlifeBag[listIndex]
            game.displayedWildLifeToken[marketIndex] =
                WildLifeToken(WildLifeTokenType.valueOf(newNetToken.name))
        }

        // 3. GUI aktualisieren
        onAllRefreshables { refreshAfterWipeWildlife() }
    }

    /**
     * Sends a request to select a wildlife token from the current wildlife shop.
     *
     * @param index Index of the wildlife token to select.
     */
    fun sendSelectWildlife(index: Int) {
        check(connectionState == ConnectionState.SELECTING) {
            "not placing or selecting"
        }

        val message = SelectWildlifeMessage(wildlifeShopIndex = index)
        client?.sendGameActionMessage(message)
    }

    /**
     * Handles a received wildlife selection message and updates the game view.
     *
     * @param message Contains the index of the selected wildlife token.
     */
    fun onSelectWildlifeReceived(message: SelectWildlifeMessage) {
        val game = rootService.currentGame ?: return


        println("Gegner hat Wildlife-Token an Position ${message.wildlifeShopIndex} ausgewählt.")

        // GUI-Aktualisierung
        onAllRefreshables { refreshAfterOpponentSelectedWildLifeToken(message.wildlifeShopIndex) }
    }

    /**
     * Sends a request to use a nature token during the game.
     */
    fun sendUseNatureToken() {
        val message = UseNatureTokenMessage()
        client?.sendGameActionMessage(message)

    }

    /**
     * Handles a received nature token usage message and updates the current player's
     * nature token count.
     *
     */
    fun onUseNatureTokenReceived() {
        val game = rootService.currentGame ?: return
        game.userList[game.currentUser].natureToken -= 1

        // GUI aktualisieren
        onAllRefreshables { refreshAfterNatureTokenUsed() }
    }

    /**
     * Sends a request to select a habitat tile from the current habitat shop.
     *
     * @param index Index of the habitat tile to select.
     */
    fun sendSelectHabitat(index: Int) {
        val message = SelectHabitatTileMessage(habitatShopIndex = index)
        client?.sendGameActionMessage(message)
    }

    /**
     * Adds a player to the current player list and notifies all refreshable
     * components about the update.
     *
     * @param userName Name of the joined player.
     * @param isLocal Whether the player is the local player.
     */
    fun onPlayerJoined(userName: String, isLocal: Boolean) {
        // Erstelle ein User-Objekt für den neu hinzugekommenen Spieler
        val newUser = User(
            name = userName,
            type = if (isLocal) UserType.LOCAL_PLAYER else UserType.ONLINE_PLAYER
        )

        players.add(newUser)

        // Benachrichtige alle Refreshables (z.B. LobbyScene), dass jemand beigetreten ist
        onAllRefreshables { refreshAfterUserJoined(newUser) }
    }

    /**
     * Handles a received habitat tile selection message and updates the game view.
     *
     * @param message Contains the index of the selected habitat tile.
     */
    fun onSelectHabitatReceived(message: SelectHabitatTileMessage) {
        val game = rootService.currentGame ?: return
        val habitatIndex = message.habitatShopIndex

        println("Gegner hat Habitat-Plättchen an Position $habitatIndex gewählt.")

        onAllRefreshables { refreshAfterOpponentSelectedHabitatTile(habitatIndex) }
    }

    /**
     * Sends a request to rotate the selected habitat tile.
     *
     * @param degree Rotation angle of the habitat tile.
     * @throws IllegalStateException If the player is not currently placing or selecting.
     */
    fun sendRotation(degree: Int) {
        check(connectionState == ConnectionState.PLACING || connectionState == ConnectionState.SELECTING) {
            "not placing or selecting"
        }
        val message = RotationMessage(habitatRotation = degree)
        client?.sendGameActionMessage(message)
    }

    /**
     * Handles a received habitat tile rotation message and updates the selected tile.
     *
     * @param message Contains the new rotation value for the habitat tile.
     * @throws IllegalStateException If the game is not waiting for the player's turn.
     */
    fun onRotationReceived(message: RotationMessage) {
        check(connectionState == ConnectionState.WAITING_FOR_PLAYER_TURN)
        { "not waiting for player turn. " }

        val game = rootService.currentGame ?: return

        // 1. Zugriff auf den ActionBuilder aus dem aktuellen Spiel
        val actionBuilder = game.currentAction

        val tileToRotate = actionBuilder.selection.habitatTile

        if (tileToRotate != null) {
            tileToRotate.rotation = message.habitatRotation

            onAllRefreshables { refreshAfterRotateHabitatTile(tileToRotate) }
        }
    }

    /**
     * Sends a request to place a habitat tile and optionally a wildlife token.
     *
     * @param posX X-coordinate of the habitat tile position.
     * @param posY Y-coordinate of the habitat tile position.
     * @param wildlifeCoords Coordinates of the wildlife token placement, if any.
     * @param rotation Rotation of the placed habitat tile.
     */
    fun sendPlaceAction(posX: Int, posY: Int, wildlifeCoords: Pair<Int, Int>?, rotation: Int) {
        val message = PlaceMessage(
            habitatCoordinates = Pair(posX, posY),
            wildlifeCoordinates = wildlifeCoords,
            habitatRotation = rotation
        )
        client?.sendGameActionMessage(message)
    }

    /**
     * Handles a received placement message and updates the game state by placing
     * the selected habitat tile and optionally a wildlife token.
     *
     * @param message Contains the placement coordinates, rotation, and optional wildlife coordinates.
     */
    fun onPlaceActionReceived(message: PlaceMessage) {
        val game = rootService.currentGame ?: return

        // 1. Koordinaten extrahieren
        val posX = message.habitatCoordinates.first
        val posY = message.habitatCoordinates.second
        val habCoords = Coordinate(posX, posY)
        val wildCoords = message.wildlifeCoordinates?.let { Coordinate(it.first, it.second) }

        // 2. Das Plättchen holen
        val habitatTile = game.currentAction.selection.habitatTile ?: return
        habitatTile.rotation = message.habitatRotation
        val currentUser = game.userList[game.currentUser]

        // 3. Habitat auf dem Board platzieren
        currentUser.board.placedHabitatTiles[habCoords] = habitatTile

        // 4. GUI für Habitat aktualisieren
        onAllRefreshables { refreshAfterPlaceHabitatTile(habitatTile, posX, posY) }

        // 5. Wildlife platzieren und GUI aktualisieren
        if (wildCoords != null) {
            val wildlife = game.currentAction.selection.wildlifeToken ?: return
            habitatTile.placedWildLifeToken = wildlife
            if (client?.playerName == game.userList[game.currentUser].name) {
                updateConnectionState(ConnectionState.SELECTING)



                onAllRefreshables { refreshAfterPlaceWildLifeToken(wildlife, habitatTile) }
            }
        }
    }

    /**
     * Updates the game configuration with the received players and score cards.
     *
     * @param players List of players participating in the game.
     * @param scoreCards List indicating the available score cards.
     */
    fun onGameConfigReceived(players: List<User>, scoreCards: List<Boolean>) {
        val copiedPlayers = this.players.toList()

        this.players.clear()

        for (player in players) {
            this.players.add(copiedPlayers.first { it.name == player.name })
        }

        onAllRefreshables { refreshAfterGameConfigUpdated(players, scoreCards) }
    }

    /**
     * Sends the game configuration to connected players.
     *
     * @param players List of players participating in the game.
     * @param scoreCards List of available score cards.
     * @throws IllegalStateException If the connection state is not waiting for game initialization.
     */
    fun sendGameConfigMessage(players: List<User>, scoreCards: List<Boolean>) {

        check(connectionState in listOf(ConnectionState.WAITING_FOR_GUEST, ConnectionState.WAITING_FOR_INIT))
        { "not waiting for game init message. " }

        val gameConfigMessage = GameConfigMessage(
            players = players.map { it.name },
            scoringCards = scoreCards,
        )

        this.players.clear()
        this.players.addAll(players)

        client?.sendGameActionMessage(gameConfigMessage)
    }

    /**
     * Handles a player leaving the game and updates the player list.
     *
     * @param userName Name of the player who left the game.
     */
    fun onPlayerLeft(userName: String) {
        // Finde den Benutzer im aktuellen Spiel, falls er existiert
        val user = rootService.currentGame?.userList?.find { it.name == userName }

        // Benachrichtige alle Refreshables, damit die GUI reagieren kann
        if (user != null) {
            onAllRefreshables { refreshAfterUserLeft(user) }
        } else {
            onAllRefreshables { refreshAfterUserLeft(User(name = userName)) }
        }

        players.removeIf { it.name == userName }

    }

    /**
     * Sends a chat message to the connected players.
     *
     * @param text Content of the chat message.
     */
    fun sendChatMessage(text: String) {
        val message = ChatMessage(message = text)
        client?.sendGameActionMessage(message)
    }

    /**
     * Handles a received chat message and notifies all refreshable components.
     *
     * @param message Contains the received chat message content.
     */
    fun onChatMessageReceived(message: ChatMessage) {
        println("Nachricht erhalten: ${message.message}")
        onAllRefreshables { refreshAfterChatMessageReceived(message.message) }
    }

    /**
     * Sends a request to select a habitat tile and wildlife token for the current turn.
     *
     * @param wildlifeIndex Index of the selected wildlife token.
     * @param habitatIndex Index of the selected habitat tile.
     * @param usedNatureToken Whether a nature token is used to select different indices.
     * @throws IllegalStateException If the player is not currently selecting.
     * @throws IllegalArgumentException If different indices are selected without using a nature token.
     */
    fun sendSelectAction(wildlifeIndex: Int, habitatIndex: Int, usedNatureToken: Boolean) {
        check(connectionState == ConnectionState.SELECTING)
        { "not selecting" }

        require(wildlifeIndex == habitatIndex || usedNatureToken)
        { "need to use nature token" }


        val message = SelectMessage(
            wildlifeShopIndex = wildlifeIndex,
            habitatShopIndex = habitatIndex,
            usedNatureToken = usedNatureToken
        )
        client?.sendGameActionMessage(message)
        updateConnectionState(ConnectionState.PLACING)
    }

    /**
     * Handles a received selection message and updates the local game state.
     *
     * @param message Contains the selected habitat tile, wildlife token, and nature token usage.
     * @throws IllegalStateException If the game is not waiting for the player's turn.
     */
    fun onSelectReceived(message: SelectMessage) {
        check(connectionState == ConnectionState.WAITING_FOR_PLAYER_TURN)
        { "not waiting for player turn" }

        val game = rootService.currentGame ?: return


        if (message.usedNatureToken) {
            game.userList[game.currentUser].natureToken -= 1

            onAllRefreshables { refreshAfterNatureTokenUsed() }
        }

        onAllRefreshables {
            refreshAfterOpponentSelectedHabitatTile(message.habitatShopIndex)
            refreshAfterOpponentSelectedWildLifeToken(message.wildlifeShopIndex)
        }

        println("Gegner hat Habitat ${message.habitatShopIndex} und Wildlife ${message.wildlifeShopIndex} gewählt.")
    }

    /**
     * Contains constants required to connect to the game server.
     */
    companion object {
        /** Address of the BGW-Net server. */
        const val SERVER_ADDRESS = "sopra.cs.tu-dortmund.de:80/bgw-net/connect"

        /** Identifier of the Cascadia game on the server. */
        const val GAME_ID = "Cascadia"
    }


}
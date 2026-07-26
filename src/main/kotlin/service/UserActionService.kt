package service

import entity.HabitatTile
import entity.*
import entity.action.ActionSelection
import entity.action.CollectionState
import entity.action.DisplayState
import entity.action.UserStateChange
import tools.aqua.bgw.util.Stack
import java.io.File

/**
 * Service layer class that handles user actions such as placing habitat tiles,
 * placing wildlife tokens, rotating tiles, swapping wildlife tokens and using
 * nature tokens.
 *
 * @param rootService The [RootService] instace to access the other service
 * methods and entity layer.
 */

class UserActionService(val rootService: RootService) : AbstractRefreshingService() {
    private var stateBeforePause: GameState? = null

    /**
     * Allows setting the speed at which bot moves and animations are executed during the game.
     *
     * @param speed The desired simulation speed, must be greater than 0.
     *
     * Preconditions:
     * - A current game must exist (`rootService.currentGame` must not be `null`).
     *
     * Postconditions:
     * - The attribute `gamePlaySpeed` of the current game is set to `speed`.
     * - The rest of the game state remains unchanged.
     * - No refresh method is called.
     *
     * @return This method has no return value (`Unit`).
     *
     * @throws IllegalStateException If no current game exists.
     * @throws IllegalArgumentException If `speed` is less than or equal to 0.
     *
     * @sample setSimulationSpeed(3)
     */
    fun setSimulationSpeed(speed: Int) {
        val game = checkNotNull(rootService.currentGame) { "No active game." }
        require(speed > 0) { "Simulation speed must be greater than 0." }

        game.gamePlaySpeed = speed
    }

    /**
     * Allows the current player to use a nature token to exchange any number of displayed
     * wildlife tokens with new ones from the supply instead of choosing a predefined combination.
     * A nature token can also be used to exchange zero tokens, only to reshuffle the bag.
     *
     * @param indices List of indices of displayed wildlife tokens to be exchanged.
     *
     * Preconditions:
     * - The game must be started and in state `WAIT_FOR_TURN`.
     * - The current player must own at least one nature token.
     * - All indices must be valid.
     * - If `indices` contains exactly three identical wildlife types (triple swap),
     *      it must not have been used already this turn.
     *
     * Postconditions:
     * - For each index, the displayed wildlife token is replaced with a new one from the supply;
     *      the old token is returned to the supply.
     * - The supply is reshuffled, even if `indices` is empty.
     * - One nature token is consumed, and the shared supply is updated.
     * - A [SwapWildLifeToken] entry is recorded for each exchange.
     * - Overpopulation is checked again immediately after ([GameService.checkOverPopulation]).
     * - The action is stored in the undo history, and the redo history is cleared.
     * - The game state remains `WAIT_FOR_TURN`.
     * - [Refreshable.refreshAfterSwapWildLifeToken] is called.
     *
     * @return This method has no return value (`Unit`).
     *
     * @throws IllegalStateException If the game is not running, not in state `WAIT_FOR_TURN`,
     *      or the player has no nature token.
     * @throws IllegalArgumentException If indices are invalid or a triple swap is reused.
     *
     * @sample swapWildLifeToken(listOf(0, 2))
     */
    fun swapWildLifeToken(indices: List<Int>) {
        val game = rootService.currentGame ?: throw IllegalStateException("No active game match.")
        val currentPlayer = game.userList[game.currentUser]

        // Prevent out-of-bounds indices
        require(indices.all { it in 0..3 }) { "Invalid market slot index provided." }

        // Prevent duplicate index exploits (e.g., passing [0, 0, 0])
        require(indices.toSet().size == indices.size) { "Selected market indices must be unique." }

        val chosenTokens = indices.map {
            game.displayedWildLifeToken[it] ?: throw IllegalArgumentException("Market slot $it is empty.")
        }
        val areTokensIdentical = chosenTokens.map { it.type }.toSet().size == 1
        val isFreeSwap = indices.size == 3 && areTokensIdentical && !currentPlayer.hasSwappedThree

        if (isFreeSwap) {
            currentPlayer.hasSwappedThree = true // Lock the free swap usage for this turn
        } else {
            // Otherwise, it costs a Nature token.
            require(currentPlayer.natureToken > 0) { "Insufficient Nature Tokens to perform this swap." }
            currentPlayer.natureToken--
            game.currentAction.selection.usedNatureToken++
        }

        val tokensToReturn = mutableListOf<WildLifeToken>()
        // Process swaps and generate history objects
        indices.forEach { index ->
            val oldToken = game.displayedWildLifeToken[index] ?: return@forEach
            tokensToReturn.add(oldToken)

            if (game.wildLifeCollection.isEmpty()) {
                onAllRefreshables { refreshAfterSwapWildLifeToken() }
                return
            }

            // Draw a completely fresh token from the bag
            val newToken = game.wildLifeCollection.pop()
            game.displayedWildLifeToken[index] = newToken

            // Instantiate the audit entry tracking slot position and token transitions
            val swapEvent = SwapWildLifeToken(
                oldWildLifeToken = oldToken,
                newWildLifeToken = newToken,
                displayIndex = index
            )

            game.redoableHistory.clear()

            // Append audit event tracking structures to history systems
            game.currentAction.selection.swappedWildLifeTokens.add(swapEvent)
        }

        //Now it's safe to push old tokens back into the supply pool
        tokensToReturn.forEach { oldToken ->
            game.wildLifeCollection.push(oldToken)
        }

        // Safely reshuffle the custom BGW Stack pool utility
        val tempBagList = mutableListOf<WildLifeToken>()
        while (!game.wildLifeCollection.isEmpty()) {
            tempBagList.add(game.wildLifeCollection.pop())
        }
        tempBagList.shuffle()
        tempBagList.forEach { game.wildLifeCollection.push(it) }

        rootService.gameService.checkOverPopulation()
        onAllRefreshables { refreshAfterSwapWildLifeToken() }
    }

    /**
     * Places the previously selected habitat tile at the specified position on the board.
     *
     * @param habitatTile the habitat tile which is being placed onto the given coordinates.
     * @param posX X-coordinate of the target position.
     * @param posY Y-coordinate of the target position.
     *
     * Preconditions:
     * - The game must be started and in state `WAIT_FOR_MOVE`.
     * - A habitat tile must have been selected.
     * - The position `(posX, posY)` must be free and adjacent to at least one already placed tile.
     *
     * Postconditions:
     * - The selected tile is placed on the board.
     * - The tile is removed from the display and replaced by a new one from the supply.
     * - The selected rotation is preserved.
     * - [Refreshable.refreshAfterPlaceHabitatTile] is called.
     *
     * @return This method has no return value (`Unit`).
     *
     * @throws IllegalStateException If conditions are not met.
     * @throws IllegalArgumentException If the position is invalid.
     *
     * @sample placeHabitatTile(2, 1)
     */
    fun placeHabitatTile(habitatTile: HabitatTile, posX: Int, posY: Int) {
        val game = checkNotNull(rootService.currentGame) { "No Game running" }
        val coordinate = Coordinate(posX, posY)

        // Get current User
        val currentUser = game.userList[game.currentUser]

        require(coordinate !in currentUser.board.placedHabitatTiles) { "Position already occupied" }
        require(isValidPosition(posX, posY, currentUser)) { "Position not adjacent to existing tile" }
        // Get chosen Tile from the current Action
        val action = checkNotNull(game.currentAction) { "No action found" }

        // Place on the Board
        currentUser.board.placedHabitatTiles[coordinate] = habitatTile

        action.selection.habitatTile = habitatTile.deepCopy()
        action.selection.habitatTileIndex =
            game.displayedHabitatTiles.entries.find { it.value == habitatTile }?.key

        val slot = game.displayedHabitatTiles.values.indexOf(habitatTile)

        // Network-Service Connection
        val isNetworkGame = game.userList.any { it.type == UserType.ONLINE_PLAYER }
        if (isNetworkGame && currentUser.type != UserType.ONLINE_PLAYER && slot != -1) {
            rootService.networkService.sendSelectHabitat(slot)
            rootService.networkService.sendPlaceAction(
                posX = posX,
                posY = posY,
                wildlifeCoords = null, // No token placed yet in this step
                rotation = habitatTile.rotation
            )
        }

        // Replacing habitatTile in the display with top one of the collection
        if (game.habitatTileCollection.isNotEmpty()) {
            game.displayedHabitatTiles[slot] = game.habitatTileCollection.pop()
        }

        game.state = GameState.WAIT_FOR_MOVE

        onAllRefreshables { refreshAfterPlaceHabitatTile(habitatTile, posX, posY) }

    }

    private fun isValidPosition(posX: Int, posY: Int, user: User): Boolean {

        val coordinate = Coordinate(posX, posY)

        return coordinate.getNeighbors().any { it in user.board.placedHabitatTiles }
    }

    /**
     * Places the selected wildlife token and completes the turn.
     *
     * @param wildLifeToken the token which is being placed onto the habitatTile.
     * @param habitatTile The tile where the token is placed.
     *
     * Preconditions:
     * - The game must be started and in state `WAIT_FOR_MOVE`.
     * - The tile must already be placed and empty.
     * - A wildlife token must have been selected.
     *
     * Postconditions:
     * - The token is placed.
     * - Keystone tiles grant a nature token.
     * - Turn is advanced via [GameService.nextUser].
     * - Refresh methods are called.
     *
     * @return Unit
     *
     * @throws IllegalStateException / IllegalArgumentException
     *
     * @sample placeWildLifeToken(tile)
     */
    fun placeWildLifeToken(
        wildLifeToken: WildLifeToken,
        habitatTile: HabitatTile
    ) {
        val game = checkNotNull(rootService.currentGame) {
            "No active game."
        }

        val currentAction = game.currentAction

        val slot = game.displayedWildLifeToken.values
            .indexOf(wildLifeToken)

        check(slot >= 0) {
            "The selected wildlife token is not in the display."
        }

        require(habitatTile.placedWildLifeToken == null) {
            "Tile already has a wildlife token placed."
        }

        require(
            habitatTile.availableWildLifeToken.contains(wildLifeToken.type)
        ) {
            "This tile does not support the selected wildlife token type."
        }

        // Record the selected wildlife token in the current action.
        currentAction.selection.wildlifeToken = wildLifeToken
        currentAction.selection.wildlifeTokenIndex = slot

        // Place the wildlife token onto the selected habitat tile.
        habitatTile.placedWildLifeToken = wildLifeToken

        // Placing wildlife on a Keystone tile grants a nature token.
        if (habitatTile.keyStone && game.natureToken > 0) {
            val currentUser = game.userList[game.currentUser]

            currentUser.natureToken++
            game.natureToken--
        }

        // Network-Service Connection
        val isNetworkGame = game.userList.any { it.type == UserType.ONLINE_PLAYER }
        val currentUser = game.userList[game.currentUser]
        if (isNetworkGame && currentUser.type != UserType.ONLINE_PLAYER) {
            val coord = currentUser.board.placedHabitatTiles.entries.find { it.value == habitatTile }?.key
            if (coord != null) {
                rootService.networkService.sendSelectWildlife(slot)
                rootService.networkService.sendPlaceAction(
                    posX = coord.q,
                    posY = coord.r,
                    wildlifeCoords = Pair(coord.q, coord.r), // Nested on the tile hex location
                    rotation = habitatTile.rotation
                )
            }
        }

        // Replace the selected wildlife token in the display.
        if (game.wildLifeCollection.isNotEmpty()) {
            val topMostToken = game.wildLifeCollection.pop()
            game.displayedWildLifeToken[slot] = topMostToken
        } else {
            // Avoid leaving the already placed token in the display.
            game.displayedWildLifeToken.remove(slot)
        }

        game.state = GameState.WAIT_FOR_MOVE

        /*
         * First refresh the GUI for the token that was just placed.
         *
         * This must happen before checkOverPopulation(), because the GUI
         * still needs to find the old wildlife token in its visual map.
         */
        onAllRefreshables {
            refreshAfterPlaceWildLifeToken(
                wildLifeToken,
                habitatTile
            )
        }

        /*
         * After the placed token has been removed from the GUI mapping,
         * check whether the newly filled display contains four identical
         * wildlife tokens.
         */
        if (game.state != GameState.END) {
            rootService.gameService.checkOverPopulation()
        }

        /*
         * checkOverPopulation() may modify the wildlife collection,
         * so save the final collection only after that check.
         */
        currentAction.collections.newWildLifeCollection =
            Stack(game.wildLifeCollection.peekAll())
    }

    /**
     * Uses a natural token if the selected habitat tile and wildlife token slots differ.
     *
     * A natural token is required when a player chooses a wildlife token from a
     * different slot than the corresponding habitat tile slot. If this condition
     * is met, the current player's number of natural tokens is reduced by one.
     *
     * @param habitatTileSlot The slot index of the selected habitat tile.
     * @param wildLifeTokenSlot The slot index of the selected wildlife token.
     * @throws IllegalStateException If no game is currently running.
     */
    fun useNaturalTokenIfNeeded(habitatTileSlot: Int, wildLifeTokenSlot: Int) {
        val game = checkNotNull(rootService.currentGame) { "No Game running" }
        val currentPlayer = game.userList[game.currentUser]

        if (habitatTileSlot != wildLifeTokenSlot) {
            currentPlayer.natureToken--

            // Network-Service Connection
            val isNetworkGame = game.userList.any { it.type == UserType.ONLINE_PLAYER }
            if (isNetworkGame && currentPlayer.type != UserType.ONLINE_PLAYER) {
                rootService.networkService.sendUseNatureToken()
            }
        }
    }

    /**
     * Rotates the currently selected habitat tile.
     *
     * @param habitatTile The tile which is being rotated.
     *
     * Preconditions:
     * - The game must be started and in state `WAIT_FOR_MOVE`.
     *
     * Postconditions:
     * - The rotation is increased by one step.
     * - [Refreshable.refreshAfterRotateHabitatTile] is called.
     */
    fun rotateHabitatTile(habitatTile: HabitatTile) {

        //Check if game is running
        val game = checkNotNull(rootService.currentGame) { "No Game running" }

        // update the rotation
        habitatTile.rotation = (habitatTile.rotation + 1) % 6

        // Network-Service Connection
        val currentUser = game.userList[game.currentUser]
        val isNetworkGame = game.userList.any { it.type == UserType.ONLINE_PLAYER }
        if (isNetworkGame && currentUser.type != UserType.ONLINE_PLAYER) {
            rootService.networkService.sendRotation(habitatTile.rotation)
        }

        // inform the Gui
        onAllRefreshables { refreshAfterRotateHabitatTile(habitatTile) }


    }


    /**
     * A lightweight tracking context used to process save file lines
     * and maintain cursor pointer progression cleanly across helper scopes.
     */
    private class LoadContext(val lines: List<String>) {
        var i = 0
        fun next(): String = lines[i++].substringAfter("=")
    }

    /**
     * Loads a previously saved game from the given file and restores the
     * complete game including undo/redo history, so that undo and redo work
     * exactly as if the game had never been paused.
     *
     * @param filePath Path to the file containing the saved game.
     * Must be a non-empty string pointing to an existing, readable file.
     *
     * Preconditions:
     * - The file at `filePath` must exist and contain a valid format created by [saveGame].
     *
     * Postconditions:
     * - A new game is reconstructed from the saved data, including full undo and redo history.
     * - `rootService.currentGame` is set to the loaded game.
     * - [Refreshable.refreshAfterLoadGame] is called.
     *
     * @return The loaded game as [CascadiaGame].
     *
     * @throws IllegalArgumentException If `filePath` is empty.
     * @throws IllegalStateException If the file does not exist, cannot be read,
     * or does not contain a valid save format.
     *
     * @sample loadGame("saves/game1.json")
     */
    fun loadGame(filePath: String): CascadiaGame {
        if (filePath.isBlank()) throw IllegalArgumentException("File path cannot be blank.")

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw IllegalStateException("The save file at '$filePath' does not exist or is unreadable.")
        }

        val ctx = LoadContext(file.readLines())

        val currentUser = ctx.next().toInt()
        val natureToken = ctx.next().toInt()
        val savedState = GameState.valueOf(ctx.next())

        /**
         * for save game to run the bot
         */
        val state = if (savedState == GameState.PAUSE) {
            GameState.WAIT_FOR_TURN
        } else {
            savedState
        }
        val gamePlaySpeed = ctx.next().toInt()

        val userCount = ctx.next().toInt()
        val userList = mutableListOf<User>()
        repeat(userCount) { userList.add(loadUser(ctx)) }

        val scoringCardCount = ctx.next().toInt()
        val scoringCards = mutableListOf<ScoringCard>()
        repeat(scoringCardCount) {
            val parts = ctx.next().split(",")
            scoringCards.add(ScoringCard(parts[1].toBoolean(), WildLifeTokenType.valueOf(parts[0])))
        }

        val displayedTokens = loadMarketTokens(ctx)
        val displayedTiles = loadMarketTiles(ctx)

        val wildLifeBag = loadTokenStack(ctx)
        val habitatTileBag = loadTileStack(ctx)

        val undoCount = ctx.next().toInt()
        val undoHistory = mutableListOf<Action>()
        repeat(undoCount) { undoHistory.add(loadAction(ctx)) }

        val redoCount = ctx.next().toInt()
        val redoHistory = mutableListOf<Action>()
        repeat(redoCount) { redoHistory.add(loadAction(ctx)) }

        val loadedGame = CascadiaGame(
            currentUser = currentUser,
            userList = userList,
            scoringCards = scoringCards,
            displayedWildLifeToken = displayedTokens,
            displayedHabitatTiles = displayedTiles,
        ).apply {
            this.natureToken = natureToken
            this.state = state
            this.gamePlaySpeed = gamePlaySpeed
            this.wildLifeCollection = wildLifeBag
            this.habitatTileCollection = habitatTileBag

            undoableHistory.addAll(undoHistory)
            redoableHistory.addAll(redoHistory)
        }

        rootService.currentGame = loadedGame
        refreshCurrentAction()

        onAllRefreshables { refreshAfterLoadGame() }

        return loadedGame
    }

    /**
     * Reads and parses a single [HabitatTile] from consecutive text lines.
     */
    private fun loadTile(ctx: LoadContext): HabitatTile {
        val tileParts = ctx.next().split(",")
        val rotation = tileParts[0].toInt()
        val keyStone = tileParts[1].toBoolean()

        val edges = ctx.next().split(",").map { HabitatTileType.valueOf(it) }.toMutableList()
        val available = ctx.next().let {
            if (it.isBlank()) listOf() else it.split(",").map { t -> WildLifeTokenType.valueOf(t) }
        }
        val placedStr = ctx.next()
        val placedToken = if (placedStr == "NONE") null else WildLifeToken(WildLifeTokenType.valueOf(placedStr))

        return HabitatTile(keyStone, rotation, edges, available, placedToken)
    }

    /**
     * Reads a player's core attributes and completely reconstructs their personal hex board layout.
     */
    private fun loadUser(ctx: LoadContext): User {
        val name = ctx.next()
        val type = UserType.valueOf(ctx.next())
        val userNatureToken = ctx.next().toInt()
        val hasSwappedThree = ctx.next().toBoolean()

        val board = UserBoard()
        val tileCount = ctx.next().toInt()
        repeat(tileCount) {
            val coordParts = ctx.next().split(",")
            val q = coordParts[0].toInt()
            val r = coordParts[1].toInt()
            board.placedHabitatTiles[Coordinate(q, r)] = loadTile(ctx)
        }
        return User(name, userNatureToken, type, ScorePad(), board, hasSwappedThree)
    }

    /**
     * Reconstructs the indexed map representation of wildlife tokens active in the market display.
     */
    private fun loadMarketTokens(ctx: LoadContext): MutableMap<Int, WildLifeToken> {
        val displayMap = mutableMapOf<Int, WildLifeToken>()
        val count = ctx.next().toInt()
        repeat(count) {
            val parts = ctx.next().split(",")
            displayMap[parts[0].toInt()] = WildLifeToken(WildLifeTokenType.valueOf(parts[1]))
        }
        return displayMap
    }

    /**
     * Reconstructs the indexed map representation of habitat tiles active in the market display.
     */
    private fun loadMarketTiles(ctx: LoadContext): MutableMap<Int, HabitatTile> {
        val displayMap = mutableMapOf<Int, HabitatTile>()
        val count = ctx.next().toInt()
        repeat(count) {
            val parts = ctx.next().split(",")
            displayMap[parts[0].toInt()] = loadTile(ctx)
        }
        return displayMap
    }

    /**
     * Reconstructs a wildlife token supply bag
     */
    private fun loadTokenStack(ctx: LoadContext): Stack<WildLifeToken> {
        val stack = Stack<WildLifeToken>()
        val count = ctx.next().toInt()
        if (count > 0) {
            ctx.next().split(",").map { WildLifeTokenType.valueOf(it) }.forEach { type ->
                stack.push(WildLifeToken(type))
            }
        }
        return stack
    }

    /**
     * Reconstructs a habitat tile supply bag, reversing parsing order to prevent LIFO inversion.
     */
    private fun loadTileStack(ctx: LoadContext): Stack<HabitatTile> {
        val stack = Stack<HabitatTile>()
        val count = ctx.next().toInt()
        repeat(count) { stack.push(loadTile(ctx)) }
        return stack
    }

    /**
     * Reconstructs a comprehensive timeline snapshot [Action] tracking point frames for undo/redo.
     */
    private fun loadAction(ctx: LoadContext): Action {
        val usedNatureToken = ctx.next().toInt()
        val selectedHabitatTileIndex = ctx.next().toInt()
        val selectedWildLifeTokenIndex = ctx.next().toInt()
        val selectedHabitatTile = loadTile(ctx)
        val selectedWildLifeToken = WildLifeToken(WildLifeTokenType.valueOf(ctx.next()))

        val oldUserState = loadUser(ctx)
        val newUserState = loadUser(ctx)

        val swappedCount = ctx.next().toInt()
        val swappedList = mutableListOf<SwapWildLifeToken>()
        repeat(swappedCount) {
            val parts = ctx.next().split(",")
            swappedList.add(
                SwapWildLifeToken(
                    oldWildLifeToken = WildLifeToken(WildLifeTokenType.valueOf(parts[0])),
                    newWildLifeToken = WildLifeToken(WildLifeTokenType.valueOf(parts[1])),
                    displayIndex = parts[2].toInt()
                )
            )
        }

        return Action(
            userStates = UserStateChange(
                oldState = oldUserState,
                newState = newUserState
            ),
            selection = ActionSelection(
                habitatTileIndex = selectedHabitatTileIndex,
                habitatTile = selectedHabitatTile,
                wildlifeTokenIndex = selectedWildLifeTokenIndex,
                wildlifeToken = selectedWildLifeToken,
                usedNatureToken = usedNatureToken,
                swappedWildLifeTokens = swappedList
            ),
            collections = CollectionState(
                oldWildLifeCollection = loadTokenStack(ctx),
                newWildLifeCollection = loadTokenStack(ctx),
                oldHabitatTileCollection = loadTileStack(ctx),
                newHabitatTileCollection = loadTileStack(ctx)
            ),
            displays = DisplayState(
                oldWildLifeDisplay = loadMarketTokens(ctx),
                newWildLifeDisplay = loadMarketTokens(ctx),
                oldHabitatDisplay = loadMarketTiles(ctx),
                newHabitatDisplay = loadMarketTiles(ctx)
            )
        )
    }

    /**
     * Saves the current game state including undo/redo history.
     *
     * @param filePath File path where the game is saved.
     *
     * Preconditions:
     * - Game must be running.
     *
     * Postconditions:
     * - Game is saved.
     * - State unchanged.
     * - [Refreshable.refreshAfterSaveGame] is called.
     *
     * @return File path as String.
     */
    fun saveGame(filePath: String): String {
        val game = checkNotNull(rootService.currentGame) { "No game running!" }
        val sb = StringBuilder()

        // state values
        sb.appendLine("currentUser=${game.currentUser}")
        sb.appendLine("natureToken=${game.natureToken}")
        // Saving is normally initiated from the pause screen. Persist the state
        // that can actually be resumed instead of PAUSE, which would leave a
        // loaded game permanently stopped. The fallback also keeps legacy flows
        // usable if no pre-pause state is available.
        val stateToSave = if (game.state == GameState.PAUSE) {
            stateBeforePause ?: GameState.WAIT_FOR_TURN
        } else {
            game.state
        }
        sb.appendLine("state=$stateToSave")
        sb.appendLine("gamePlaySpeed=${game.gamePlaySpeed}")

        sb.appendLine("userCount=${game.userList.size}")
        game.userList.forEach { writeUser(sb, it) }

        sb.appendLine("scoringCardCount=${game.scoringCards.size}")
        game.scoringCards.forEach { card -> sb.appendLine("scoringCard=${card.wildLife},${card.isTypeB}") }

        writeMarketTokens(sb, game.displayedWildLifeToken)
        writeMarketTiles(sb, game.displayedHabitatTiles)

        writeTokenStack(sb, game.wildLifeCollection)
        writeTileStack(sb, game.habitatTileCollection)

        sb.appendLine("undoHistoryCount=${game.undoableHistory.size}")
        game.undoableHistory.forEach { writeAction(sb, it) }

        sb.appendLine("redoHistoryCount=${game.redoableHistory.size}")
        game.redoableHistory.forEach { writeAction(sb, it) }

        File(filePath).writeText(sb.toString())
        return filePath
    }

    /**
     * Serializes a single [HabitatTile] object configuration into lines of plain text data strings.
     */
    private fun writeTile(sb: StringBuilder, tile: HabitatTile) {
        sb.appendLine("tileData=${tile.rotation},${tile.keyStone}")
        sb.appendLine("tileEdges=${tile.edges.joinToString(",")}")
        sb.appendLine("tileAvailable=${tile.availableWildLifeToken.joinToString(",")}")
        sb.appendLine("tilePlaced=${tile.placedWildLifeToken?.type ?: "NONE"}")
    }

    /**
     * Serializes a [User] profile, saving their metadata metrics alongside their entire map grid layout.
     */
    private fun writeUser(sb: StringBuilder, user: User) {
        sb.appendLine("userName=${user.name}")
        sb.appendLine("userType=${user.type}")
        sb.appendLine("userNatureToken=${user.natureToken}")
        sb.appendLine("userHasSwappedThree=${user.hasSwappedThree}")

        sb.appendLine("boardTileCount=${user.board.placedHabitatTiles.size}")
        user.board.placedHabitatTiles.forEach { (coord, tile) ->
            sb.appendLine("coord=${coord.q},${coord.r}")
            writeTile(sb, tile)
        }
    }

    /**
     * Converts active central wildlife market displays into flat index mapping text entries.
     */
    private fun writeMarketTokens(sb: StringBuilder, tokens: Map<Int, WildLifeToken>) {
        sb.appendLine("marketTokenCount=${tokens.size}")
        tokens.forEach { (index, token) -> sb.appendLine("tokenEntry=$index,${token.type}") }
    }

    /**
     * Converts active central habitat tile market displays into serial sequential text structures.
     */
    private fun writeMarketTiles(sb: StringBuilder, tiles: Map<Int, HabitatTile>) {
        sb.appendLine("marketTileCount=${tiles.size}")
        tiles.forEach { (index, tile) ->
            sb.appendLine("tileEntry=$index")
            writeTile(sb, tile)
        }
    }

    /**
     * Flattens a wildlife token stack into a plain comma-separated text data stream.
     */
    private fun writeTokenStack(sb: StringBuilder, stack: Stack<WildLifeToken>) {
        val tokens = stack.peekAll()
        sb.appendLine("tokenStackCount=${tokens.size}")
        if (tokens.isNotEmpty()) {
            sb.appendLine("tokenStackData=${tokens.joinToString(",") { it.type.name }}")
        }
    }

    /**
     * Flattens a habitat tile stack container sequentially line-by-line into text properties.
     */
    private fun writeTileStack(sb: StringBuilder, stack: Stack<HabitatTile>) {
        val tiles = stack.peekAll()
        sb.appendLine("tileStackCount=${tiles.size}")
        tiles.forEach { writeTile(sb, it) }
    }

    /**
     * Deconstructs an absolute timeline audit snapshot [Action] frame completely for deep system storage.
     */
    private fun writeAction(sb: StringBuilder, action: Action) {
        sb.appendLine("actionUsedNature=${action.selection.usedNatureToken}")
        sb.appendLine("actionTileIdx=${action.selection.habitatTileIndex}")
        sb.appendLine("actionTokenIdx=${action.selection.wildlifeTokenIndex}")
        writeTile(sb, action.selection.habitatTile)
        sb.appendLine("actionTokenSelected=${action.selection.wildlifeToken.type}")

        writeUser(sb, action.userStates.oldState)
        writeUser(sb, action.userStates.newState)

        sb.appendLine("actionSwappedCount=${action.selection.swappedWildLifeTokens.size}")
        action.selection.swappedWildLifeTokens.forEach { swap ->
            sb.appendLine("swapData=${swap.oldWildLifeToken.type},${swap.newWildLifeToken.type},${swap.displayIndex}")
        }

        writeTokenStack(sb, action.collections.oldWildLifeCollection)
        writeTokenStack(sb, action.collections.newWildLifeCollection)
        writeTileStack(sb, action.collections.oldHabitatTileCollection)
        writeTileStack(sb, action.collections.newHabitatTileCollection)

        writeMarketTokens(sb, action.displays.oldWildLifeDisplay)
        writeMarketTokens(sb, action.displays.newWildLifeDisplay)
        writeMarketTiles(sb, action.displays.oldHabitatDisplay)
        writeMarketTiles(sb, action.displays.newHabitatDisplay)
    }

    /**
     * Pauses the game.
     *
     * Preconditions:
     * - The game must be running.
     *
     * Postconditions:
     * - State changes to `PAUSE`.
     * - [Refreshable.refreshAfterPauseGame] is called.
     *
     */
    fun pauseGame() {
        //check if the game is running
        val game = checkNotNull(rootService.currentGame) { "No Game running" }

        if (rootService.botService.isExecutingBotAction) {
            return
        }

        // Access the network service to check if we are in an active network session
        val networkService = rootService.networkService

        // If the network service is not null and the state is not DISCONNECTED,
        // we assume it's a network game that cannot be paused.
        check(networkService.connectionState == ConnectionState.DISCONNECTED) {
            "Network games cannot be paused."
        }

        // Update the state to PAUSE
        stateBeforePause = game.state

        game.state = GameState.PAUSE

        // Notify UI to display the pause menu
        onAllRefreshables { refreshAfterPauseGame() }

    }


    /**
     * Continues a paused game.
     */
    fun continueGame() {
        val game = rootService.currentGame
            ?: throw IllegalStateException(
                "No active game session found."
            )

        check(game.state == GameState.PAUSE) {
            "The game is not paused."
        }

        game.state = stateBeforePause ?: GameState.WAIT_FOR_TURN

        stateBeforePause = null

        onAllRefreshables {
            refreshAfterContinueGame()
        }
    }

    private fun refreshCurrentAction() {
        val game = rootService.currentGame
        checkNotNull(game) { "No game currently running." }
        val currentUser = game.userList[game.currentUser]

        // Reset currentAction.
        val currentAction = ActionBuilder()

        // Applying current user state and collections.
        currentAction.userStates = UserStateChange(
            oldState = currentUser.deepCopy(),
            newState = currentUser.deepCopy()
        )

        currentAction.collections.oldWildLifeCollection =
            Stack(game.wildLifeCollection.peekAll())

        currentAction.collections.oldHabitatTileCollection =
            Stack(game.habitatTileCollection.peekAll().map { it.deepCopy() })

        currentAction.displays.oldHabitatDisplay =
            game.displayedHabitatTiles
                .mapValues { it.value.deepCopy() }
                .toMutableMap()

        currentAction.displays.oldWildLifeDisplay =
            game.displayedWildLifeToken.toMutableMap()

        game.currentAction = currentAction
    }

    /**
     * Undoes the last human action.
     *
     * @return true if successful, false otherwise.
     */
    fun undo(): Boolean {
        val game = rootService.currentGame
        checkNotNull(game) { "No game currently running." }

        // Network or pure bot mode → not allowed
        check(!game.userList.any { it.type == UserType.ONLINE_PLAYER }
                && game.userList.any { it.type == UserType.LOCAL_PLAYER })
        { "Undo is disabled in network or pure-bot games." }

        //Nothing to undo
        if (game.undoableHistory.isEmpty()) {
            onAllRefreshables { refreshAfterUndo(false) }
            return false
        }

        // First step back
        var action = game.undoableHistory.removeAt(game.undoableHistory.size - 1)
        game.redoableHistory.add(action)
        applyUndo(game, action)

        // Go back until it's another human player's turn
        while (game.undoableHistory.isNotEmpty() &&
            (game.userList[game.currentUser].type == UserType.RANDOM_BOT
                    || game.userList[game.currentUser].type == UserType.PROFESSIONAL_BOT)
        ) {

            action = game.undoableHistory.removeAt(game.undoableHistory.size - 1)
            game.redoableHistory.add(action)
            applyUndo(game, action)
        }

        refreshCurrentAction()

        game.state = GameState.WAIT_FOR_TURN
        onAllRefreshables { refreshAfterUndo(true) }
        return true
    }


    /**
     * Redoes a previously undone action.
     *
     * @return true if successful, false otherwise.
     */
    fun redo(): Boolean {
        val game = rootService.currentGame
        checkNotNull(game) { "No game currently running." }

        check(!game.userList.any { it.type == UserType.ONLINE_PLAYER }
                && game.userList.any { it.type == UserType.LOCAL_PLAYER })
        { "Redo is disabled in network or pure-bot games." }

        if (game.redoableHistory.isEmpty()) {
            onAllRefreshables { refreshAfterRedo(false) }
            return false
        }

        // First step forward
        var action = game.redoableHistory.removeAt(game.redoableHistory.size - 1)
        game.undoableHistory.add(action)
        applyRedo(game, action)

        while (game.redoableHistory.isNotEmpty() &&
            (game.userList[game.currentUser].type == UserType.RANDOM_BOT
                    || game.userList[game.currentUser].type == UserType.PROFESSIONAL_BOT)
        ) {

            action = game.redoableHistory.removeAt(game.redoableHistory.size - 1)
            game.undoableHistory.add(action)
            applyRedo(game, action)
        }

        refreshCurrentAction()

        game.state = GameState.WAIT_FOR_TURN
        onAllRefreshables { refreshAfterRedo(true) }
        return true
    }


    /**
     * Applies undo for one action: restores everything to the state before the action.
     */
    private fun applyUndo(game: CascadiaGame, action: Action) {
        applyUserState(game, action.userStates.oldState)

        game.wildLifeCollection = action.collections.oldWildLifeCollection
        game.habitatTileCollection = action.collections.oldHabitatTileCollection

        game.natureToken += action.selection.usedNatureToken

        game.displayedHabitatTiles = action.displays.oldHabitatDisplay
        game.displayedWildLifeToken = action.displays.oldWildLifeDisplay

        game.currentUser = (game.currentUser - 1 + game.userList.size) % game.userList.size

        game.userList[game.currentUser].hasSwappedThree = false
    }

    /**
     * Applies redo for one action: restores everything to the state after the action.
     */
    private fun applyRedo(game: CascadiaGame, action: Action) {
        applyUserState(game, action.userStates.newState)

        game.wildLifeCollection = action.collections.newWildLifeCollection
        game.habitatTileCollection = action.collections.newHabitatTileCollection

        game.natureToken -= action.selection.usedNatureToken

        game.displayedHabitatTiles = action.displays.newHabitatDisplay
        game.displayedWildLifeToken = action.displays.newWildLifeDisplay

        game.currentUser = (game.currentUser + 1) % game.userList.size

        game.userList[game.currentUser].hasSwappedThree = false

    }

    /**
     * Restores the mutable fields of the user in userList to match the snapshot state.
     */
    private fun applyUserState(game: CascadiaGame, user: User) {
        val index = game.userList.indexOfFirst { it.name == user.name }
        require(index >= 0) { "User '${user.name}' from action not found in current game." }
        val target = game.userList[index]

        // NatureToken
        target.natureToken = user.natureToken

        // Board
        target.board.placedHabitatTiles.clear()
        target.board.placedHabitatTiles.putAll(user.board.placedHabitatTiles)

        // ScorePad
        target.scorePad.pointsByWildLifeToken.clear()
        target.scorePad.pointsByWildLifeToken.putAll(user.scorePad.pointsByWildLifeToken)
        target.scorePad.pointsByHabitatTiles = user.scorePad.pointsByHabitatTiles
        target.scorePad.pointsByNatureToken = user.scorePad.pointsByNatureToken
        target.scorePad.bonusPoints = user.scorePad.bonusPoints
        target.scorePad.totalPoints = user.scorePad.totalPoints
    }

}


package service

import entity.*
import entity.action.CollectionState
import entity.action.DisplayStateBuilder
import entity.action.UserStateChange
import tools.aqua.bgw.util.Stack
import java.io.File
import kotlin.collections.set

/**
 * Service layer class that provides the logic for the actions and general game flow.
 *
 * @param rootService The [RootService] instance to access the other service methods and entity layer
 */

class GameService(val rootService: RootService) : AbstractRefreshingService() {

    /**
     * Initializes a new game with the given players and scoring cards,
     * sets up the habitat and wildlife supplies, and places the starting tile on each board.
     *
     * @param users List of participating players. Must contain 2 to 4 entries with unique names.
     * @param scoringCards The five scoring cards chosen for this game.
     *      Must contain exactly 5 cards, one per wildlife type.
     *
     * Preconditions:
     * - `users` must contain between 2 and 4 entries.
     * - `scoringCards` must contain exactly 5 cards, one per wildlife type.
     *
     * Postconditions:
     * - A new game is created and assigned to `rootService.currentGame`.
     * - Each user receives an empty board and scorepad.
     * - The wildlife supply is filled with 100 tokens via CSV import (`id;habitats;wildlife;keystone`),
     *      and the habitat tile supply is filled and shuffled.
     * - The starting tile (3 standard tiles arranged clockwise from the top, without rotation) is placed on each board.
     * - Displayed tiles and tokens are filled with the first available entries.
     * - Game state changes to `WAIT_FOR_TURN`.
     * - [Refreshable.refreshAfterGameStart] is called.
     *
     * @return This method has no return value (`Unit`).
     *
     * @throws IllegalArgumentException If constraints on users or scoring cards are violated.
     *
     * @sample startNewGame(listOf(anna, ben), scoringCards)
     */
    fun startNewGame(users: List<User>, scoringCards: List<ScoringCard>) {
        require(users.size in 2..4) { "Cascadia requires between 2 and 4 players." }
        require(scoringCards.size == 5) { "Exactly 5 scoring cards must be provided." }

        val wildLifeBag = createWildLifeBag()
        val tileBag = createHabitatTileBag(users.size)
        assignStarterTiles(users)

        val newGame = CascadiaGame(
            currentUser = 0,
            userList = users.toMutableList(),
            displayedWildLifeToken = createWildLifeDisplay(wildLifeBag),
            displayedHabitatTiles = createHabitatDisplay(tileBag),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.WAIT_FOR_TURN
            habitatTileCollection = tileBag
            wildLifeCollection = wildLifeBag
        }

        rootService.currentGame = newGame

        checkOverPopulation()
        initializeCurrentAction(newGame)

        onAllRefreshables { refreshAfterGameStart() }
    }

    private fun createWildLifeBag(): Stack<WildLifeToken> {
        val tokens = WildLifeTokenType.entries.flatMap { type ->
            List(20) { WildLifeToken(type) }
        }.shuffled()

        return Stack<WildLifeToken>().apply {
            tokens.forEach { push(it) }
        }
    }

    private fun createHabitatTileBag(playerCount: Int): Stack<HabitatTile> {
        val tiles = parseTilesFromCSV(loadCsvLines("csv/tiles.csv"))
            .shuffled()

        return Stack<HabitatTile>().apply {
            tiles.take(20 * playerCount + 3).forEach { push(it) }
        }
    }

    private fun assignStarterTiles(users: List<User>) {
        val starterBlocks = parseStarterTilesFromCSV(loadCsvLines("csv/start_tiles.csv"))
        val availableIds = starterBlocks.keys.shuffled()

        users.forEachIndexed { index, user ->
            val tiles = starterBlocks[availableIds[index]]
                ?: error("Starter block not found")

            setupPlayerStarterTiles(user.board, tiles)
        }
    }

    private fun createWildLifeDisplay(
        bag: Stack<WildLifeToken>
    ): MutableMap<Int, WildLifeToken> =
        MutableList(4) { bag.pop() }
            .withIndex()
            .associate { it.index to it.value }
            .toMutableMap()

    private fun createHabitatDisplay(
        bag: Stack<HabitatTile>
    ): MutableMap<Int, HabitatTile> =
        MutableList(4) { bag.pop() }
            .withIndex()
            .associate { it.index to it.value }
            .toMutableMap()

    private fun initializeCurrentAction(game: CascadiaGame) {
        game.currentAction.userStates = UserStateChange(
            oldState = game.userList[game.currentUser].deepCopy(),
            newState = game.userList[game.currentUser].deepCopy()
        )

        game.currentAction.collections = CollectionState(
            oldWildLifeCollection = Stack(game.wildLifeCollection.peekAll()),
            oldHabitatTileCollection =
                Stack(game.habitatTileCollection.peekAll().map { it.deepCopy() })
        )

        game.currentAction.displays = DisplayStateBuilder(
            oldWildLifeDisplay = game.displayedWildLifeToken.toMutableMap(),
            oldHabitatDisplay = game.displayedHabitatTiles
                .mapValues { it.value.deepCopy() }
                .toMutableMap()
        )
    }

    /**
     * Places the 3 pre-joined individual starter tiles from an assigned block
     * onto a player's layout using your triangle coordinate offsets.
     */
    private fun setupPlayerStarterTiles(board: UserBoard, starterTiles: List<HabitatTile>) {
        board.placedHabitatTiles.clear()
        require(starterTiles.size == 3) { "A starter block must consist of exactly 3 tiles." }

        // 1. Top Hex of the cluster (ID ending in 0, e.g., 10, 20)
        board.placedHabitatTiles[Coordinate(0, 0)] = starterTiles[0]

        // 2. Bottom-Right Hex of the cluster (ID ending in 1, e.g., 11, 21)
        board.placedHabitatTiles[Coordinate(0, 1)] = starterTiles[1]

        // 3. Bottom-Left Hex of the cluster (ID ending in 2, e.g., 12, 22)
        board.placedHabitatTiles[Coordinate(-1, 1)] = starterTiles[2]
    }

    /**
     * Helper to load csv files reliably from resources
     */
    private fun loadCsvLines(fileName: String): List<String> {

        // 1. Try classpath loading
        val resourceStream = this::class.java.getResourceAsStream("/$fileName")
            ?: Thread.currentThread().contextClassLoader.getResourceAsStream(fileName)

        if (resourceStream != null) {
            return resourceStream.bufferedReader().readLines()
        }

        // Fallback: Look directly at the hard drive path
        val potentialPaths = listOf(
            "src/main/resources/$fileName",
            "Projekt2/src/main/resources/$fileName",
            "projekt2/src/main/resources/$fileName"
        )

        for (path in potentialPaths) {
            val file = File(path)
            if (file.exists()) return file.readLines()
        }

        throw java.io.FileNotFoundException("Could not locate '$fileName' via classpath or hard-drive fallback.")
    }

    // Helper method for startNewGame
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

    // Helper method for startNewGame
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

    // Helper method for startNewGame
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

    // Helper method for startNewGame
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
     * Switches the current player to the next player in turn order and checks
     * whether a habitat tile is still available for refilling; if not, the game ends immediately.
     *
     * Preconditions:
     * - The game must be started.
     *
     * Postconditions:
     * - If no habitat tile is available for refilling, the game ends (`END`) and [evaluateScores] is called.
     * - Otherwise, `currentUser` is updated to the next player and state becomes `WAIT_FOR_TURN`.
     * - [Refreshable.refreshAfterTurn] is called.
     *
     * @return This method has no return value (`Unit`).
     *
     * @throws IllegalStateException If the game is not running.
     *
     * @sample nextUser()
     */
    fun nextUser() {
        val game = checkNotNull(rootService.currentGame) { "No active game." }
        val previousUser = game.userList[game.currentUser]
        var currentAction = game.currentAction

        // As turn is over, current user state and collections are final and can be added to currentAction.
        currentAction.userStates = currentAction.userStates?.copy(
            newState = previousUser.deepCopy()
        ) ?: UserStateChange(
            oldState = previousUser.deepCopy(),
            newState = previousUser.deepCopy()
        )

        currentAction.collections.newWildLifeCollection =
            Stack(game.wildLifeCollection.peekAll())

        currentAction.collections.newHabitatTileCollection =
            Stack(game.habitatTileCollection.peekAll().map { it.deepCopy() })

        currentAction.displays.newHabitatDisplay =
            game.displayedHabitatTiles
                .mapValues { it.value.deepCopy() }
                .toMutableMap()

        currentAction.displays.newWildLifeDisplay =
            game.displayedWildLifeToken.toMutableMap()

        // Action should be final, so it can be added to history.
        game.undoableHistory.add(currentAction.build())

        // Checking if game end
        if (game.habitatTileCollection.size == 0) {
            game.state = GameState.END
            evaluateScores()
            onAllRefreshables { refreshAfterGameEnd() }
            return
        }

        // Refresh the state.
        game.currentUser = (game.currentUser + 1) % game.userList.size
        game.state = GameState.WAIT_FOR_TURN

        val nextUser = game.userList[game.currentUser]

        nextUser.hasSwappedThree = false

        // Reset currentAction.
        currentAction = ActionBuilder()

        // Applying current user state and collections for the next action.
        currentAction.userStates = UserStateChange(
            oldState = nextUser.deepCopy(),
            newState = nextUser.deepCopy()
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

        game.redoableHistory.clear()

        onAllRefreshables { refreshAfterTurn() }
    }

    /**
     * Calculates the final score of all players at the end of the game based on
     * scoring cards, habitat tiles, corridor bonus, and remaining nature tokens.
     *
     * Preconditions:
     * - The game must be started.
     * - The state must be `END`.
     *
     * Postconditions:
     * - Points per wildlife type are calculated based on scoring cards.
     * - Salmon chains are split if needed.
     * - Habitat groups are evaluated (largest group per type).
     * - Corridor bonus assigned based on player count.
     * - 1 point per remaining nature token is awarded.
     * - Total scores are computed.
     * - Results stored in scorepads.
     * - Winner(s) determined (tie-breaking rules applied).
     * - [Refreshable.refreshAfterGameEnd] is called.
     *
     * @return This method has no return value (`Unit`).
     *
     * @throws IllegalStateException If not in `END` state.
     *
     */
    fun evaluateScores() {
        val game = checkNotNull(rootService.currentGame) {
            "No game running"
        }
        check(game.state == GameState.END) { "Game is not in END state" }
        for (user in game.userList) {
            user.scorePad.pointsByWildLifeToken.clear()
            user.scorePad.pointsByHabitatTiles.clear()
            user.scorePad.pointsByNatureToken = 0
            user.scorePad.bonusPoints = 0
            user.scorePad.totalPoints = 0
        }


        for (user in game.userList) {
            calculateWildLifePoints(user, game.scoringCards)
        }

        calculateHabitatPoints(game)

        for (user in game.userList) {
            calculateNatureTokenPoints(user)
            calculateTotalPoints(user)
        }

        onAllRefreshables { refreshAfterGameEnd() }

    }

    /**
     * Checks displayed wildlife tokens for overpopulation (3 or 4 identical types)
     * and replaces affected tokens according to the rules.
     *
     * Preconditions:
     * - The game must be started.
     *
     * Postconditions:
     * - Overpopulated indices are identified.
     * - Affected tokens are temporarily removed and replaced.
     * - Removed tokens are returned to supply afterward.
     * - If supply is empty, the game ends.
     * - Display is refilled.
     * - Check repeats if necessary.
     * - [Refreshable.refreshAfterCheckOverPopulation] is called.
     *
     * @return List of affected indices (empty if none).
     *
     * @throws IllegalStateException If the game is not running.
     *
     * @sample checkOverPopulation()
     */
    fun checkOverPopulation(): List<Int> {
        val game = rootService.currentGame
        checkNotNull(game) { "No game currently running." }
        val overpopulatedType = findOverpopulatedType(game.displayedWildLifeToken)
        // No overpopulation → no swap, no shuffle
        if (overpopulatedType == null) {
            onAllRefreshables { refreshAfterCheckOverPopulation(false) }
            return emptyList()
        }
        //Collect all indices with the overpopulated type
        val affectedIndices = mutableListOf<Int>()
        for ((index, token) in game.displayedWildLifeToken) {
            if (token.type == overpopulatedType) {
                affectedIndices.add(index)
            }
        }
        affectedIndices.sort()
        //Set affected tokens aside
        val setAside = mutableListOf<WildLifeToken>()
        for (index in affectedIndices) {
            setAside.add(checkNotNull(game.displayedWildLifeToken[index]) {
                "Expected a wildlife token at index $index"
            })
        }
        //Refill display with new tokens from the supply
        val gameEnded = refillDisplay(game, affectedIndices, setAside)
        if (gameEnded) {
            onAllRefreshables { refreshAfterCheckOverPopulation(true) }
            return affectedIndices
        }
        //Return set-aside tokens to the supply and shuffle
        for (token in setAside) {
            game.wildLifeCollection.push(token)
        }
        game.wildLifeCollection.shuffle()
        onAllRefreshables { refreshAfterCheckOverPopulation(true) }
        //Recursive check
        val furtherIndices = checkOverPopulation()
        //Merge all affected indices, remove duplicates
        val allIndices = mutableListOf<Int>()
        for (index in affectedIndices) {
            if (index !in allIndices) {
                allIndices.add(index)
            }
        }
        for (index in furtherIndices) {
            if (index !in allIndices) {
                allIndices.add(index)
            }
        }
        return allIndices
    }

    /**
     * Refills the display slots with new tokens from the supply.
     * If supply runs out, returns set-aside tokens to supply, ends the game, and returns true.
     */
    private fun refillDisplay(
        game: CascadiaGame, indices: List<Int>, setAside: List<WildLifeToken>
    ): Boolean {
        for (index in indices) {
            if (game.wildLifeCollection.isEmpty()) {
                for (token in setAside) {
                    game.wildLifeCollection.push(token)
                }
                game.state = GameState.END
                onAllRefreshables { refreshAfterCheckOverPopulation(true) }
                return true
            }
            game.displayedWildLifeToken[index] = game.wildLifeCollection.pop()
        }
        return false
    }

    /**
     * Counts the types in the display and returns the type with exactly 4 occurrences, or null.
     */
    private fun findOverpopulatedType(displayed: Map<Int, WildLifeToken>): WildLifeTokenType? {
        //Count how often each wildlife type appears in the display
        val counts = mutableMapOf<WildLifeTokenType, Int>()
        for (token in displayed.values) {
            counts[token.type] = (counts[token.type] ?: 0) + 1
        }

        // Find a wildlife type with exactly 4 occurrences
        for ((type, count) in counts) {
            if (count == 4) {
                return type
            }
        }
        return null
    }


    // Helper Functions:

    /**
     * Returns a map of each user to the size of their largest contiguous group of [habitatType]
     *
     * @param users The users to evaluate
     * @param habitatType The habitat tile type to count
     * @return largest group size of given tile for given user
     */
    private fun calculateBaseHabitatPoints(users: List<User>, habitatType: HabitatTileType): Map<User, Int> {
        val sizes = mutableMapOf<User, Int>()
        for (user in users) {
            sizes[user] = getLargestHabitatGroup(user, habitatType)
        }

        return sizes

    }

    /**
     * Adds habitat bonus points to the given users' scorePads based on largest group sizes
     *
     * Modifies bonusPoints in place.
     * - 2 players: max gets +2, tie gets +1 each
     * - 3 players: sole max gets +3, sole second gets +1, tie for max gets +2 each, 3+ tie gets +1 each
     */
    private fun getUsersWithSize(users: List<User>, sizes: Map<User, Int>, targetSize: Int): List<User> =
        users.filter { sizes[it] == targetSize }

    private fun addSecondPlaceBonus(users: List<User>, sizes: Map<User, Int>, maxSize: Int) {
        val secondSize = sizes.values.filter { it < maxSize }.maxOrNull() ?: 0
        if (secondSize == 0) return
        val usersWithSecond = getUsersWithSize(users, sizes, secondSize)
        if (usersWithSecond.size == 1) usersWithSecond[0].scorePad.bonusPoints += 1
    }

    private fun addTwoPlayerBonus(usersWithMax: List<User>) {
        if (usersWithMax.size == 1) {
            usersWithMax[0].scorePad.bonusPoints += 2
        } else {
            usersWithMax.forEach { it.scorePad.bonusPoints += 1 }
        }
    }

    private fun addMultiPlayerBonus(users: List<User>, sizes: Map<User, Int>, maxSize: Int, usersWithMax: List<User>) {
        when (usersWithMax.size) {
            1 -> {
                usersWithMax[0].scorePad.bonusPoints += 3
                addSecondPlaceBonus(users, sizes, maxSize)
            }

            2 -> usersWithMax.forEach { it.scorePad.bonusPoints += 2 }
            else -> usersWithMax.forEach { it.scorePad.bonusPoints += 1 }
        }
    }

    private fun addHabitatBonus(users: List<User>, sizes: Map<User, Int>) {
        val maxSize = sizes.values.maxOrNull() ?: 0
        if (maxSize == 0) return
        val usersWithMax = getUsersWithSize(users, sizes, maxSize)
        if (users.size == 2) {
            addTwoPlayerBonus(usersWithMax)
            return
        }
        addMultiPlayerBonus(users, sizes, maxSize, usersWithMax)
    }


    private fun calculateHabitatPoints(game: CascadiaGame) {
        val habitatTypes = listOf(
            HabitatTileType.MOUNTAINS,
            HabitatTileType.FORESTS,
            HabitatTileType.PRAIRIES,
            HabitatTileType.WETLANDS,
            HabitatTileType.RIVERS
        )

        for (habitatType in habitatTypes) {
            val sizes = calculateBaseHabitatPoints(game.userList, habitatType)

            for (user in game.userList) {
                val currentPoints = user.scorePad.pointsByHabitatTiles[habitatType] ?: 0
                val pointsToAdd = sizes[user] ?: 0
                user.scorePad.pointsByHabitatTiles[habitatType] = currentPoints + pointsToAdd
            }

            addHabitatBonus(game.userList, sizes)
        }

    }


    /**
     * Sets user's nature token points directly
     */
    private fun calculateNatureTokenPoints(user: User) {
        user.scorePad.pointsByNatureToken = user.natureToken
    }

    /**
     * sums all score components (habitat, nature, bonus, wildlife) into `totalPoints`
     */
    private fun calculateTotalPoints(user: User) {
        var total = 0
        val habitatTypes = listOf(
            HabitatTileType.MOUNTAINS,
            HabitatTileType.FORESTS,
            HabitatTileType.PRAIRIES,
            HabitatTileType.WETLANDS,
            HabitatTileType.RIVERS
        )
        for (habitatType in habitatTypes) {
            val habitatPoints = user.scorePad.pointsByHabitatTiles[habitatType] ?: 0
            total += habitatPoints
        }
        total += user.scorePad.pointsByNatureToken
        total += user.scorePad.bonusPoints

        for (points in user.scorePad.pointsByWildLifeToken.values) {
            total += points
        }
        user.scorePad.totalPoints = total
    }


    /**
     * Computes wildlife points for ach scoring card and stores in `pointsByWildLifeToken`
     */
    private fun calculateWildLifePoints(user: User, scoringCards: List<ScoringCard>) {
        for (card in scoringCards) {
            val points = when (card.wildLife) {
                WildLifeTokenType.BEAR -> scoreBear(user, card.isTypeB)
                WildLifeTokenType.ELK -> scoreElk(user, card.isTypeB)
                WildLifeTokenType.SALMON -> scoreSalmon(user, card.isTypeB)
                WildLifeTokenType.HAWK -> scoreHawk(user, card.isTypeB)
                WildLifeTokenType.FOX -> scoreFox(user, card.isTypeB)
            }

            user.scorePad.pointsByWildLifeToken[card] = points
        }
    }


    /**
     * Returns th size of the largest contiguous group of habitat tiles of given type
     * for a user
     */
    private fun getLargestHabitatGroup(user: User, habitatType: HabitatTileType): Int {
        val positions = mutableListOf<Coordinate>()
        for (entry in user.board.placedHabitatTiles) {
            if (habitatType in entry.value.edges) {
                positions.add(entry.key)
            }
        }

        val groups = getGroups(positions)
        var largest = 0
        for (group in groups) {
            if (group.size > largest) largest = group.size
        }
        return largest
    }


    /**
     * Scores bears for a user based on scoring card (A or B)
     * -Type A: points by number of adjacent pairs (0->0, 1->4, 2->11, 3->19, 4+->27)
     * -Type B: +10 for each group of exactly 3 bears
     */
    private fun scoreBear(user: User, isTypeB: Boolean): Int {

        val allBears = getAnimalPositions(user, WildLifeTokenType.BEAR)
        val groups = getGroups(allBears)

        var total = 0

        if (isTypeB) {
            for (group in groups) {
                if (group.size == 3) total += 10
            }

        } else {
            val tableA = mapOf(0 to 0, 1 to 4, 2 to 11, 3 to 19)
            var pairCount = 0
            for (group in groups) {
                if (group.size == 2) pairCount++
            }
            total = tableA[pairCount] ?: 27
        }

        return total
    }

    /**
     * Calculates the elk score for the given user based on scoring card
     * @param user The user whose board we want to evaluate
     * @param isTypeB whether to use Type B or Type A scoring cards
     * @return Total elk score
     */
    private fun scoreElk(user: User, isTypeB: Boolean): Int {
        val allElk = getAnimalPositions(user, WildLifeTokenType.ELK)
        val groups = getGroups(allElk)
        var total = 0

        for (group in groups) {
            total += findBestElkPartition(group, isTypeB)
        }
        return total
    }

    /**
     * calculates the best possible score for a straight line of elk
     * For lines larger than 4, recursively splits into groups of 4
     *
     * @param size The number of elk in the line
     * @return Best possible points for the line size
     */
    private fun bestElkLineScore(size: Int): Int {/* if (size == 0) return 0
        if (size == 1) return 2
        if (size == 2) return 5
        if (size == 3) return 9
        if (size == 4) return 13
        return 13 + bestElkLineScore(size - 4)
        */
        val table = intArrayOf(0, 2, 5, 9, 13)
        return table[size]
        // If needed for 4+ Elks
        // return if (size < table.size) table[size] else 13 + bestElkLineScore(size - 4)
    }

    /**
     * checks if a group of elk coordinates form a valid type A
     * scoring shape
     * A valid shape is a straight lune connecting flat sides
     *
     * @param group The group of Elk coordinates to check
     * @return True if the group forms a valid straight line
     */
    private fun isValidElkGroupTypeA(group: Set<Coordinate>): Boolean {
        if (group.size <= 1) return true

        val list = group.toList()

        val directions = listOf(
            Coordinate(1, 0),
            Coordinate(-1, 0),
            Coordinate(0, 1),
            Coordinate(0, -1),
            Coordinate(1, -1),
            Coordinate(-1, 1)
        )

        for (dir in directions) {
            var start = list[0]
            var prev = Coordinate(start.q - dir.q, start.r - dir.r)
            while (prev in group) {
                start = prev
                prev = Coordinate(start.q - dir.q, start.r - dir.r)
            }

            var count = 0
            var current = start
            while (current in group) {
                count++
                current = Coordinate(current.q + dir.q, current.r + dir.r)
            }

            if (count == group.size) return true

        }
        return false
    }

    /**
     * checks if a group of elk coords for a valid Type B scoring pattern
     * rotatable in any orientation
     * 1 elk: always valid
     * 2 elk: must be adjacent
     * 3 elk: all three elk must have exactly 2 elk neighbors in the group
     * 4 elk: must form a diamond shape - two elk with 2 neighbors, two
     * with 3 neighbors
     *
     * @param group The group of elk coordinates to check
     * @return True if the group forms a valid Type B pattern
     */
    private fun isValidElkGroupTypeB(group: Set<Coordinate>): Boolean {
        val list = group.toList()
        return when (group.size) {
            1 -> true
            2 -> areAdjacent(list[0], list[1])
            3 -> group.all { pos -> pos.getNeighbors().count { it in group } == 2 }
            4 -> {
                val neighborCounts = group.map { pos -> pos.getNeighbors().count { it in group } }.sorted()
                neighborCounts == listOf(2, 2, 3, 3)
            }

            else -> false
        }
    }


    /**
     * Returns all possible combinations of give size from a list of coordinates
     * Uses recursion, either include the first element ot not
     *
     * @param list The list to generate combinations from
     * @param size The size of each combination
     * @return List of all possible combinations
     */
    private fun getCombinations(list: List<Coordinate>, size: Int): List<List<Coordinate>> {
        if (size == 0) return listOf(emptyList())
        if (list.isEmpty()) return emptyList()

        val first = list.first()
        val rest = list.drop(1)

        val withFirst = getCombinations(rest, size - 1).map { listOf(first) + it }

        val withoutFirst = getCombinations(rest, size)
        return withFirst + withoutFirst
    }

    /**
     * finds the best way to divide elk into valid groups using backtracking
     *
     * Uses anchor based selection: always picks the first remaining elk as the anchor
     * and only tries groups that contain this as anchor. Because every elk must be
     * assigned exactly to one group, this avoids recalculating the same
     * partition multiple times in different orders.
     *
     * @param remaining The remaining elk coordinates to partition
     * @param isTypeB Whether to use Type B scoring shape ot Type A
     * @return Maximum points achievable from this partition
     */
    private fun findBestElkPartition(remaining: Set<Coordinate>, isTypeB: Boolean): Int {
        if (remaining.isEmpty()) return 0
        val anchor = remaining.first()
        val others = remaining - anchor

        val points = mapOf(1 to 2, 2 to 5, 3 to 9, 4 to 13)
        var best = 0

        for (size in 1..minOf(4, remaining.size)) {
            val combinationsOfOthers = getCombinations(others.toList(), size - 1)

            for (combo in combinationsOfOthers) {
                val group = (combo + anchor).toSet()

                // skip groups that are not connected
                if (getGroups(group.toList()).size <= 1) {
                    val isValid: Boolean = if (isTypeB) {
                        isValidElkGroupTypeB(group)
                    } else {
                        isValidElkGroupTypeA(group)
                    }

                    if (isValid) {
                        val score: Int = if (isTypeB) {
                            points[size] ?: 0
                        } else {
                            bestElkLineScore(size)
                        }

                        val rest = remaining - group
                        val total = score + findBestElkPartition(rest, isTypeB)

                        if (total > best) best = total
                    }
                }
            }
        }
        return best
    }


    /**
     * Calculates the hawk score for the given user based on the scoring card
     * @param user The user whose board we want to calculate
     * @param isTypeB Whether to use Type B or Type A scoring card
     * @return Total hawk score
     */
    private fun scoreHawk(user: User, isTypeB: Boolean): Int {
        val allHawks = getAnimalPositions(user, WildLifeTokenType.HAWK)

        val isolatedHawks = allHawks.filter { hawk -> hawk.getNeighbors().none { it in allHawks } }

        val boardKeys = user.board.placedHabitatTiles.keys

        var qualifyingHawks = 0
        for (hawk in isolatedHawks) {
            if (isTypeB) {
                if (hasLineSight(hawk, allHawks, boardKeys)) {
                    qualifyingHawks++
                }
            } else {
                qualifyingHawks++
            }
        }

        val tableA = intArrayOf(0, 2, 5, 8, 11, 14, 18, 22, 26)
        val tableB = intArrayOf(0, 0, 5, 9, 12, 16, 20, 24, 28)
        val table = if (isTypeB) tableB else tableA

        return if (qualifyingHawks < table.size) table[qualifyingHawks] else table.last()


    }


    /**
     * Calculates the fox score for the given user based on the scoring card
     *
     * @param user The user whose board we want to evaluate
     * @param isTypeB whether to use Type B or Type A scoring card
     * @return Total fox score.
     */
    private fun scoreFox(user: User, isTypeB: Boolean): Int {
        val allFoxes = getAnimalPositions(user, WildLifeTokenType.FOX)
        var total = 0

        for (fox in allFoxes) {
            if (isTypeB) {
                val pairs = getUniqueAdjacentAnimalPairs(fox, user)
                if (pairs == 1) total += 3
                if (pairs == 2) total += 5
                if (pairs >= 3) total += 7
            } else {
                val animals = getUniqueAdjacentAnimals(fox, user).size
                total += animals
            }
        }
        return total
    }

    /**
     * calculates the salmon score for the given user based on the scoring card
     *
     * a salmon chain is a group of connected salmons where no salmon has more than
     * two salmon neighbors. Salmon with more salmon neighbors are excluded and
     * split the chain into smaller ones.
     *
     * @param user The user whose board we want to evaluate
     * @param isTypeB Whether to use the TypeB or TypeA scoring table
     * @return Total salmon score
     */
    private fun scoreSalmon(user: User, isTypeB: Boolean): Int {
        val chains = getSalmonChains(user)
        // scoring table A
        val tableA = mapOf(1 to 2, 2 to 5, 3 to 8, 4 to 12, 5 to 16, 6 to 20)

        // scoring table B
        val tableB = mapOf(1 to 2, 2 to 4, 3 to 9, 4 to 11)

        var total = 0
        for (chain in chains) {
            val size = chain.size
            total += if (isTypeB) {
                tableB[size] ?: 17 // for 5+ salmons
            } else {
                tableA[size] ?: 25 // for 7+ salmons
            }
        }
        return total
    }

    /**
     * Finds all valid salmon chains on the give users boards
     *
     * @param user The user whose board we want to evaluate
     * @return List of chains and each chain is a list of coordinates.
     */
    private fun getSalmonChains(user: User): List<List<Coordinate>> {
        val allSalmon = getAnimalPositions(user, WildLifeTokenType.SALMON)
        // exclude salmon with more than 2 salmon neighbors because they break the chain
        val validSalmon = allSalmon.filter { pos -> pos.getNeighbors().count { it in allSalmon } <= 2 }

        val groups = getGroups(validSalmon)

        return groups.map { it.toList() }
    }


    /**
     * Returns all coordinates where the user has placed a tile with the given animal token
     */
    private fun getAnimalPositions(user: User, animal: WildLifeTokenType): List<Coordinate> {

        // was eine line, bin stolz drauf. 1-10?
        // filters all placed tiles for the given animal and return the coords
        return user.board.placedHabitatTiles.filter { it.value.placedWildLifeToken?.type == animal }.map { it.key }
    }

    /**
     * Returns true if two coordinates are adjacent
     */
    private fun areAdjacent(a: Coordinate, b: Coordinate): Boolean {
        return b in a.getNeighbors()
    }

    private fun buildGroup(
        start: Coordinate,
        positions: List<Coordinate>,
        visited: MutableList<Coordinate>
    ): Set<Coordinate> {
        val group = mutableSetOf<Coordinate>()
        val queue = ArrayDeque<Coordinate>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current in visited) continue
            visited.add(current)
            if (current in positions) {
                group.add(current)
                current.getNeighbors()
                    .filter { it in positions && it !in visited }
                    .forEach { queue.add(it) }
            }
        }
        return group
    }

    /**
     * Groups a list of coordinates into sets of connected components
     */
    private fun getGroups(positions: List<Coordinate>): List<Set<Coordinate>> {
        val visited = mutableListOf<Coordinate>()
        val groups = mutableListOf<Set<Coordinate>>()
        for (pos in positions) {
            if (pos in visited) continue
            val group = buildGroup(pos, positions, visited)
            if (group.isNotEmpty()) groups.add(group)
        }
        return groups
    }


    /**
     * Returns the set of animal types that are adjacent to the given coordinate, ignoring null tokens and duplicates
     */
    private fun getUniqueAdjacentAnimals(coordinate: Coordinate, user: User): Set<WildLifeTokenType> {
        // get all neighbors, map to their placed wildlife token, filter nulls and return unique set
        return coordinate.getNeighbors().mapNotNull { user.board.placedHabitatTiles[it]?.placedWildLifeToken?.type }
            .toSet()
    }

    /**
     * Counts how many animal types appear at least twice among the neighbors of the coordinate
     *
     * excluding foxes
     */
    private fun getUniqueAdjacentAnimalPairs(coordinate: Coordinate, user: User): Int {
        val animals =
            coordinate.getNeighbors().mapNotNull { user.board.placedHabitatTiles[it]?.placedWildLifeToken?.type }
                .filter { it != WildLifeTokenType.FOX }

        val animalCounts = mutableMapOf<WildLifeTokenType, Int>()
        for (animal in animals) {
            animalCounts[animal] = (animalCounts[animal] ?: 0) + 1
        }

        var pairs = 0
        for (count in animalCounts.values) {
            if (count >= 2) pairs++
        }
        return pairs

    }


    /**
     * Checks if a hawk at [from] can "see" any other hawk in a straight line
     * without crosing an empty tile
     *
     * Returns true if another hawk is found along any of the 6 directions.
     */
    private fun hasLineSight(from: Coordinate, allHawks: List<Coordinate>, boardKeys: Set<Coordinate>): Boolean {
        val directions = listOf(
            Coordinate(1, 0),
            Coordinate(-1, 0),
            Coordinate(0, 1),
            Coordinate(0, -1),
            Coordinate(1, -1),
            Coordinate(-1, 1)
        )
        for (dir in directions) {
            var current = Coordinate(from.q + dir.q, from.r + dir.r)
            while (current in boardKeys) {
                if (current in allHawks) {
                    return true
                }
                current = Coordinate(current.q + dir.q, current.r + dir.r)
            }

        }
        return false
    }


}

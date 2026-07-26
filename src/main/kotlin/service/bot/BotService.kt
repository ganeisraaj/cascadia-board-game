package service.bot

import service.AbstractRefreshingService
import service.RootService
import java.util.concurrent.atomic.AtomicLong

import entity.*

/**
 * Service that manages all bot-related game logic
 *
 * Responsibilities:
 * - Generating all legal actions for a bot
 * - Executing a selected action on the real game state
 * - Orchestrating a complete bot turn
 *
 * @param rootService The [RootService] instance to access the game state
 */
class BotService(val rootService: RootService) : AbstractRefreshingService() {


    private val botTurnGeneration = AtomicLong(0)

    @Volatile
    var isExecutingBotAction: Boolean = false
        private set

    private val directions = listOf(
        Pair(1, 0), Pair(-1, 0),
        Pair(0, 1), Pair(0, -1),
        Pair(1, -1), Pair(-1, 1)
    )

    /**
     * cancel bot turn
     */
    fun cancelBotTurn() {
        botTurnGeneration.incrementAndGet()
    }

    /**
     * Generates all legal actions available
     *
     * One [BotAction] represents one complete bot turn
     *
     * @param game The current game state
     * @param user The bot player to generate actions for
     * @return List of all legal [BotAction]s
     */
    fun generateLegalActions(game: CascadiaGame, user: User): List<BotAction> {
        val legalActions = mutableListOf<BotAction>()
        val legalHabitatPositions = generateLegalHabitatPositions(user)

        for ((habitatTileIndex, originalHabitatTile) in game.displayedHabitatTiles) {
            for (rotation in 0 until 6) {
                val rotatedHabitatTile = createRotatedTile(originalHabitatTile, rotation)

                for ((wildLifeTokenIndex, wildLifeToken) in game.displayedWildLifeToken) {
                    val useNatureToken = habitatTileIndex != wildLifeTokenIndex

                    if (useNatureToken && user.natureToken <= 0) {
                        continue
                    }

                    for (habitatPosition in legalHabitatPositions) {
                        val legalWildLifePositions = generateLegalWildLifePositions(
                            user = user,
                            placedHabitatTile = rotatedHabitatTile,
                            placedHabitatX = habitatPosition.first,
                            placedHabitatY = habitatPosition.second,
                            wildLifeToken = wildLifeToken.type
                        )

                        for (wildLifePosition in legalWildLifePositions) {
                            legalActions.add(
                                BotAction(
                                    habitatTile = rotatedHabitatTile.deepCopy(),
                                    wildLifeToken = wildLifeToken.type,
                                    habitatTileIndex = habitatTileIndex,
                                    wildLifeTokenIndex = wildLifeTokenIndex,
                                    habitatPosX = habitatPosition.first,
                                    habitatPosY = habitatPosition.second,
                                    rotation = rotation,
                                    wildLifePosX = wildLifePosition.first,
                                    wildLifePosY = wildLifePosition.second,
                                    useNatureToken = useNatureToken
                                )
                            )
                        }
                    }
                }
            }
        }

        return legalActions
    }


    /**
     * Generates all valid board positions where the given user can place
     * a habitat tile
     *
     * A position is valid if it is adjacent to at least one already placed tile
     * and not already occupied. Returns [(0.0)] If the board is empty(first tile=
     *
     * @param user The player whose board to check
     * @return List of valid (q, r) positions
     */
    fun generateLegalHabitatPositions(user: User): List<Pair<Int, Int>> {
        val placedTiles = user.board.placedHabitatTiles

        if (placedTiles.isEmpty()) {
            return listOf(Pair(0, 0))
        }

        val legalPositions = mutableSetOf<Pair<Int, Int>>()

        for (coordinate in placedTiles.keys) {
            for (direction in directions) {
                val newPos = Pair(
                    coordinate.q + direction.first,
                    coordinate.r + direction.second
                )

                if (!placedTiles.containsKey(Coordinate(newPos.first, newPos.second))) {
                    legalPositions.add(newPos)
                }
            }
        }

        return legalPositions.toList()
    }


    /**
     * Executes one complete bot action on the real game state
     *
     * @param action the [BotAction] to execute
     * @throws IllegalStateException if no game is running or if the required
     * market slots are empty
     */
    fun executeAction(action: BotAction) {

        val game = checkNotNull(rootService.currentGame) { "No game running" }
        val currentPlayer = game.userList[game.currentUser]

        // Use the actual market objects. Passing copies here prevents the action service
        // from finding their market slots and leaves the undo action incomplete.
        val habitatTile = checkNotNull(game.displayedHabitatTiles[action.habitatTileIndex]) {
            "No habitat tile at market slot ${action.habitatTileIndex}"
        }
        val wildLifeToken = checkNotNull(game.displayedWildLifeToken[action.wildLifeTokenIndex]) {
            "No wildlife token at market slot ${action.wildLifeTokenIndex}"
        }

        // simulate chooseCombination internally because it is not implemented.

        if (action.useNatureToken) {
            currentPlayer.natureToken--
            game.natureToken++
            game.currentAction.selection.usedNatureToken++
        }
        game.currentAction.selection.habitatTileIndex = action.habitatTileIndex
        game.currentAction.selection.wildlifeTokenIndex = action.wildLifeTokenIndex
        game.state = GameState.WAIT_FOR_MOVE

        habitatTile.edges.clear()
        habitatTile.edges.addAll(action.habitatTile.edges)
        habitatTile.rotation = action.rotation


        Thread.sleep(game.gamePlaySpeed * 2000L / 4)
        rootService.playerActionService.placeHabitatTile(
            habitatTile = habitatTile,
            posX = action.habitatPosX,
            posY = action.habitatPosY
        )

        val habitatTileForWildLife = findHabitatTileForWildLife(action)

        Thread.sleep(game.gamePlaySpeed * 2000L / 4)
        rootService.playerActionService.placeWildLifeToken(
            wildLifeToken = wildLifeToken,
            habitatTile = habitatTileForWildLife
        )


        Thread.sleep(game.gamePlaySpeed * 2000L / 4)


    }


    /**
     * Performs one complete bot turn:
     * 1. Determines the bot strategy based on the current player type
     * 2. Generates all legal actions
     * 3. Selects the best action
     * 4. Executes the action
     * 5. Advances to the next player if the game has not ended
     *
     * @throws IllegalStateException if no game is running or the current player
     * not a bot
     * @throws IllegalArgumentException if no legal actions are available
     */
    fun botTurnPerform() {
        val turnGeneration = botTurnGeneration.get()
        val game = rootService.currentGame ?: return
        val scheduledUserIndex = game.currentUser

        fun isCancelled() =
            botTurnGeneration.get() != turnGeneration ||
                    rootService.currentGame !== game ||
                    game.currentUser != scheduledUserIndex

        if (isCancelled() || game.state != GameState.WAIT_FOR_TURN) return

        rootService.gameService.checkOverPopulation()

        if (game.state == GameState.END || isCancelled()) return

        val currentPlayer = game.userList[scheduledUserIndex]
        val strategy = createBotStrategy(currentPlayer, ::isCancelled)

        val legalActions = generateLegalActions(game, currentPlayer)

        if (isCancelled()) return

        if (legalActions.isEmpty()) {
            handleNoActionTurn(game, currentPlayer, { isCancelled() })
            return
        }

        executeBotAction(
            strategy.chooseAction(legalActions),
            game,
            { isCancelled() }
        )
    }

    private fun createBotStrategy(
        user: User,
        isCancelled: () -> Boolean
    ): BotStrategy {
        return when (user.type) {
            UserType.RANDOM_BOT -> RandomStrategy()

            UserType.PROFESSIONAL_BOT -> RealStrategy(
                rootService = rootService,
                iterations = 100,
                isCancelled = isCancelled
            )

            else -> throw IllegalStateException(
                "The current user is not a bot."
            )
        }
    }

    private fun handleNoActionTurn(
        game: CascadiaGame,
        user: User,
        isCancelled: () -> Boolean
    ) {
        isExecutingBotAction = true

        try {
            if (!isCancelled() && game.state == GameState.WAIT_FOR_TURN) {
                handleNoLegalActions(game, user)
            }
        } finally {
            isExecutingBotAction = false
        }
    }

    private fun executeBotAction(
        action: BotAction,
        game: CascadiaGame,
        isCancelled: () -> Boolean
    ) {
        if (isCancelled() || game.state != GameState.WAIT_FOR_TURN) return

        isExecutingBotAction = true

        try {
            executeAction(action)

            try {
                Thread.sleep(game.gamePlaySpeed * 500L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }

            if (rootService.currentGame === game && game.state != GameState.END) {
                rootService.gameService.nextUser()
            }
        } finally {
            isExecutingBotAction = false
        }
    }


    /**
     * handles the case where the bot has no legal wildlife token placements
     * Discards the wildlife token (drag in bag)
     */
    private fun handleNoLegalActions(
        game: CascadiaGame,
        user: User
    ) {
        val habitatPosition = checkNotNull(generateLegalHabitatPositions(user).firstOrNull())
        { "Bot ${user.name} has no legal habitat position." }

        val marketIndex = checkNotNull(game.displayedHabitatTiles.keys.firstOrNull { index ->
            game.displayedWildLifeToken.containsKey(index)
        }) { "No complete market pair is available." }

        val habitatTile = checkNotNull(game.displayedHabitatTiles[marketIndex])
        { "No habitat tile at market slot $marketIndex." }

        val wildLifeToken = checkNotNull(game.displayedWildLifeToken[marketIndex])
        { "No wildlife token at market slot $marketIndex." }

        val currentAction = game.currentAction

        currentAction.selection.habitatTileIndex = marketIndex
        currentAction.selection.wildlifeTokenIndex = marketIndex
        currentAction.selection.wildlifeToken = wildLifeToken

        game.state = GameState.WAIT_FOR_MOVE

        rootService.playerActionService.placeHabitatTile(
            habitatTile = habitatTile,
            posX = habitatPosition.first,
            posY = habitatPosition.second
        )

        game.displayedWildLifeToken.remove(marketIndex)

        val replacementToken =
            if (game.wildLifeCollection.isNotEmpty()) {
                game.wildLifeCollection.pop()
            } else {
                null
            }

        game.wildLifeCollection.push(wildLifeToken)
        game.wildLifeCollection.shuffle()

        if (replacementToken != null) {
            game.displayedWildLifeToken[marketIndex] =
                replacementToken
        }

        rootService.gameService.nextUser()
    }

    /**
     * Generates all valid positions where the given wildlife token cna be placed
     *
     * @param user the current player
     * @param placedHabitatTile The habitat tile just placed this turn
     * @param placedHabitatX The q-coordinate of the newly placed tile
     * @param placedHabitatY The r-coordinate of the newly placed tile
     * @param wildLifeToken the wildlife token type to place
     * @return List of valid (q, r) positions
     */
    private fun generateLegalWildLifePositions(
        user: User,
        placedHabitatTile: HabitatTile,
        placedHabitatX: Int,
        placedHabitatY: Int,
        wildLifeToken: WildLifeTokenType
    ): List<Pair<Int, Int>> {
        val legalPositions = mutableListOf<Pair<Int, Int>>()


        for ((coordinate, habitatTile) in user.board.placedHabitatTiles) {
            if (habitatTile.placedWildLifeToken == null &&
                habitatTile.availableWildLifeToken.contains(wildLifeToken)
            ) {
                legalPositions.add(Pair(coordinate.q, coordinate.r))
            }
        }


        val newTileCoord = Coordinate(placedHabitatX, placedHabitatY)
        if (user.board.placedHabitatTiles[newTileCoord]?.placedWildLifeToken == null &&
            placedHabitatTile.availableWildLifeToken.contains(wildLifeToken)
        ) {
            legalPositions.add(Pair(placedHabitatX, placedHabitatY))
        }
        return legalPositions

    }


    /**
     * Finds the [HabitatTile] on the current player's board at the wildlife position
     * specified in the given action.
     *
     * @param action The action containing the wildlife placement coordinates
     * @return The [HabitatTile] at the wildlife position
     * @throws IllegalStateException if no game is running or no tile exists at that position
     *
     */
    private fun findHabitatTileForWildLife(action: BotAction): HabitatTile {
        val game = rootService.currentGame
            ?: throw IllegalStateException("The game is not started.")
        val currentUser = game.userList[game.currentUser]

        val wildLifeCoord = Coordinate(action.wildLifePosX, action.wildLifePosY)
        return currentUser.board.placedHabitatTiles[wildLifeCoord]
            ?: throw IllegalStateException("No habitat tile found at wildlife position $wildLifeCoord")

    }


    /**
     * Creates a rotated copy of the given habitat tile
     *
     * @param originalTile The tile to rotate
     * @param rotationSteps number of 60° clockwise rotation steps to apply
     * @return a deep copy of [originalTile] with the edges rotated accordingly
     */

    private fun createRotatedTile(originalTile: HabitatTile, rotationSteps: Int): HabitatTile {
        val rotatedTile = originalTile.deepCopy()
        val normalizedRotation = rotationSteps % 6

        repeat(normalizedRotation) {
            val lastEdge = rotatedTile.edges.removeAt(rotatedTile.edges.lastIndex)
            rotatedTile.edges.add(0, lastEdge)
        }

        rotatedTile.rotation = normalizedRotation

        return rotatedTile
    }


    /*

    fun generateLegalActions(game:CascadiaGame, user: User): List<BotAction>{
        val legalActions = mutableListOf<BotAction>()
        val legalHabitatPositions = generateLegalHabitatPositions(user)

        for ((habitatTileIndex, habitatTile) in game.displayedHabitatTiles){
            for ((wildLifeTokenIndex, wildLifeToken) in game.displayedWildLifeToken){
                val useNatureToken = habitatTileIndex != wildLifeTokenIndex
                if (useNatureToken && user.natureToken <= 0) continue

                for (habitatPosition in legalHabitatPositions){
                    val legalWidLifePosition = generateLegalWildLifePositions(
                        user = user,
                        placedHabitatTile = habitatTile,
                        placedHabitatX = habitatPosition.first,
                        placedHabitatY = habitatPosition.second,
                        wildLifeTokenType = wildLifeToken.type
                    )
                    for (wildLifePosition in legalWidLifePosition){
                        legalActions.add(BotAction(
                            habitatTile = habitatTile,
                            wildLifeToken = wildLifeToken.type,
                            habitatTileIndex = habitatTileIndex,
                            wildLifeTokenIndex = wildLifeTokenIndex,
                            habitatPosX = habitatPosition.first,
                            habitatPosY = habitatPosition.second,
                            rotation = habitatTile.rotation,
                            wildLifePosX = wildLifePosition.first,
                            wildLifePosY = wildLifePosition.second,
                            useNatureToken = useNatureToken
                        ))
                    }
                }
            }
        }
        return legalActions

    }



    private fun generateLegalHabitatPositions(user: User): List<Pair<Int, Int>> {
        val placedTiles = user.board.placedHabitatTiles
        if (placedTiles.isEmpty()) return listOf(Pair(0, 0))

        val directions = listOf(
            Pair(1,0), Pair(-1,0),
            Pair(0, 1), Pair(0, -1),
            Pair(1, -1), Pair(-1, 1)
        )



        val legalPositions = mutableSetOf<Pair<Int, Int>>()
        for (position in placedTiles.keys){
            for (direction in directions){
                val newPos = Pair(position.q + direction.first, position.r + direction.second)
                if (!placedTiles.containsKey(Coordinate(newPos.first, newPos.second))){
                    legalPositions.add(newPos)
                }
            }
        }
        return legalPositions.toList()


    }



    fun executeAction(action: BotAction){
        rootService.playerActionService.chooseCombination(
            wildLifeTokenIndex = action.wildLifeTokenIndex,
            habitatTileIndex = action.habitatTileIndex,
            useNatureToken = action.useNatureToken
        )

        rootService.playerActionService.rotateHabitatTile(habitatTile = action.habitatTile)
        rootService.playerActionService.placeHabitatTile(
            posX = action.habitatPosX,
            posY = action.habitatPosY)
        val habitatTileForWildLife = findHabitatTileForWildLife(action)
        rootService.playerActionService.placeWildLifeToken(habitatTile = habitatTileForWildLife)



    }


    fun botTurnPerform(){
        val game = rootService.currentGame ?: throw IllegalStateException("The Game is not started.")
        val currentPlayer = game.userList[game.currentUser]

        require(currentPlayer.type == UserType.RANDOM_BOT || currentPlayer.type == UserType.PROFESSIONAL_BOT){
            "Current user is not a bot."
        }



        val botStrategy: BotStrategy = when (currentPlayer.type){
            UserType.RANDOM_BOT -> RandomStrategy(rootService)
            UserType.PROFESSIONAL_BOT -> RealStrategy(rootService, 100)
            else -> throw IllegalStateException("Current user is not a bot")


        }



        val legalActions = generateLegalActions(game, currentPlayer)
        require(legalActions.isNotEmpty()) {"Bot has no legal actions."}

        val selectedAction = botStrategy.chooseAction(legalActions)
        executeAction(selectedAction)
    }



    private fun generateLegalWildLifePositions(
        user: User,
        placedHabitatTile: HabitatTile,
        placedHabitatX: Int,
        placedHabitatY: Int,
        wildLifeTokenType: WildLifeTokenType
    ): List<Pair<Int, Int>> {
        val legalPositions = mutableListOf<Pair<Int, Int>>()

        for ((coordinate, habitatTile) in user.board.placedHabitatTiles){
            val positions = Pair(coordinate.q, coordinate.r)

            if (habitatTile.placedWildLifeToken == null &&
                canHabitatTileAcceptWildLifeToken(habitatTile, wildLifeToken)){
                legalPositions.add(positions)
            }
        }


        val newTileCoord = Coordinate(placedHabitatX, placedHabitatY)
        if (user.board.placedHabitatTiles[newTileCoord]?.placedWildLifeToken == null&&
            canHabitatTileAcceptWildLifeToken(placedHabitatTile, wildLifeToken)){
            legalPositions.add(Pair(placedHabitatX, placedHabitatY))
        }

        return legalPositions
    }


    private fun canHabitatTileAcceptWildLifeToken(
        habitatTile: HabitatTile,
        wildLifeToken: WildLifeTokenType
    ): Boolean {
        return habitatTile.availableWildLifeToken.contains(wildLifeToken)
    }

    private fun findHabitatTileForWildLife(action: BotAction): HabitatTile{
        val game = rootService.currentGame ?: throw IllegalStateException("The game is not started.")
        val currentUser = game.userList[game.currentUser]


        if (action.habitatPosX == action.wildLifePosX && action.habitatPosY == action.wildLifePosY){
            return action.habitatTile
        }


        val wildLifeCoord = Coordinate(action.wildLifePosX, action.wildLifePosY)
        return currentUser.board.placedHabitatTiles[wildLifeCoord]
            ?:throw IllegalStateException("No habitat tile found at wildlife position $wildLifeCoord.")
    }

*/


}

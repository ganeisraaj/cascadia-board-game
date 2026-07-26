package service.bot

import entity.BotAction
import entity.CascadiaGame
import entity.Coordinate
import entity.GameState
import entity.HabitatTileType
import entity.User
import entity.WildLifeToken
import service.RootService
import service.getNeighbors
import kotlin.random.Random


/**
 * Implements the professional bot strategy using a Monte Carlo Tree Search approach.
 *
 * The strategy receives all legal bot actions, reduces the action space by using
 * a heuristic pre-evaluation, and then performs several MCTS iterations. Each
 * iteration consists of selection, expansion, rollout simulation, and
 * backpropagation.
 *
 * The search is performed on deep copies of the current game state, so the real
 * game is not modified while the bot is thinking.
 *
 * @property rootService Provides access to the current game and other service classes.
 * @property iterations Number of MCTS iterations used to search for the best action.
 * @property rolloutDepth Maximum number of simulated turns during each rollout.
 * @property weights Evaluation weights used by the heuristic scoring functions.
 */


class RealStrategy(
    private val rootService: RootService,
    private val iterations: Int = 100,
    private val rolloutDepth: Int = 5,
    private val weights: EvaluationWeights = EvaluationWeights(),
    private val isCancelled: () -> Boolean = { false }
): BotStrategy{
    /**
     * Chooses the best action from the given list of legal actions.
     *
     * The method first filters the available actions by using a heuristic evaluation.
     * selection, expansion, rollout simulation, and backpropagation.
     *
     * The action with the best average simulation score is returned.
     *
     * @param legalActions All actions that the bot is allowed to perform.
     * @return The selected [BotAction] that should be executed by the bot.
     * @throws IllegalArgumentException If the list of legal actions is empty.
     * @throws IllegalStateException If no game is currently running.
     */
    override fun chooseAction(legalActions: List<BotAction>): BotAction {
        require(legalActions.isNotEmpty()) {"Legal actions must not be empty."}

        val currentGame = rootService.currentGame
            ?: throw IllegalStateException("The game is not started.")

        //new add
        val candidateActions = legalActions
            .sortedByDescending { action -> evaluateAction(action) }
            .take(100)

        val rootNode = MCTSNode(
            action = null,
            parent = null,
            unUsedAction = candidateActions.toMutableList(),
            gameState = currentGame.deepCopy()
        )

        repeat(iterations){
            if (isCancelled()) {
                return candidateActions.first()
            }

            var node = rootNode

            /**
             * selection
             */
            while (node.isFullyExpanded() && node.children.isNotEmpty()) {
                node = node.bestChild()
            }

            /**
             * expansion
             */
            if (node.unUsedAction.isNotEmpty()){
                node = expand(node)
            }

            /**
             * simulation
             */
            val simulationResult = rollout(
                game = node.gameState.deepCopy(),
                depth = rolloutDepth
            )

            /**
             * backpropagate
             */
            backpropagate(node, simulationResult)
        }
        return bestActionFromRoot(rootNode)


    }



    /**
     * Expands the given MCTS node by selecting one unused action.
     *
     * A random unused action is removed from the node and applied to a deep copy of
     * the node's game state. The resulting game state becomes the state of the new
     * child node. If the game has not ended, the simulated turn is advanced to the
     * next player and the next legal actions are generated.
     *
     * @param node The node that should be expanded.
     * @return The newly created child node.
     */
    private fun expand(node: MCTSNode): MCTSNode {
        val actionIndex = Random.nextInt(node.unUsedAction.size)
        val action = node.unUsedAction.removeAt(actionIndex)

        val newGameState = node.gameState.deepCopy()

        applyActionToGameCopy(newGameState, action)

        if (newGameState.state != GameState.END) {
            nextUserInCopy(newGameState)
        }

        val nextLegalActions =
            if (newGameState.state == GameState.END) {
                mutableListOf()
            } else {
                val nextUser = newGameState.userList[newGameState.currentUser]
                rootService.botService
                    .generateLegalActions(newGameState, nextUser)
                    .toMutableList()
            }

        val childNode = MCTSNode(
            action = action,
            parent = node,
            unUsedAction = nextLegalActions,
            gameState = newGameState
        )

        node.children.add(childNode)
        return childNode
    }

    /**
     * Performs a rollout simulation from the given game state.
     *
     * The rollout repeatedly chooses random legal actions and applies them to the
     * copied game state. The simulation stops when the game ends, no legal action
     * is available, or the maximum rollout depth is reached.
     *
     * The final simulated game state is evaluated from the perspective of the user
     * who was active at the beginning of the rollout.
     *
     * @param game The copied game state used for simulation.
     * @param depth Maximum number of simulated turns.
     * @return A heuristic score for the simulated game state.
     */
    private fun rollout(game: CascadiaGame, depth: Int): Double {
        val originalUserIndex = game.currentUser

        repeat(depth) {
            if (isCancelled()) {
                return evaluateGameState(game, originalUserIndex)
            }

            if (game.state == GameState.END) {
                return evaluateGameState(game, originalUserIndex)
            }

            val currentUser = game.userList[game.currentUser]
            val legalActions = rootService.botService.generateLegalActions(game, currentUser)

            if (legalActions.isEmpty()) {
                return evaluateGameState(game, originalUserIndex)
            }

            val action = legalActions.random()

            applyActionToGameCopy(game, action)

            if (game.state != GameState.END) {
                nextUserInCopy(game)
            }
        }

        return evaluateGameState(game, originalUserIndex)
    }

    /**
     * Advances the copied game state to the next user.
     *
     * This method is only used during MCTS simulation and must not affect the real
     * game. The current user index is increased cyclically and the game state is
     * reset to waiting for the next turn.
     *
     * @param game The copied game state that should be advanced.
     */
    private fun nextUserInCopy(game: CascadiaGame) {
        game.currentUser = (game.currentUser + 1) % game.userList.size
        game.state = GameState.WAIT_FOR_TURN
    }


    /**
     * Evaluates a copied game state from the perspective of one user.
     *
     * The evaluation is a heuristic approximation of the final score. It considers
     * the number of placed habitat tiles, saved nature tokens, habitat connectivity,
     * keystone tiles, and placed wildlife tokens.
     *
     * @param game The copied game state to evaluate.
     * @param userIndex Index of the user whose position should be evaluated.
     * @return A heuristic score for the given user.
     */
    private fun evaluateGameState(game: CascadiaGame, userIndex: Int): Double {
        val user = game.userList[userIndex]

        var score = 0.0

        score += user.board.placedHabitatTiles.size * 1.0
        //new add
        score += user.natureToken * weights.saveNatureTokenBonus
        score += calculateHabitatScore(user) * weights.habitatEdgeWeight

        for ((_, tile) in user.board.placedHabitatTiles) {
            if (tile.keyStone) {
                score += weights.keyStoneBonus
            }

            if (tile.placedWildLifeToken != null) {
                score += weights.wildlifePlacementBonus
            }

        }

        return score
    }

    /**
     * Calculates a simplified habitat score for the given user.
     *
     * For each habitat type, the method searches for the largest connected group of
     * tiles containing that habitat type. The final value is the sum of the largest
     * groups for all habitat types.
     *
     * @param user The user whose board should be evaluated.
     * @return The summed size of the largest connected groups for all habitat types.
     */
    private fun calculateHabitatScore(user: User): Int {
        return HabitatTileType.entries.toTypedArray().sumOf { habitatType ->
            largestHabitatGroup(user, habitatType)
        }
    }

    /**
     * Finds the largest connected group for one habitat type.
     *
     * The method collects all coordinates containing the requested habitat type
     * and calculates the size of the largest connected group.
     *
     * @param user The user whose board should be searched.
     * @param habitatType The habitat type for which the largest group is calculated.
     * @return The size of the largest connected group of the given habitat type.
     */
    private fun largestHabitatGroup(user: User, habitatType: HabitatTileType): Int {
        val matchingCoordinates = user.board.placedHabitatTiles
            .filterValues { tile -> habitatType in tile.edges }
            .keys
            .toSet()

        val visited = mutableSetOf<Coordinate>()

        return matchingCoordinates.maxOfOrNull { start ->
            if (visited.add(start)) {
                findConnectedGroupSize(start, matchingCoordinates, visited)
            } else {
                0
            }
        } ?: 0
    }

    /**
     * Calculates the size of a connected habitat group using breadth-first search.
     *
     * @param start Starting coordinate of the group.
     * @param matchingCoordinates Coordinates containing the relevant habitat type.
     * @param visited Coordinates that have already been processed.
     * @return Size of the connected group.
     */
    private fun findConnectedGroupSize(
        start: Coordinate,
        matchingCoordinates: Set<Coordinate>,
        visited: MutableSet<Coordinate>
    ): Int {
        var groupSize = 0
        val openCoordinates = ArrayDeque<Coordinate>()
        openCoordinates.add(start)

        while (openCoordinates.isNotEmpty()) {
            val current = openCoordinates.removeFirst()
            groupSize++

            current.getNeighbors()
                .filter { it in matchingCoordinates }
                .filter { visited.add(it) }
                .forEach { openCoordinates.add(it) }
        }

        return groupSize
    }


    /**
     * Applies a bot action to a copied game state.
     *
     * This method is used only inside the MCTS simulation. It places the selected
     * habitat tile, places the selected wildlife token, removes the selected display
     * elements, refills the display, and updates the simulated game state.
     *
     * The real game state stored in [RootService] is not changed by this method.
     *
     * @param game The copied game state to modify.
     * @param action The action that should be applied to the copied game.
     * @throws IllegalStateException If the wildlife token should be placed on a tile
     * that does not exist in the copied board.
     */
    private fun applyActionToGameCopy(game: CascadiaGame, action: BotAction) {
        val currentUser = game.userList[game.currentUser]

        if (action.useNatureToken) {
            currentUser.natureToken--
            game.natureToken++
            game.currentAction.selection.usedNatureToken++
        }

        game.currentAction.selection.habitatTileIndex = action.habitatTileIndex
        game.currentAction.selection.wildlifeTokenIndex = action.wildLifeTokenIndex

        val habitatCoord = Coordinate(action.habitatPosX, action.habitatPosY)

        val placedTile = action.habitatTile.deepCopy()
        placedTile.rotation = action.rotation

        currentUser.board.placedHabitatTiles[habitatCoord] = placedTile

        val wildlifeCoord = Coordinate(action.wildLifePosX, action.wildLifePosY)

        val targetTile =
            if (wildlifeCoord == habitatCoord) {
                placedTile
            } else {
                currentUser.board.placedHabitatTiles[wildlifeCoord]
                    ?: throw IllegalStateException("No habitat tile found at wildlife position $wildlifeCoord")
            }

        targetTile.placedWildLifeToken = WildLifeToken(action.wildLifeToken)

        game.displayedHabitatTiles.remove(action.habitatTileIndex)
        game.displayedWildLifeToken.remove(action.wildLifeTokenIndex)

        refillDisplaysInCopy(game)

        game.state = GameState.WAIT_FOR_MOVE
    }

    /**
     * Refills the displayed habitat tiles and wildlife tokens in a copied game.
     *
     * Missing habitat tile slots and wildlife token slots are filled from their
     * corresponding draw stacks. If no habitat tiles remain, the copied game is
     * marked as ended.
     *
     * @param game The copied game state whose display should be refilled.
     */
    private fun refillDisplaysInCopy(game: CascadiaGame) {
        val missingHabitatIndices = (0..3).filter { index ->
            !game.displayedHabitatTiles.containsKey(index)
        }

        for (index in missingHabitatIndices) {
            if (game.habitatTileCollection.isNotEmpty()) {
                game.displayedHabitatTiles[index] = game.habitatTileCollection.pop()
            }
        }

        val missingWildLifeIndices = (0..3).filter { index ->
            !game.displayedWildLifeToken.containsKey(index)
        }

        for (index in missingWildLifeIndices) {
            if (game.wildLifeCollection.isNotEmpty()) {
                game.displayedWildLifeToken[index] = game.wildLifeCollection.pop()
            }
        }

        if (game.habitatTileCollection.isEmpty()) {
            game.state = GameState.END
        }
    }



    /**
     * Backpropagates a rollout result through the MCTS tree.
     *
     * Starting from the given node, the method walks up to the root node. Each node
     * on this path receives one additional visit and adds the rollout result to its
     * accumulated score.
     *
     * @param startNode The node from which backpropagation starts.
     * @param result The simulation result that should be added to the visited nodes.
     */
    private fun backpropagate(startNode: MCTSNode, result: Double){
        var node: MCTSNode? = startNode
        while (node != null){
            node.visits++
            node.totalScore += result
            node = node.parent
        }
    }




    //get changed
    /**
     * Selects the final action from the root node.
     *
     * The method chooses the child with the highest average score. The average score
     * is calculated as total simulation score divided by visit count.
     *
     * @param rootNode The root node of the MCTS tree.
     * @return The action stored in the best child node.
     * @throws IllegalStateException If no child node was created or the best child
     * does not contain an action.
     */
    private fun bestActionFromRoot(rootNode: MCTSNode): BotAction {
        val bestChild = rootNode.children.maxByOrNull { child ->
            if (child.visits == 0) {
                Double.NEGATIVE_INFINITY
            } else {
                child.totalScore / child.visits
            }
        } ?: throw IllegalStateException(
            "MCTS did not create any child node."
        )

        return bestChild.action
            ?: throw IllegalStateException("Best child has no action.")
    }




    /**
     * Evaluates whether the selected habitat tile and wildlife token form a standard
     * combination.
     *
     * A standard combination means that the habitat tile and wildlife token were
     * taken from the same display index. Non-standard combinations are still legal
     * when a nature token is used, but they receive a different score.
     *
     * @param action The action to evaluate.
     * @return The weighted score for the selected combination.
     */
    private fun evaluateCombination(action: BotAction): Double{
        return if (action.habitatTileIndex == action.wildLifeTokenIndex){
            weights.standardCombinationBonus
        } else {
            weights.nonStandardCombinationBonus
        }
    }


    /**
     * Evaluates the placement quality of a habitat tile.
     *
     * The method rewards keystone tiles and matching habitat types with neighboring
     * tiles that are already placed on the current user's board.
     *
     * @param action The action containing the habitat tile and target position.
     * @return A heuristic score for the habitat tile placement.
     * @throws IllegalStateException If no game is currently running.
     */
    private fun evaluateTilePlacement(action: BotAction): Double{
        var score = 0.0
        if (action.habitatTile.keyStone) score += weights.keyStoneBonus

        val game = rootService.currentGame
            ?: throw IllegalStateException("The game is not started.")
        val user = game.userList[game.currentUser]
        val position = Coordinate(action.habitatPosX, action.habitatPosY)
        val newHabitatTypes = action.habitatTile.edges.toSet()

        val matchingNeighborTypes = position.getNeighbors().sumOf { neighbor ->
            val neighborTile = user.board.placedHabitatTiles[neighbor]
            if (neighborTile == null) {
                0
            } else {
                newHabitatTypes.intersect(neighborTile.edges.toSet()).size
            }
        }

        score += matchingNeighborTypes * weights.habitatEdgeWeight

        return score
    }

    /**
     * Evaluates whether the selected wildlife token can be placed on the selected
     * habitat tile.
     *
     * The method gives a bonus if the habitat tile allows the selected wildlife
     * token type.
     *
     * @param action The action containing the selected tile and wildlife token.
     * @return A wildlife placement score.
     */
    private fun evaluateWildLifePlacement(action: BotAction): Double{
        if (action.habitatTile.availableWildLifeToken.contains(action.wildLifeToken)) {
            return weights.wildlifePlacementBonus
        }
        return 0.0

    }


    /**
     * Evaluates the nature token effect of an action.
     *
     * Using a nature token receives a penalty, while keeping a nature token receives
     * a small bonus. This encourages the bot to use nature tokens only when the
     * resulting action is valuable enough.
     *
     * @param action The action to evaluate.
     * @return The weighted score for using or saving a nature token.
     */
    private fun evaluateNatureTokenAction(action: BotAction): Double{
        return if (action.useNatureToken) weights.useNatureTokenPenalty
        else weights.saveNatureTokenBonus
    }


    /**
     * Calculates a heuristic score for one action.
     *
     * This method is used to pre-rank legal actions before MCTS starts. It combines
     * the combination score, tile placement score, wildlife placement score, and
     * nature token score.
     *
     * @param action The action to evaluate.
     * @return The heuristic score of the action.
     */
    private fun evaluateAction(action: BotAction): Double{
        var score = 0.0
        score += evaluateCombination(action)
        score += evaluateTilePlacement(action)
        score += evaluateWildLifePlacement(action)
        score += evaluateNatureTokenAction(action)
        return score
    }


}

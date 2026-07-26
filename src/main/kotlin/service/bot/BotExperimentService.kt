package service.bot

import entity.*

import service.RootService

/**
 * Provides an experimental environment for comparing two different bot evaluation settings.
 *
 * The service runs multiple games between two professional bots. Each bot uses
 * a [RealStrategy], but with different [EvaluationWeights]. After all games are
 * simulated, the service reports wins, excluded ties, and average scores for
 * both weight configurations.
 *
 * This class is mainly used to test whether one set of heuristic evaluation
 * weights performs better than another one.
 *
 * @property rootService Provides access to the game service, bot service, and current game.
 */
class BotExperimentService(private val rootService: RootService) {

    /**
     * Runs a bot-vs-bot experiment for a fixed number of games.
     *
     * Two professional bots are created for every simulated game. The first bot uses
     * [weights1], while the second bot uses [weights2]. After each game, the method
     * compares the final scores and counts the winning bot. Tied games are excluded
     * from the decisive game count and from the average score calculation.
     *
     * The method prints progress information for every game and a final summary
     * after all games have been simulated.
     *
     * @param weights1 Evaluation weights used by the first bot, named Jarvis.
     * @param weights2 Evaluation weights used by the second bot, named R2D2.
     * @param gamesCount Number of games that should be simulated.
     * @return A pair of [ExperimentResult] objects. The first result belongs to
     * [weights1], and the second result belongs to [weights2].
     */
    fun runExperiment(
        weights1: EvaluationWeights,
        weights2: EvaluationWeights,
        gamesCount: Int
    ): Pair<ExperimentResult, ExperimentResult> {
        var wins1 = 0
        var wins2 = 0
        var ties = 0
        var totalScore1 = 0.0
        var totalScore2 = 0.0



        println("=== STARTING EXPERIENT: $gamesCount GAMES ===")
        repeat(gamesCount) { gameIndex ->
            val (score1, score2) = playSingleGame(weights1, weights2)

            when {
                score1 > score2 -> {
                    wins1++
                    totalScore1 += score1
                    totalScore2 += score2
                    println("Game ${gameIndex + 1}: Jarvis wins! ($score1 vs $score2)")
                }

                score2 > score1 -> {
                    wins2++
                    totalScore1 += score1
                    totalScore2 += score2
                    println("Game ${gameIndex + 1}: R2D2 wins! ($score1 vs $score2)")
                }

                else -> {
                    ties++
                    println("Game ${gameIndex + 1}: Tie excluded! ($score1 vs $score2)")
                }
            }
        }

        val decisiveGames = gamesCount - ties
        val averageScore1 = if (decisiveGames > 0) totalScore1 / decisiveGames else 0.0
        val averageScore2 = if (decisiveGames > 0) totalScore2 / decisiveGames else 0.0

        val result1 = ExperimentResult(weights1, decisiveGames, wins1, averageScore1)
        val result2 = ExperimentResult(weights2, decisiveGames, wins2, averageScore2)

        println("\n=== FINAL RESULTS ===")
        println("Games simulated: $gamesCount")
        println("Decisive games counted: $decisiveGames")
        println("Ties excluded: $ties")
        println("Jarvis: ${result1.wins} wins, avg score: ${result1.averageScore}")
        println("R2D2: ${result2.wins} wins, avg score: ${result2.averageScore}")

        if (result1.wins > result2.wins) println(">>> WINNER: Jarvis (weights1)")
        else if (result2.wins > result1.wins) println(">>> WINNER: R2D2 (weights2)")
        else println(">>> TIE!")

        return Pair(result1, result2)
    }


    /**
     * Simulates one complete game between two professional bots.
     *
     * The method creates two bot users, initializes a new Cascadia game with fixed
     * scoring cards, and repeatedly lets the current bot choose and execute one
     * legal action. The current player is advanced after every executed action.
     *
     * When the game reaches the end state, the final scores are evaluated and
     * returned as a pair.
     *
     * @param weights1 Evaluation weights used by the first bot.
     * @param weights2 Evaluation weights used by the second bot.
     * @return A pair containing the final score of the first bot and the final score
     * of the second bot.
     */

    private fun playSingleGame(
        weights1: EvaluationWeights,
        weights2: EvaluationWeights
    ): Pair<Double, Double> {
        val bot1 = User(name = "Jarvis", type = UserType.PROFESSIONAL_BOT)
        val bot2 = User(name = "R2D2", type = UserType.PROFESSIONAL_BOT)


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.FOX)
        )

        rootService.gameService.startNewGame(
            users = listOf(bot1, bot2),
            scoringCards = scoringCards,
        )

        val currentGame = rootService.currentGame

        while (currentGame != null && currentGame.state != GameState.END) {
            val currentUser = currentGame.userList[currentGame.currentUser]
            val weights = if (currentGame.currentUser == 0) weights1 else weights2


            val legalActions = rootService.botService.generateLegalActions(currentGame, currentUser)
            if (legalActions.isEmpty()) break

            val strategy = RealStrategy(
                rootService = rootService,
                iterations = 100,
                rolloutDepth = 5,
                weights = weights
            )

            val action = strategy.chooseAction(legalActions)
            rootService.botService.executeAction(action)
            rootService.gameService.nextUser()
        }


        val game = rootService.currentGame
        if (game != null && game.state == GameState.END) {
            rootService.gameService.evaluateScores()
        }

        return Pair(
            bot1.scorePad.totalPoints.toDouble(),
            bot2.scorePad.totalPoints.toDouble()
        )

    }


}

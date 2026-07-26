package service.bot

import service.RootService

/**
 * Starts a bot experiment by running a series of games with predefined
 * evaluation weights.
 */
fun main() {

    val rootService = RootService()
    val experimentService = BotExperimentService(rootService)

    val weights1 = EvaluationWeights(
        standardCombinationBonus = 2.0,
        nonStandardCombinationBonus = 1.0,
        keyStoneBonus = 3.0,
        habitatEdgeWeight = 2.5,
        wildlifePlacementBonus = 5.0,
        useNatureTokenPenalty = -0.5,
        saveNatureTokenBonus = 1.5
    )
    val weights2 = EvaluationWeights(
        standardCombinationBonus = 3.0,
        nonStandardCombinationBonus = 0.5,
        keyStoneBonus = 5.0,
        habitatEdgeWeight = 2.0,
        wildlifePlacementBonus = 2.5,
        useNatureTokenPenalty = -0.75,
        saveNatureTokenBonus = 1.5
    )

    val results = experimentService.runExperiment(
        weights1 = weights1,
        weights2 = weights2,
        gamesCount = 5
    )

    printAverageScores(results)
}

/**
 * Prints the average scores of both bots and their combined average score.
 *
 * @param results Results returned by [BotExperimentService.runExperiment].
 */
private fun printAverageScores(
    results: Pair<ExperimentResult, ExperimentResult>
) {
    val (jarvisResult, r2d2Result) = results


    println("\n=== AVERAGE SCORES ===")
    println("Jarvis: %.2f".format(jarvisResult.averageScore))
    println("R2D2: %.2f".format(r2d2Result.averageScore))

}

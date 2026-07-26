package service.bot


/**
 * Stores the outcome of one bot experiment run by [service.bot.BotExperimentService]
 *
 * @property weights the [EvaluationWeights] used by the bot in this experiment
 * @property gamesPlayed Total number of games played in the experiment
 * @property wins Number of games won by this bot
 * @property averageScore Average final score across all games that have been played.
 */
data class ExperimentResult(
    val weights: EvaluationWeights,
    val gamesPlayed: Int,
    val wins: Int,
    val averageScore: Double
)
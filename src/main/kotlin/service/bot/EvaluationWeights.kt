package service.bot

/**
 * Hold the tunable weights used by [RealStrategy]
 *
 * Each weight controls how strongly the bot values a specific aspect of a move
 *
 * @property standardCombinationBonus Score bonus for selecting a tile
 * and token from the same display slot
 * @property nonStandardCombinationBonus Score bonus for selecting a tile
 * and token from different display slots
 * @property keyStoneBonus Score bonus for selecting and keystone habitat tile
 * @property habitatEdgeWeight Weight multiplied by the number of matching habitat
 * edges between the placed tile and its neighbors
 * @property wildlifePlacementBonus Score bonus for placing a wildlife token on
 * a tile that accepts that token type
 * @property useNatureTokenPenalty Score penalty when a nature token is spent
 * @property saveNatureTokenBonus Score bonus applied when no nature token was spent
 */



data class EvaluationWeights(
    val standardCombinationBonus:Double = 2.0,
    val nonStandardCombinationBonus: Double = 1.0,
    val keyStoneBonus: Double = 3.0,
    val habitatEdgeWeight: Double = 2.5,
    val wildlifePlacementBonus: Double = 5.0,
    val useNatureTokenPenalty: Double = -0.5,
    val saveNatureTokenBonus: Double = 1.5,
)
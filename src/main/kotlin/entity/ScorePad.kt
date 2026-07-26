package entity

/**
 * Stores all scoring results for a [User].
 *
 * @property pointsByWildLifeToken Points earned per [ScoringCard].
 * @property pointsByHabitatTiles Points from habitat tile patterns.
 * @property pointsByNatureToken Points from remaining nature tokens.
 * @property bonusPoints Additional bonus points.
 * @property totalPoints Final total score.
 */
class ScorePad(
    val pointsByWildLifeToken: MutableMap<ScoringCard, Int> = mutableMapOf(),
    var pointsByHabitatTiles: MutableMap<HabitatTileType, Int> = mutableMapOf(),
    var pointsByNatureToken: Int = 0,
    var bonusPoints: Int = 0,
    var totalPoints: Int = 0,
) {

    /**
     * Creates a deep copy of this scorePad with independent copies of mutable objects.
     *
     * @return A new user instance containing the same values and deeply copied mutable properties.
     */
    fun deepCopy() =
        ScorePad(pointsByWildLifeToken, pointsByHabitatTiles, pointsByNatureToken, bonusPoints, totalPoints)
}
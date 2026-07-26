package entity

/**
 * Represents a habitat tile in the game, including its edges and wildlife tokens allowed to be placed and the
 * actually placed wildlife token.
 *
 * @property keyStone Indicates whether this tile returns a keystone to the [User] if wildlife token is placed.
 * @property rotation Current rotation of the tile in steps 0 to 5.
 * @property edges [HabitatTileType] on each edge of the tile.
 * @property availableWildLifeToken Wildlife tokens that can be placed on this tile.
 * @property placedWildLifeToken Wildlife token currently placed on the tile, if any.
 */
class HabitatTile(
    val keyStone: Boolean = false,
    var rotation: Int = 0,
    val edges: MutableList<HabitatTileType>,
    val availableWildLifeToken: List<WildLifeTokenType> = listOf(),
    var placedWildLifeToken: WildLifeToken? = null,
) {

    /**
     * Creates a deep copy of this habitatTile with independent copies of mutable objects.
     *
     * @return A new user instance containing the same values and deeply copied mutable properties.
     */
    fun deepCopy() = HabitatTile(
        keyStone = keyStone,
        rotation = rotation,
        edges = edges.toMutableList(),
        availableWildLifeToken = availableWildLifeToken,
        placedWildLifeToken = placedWildLifeToken?.let { WildLifeToken(it.type) },
    )
}
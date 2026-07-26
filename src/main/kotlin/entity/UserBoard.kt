package entity

/**
 * Represents a [User]'s board, storing placed [HabitatTile]s and their positions.
 *
 * @property placedHabitatTiles Map of [Coordinate] to placed [HabitatTile].
 * @property tiles List of board positions.
 */
class UserBoard(
    var placedHabitatTiles: MutableMap<Coordinate, HabitatTile> = mutableMapOf(),
    var tiles: MutableList<Pair<Int, Int>> = mutableListOf()
) {

    /**
     * Creates a deep copy of this userBoard with independent copies of mutable objects.
     *
     * @return A new user instance containing the same values and deeply copied mutable properties.
     */
    fun deepCopy(): UserBoard {
        return UserBoard(
            placedHabitatTiles = placedHabitatTiles.mapValues { (_, habitatTile) -> habitatTile.deepCopy() }
                .toMutableMap(),
        ).also {
            it.tiles = tiles.toMutableList()
        }
    }
}
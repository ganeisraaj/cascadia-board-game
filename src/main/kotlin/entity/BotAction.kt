package entity

/**
 * Represents one possible action that can be performed by bot
 *
 * A [BotAction] contains all information needed to execute one full but turn
 *
 *
 * @property habitatTile The habitat tile chosen by the bot (already rotated)
 * @property wildLifeToken The wildlife token type chosen by the bot
 * @property habitatTileIndex The display slot index of the selected habitat tile
 * @property wildLifeTokenIndex The display slot of the selected wildlife token
 * @property habitatPosX The axial q-coordinate where the habitat tile will be placed
 * @property habitatPosY the axial r-coordinate where the habitat tile will be placed
 * @property rotation The number of clockwise rotation steps applied
 * @property wildLifePosX The axial q-coordinate where the wildlife token will be placed
 * @property wildLifePosY The axial r-coordinate where the wildlife token will be placed
 * @property useNatureToken Whether the bot spends a nature token to pick a non-standard combination
 *
 *
 */



data class BotAction(
    val habitatTile : HabitatTile,
    val wildLifeToken: WildLifeTokenType,
    val habitatTileIndex: Int,
    val wildLifeTokenIndex: Int,
    val habitatPosX: Int,
    val habitatPosY: Int,
    val rotation: Int = 0,
    val wildLifePosX: Int,
    val wildLifePosY: Int,
    val useNatureToken: Boolean = false
)
package entity

/**
 * Represents a swap action of a wildlife token in the display.
 *
 * @property oldWildLifeToken The token that was replaced.
 * @property newWildLifeToken The token that replaced the old one.
 * @property displayIndex The index of the replaced token in the display.
 */
class SwapWildLifeToken(
    val oldWildLifeToken: WildLifeToken,
    val newWildLifeToken: WildLifeToken,
    val displayIndex: Int
)
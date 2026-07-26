package entity.action

import entity.HabitatTile
import entity.SwapWildLifeToken
import entity.WildLifeToken

/**
 * Builder for creating an [ActionSelection] object.
 *
 * Stores the selected habitat tile, wildlife token, and additional selection
 * information while a player's action is being constructed.
 *
 * @property habitatTileIndex Index of the selected habitat tile.
 * @property habitatTile Selected [HabitatTile].
 * @property wildlifeTokenIndex Index of the selected wildlife token.
 * @property wildlifeToken Selected [WildLifeToken].
 * @property usedNatureToken Number of nature tokens used.
 * @property swappedWildLifeTokens Wildlife token swaps performed during the action.
 */
class ActionSelectionBuilder(
    var habitatTileIndex: Int? = null,
    var habitatTile: HabitatTile? = null,
    var wildlifeTokenIndex: Int? = null,
    var wildlifeToken: WildLifeToken? = null,
    var usedNatureToken: Int = 0,
    var swappedWildLifeTokens: MutableList<SwapWildLifeToken> = mutableListOf()
)
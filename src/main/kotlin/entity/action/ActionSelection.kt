package entity.action

import entity.HabitatTile
import entity.SwapWildLifeToken
import entity.WildLifeToken

/**
 * Stores the selected tiles and tokens of an action.
 */
data class ActionSelection(
    val habitatTileIndex: Int,
    val habitatTile: HabitatTile,
    val wildlifeTokenIndex: Int,
    val wildlifeToken: WildLifeToken,
    val usedNatureToken: Int = 0,
    val swappedWildLifeTokens: MutableList<SwapWildLifeToken> = mutableListOf()
)
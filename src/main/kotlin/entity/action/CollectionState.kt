package entity.action

import entity.HabitatTile
import entity.WildLifeToken
import tools.aqua.bgw.util.Stack

/**
 * Stores collections before and after an action.
 */
data class CollectionState(
    var oldWildLifeCollection: Stack<WildLifeToken> = Stack(),
    var newWildLifeCollection: Stack<WildLifeToken> = Stack(),
    var oldHabitatTileCollection: Stack<HabitatTile> = Stack(),
    var newHabitatTileCollection: Stack<HabitatTile> = Stack()
)
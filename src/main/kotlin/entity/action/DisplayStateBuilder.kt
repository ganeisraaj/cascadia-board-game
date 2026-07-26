package entity.action

import entity.HabitatTile
import entity.WildLifeToken

/**
 * Builder for creating a [DisplayState] object.
 *
 * Stores habitat and wildlife displays before and after an action to support
 * undo and redo operations.
 *
 * @property oldWildLifeDisplay Wildlife display before the action.
 * @property newWildLifeDisplay Wildlife display after the action.
 * @property oldHabitatDisplay Habitat display before the action.
 * @property newHabitatDisplay Habitat display after the action.
 */
class DisplayStateBuilder(
    var oldWildLifeDisplay: MutableMap<Int, WildLifeToken> = mutableMapOf(),
    var newWildLifeDisplay: MutableMap<Int, WildLifeToken> = mutableMapOf(),
    var oldHabitatDisplay: MutableMap<Int, HabitatTile> = mutableMapOf(),
    var newHabitatDisplay: MutableMap<Int, HabitatTile> = mutableMapOf()
)
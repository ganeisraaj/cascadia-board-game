package entity.action

import entity.HabitatTile
import entity.WildLifeToken

/**
 * Stores shop displays before and after an action.
 */
data class DisplayState(
    val oldWildLifeDisplay: MutableMap<Int, WildLifeToken>,
    val newWildLifeDisplay: MutableMap<Int, WildLifeToken>,
    val oldHabitatDisplay: MutableMap<Int, HabitatTile>,
    val newHabitatDisplay: MutableMap<Int, HabitatTile>
)
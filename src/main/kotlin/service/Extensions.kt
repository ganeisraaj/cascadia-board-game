package service

import entity.Action
import entity.ActionBuilder
import entity.Coordinate
import entity.action.ActionSelection
import entity.action.DisplayState

/**
 * Calculates and returns the 6 immediate neighbors surrounding this hexagon
 * in an axial coordinate grid system.
 *
 * @return A list containing the 6 adjacent Coordinates.
 */
fun Coordinate.getNeighbors(): List<Coordinate> {
    return listOf(
        Coordinate(q + 1, r),     // Right
        Coordinate(q + 1, r - 1), // Top-Right
        Coordinate(q, r - 1),     // Top-Left
        Coordinate(q - 1, r),     // Left
        Coordinate(q - 1, r + 1), // Bottom-Left
        Coordinate(q, r + 1)      // Bottom-Right
    )
}

/**
 * Checks if an action is ready to build without any exceptions.
 *
 * @return True if all required parameters are available, otherwise false.
 */
fun ActionBuilder.isComplete(): Boolean =
    userStates != null &&
            selection.habitatTileIndex != null &&
            selection.habitatTile != null &&
            selection.wildlifeTokenIndex != null &&
            selection.wildlifeToken != null

/**
 * Builds an immutable [Action] object which is ready to be used and stored.
 *
 * @return Immutable [Action] object.
 */
fun ActionBuilder.build(): Action {
    val userStatesChecked = checkNotNull(userStates) {
        "The user states are not defined."
    }

    val habitatTileIndexChecked = checkNotNull(selection.habitatTileIndex) {
        "No habitat tile index has been defined."
    }

    val habitatTileChecked = checkNotNull(selection.habitatTile) {
        "No habitat tile object has been defined."
    }

    val wildlifeTokenIndexChecked = checkNotNull(selection.wildlifeTokenIndex) {
        "No wildlife token index has been defined."
    }

    val wildlifeTokenChecked = checkNotNull(selection.wildlifeToken) {
        "No wildlife token has been defined."
    }

    val actionSelection = ActionSelection(
        habitatTileIndex = habitatTileIndexChecked,
        habitatTile = habitatTileChecked,
        wildlifeTokenIndex = wildlifeTokenIndexChecked,
        wildlifeToken = wildlifeTokenChecked,
        usedNatureToken = selection.usedNatureToken,
        swappedWildLifeTokens = selection.swappedWildLifeTokens
    )

    val displayState = DisplayState(
        oldWildLifeDisplay = displays.oldWildLifeDisplay,
        newWildLifeDisplay = displays.newWildLifeDisplay,
        oldHabitatDisplay = displays.oldHabitatDisplay,
        newHabitatDisplay = displays.newHabitatDisplay
    )

    return Action(
        userStates = userStatesChecked,
        selection = actionSelection,
        collections = collections,
        displays = displayState
    )
}
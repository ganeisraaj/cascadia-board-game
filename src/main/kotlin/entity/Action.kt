package entity

import entity.action.ActionSelection
import entity.action.CollectionState
import entity.action.DisplayState
import entity.action.UserStateChange

/**
 * Represents an action from a [User] and stores the data required for undo and redo operations.
 *
 * @property userStates User states before and after the action.
 * @property selection Selected habitat tile and wildlife token information.
 * @property collections Wildlife and habitat tile collections before and after the action.
 * @property displays Shop displays before and after the action.
 */
class Action(
    val userStates: UserStateChange,
    val selection: ActionSelection,
    val collections: CollectionState,
    val displays: DisplayState
)
package entity

import entity.action.ActionSelectionBuilder
import entity.action.CollectionState
import entity.action.DisplayStateBuilder
import entity.action.UserStateChange

/**
 * Builder class for creating an immutable [Action] object.
 *
 * The parameters are filled step by step as a [User]'s move is performed.
 * The builder stores intermediate states until a complete [Action] can be created.
 *
 * @property userStates User states before and after the action.
 * @property selection Selected habitat tile and wildlife token information.
 * @property collections Wildlife and habitat tile collections before and after the action.
 * @property displays Shop displays before and after the action.
 */
class ActionBuilder(
    var userStates: UserStateChange? = null,
    var selection: ActionSelectionBuilder = ActionSelectionBuilder(),
    var collections: CollectionState = CollectionState(),
    var displays: DisplayStateBuilder = DisplayStateBuilder()
)
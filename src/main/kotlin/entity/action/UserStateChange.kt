package entity.action

import entity.User

/**
 * Stores the user state before and after an action.
 */
data class UserStateChange(
    val oldState: User,
    val newState: User
)
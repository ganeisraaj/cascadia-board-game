package entity

/**
 * Defines the different types of [User]s in the game.
 */
enum class UserType {

    /** A human player local. */
    LOCAL_PLAYER,

    /** A human player online. */
    ONLINE_PLAYER,

    /** A bot making random moves. */
    RANDOM_BOT,

    /** A bot with strategy. */
    PROFESSIONAL_BOT
}
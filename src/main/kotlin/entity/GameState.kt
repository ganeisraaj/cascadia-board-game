package entity

/**
 * Defines the states of a [CascadiaGame] instance.
 */
enum class GameState {

    /** Game setup is in progress. */
    SETUP,

    /** Waiting for the next user's turn. */
    WAIT_FOR_TURN,

    /** Waiting for the current user to make a move. */
    WAIT_FOR_MOVE,

    /** A move is currently being executed. */
    MOVING,

    /** The game is paused. */
    PAUSE,

    /** The game has ended. */
    END
}
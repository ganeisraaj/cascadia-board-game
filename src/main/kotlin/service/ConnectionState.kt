package service
/**
 * Represents the various network connection states in the Cascadia game,
 * as defined in class.jpg, ranging from initial disconnection to game end.
 */
enum class ConnectionState {
    /** The connection is disconnected or has not been established yet. */
    DISCONNECTED,

    /** Connection successfully established. */
    CONNECTED,

    /** Waiting for the host to confirm the join request. */
    WAITING_FOR_HOST_CONFIRMATION,

    /** As a host: Waiting for a guest to join the game. */
    WAITING_FOR_GUEST,

    /** As a guest: Waiting for the host to confirm that the join was successful. */
    WAITING_FOR_JOIN_CONFIRMATION,

    /** Waiting for the game's initialization data. */
    WAITING_FOR_INIT,

    /** The game is running, and it is the local player's turn. */
    WAITING_FOR_PLAYER_TURN,

    /** The game is currently paused. */
   SELECTING,

    /** The game session has ended. */
    PLACING


}

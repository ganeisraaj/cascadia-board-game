package service.network

import edu.udo.cs.sopra.ntf.GameConfigMessage
import edu.udo.cs.sopra.ntf.GameInitMessage
import edu.udo.cs.sopra.ntf.PlaceMessage
import edu.udo.cs.sopra.ntf.SelectMessage
import edu.udo.cs.sopra.ntf.UseNatureTokenMessage
import edu.udo.cs.sopra.ntf.WipeWildlifeMessage
import entity.User
import entity.UserType
import service.ConnectionState
import tools.aqua.bgw.core.BoardGameApplication
import tools.aqua.bgw.net.client.BoardGameClient
import tools.aqua.bgw.net.client.NetworkLogging
import tools.aqua.bgw.net.common.annotations.GameActionReceiver
import tools.aqua.bgw.net.common.notification.PlayerJoinedNotification
import tools.aqua.bgw.net.common.notification.PlayerLeftNotification
import tools.aqua.bgw.net.common.response.CreateGameResponse
import tools.aqua.bgw.net.common.response.CreateGameResponseStatus
import tools.aqua.bgw.net.common.response.JoinGameResponse
import tools.aqua.bgw.net.common.response.JoinGameResponseStatus

/**
 * [BoardGameClient] implementation for network communication.
 *
 * @param playerName the name of the player using this client.
 * @param host the host to connect to.
 * @param secret the secret to use for the connection.
 * @property networkService the [NetworkService] to potentially forward received messages to.
 */
class CascadiaNetworkClient(
    playerName: String,
    host: String,
    secret: String,
    var networkService: NetworkService,
) : BoardGameClient(playerName, host, secret, NetworkLogging.VERBOSE) {

    /** the identifier of this game session; can be null if no session started yet. */
    var sessionID: String? = null

    /** the name of the opponent player; can be null if no message from the opponent received yet */
    var otherPlayerName: String? = null

    /**
     * Handle a [CreateGameResponse] sent by the server. Will await the guest player when its
     * status is [CreateGameResponseStatus.SUCCESS]. As recovery from network problems is not
     * implemented in NetWar, the method disconnects from the server and throws an
     * [IllegalStateException] otherwise.
     *
     * @throws IllegalStateException if status != success or currently not waiting for a game creation response.
     */
    override fun onCreateGameResponse(response: CreateGameResponse) {
        BoardGameApplication.runOnGUIThread {
            check(networkService.connectionState == ConnectionState.WAITING_FOR_HOST_CONFIRMATION)
            { "unexpected CreateGameResponse" }

            when (response.status) {
                CreateGameResponseStatus.SUCCESS -> {
                    sessionID = response.sessionID

                    networkService.handleHostGameSuccess(playerName)
                }

                else -> disconnectAndError(response.status)
            }
        }
    }

    /**
     * Handle a [JoinGameResponse] sent by the server. Will await the init message when its
     * status is [JoinGameResponseStatus.SUCCESS]. As recovery from network problems is not
     * implemented in NetWar, the method disconnects from the server and throws an
     * [IllegalStateException] otherwise.
     *
     * @throws IllegalStateException if status != success or currently not waiting for a join game response.
     */
    override fun onJoinGameResponse(response: JoinGameResponse) {
        BoardGameApplication.runOnGUIThread {
            check(networkService.connectionState == ConnectionState.WAITING_FOR_JOIN_CONFIRMATION)
            { "unexpected JoinGameResponse" }

            when (response.status) {
                JoinGameResponseStatus.SUCCESS -> {

                    /*
                    If host is not in the game anymore,
                    no player could start the game so joining would be meaningless
                     */
                    if (response.opponents.isEmpty()) return@runOnGUIThread

                    otherPlayerName = response.opponents[0]
                    sessionID = response.sessionID

                    networkService.handleJoinGameSuccess(playerName, response.opponents)

                }

                else -> disconnectAndError(response.status)
            }
        }
    }

    /**
     * Handle a [PlayerJoinedNotification] sent by the server.
     *
     * @throws IllegalStateException if not currently expecting any guests to join.
     */
    override fun onPlayerJoined(notification: PlayerJoinedNotification) {
        BoardGameApplication.runOnGUIThread {
            check(
                networkService.connectionState in listOf(
                    ConnectionState.WAITING_FOR_GUEST,
                    ConnectionState.WAITING_FOR_INIT
                )
            ) { "not awaiting any guests." }

            otherPlayerName = notification.sender

            networkService.onPlayerJoined(notification.sender, false)
        }
    }

    /**
     * Handle a [PlayerJoinedNotification] sent by the server.
     *
     * @throws IllegalStateException if not currently expecting any guests to join.
     */
    override fun onPlayerLeft(notification: PlayerLeftNotification) {
        BoardGameApplication.runOnGUIThread {
            otherPlayerName = null
            networkService.onPlayerLeft(otherPlayerName ?: "unknown")
        }
    }

    /**
     * Handles the received game configuration and initializes the players
     * and scoring cards for the current game.
     *
     * @param message Contains the game configuration data received from the server.
     */
    @Suppress("UNUSED_PARAMETER", "unused")
    @GameActionReceiver
    fun onGameConfigReceived(message: GameConfigMessage, sender: String) {
        BoardGameApplication.runOnGUIThread {
            check(
                networkService.connectionState in listOf(
                    ConnectionState.WAITING_FOR_GUEST,
                    ConnectionState.WAITING_FOR_INIT
                )
            ) { "not awaiting config." }

            val players = message.players.map {
                if (it == playerName) User(name = it, type = UserType.LOCAL_PLAYER) else User(
                    name = it,
                    type = UserType.ONLINE_PLAYER
                )
            }
            val scoreCards = message.scoringCards

            println("SCORECARDS")

            for (card in scoreCards) println(card.toString())

            networkService.onGameConfigReceived(players, scoreCards.filterNotNull())
        }

    }

    /**
     * handle a [GameInitMessage] sent by the server
     */
    @Suppress("UNUSED_PARAMETER", "unused")
    @GameActionReceiver
    fun onInitReceived(message: GameInitMessage, sender: String) {
        BoardGameApplication.runOnGUIThread {
            networkService.startNewJoinedGame(
                message,
                userName = playerName,
                otherUserName = otherPlayerName ?: "unknown",
            )
        }
    }

    /**
     * handle a [WipeWildlifeMessage] sent by the server
     */
    @Suppress("UNUSED_PARAMETER", "unused")
    @GameActionReceiver
    fun onWipeWildlifeReceived(message: WipeWildlifeMessage, sender: String) {
        BoardGameApplication.runOnGUIThread {
            networkService.onWipeWildlifeReceived(
                message
            )
        }
    }

    /**
     * handle a [SelectMessage] sent by the server
     */
    @Suppress("UNUSED_PARAMETER", "unused")
    @GameActionReceiver
    fun onSelectWildlifeReceived(message: SelectMessage, sender: String) {
        BoardGameApplication.runOnGUIThread {
            networkService.onSelectReceived(
                message
            )
        }
    }

    /**
     * handle a [PlaceMessage] sent by the server
     */
    @Suppress("UNUSED_PARAMETER", "unused")
    @GameActionReceiver
    fun onPlaceWildlifeReceived(message: PlaceMessage, sender: String) {
        BoardGameApplication.runOnGUIThread {
            networkService.onPlaceActionReceived(
                message
            )
        }
    }

    /**
     * handle a [edu.udo.cs.sopra.ntf.UseNatureTokenMessage] sent by the server
     */
    @Suppress("UNUSED_PARAMETER", "unused")
    @GameActionReceiver
    fun onUseNatureTokenReceived(message: UseNatureTokenMessage, sender: String) {
        BoardGameApplication.runOnGUIThread {
            networkService.onUseNatureTokenReceived()
        }
    }

    /**
     * Disconnects from the server and throws an error with the given message.
     *
     * @param message Error message or object describing the cause of the error.
     */
    fun disconnectAndError(message: Any) {
        networkService.disconnect()
        error(message)
    }
}
package service

import edu.udo.cs.sopra.ntf.*
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import service.network.CascadiaNetworkClient
import service.network.NetworkService
import tools.aqua.bgw.core.BoardGameApplication
import tools.aqua.bgw.net.common.notification.PlayerJoinedNotification
import tools.aqua.bgw.net.common.notification.PlayerLeftNotification
import tools.aqua.bgw.net.common.response.CreateGameResponse
import tools.aqua.bgw.net.common.response.CreateGameResponseStatus
import tools.aqua.bgw.net.common.response.JoinGameResponse
import tools.aqua.bgw.net.common.response.JoinGameResponseStatus

/**
 * Test suite for [CascadiaNetworkClient].
 *
 * Verifies the network communication layer, including inbound message handlers,
 * server response callbacks, and game state initialization hooks.
 */
class CascadiaNetworkClientTest {

    private lateinit var networkServiceMock: NetworkService
    private lateinit var client: CascadiaNetworkClient

    /**
     * sets everything up for tests
     */
    @BeforeEach
    fun setUp() {
        // Mock the high-level NetworkService dependency
        networkServiceMock = mockk(relaxed = true)

        // Initialize the client under test
        client = CascadiaNetworkClient(
            playerName = "Alice",
            host = "localhost",
            secret = "secret123",
            networkService = networkServiceMock
        )

        mockkObject(BoardGameApplication.Companion)
        every { BoardGameApplication.runOnGUIThread(any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
        }
    }

    /**
     * Resets the testing environment after each test case.
     */
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that when a game is successfully hosted, the client stores
     * the session ID and invokes the host success callback on the service layer.
     */
    @Test
    fun createSuccess() {
        every { networkServiceMock.connectionState } returns ConnectionState.WAITING_FOR_HOST_CONFIRMATION

        val response = CreateGameResponse(
            status = CreateGameResponseStatus.SUCCESS,
            sessionID = "session-xyz"
        )

        client.onCreateGameResponse(response)

        // Assert
        assertEquals("session-xyz", client.sessionID)
        verify(exactly = 1) { networkServiceMock.handleHostGameSuccess("Alice") }
    }

    /**
     * Verifies that when a game creation fails, the client automatically
     * disconnects from the server and throws an IllegalStateException.
     */
    @Test
    fun createFailure() {
        every { networkServiceMock.connectionState } returns ConnectionState.WAITING_FOR_HOST_CONFIRMATION

        val response = CreateGameResponse(
            status = CreateGameResponseStatus.GAME_ID_DOES_NOT_EXIST,
            sessionID = null
        )

        // Act & Assert
        assertThrows<IllegalStateException> {
            client.onCreateGameResponse(response)
        }
        verify(exactly = 1) { networkServiceMock.disconnect() }
    }

    /**
     * Forces an invalid status state on game creation response
     */
    @Test
    fun createStateError() {
        every { networkServiceMock.connectionState } returns ConnectionState.DISCONNECTED
        val response = CreateGameResponse(CreateGameResponseStatus.SUCCESS, "session-xyz")

        assertThrows<IllegalStateException> {
            client.onCreateGameResponse(response)
        }
    }

    /**
     * Verifies that when a guest successfully joins a match, the opponent's name
     * and session details are stashed locally and passed down to initialize the game.
     */
    @Test
    fun joinSuccess() {
        // Arrange
        every { networkServiceMock.connectionState } returns ConnectionState.WAITING_FOR_JOIN_CONFIRMATION

        val response = JoinGameResponse(
            status = JoinGameResponseStatus.SUCCESS,
            message = "Guest successfully authenticated and joined.",
            sessionID = "join-session-123",
            opponents = listOf("Bob")
        )

        client.onJoinGameResponse(response)

        // Assert
        assertEquals("join-session-123", client.sessionID)
        assertEquals("Bob", client.otherPlayerName)
        verify(exactly = 1) {
            networkServiceMock.handleJoinGameSuccess("Alice", listOf("Bob"))
        }
    }

    /**
     * Verifies that if an empty array of opponents is sent by the server,
     * the join handler hits the early loop exit branch cleanly.
     */
    @Test
    fun joinEmptyOpponents() {
        every { networkServiceMock.connectionState } returns ConnectionState.WAITING_FOR_JOIN_CONFIRMATION
        val response = JoinGameResponse(
            status = JoinGameResponseStatus.SUCCESS,
            opponents = emptyList(),
            sessionID = "join-123",
            message = "ok"
        )

        client.onJoinGameResponse(response)

        assertNull(client.otherPlayerName)
        verify(exactly = 0) { networkServiceMock.handleJoinGameSuccess(any(), any()) }
    }

    /**
     * Verifies that when a join request fails, the framework automatically
     * drops the connection and raises an exception.
     */
    @Test
    fun joinFailure() {
        every { networkServiceMock.connectionState } returns ConnectionState.WAITING_FOR_JOIN_CONFIRMATION
        val response = JoinGameResponse(
            status = JoinGameResponseStatus.SERVER_ERROR,
            opponents = emptyList(),
            sessionID = null,
            message = "fail"
        )

        assertThrows<IllegalStateException> {
            client.onJoinGameResponse(response)
        }
        verify(exactly = 1) { networkServiceMock.disconnect() }
    }

    /**
     * Forces an invalid context status on joining to trigger coverage
     * for the unexpected response string check description block.
     */
    @Test
    fun joinStateError() {
        every { networkServiceMock.connectionState } returns ConnectionState.DISCONNECTED
        val response = JoinGameResponse(
            status = JoinGameResponseStatus.SUCCESS,
            opponents = listOf("Bob"),
            sessionID = "id",
            message = "ok"
        )

        // Act & Assert
        assertThrows<IllegalStateException> {
            client.onJoinGameResponse(response)
        }
    }

    /**
     * Verifies that when a notification arrives indicating another player has
     * entered the lobby, their identity is mapped into the active session lists.
     */
    @Test
    fun playerJoinedSuccess() {
        every { networkServiceMock.connectionState } returns ConnectionState.WAITING_FOR_GUEST
        val notification = mockk<PlayerJoinedNotification> { every { sender } returns "Bob" }

        client.onPlayerJoined(notification)

        // Assert
        assertEquals("Bob", client.otherPlayerName)
        verify(exactly = 1) { networkServiceMock.onPlayerJoined("Bob", false) }
    }

    /**
     * Covers validation boundaries preventing player entry signals from executing
     * outside expected match configurations.
     */
    @Test
    fun playerJoinedStateError() {
        every { networkServiceMock.connectionState } returns ConnectionState.CONNECTED
        val notification = mockk<PlayerJoinedNotification> { every { sender } returns "Bob" }

        assertThrows<IllegalStateException> {
            client.onPlayerJoined(notification)
        }
    }

    /**
     * Verifies player exit updates, covering the fallback to "unknown" when
     * handling disconnection parameters.
     */
    @Test
    fun playerLeft() {
        client.otherPlayerName = "Bob"
        val notification = mockk<PlayerLeftNotification>()

        client.onPlayerLeft(notification)

        assertNull(client.otherPlayerName)
        verify(exactly = 1) { networkServiceMock.onPlayerLeft("unknown") }
    }

    /**
     * Verifies parsing loops processing config configurations during initial loading phases.
     */
    @Test
    fun configSuccess() {
        every { networkServiceMock.connectionState } returns ConnectionState.WAITING_FOR_INIT
        val messageMock = mockk<GameConfigMessage> {
            every { players } returns listOf("Alice", "Bob")
            every { scoringCards } returns listOf(true, false)
        }

        client.onGameConfigReceived(messageMock, "Bob")

        verify(exactly = 1) {
            networkServiceMock.onGameConfigReceived(
                any(), listOf(true, false)
            )
        }
    }

    /**
     * Ensures that late setup configurations injected outside setup steps
     * are locked out by security guards.
     */
    @Test
    fun configStateError() {
        every { networkServiceMock.connectionState } returns ConnectionState.WAITING_FOR_PLAYER_TURN
        val messageMock = mockk<GameConfigMessage>()

        assertThrows<IllegalStateException> {
            client.onGameConfigReceived(messageMock, "Bob")
        }
    }

    /**
     * Verifies that the onInitReceived network action receiver safely interceptor
     * captures the startup configurations and routes them to build out the joined board state.
     */
    @Test
    fun initReceived() {
        val mockInitMessage = mockk<GameInitMessage>()
        client.otherPlayerName = "Bob"

        client.onInitReceived(mockInitMessage, sender = "Bob")

        verify(exactly = 1) {
            networkServiceMock.startNewJoinedGame(mockInitMessage, "Alice", "Bob")
        }
    }

    /**
     * Verifies that inbound layout scrub events clear item configurations synchronously.
     */
    @Test
    fun wipeReceived() {
        val message = mockk<WipeWildlifeMessage>()

        client.onWipeWildlifeReceived(message, "Bob")

        // Assert
        verify(exactly = 1) { networkServiceMock.onWipeWildlifeReceived(message) }
    }

    /**
     * Verifies selection frame alignment actions when choices lock across slots.
     */
    @Test
    fun selectReceived() {
        val message = mockk<SelectMessage>()

        client.onSelectWildlifeReceived(message, "Bob")

        verify(exactly = 1) { networkServiceMock.onSelectReceived(message) }
    }

    /**
     * Verifies board configuration steps handling placements across hex tiles.
     */
    @Test
    fun placeReceived() {
        val message = mockk<PlaceMessage>()

        client.onPlaceWildlifeReceived(message, "Bob")

        verify(exactly = 1) { networkServiceMock.onPlaceActionReceived(message) }
    }

    /**
     * Verifies token transactions tracking spending profiles across nature items.
     */
    @Test
    fun natureReceived() {
        val message = mockk<UseNatureTokenMessage>()

        client.onUseNatureTokenReceived(message, "Bob")

        // Assert
        verify(exactly = 1) { networkServiceMock.onUseNatureTokenReceived() }
    }
}
package service

import edu.udo.cs.sopra.ntf.GameInitMessage
import edu.udo.cs.sopra.ntf.NetPlayer
import edu.udo.cs.sopra.ntf.NetWildlife
import edu.udo.cs.sopra.ntf.SelectWildlifeMessage
import entity.GameState
import entity.UserType
import entity.WildLifeTokenType
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import service.network.CascadiaNetworkClient
import service.network.NetworkService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import entity.action.ActionSelectionBuilder
import edu.udo.cs.sopra.ntf.ChatMessage
import edu.udo.cs.sopra.ntf.GameConfigMessage
import edu.udo.cs.sopra.ntf.PlaceMessage
import edu.udo.cs.sopra.ntf.RotationMessage
import edu.udo.cs.sopra.ntf.SelectHabitatTileMessage
import edu.udo.cs.sopra.ntf.SelectMessage
import edu.udo.cs.sopra.ntf.WipeWildlifeMessage
import edu.udo.cs.sopra.ntf.UseNatureTokenMessage
import entity.ActionBuilder
import entity.CascadiaGame
import entity.Coordinate
import entity.HabitatTile
import entity.HabitatTileType
import entity.User
import entity.WildLifeToken
import kotlin.test.assertFailsWith
import entity.action.CollectionState
import entity.action.UserStateChange
import tools.aqua.bgw.util.Stack

/**
 * Test suite for [NetworkService].
 *
 * This class uses MockK to mock network dependencies and reflection to manipulate
 * private fields and states within the [NetworkService]. It ensures proper
 * connectivity, game session management, and state transitions during
 * network operations.
 */
class NetworkServiceTest {

    private lateinit var rootService: RootService
    private lateinit var networkService: NetworkService
    private val mockClient = mockk<CascadiaNetworkClient>(relaxed = true)
    private val mockRefreshable = mockk<Refreshable>(relaxed = true)

    /**
     * sets everything up for tests
     */
    @BeforeEach
    fun setup() {
        rootService = mockk()
        // Create the service and inject the mocked client, if possible.
        networkService = spyk(NetworkService(rootService))
        networkService.addRefreshable(mockRefreshable)
        val field = NetworkService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(networkService, mockClient)
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.DISCONNECTED)

        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, null)

        // Inject the mocked client.
        every { networkService.client } returns mockClient


    }

    /**
     * Verifies that [NetworkService.hostGame] correctly initiates a game session
     * when a session ID is provided.
     *
     * This test simulates a successful connection by mocking [NetworkService.connect]
     * and using reflection to inject the [mockClient]. It asserts that
     * [CascadiaNetworkClient.createGame] is invoked with the expected
     * game ID, the provided session ID, and the welcome message.
     */
    @Test
    fun `hostGame should call createGame with sessionId and three arguments`() {
        every { networkService.connect(any(), any()) } answers {
            // Inject the mocked client into the private field using reflection.
            val field = NetworkService::class.java.getDeclaredField("client")
            field.isAccessible = true
            field.set(networkService, mockClient)

            // Return true to simulate a successful connection and allow hostGame to proceed.
            true
        }

        // Act
        networkService.hostGame("secret", "Player1", "session123")

        // Assert
        verify(exactly = 1) {
            mockClient.createGame(
                NetworkService.GAME_ID, "session123", "Welcome!"
            )
        }
    }

    /**
     * Verifies that [NetworkService.hostGame] correctly initiates a game session
     * when no session ID is provided (null).
     *
     * This test mocks [NetworkService.connect] to simulate a successful connection
     * and uses reflection to inject the [mockClient]. It asserts that
     * [CascadiaNetworkClient.createGame] is invoked with the expected
     * game ID and the welcome message, specifically omitting the session ID.
     */
    @Test
    fun `hostGame should call createGame with two arguments when no session ID is provided`() {
        // Arrange: Use 'answers' to inject the mocked client into the service.
        every { networkService.connect(any(), any()) } answers {
            // Use reflection to access and set the private 'client' field.
            val field = NetworkService::class.java.getDeclaredField("client")
            field.isAccessible = true
            field.set(networkService, mockClient)
            true
        }

        // Act
        networkService.hostGame(secret = "secret", name = "Player1", sessionID = null)
        // Assert
        verify(exactly = 1) {
            mockClient.createGame(NetworkService.GAME_ID, "Welcome!")
        }
    }

    /**
     * Verifies that [NetworkService.hostGame] throws an [IllegalStateException]
     * when the network connection cannot be established.
     *
     * This test mocks [NetworkService.connect] to return `false`, simulating a
     * failed connection attempt. It asserts that the exception is thrown and
     * verifies that [CascadiaNetworkClient.createGame] is never called.
     */
    @Test
    fun `hostGame should throw IllegalStateException if the connection fails`() {
        // Arrange
        every { networkService.connect(any(), any()) } returns false

        // Act & Assert
        assertThrows<IllegalStateException> {
            networkService.hostGame("secret", "Player1", null)
        }
        verify(exactly = 0) { mockClient.createGame(any(), any(), any()) }
    }

    /**
     * Verifies that [NetworkService.handleHostGameSuccess] correctly updates the service
     * state after successfully hosting a game.
     *
     * This test asserts that:
     * 1. The [ConnectionState] is set to [ConnectionState.WAITING_FOR_GUEST].
     * 2. The host player is correctly added to the internal player list with the status [UserType.LOCAL_PLAYER].
     * 3. The associated [Refreshable] component is notified via [Refreshable.refreshAfterHostSuccessful].
     */
    @Test
    fun handleHostGameShouldUpdateGameStateOnSuccess() {
        // Arrange
        val username = "TestPlayer"

        networkService.handleHostGameSuccess(username)

        // Assert

        // 1. Verify that the connection state was updated correctly.
        assertEquals(ConnectionState.WAITING_FOR_GUEST, networkService.connectionState)

        // 2. Verify that the player was added to the player list.
        val player = networkService.players.find { it.name == username }
        assertEquals(true, player?.type == UserType.LOCAL_PLAYER)

        // 3. Verify that the refresh callback was invoked.
        verify(exactly = 1) { mockRefreshable.refreshAfterHostSuccessful() }
    }

    /**
     * Verifies that [NetworkService.updateConnectionState] correctly updates the
     * internal connection state and notifies the UI/Refreshable components.
     *
     * This test asserts that:
     * 1. The `connectionState` of the [NetworkService] is set to the provided [ConnectionState].
     * 2. The [Refreshable] mock is triggered exactly once via
     *    [Refreshable.refreshAfterConnectionStateChanged] to propagate the state change.
     */
    @Test
    fun `updateConnectionState should update the connection state and notify refreshables`() {
        // Arrange
        val newState = ConnectionState.CONNECTED

        // Act
        networkService.updateConnectionState(newState)

        // Assert

        // 1. Verify that the connection state was updated correctly.
        assertEquals(newState, networkService.connectionState)

        // 2. Verify that the refresh callback was invoked with the updated state.
        verify(exactly = 1) {
            mockRefreshable.refreshAfterConnectionStateChanged(newState)
        }
    }

    /**
     * Verifies that [NetworkService.disconnect] properly closes the network session
     * and resets the internal state.
     *
     * This test uses reflection to inject the [mockClient] and asserts that:
     * 1. The client sends a "Goodbye!" message via [CascadiaNetworkClient.leaveGame].
     * 2. The client is explicitly disconnected via [CascadiaNetworkClient.disconnect].
     * 3. The `connectionState` is successfully updated to [ConnectionState.DISCONNECTED].
     */
    @Test
    fun `disconnect should close the client and set the connection state to DISCONNECTED`() {

        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        every { mockClient.sessionID } returns "someSessionID"
        every { mockClient.isOpen } returns true

        // Act
        networkService.disconnect()

        // Assert
        verify(exactly = 1) { mockClient.leaveGame("Goodbye!") }
        verify(exactly = 1) { mockClient.disconnect() }

        assertEquals(ConnectionState.DISCONNECTED, networkService.connectionState)
    }

    /**
     * Verifies that [NetworkService.joinGame] successfully initiates a request to
     * join a game session.
     *
     * This test mocks [NetworkService.connect] to simulate a successful connection
     * and verifies that:
     * 1. The client sends a join request using [CascadiaNetworkClient.joinGame]
     *    with the specified session ID and a welcome message.
     * 2. The `connectionState` of the [NetworkService] is correctly updated to
     *    [ConnectionState.WAITING_FOR_JOIN_CONFIRMATION].
     */
    @Test
    fun `joinGame should call joinGame and update the connection state on success`() {
        // Arrange
        val secret = "secret"
        val name = "Guest"
        val sessionID = "session123"

        every { networkService.connect(any(), any()) } answers {
            // Inject the mocked client via reflection.
            val field = NetworkService::class.java.getDeclaredField("client")
            field.isAccessible = true
            field.set(networkService, mockClient)
            true
        }

        // Act
        networkService.joinGame(secret, name, sessionID)

        // Assert

        // 1. Verify that joinGame was called on the client.
        verify(exactly = 1) { mockClient.joinGame(sessionID, "Hello!") }

        // 2. Verify that the connection state was updated correctly.
        assertEquals(ConnectionState.WAITING_FOR_JOIN_CONFIRMATION, networkService.connectionState)
    }

    /**
     * Verifies that [NetworkService.joinGame] throws an [IllegalStateException]
     * when the network connection cannot be established.
     *
     * This test mocks [NetworkService.connect] to return `false`, simulating a
     * failed connection attempt. It asserts that the exception is thrown and
     * verifies that [CascadiaNetworkClient.joinGame] is never invoked.
     */
    @Test
    fun `joinGame should throw IllegalStateException if the connection fails`() {
        // Arrange
        every { networkService.connect(any(), any()) } returns false

        // Act & Assert
        assertThrows<IllegalStateException> {
            networkService.joinGame("secret", "Guest", "session123")
        }

        // Verify that joinGame was never called.
        verify(exactly = 0) { mockClient.joinGame(any(), any()) }
    }

    /**
     * Verifies that [NetworkService.handleJoinGameSuccess] correctly processes
     * the successful joining of a game.
     *
     * This test asserts that:
     * 1. The `connectionState` is updated to [ConnectionState.WAITING_FOR_INIT].
     * 2. Both the local user and the list of opponents are correctly added to the
     *    internal player list.
     * 3. The associated [Refreshable] component is notified via
     *    [Refreshable.refreshAfterJoinSuccessful].
     */
    @Test
    fun `handleJoinGameSuccess should update the connection state, add all players, and notify refreshables`() {
        // Arrange
        val localUser = "Guest"
        val opponents = listOf("Enemy1", "Enemy2")

        // Act
        networkService.handleJoinGameSuccess(localUser, opponents)

        // Assert

        // 1. Verify that the connection state was updated correctly.
        assertEquals(ConnectionState.WAITING_FOR_INIT, networkService.connectionState)

        // 2. Verify that all players (the local player and all opponents) were added.
        val allNames = networkService.players.map { it.name }
        assertTrue(
            allNames.contains(localUser),
            "The local player should be in the player list."
        )
        assertTrue(
            allNames.containsAll(opponents),
            "All opponents should be in the player list."
        )

        // 3. Verify that the refresh callback was invoked.
        verify(exactly = 1) { mockRefreshable.refreshAfterJoinSuccessful() }
    }

    /**
     * Verifies that [NetworkService.connect] successfully initializes the connection
     * with valid credentials.
     *
     * This test ensures that:
     * 1. The internal state and client reference are properly initialized before connection.
     * 2. [CascadiaNetworkClient] is correctly instantiated and its connection
     *    process returns `true`.
     * 3. The `client` instance within [NetworkService] is successfully set
     *    (not null) upon a successful connection.
     */
    @Test
    fun `connect should return true with valid credentials and initialize the client`() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.DISCONNECTED)

        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, null)

        mockkConstructor(CascadiaNetworkClient::class)
        every { anyConstructed<CascadiaNetworkClient>().connect() } returns true

        val result = networkService.connect("secret", "Player1")

        // Assert
        assertTrue(result, "connect should return true on success")
        assertNotNull(networkService.client, "client should be initialized")

        unmockkConstructor(CascadiaNetworkClient::class)
    }

    /**
     * Verifies that [NetworkService.connect] returns `false` and ensures the internal
     * client remains `null` when the underlying [CascadiaNetworkClient] fails to connect.
     *
     * This test mocks the constructor of [CascadiaNetworkClient] to simulate a
     * failed connection attempt (`connect()` returns `false`). It asserts that the
     * service correctly reports the failure and does not maintain an invalid client
     * instance.
     */
    @Test
    fun `connect should return false if the client connection fails`() {
        mockkConstructor(CascadiaNetworkClient::class)
        every { anyConstructed<CascadiaNetworkClient>().connect() } returns false

        val result = networkService.connect("secret", "Player1")

        // Debug output: Inspect the actual client value stored in the service.
        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        val clientValue = clientField.get(networkService)
        println("Debug: Current client value: $clientValue") // Check the test console

        assertFalse(result)
        assertNull(clientValue, "Client should be null, but was $clientValue")

        unmockkConstructor(CascadiaNetworkClient::class)
    }

    /**
     * Verifies that [NetworkService.connect] throws an [IllegalArgumentException]
     * when provided with invalid inputs, such as empty secrets or blank usernames.
     *
     * This test ensures input validation by asserting that an exception is raised
     * when either the secret is an empty string or the username consists only
     * of whitespace.
     */
    @Test
    fun `connect should throw IllegalArgumentException if secret or name are blank`() {
        assertThrows<IllegalArgumentException> {
            networkService.connect("", "Name")
        }
        assertThrows<IllegalArgumentException> {
            networkService.connect("Secret", "  ")
        }
    }

    /**
     * Verifies that [NetworkService.startNewJoinedGame] correctly initializes the game state
     * and transitions the [ConnectionState] to [ConnectionState.WAITING_FOR_PLAYER_TURN].
     *
     * This test ensures that:
     * 1. The game instance is correctly created within the [RootService] based on the
     *    provided [GameInitMessage].
     * 2. All game properties, such as user lists, game states, nature tokens,
     *    and displayed board elements, are initialized with expected values.
     * 3. The service state is successfully updated to reflect that the game is ready
     *    for player turns.
     */
    @Test
    fun `startNewJoinedGame should initialize the game and set the state to WAITING_FOR_PLAYER_TURN`() {
        fun testStartNewJoinedGame() {
            networkService.updateConnectionState(ConnectionState.WAITING_FOR_INIT)

            val message = GameInitMessage(
                players = listOf(
                    NetPlayer("Alice", 0),
                    NetPlayer("Bob", 1)
                ),
                tileStack = (0 until 100).toList(),
                wildlifeBag = listOf(
                    NetWildlife.ELK,
                    NetWildlife.ELK,
                    NetWildlife.HAWK,
                    NetWildlife.FOX,
                    NetWildlife.SALMON
                ),
                scoringCards = listOf(false, false, false, false, false)
            )
            networkService.startNewJoinedGame(
                message,
                "Alice",
                "Bob"
            )

            val game = rootService.currentGame

            assertNotNull(game)
            assertEquals(2, game.userList.size)
            assertEquals("Alice", game.userList[0].name)
            assertEquals("Bob", game.userList[1].name)

            assertEquals(GameState.WAIT_FOR_TURN, game.state)
            assertEquals(20, game.natureToken)
            assertEquals(4, game.displayedHabitatTiles.size)
            assertEquals(4, game.displayedWildLifeToken.size)

            assertEquals(
                ConnectionState.WAITING_FOR_PLAYER_TURN,
                networkService.connectionState
            )
        }
    }

    /**
     * Verifies that [NetworkService.startNewJoinedGame] throws an [IllegalStateException]
     * if the service is not in the correct [ConnectionState] (e.g., when trying to
     * initialize a game while disconnected).
     *
     * This test uses reflection to force the service into a [ConnectionState.DISCONNECTED]
     * state and ensures that invoking game initialization results in the expected exception.
     */
    @Test
    fun `startNewJoinedGame throws Exception when state is false`() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.DISCONNECTED)

        val message = mockk<GameInitMessage>(relaxed = true)

        // Act & Assert
        assertThrows<IllegalStateException> {
            networkService.startNewJoinedGame(message, "Guest", "Host")
        }
    }

    /**
     * Verifies that [NetworkService.sendSelectWildlife] successfully transmits the
     * selection action when the service is in the [ConnectionState.SELECTING] state.
     *
     * This test uses reflection to set the required connection state and inject the
     * [mockClient]. It asserts that exactly one [SelectWildlifeMessage] is sent via
     * [CascadiaNetworkClient.sendGameActionMessage].
     */
    @Test
    fun `sendSelectWildlife sends message when State is SELECTING`() {
        // Arrange
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)

        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        // Act
        networkService.sendSelectWildlife(2)

        // Assert
        verify(exactly = 1) {
            mockClient.sendGameActionMessage(any<SelectWildlifeMessage>())
        }
    }

    /**
     * Verifies that [NetworkService.loadCsvLines] successfully reads content from
     * a specified CSV file.
     *
     * This test uses reflection to invoke the private `loadCsvLines` method and
     * asserts that the resulting list is neither null nor empty, ensuring that
     * the file I/O operations are functioning as expected for the given resource.
     */
    @Test
    fun `loadCsvLines reads context from data`() {

        val fileName = "csv/tiles.csv"


        val method = NetworkService::class.java.getDeclaredMethod(
            "loadCsvLines",
            String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(networkService, fileName) as List<String>

        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "The list should not be empty when the file exists")
    }

    /**
     * Verifies that [NetworkService.loadCsvLines] gracefully handles missing files
     * by returning an empty list.
     *
     * This test uses reflection to invoke the private `loadCsvLines` method with
     * a non-existent file path and asserts that the returned list is empty,
     * ensuring that the service does not throw an exception when a resource is missing.
     */
    @Test
    fun `loadCsvLines shows empty list if files are missing`() {

        val nonExistentFile = "gibts_nicht.csv"


        val method = NetworkService::class.java.getDeclaredMethod(
            "loadCsvLines",
            String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(networkService, nonExistentFile) as List<String>


        assertTrue(result.isEmpty(), "An empty list should be returned if the file does not exist")
    }

    /**
     * Verifies that [NetworkService.charToWildLifeType] correctly maps single characters
     * to their corresponding [WildLifeTokenType] values.
     *
     * This test uses reflection to invoke the private helper method and asserts that
     * the characters 'B', 'E', 'S', 'H', and 'F' are correctly converted into
     * [WildLifeTokenType.BEAR], [WildLifeTokenType.ELK], [WildLifeTokenType.SALMON],
     * [WildLifeTokenType.HAWK], and [WildLifeTokenType.FOX], respectively.
     */
    @Test
    fun `charToWildLifeType to WildLifeTokenType`() {
        // Invoke the private method using reflection.
        val method = NetworkService::class.java.getDeclaredMethod("charToWildLifeType",
            Char::class.java)
        method.isAccessible = true

        // Verify that all valid character mappings are handled correctly.
        assertEquals(WildLifeTokenType.BEAR, method.invoke(networkService, 'B'))
        assertEquals(WildLifeTokenType.ELK, method.invoke(networkService, 'E'))
        assertEquals(WildLifeTokenType.SALMON, method.invoke(networkService, 'S'))
        assertEquals(WildLifeTokenType.HAWK, method.invoke(networkService, 'H'))
        assertEquals(WildLifeTokenType.FOX, method.invoke(networkService, 'F'))
    }

    /**
     * Verifies that [NetworkService.charToWildLifeType] throws an [IllegalArgumentException]
     * when provided with an invalid character code.
     *
     * This test uses reflection to invoke the private helper method. Since [java.lang.reflect.Method.invoke]
     * wraps underlying exceptions in an InvocationTargetException, the test asserts that the
     * root cause of the invocation error is the expected [IllegalArgumentException].
     */
    @Test
    fun `charToWildLifeType should throw exception for unallowed character`() {

        val method = NetworkService::class.java.getDeclaredMethod("charToWildLifeType",
            Char::class.java)
        method.isAccessible = true

        val exception = assertThrows<java.lang.reflect.InvocationTargetException> {
            method.invoke(networkService, 'X') // 'X' represents an invalid character
        }

        assertTrue(
            exception.cause is IllegalArgumentException,
            "An IllegalArgumentException should be thrown"
        )
    }

    /**
     * Helper: builds a minimal but valid [CascadiaGame] with one user and a
     * pre-selected habitat tile / wildlife token in `currentAction.selection`.
     */
    private fun buildTestGame(): Pair<CascadiaGame, User> {
        val tile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )
        val user = User(name = "Alice")
        val game = CascadiaGame(
            currentUser = 0,
            userList = listOf(user),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = emptyList(),
        ).apply {
            this.currentAction = ActionBuilder(
                selection = ActionSelectionBuilder(
                    habitatTile = tile,
                    wildlifeToken = WildLifeToken(WildLifeTokenType.BEAR)
                )
            )
        }
        return game to user
    }

    /**
     * Verifies that [NetworkService.sendPlaceAction] forwards a [PlaceMessage]
     * to the client via [CascadiaNetworkClient.sendGameActionMessage].
     *
     * The `client` field is re-injected here via reflection because [setup]
     * ends by setting it back to `null`, and internal calls inside
     * [NetworkService] bypass the spy's stubbed getter.
     */
    @Test
    fun testSendPlaceAction() {
        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        networkService.sendPlaceAction(posX = 1, posY = 0, wildlifeCoords = null, rotation = 0)

        verify(exactly = 1) { mockClient.sendGameActionMessage(any<PlaceMessage>()) }
    }

    /**
     * Verifies that [NetworkService.onPlaceActionReceived] places the currently
     * selected habitat tile on the current user's board and notifies
     * [Refreshable.refreshAfterPlaceHabitatTile].
     */
    @Test
    fun testOnPlaceActionReceived() {
        val (game, user) = buildTestGame()
        every { rootService.currentGame } returns game

        networkService.onPlaceActionReceived(
            PlaceMessage(habitatCoordinates = Pair(1, 0), wildlifeCoordinates = null, habitatRotation = 0)
        )

        assertTrue(Coordinate(1, 0) in user.board.placedHabitatTiles)
        verify(exactly = 1) { mockRefreshable.refreshAfterPlaceHabitatTile(any(), 1, 0) }
    }

    /**
     * Verifies that [NetworkService.onGameConfigReceived] reorders the local
     * `players` list to match the received order and notifies
     * [Refreshable.refreshAfterGameConfigUpdated].
     */
    @Test
    fun testOnGameConfigReceived() {
        networkService.onPlayerJoined("Alice", true)
        networkService.onPlayerJoined("Bob", false)

        networkService.onGameConfigReceived(
            players = listOf(User(name = "Bob"), User(name = "Alice")),
            scoreCards = listOf(true, false, true, false, true)
        )

        assertEquals(listOf("Bob", "Alice"), networkService.players.map { it.name })
        verify(exactly = 1) { mockRefreshable.refreshAfterGameConfigUpdated(any(), any()) }
    }

    /**
     * Verifies that [NetworkService.sendGameConfigMessage] sends a
     * [GameConfigMessage] via the client when in state `WAITING_FOR_GUEST`.
     */
    @Test
    fun `sendGameConfigMessage sends message when state is WAITING_FOR_GUEST`() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.WAITING_FOR_GUEST)

        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        networkService.sendGameConfigMessage(listOf(User(name = "Alice")),
            listOf(true, false, true, false, true))

        verify(exactly = 1) { mockClient.sendGameActionMessage(any<GameConfigMessage>()) }
    }

    /**
     * Verifies that [NetworkService.sendGameConfigMessage] throws an
     * [IllegalStateException] when the connection is not waiting for a guest
     * or init message (the default state set by [setup]).
     */
    @Test
    fun testSendGameConfigMessage() {
        assertThrows<IllegalStateException> {
            networkService.sendGameConfigMessage(listOf(), listOf())
        }
    }

    /**
     * Verifies that [NetworkService.onPlayerLeft] removes an existing player
     * from `players` and notifies [Refreshable.refreshAfterUserLeft] with the
     * actual user found in the current game.
     */
    @Test
    fun onPlayerLeft() {
        val (game, user) = buildTestGame()
        every { rootService.currentGame } returns game
        networkService.onPlayerJoined("Alice", true)

        networkService.onPlayerLeft("Alice")

        assertFalse(networkService.players.any { it.name == "Alice" })
        verify(exactly = 1) { mockRefreshable.refreshAfterUserLeft(user) }
    }

    /**
     * Verifies that [NetworkService.onPlayerLeft] still notifies
     * [Refreshable.refreshAfterUserLeft] with a fallback [User] when no
     * current game exists.
     */
    @Test
    fun testOnPlayerLeft() {
        every { rootService.currentGame } returns null

        networkService.onPlayerLeft("Unknown")

        verify(exactly = 1) { mockRefreshable.refreshAfterUserLeft(match { it.name == "Unknown" }) }
    }

    /**
     * Verifies that [NetworkService.sendChatMessage] forwards a [ChatMessage]
     * to the client.
     */
    @Test
    fun testSendChatMessage() {
        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        networkService.sendChatMessage("Hello!")

        verify(exactly = 1) { mockClient.sendGameActionMessage(any<ChatMessage>()) }
    }

    /**
     * Verifies that [NetworkService.onChatMessageReceived] notifies
     * [Refreshable.refreshAfterChatMessageReceived] with the received message content.
     */
    @Test
    fun testOnChatMessageReceived() {
        networkService.onChatMessageReceived(ChatMessage(message = "Hi there"))

        verify(exactly = 1) { mockRefreshable.refreshAfterChatMessageReceived("Hi there") }
    }

    /**
     * Verifies that sending a select action is only allowed while the client is
     * in the SELECTING connection state.
     *
     * Expects an [IllegalStateException] if the current connection state is not
     * SELECTING.
     */
    @Test
    fun `sendSelectAction throws if not selecting`() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.CONNECTED)

        assertFailsWith<IllegalStateException> {
            networkService.sendSelectAction(0, 0, false)
        }
    }

    /**
     * Verifies that selecting different habitat and wildlife indices without
     * using a nature token is rejected.
     *
     * Expects an [IllegalArgumentException] if the selected indices differ and
     * no nature token is used.
     */
    @Test
    fun `sendSelectAction throws if different indices without nature token`() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)

        assertFailsWith<IllegalArgumentException> {
            networkService.sendSelectAction(0, 2, false)
        }
    }

    /**
     * Verifies that a valid select action sends the correct [SelectMessage] and
     * changes the connection state to PLACING.
     */
    @Test
    fun `sendSelectAction sends SelectMessage`() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)

        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        networkService.sendSelectAction(1, 1, false)

        verify(exactly = 1) {
            mockClient.sendGameActionMessage(
                match<SelectMessage> {
                    it.wildlifeShopIndex == 1 &&
                            it.habitatShopIndex == 1 &&
                            !it.usedNatureToken
                }
            )
        }

        assertEquals(ConnectionState.PLACING, networkService.connectionState)
    }

    /**
     * Verifies that selecting different habitat and wildlife indices is allowed
     * when a nature token is used.
     *
     * Also verifies that the correct [SelectMessage] is sent and the connection
     * state changes to PLACING.
     */
    @Test
    fun `sendSelectAction allows different indices with nature token`() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)

        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        networkService.sendSelectAction(0, 3, true)

        verify(exactly = 1) {
            mockClient.sendGameActionMessage(
                match<SelectMessage> {
                    it.wildlifeShopIndex == 0 &&
                            it.habitatShopIndex == 3 &&
                            it.usedNatureToken
                }
            )
        }

        assertEquals(ConnectionState.PLACING, networkService.connectionState)
    }

    /**
     * Verifies that [NetworkService.onSelectReceived] consumes one nature token
     * from the current user and notifies the relevant [Refreshable] callbacks
     * when the connection is in state `WAITING_FOR_PLAYER_TURN`.
     */
    @Test
    fun testOnSelectReceived() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.WAITING_FOR_PLAYER_TURN)

        val (game, user) = buildTestGame()
        user.natureToken = 2
        every { rootService.currentGame } returns game

        networkService.onSelectReceived(
            SelectMessage(wildlifeShopIndex = 1, habitatShopIndex = 2, usedNatureToken = true)
        )

        assertEquals(1, user.natureToken)
        verify(exactly = 1) { mockRefreshable.refreshAfterNatureTokenUsed() }
        verify(exactly = 1) { mockRefreshable.refreshAfterOpponentSelectedHabitatTile(2) }
        verify(exactly = 1) { mockRefreshable.refreshAfterOpponentSelectedWildLifeToken(1) }
    }

    /**
    * Tests if being in the wrong state causes an error
    */
    @Test
    fun testSendWipeWildlifeThrows() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.DISCONNECTED)
        assertThrows<IllegalStateException> {
            networkService.sendWipeWildlife(
                1,
                listOf(0, 1, 2, 3),
                listOf(NetWildlife.SALMON)
            )
        }
    }

    /**
     * tests if sending a WipeWildLifeMessage works
     */
    @Test
    fun testSendWipeWildlife() {

        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)
        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        networkService.sendWipeWildlife(1, listOf(0, 1, 2, 3), listOf(NetWildlife.SALMON))


        verify {
            mockClient.sendGameActionMessage(withArg { message ->
                val sentMessage = message as WipeWildlifeMessage
                assertTrue(sentMessage.usedNatureToken)
                assertEquals(1, sentMessage.natureTokenAmount)
                assertEquals(listOf(0, 1, 2, 3), sentMessage.wipedWildlifeIndices)
                assertEquals(listOf(NetWildlife.SALMON), sentMessage.wildlifeBag)
            })
        }
    }

    /**
     * Tests if receiving a WipeWildLifeMessage works correctly
     */
    @Test
    fun testOnWipeWildlifeReceived() {
        val startTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val coordinate = Coordinate(0, 0)
        // Give the user 2 nature tokens by default so paid swaps are testable
        val user = User(name = "TestUser", natureToken = 2)
        user.board.placedHabitatTiles[coordinate] = startTile

        val selectedTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val action = ActionBuilder(
            userStates = UserStateChange(
                oldState = user,
                newState = user
            ),
            selection = ActionSelectionBuilder(
                habitatTileIndex = 0,
                wildlifeTokenIndex = 0,
                habitatTile = selectedTile,
                wildlifeToken = WildLifeToken(WildLifeTokenType.BEAR)
            )
        )

        // Pre-populate predictable items for wildlife token swap scenarios
        val marketTokens = mutableMapOf(
            0 to WildLifeToken(WildLifeTokenType.BEAR),
            1 to WildLifeToken(WildLifeTokenType.BEAR),
            2 to WildLifeToken(WildLifeTokenType.BEAR),
            3 to WildLifeToken(WildLifeTokenType.FOX)
        )

        val bag = Stack<WildLifeToken>()
        bag.push(WildLifeToken(WildLifeTokenType.SALMON))
        bag.push(WildLifeToken(WildLifeTokenType.ELK))
        bag.push(WildLifeToken(WildLifeTokenType.HAWK))

        val game = CascadiaGame(
            userList = listOf(user),
            displayedWildLifeToken = marketTokens,
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = listOf()
        ).apply {
            this.currentAction = action
            this.wildLifeCollection = bag
        }

        game.undoableHistory.add(action.build())

        rootService = RootService()
        rootService.currentGame = game


        val currentUser = game.userList[0]
        val message = WipeWildlifeMessage(
            true,
            2,
            listOf(0, 1, 2, 3),
            listOf(NetWildlife.SALMON, NetWildlife.ELK, NetWildlife.FOX, NetWildlife.FOX)
        )
        rootService.networkService.onWipeWildlifeReceived(message)
        assertEquals(2, currentUser.natureToken)
        assertEquals(WildLifeTokenType.SALMON, game.displayedWildLifeToken[0]?.type)
        assertEquals(WildLifeTokenType.ELK, game.displayedWildLifeToken[1]?.type)
        assertEquals(WildLifeTokenType.FOX, game.displayedWildLifeToken[2]?.type)
        assertEquals(WildLifeTokenType.FOX, game.displayedWildLifeToken[3]?.type)
    }

    /**
     * tests if receiving a SelectWildLifeMessage works
     */
    @Test
    fun testOnSelectWildlifeReceived() {
        val mockGame = mockk<CascadiaGame>(relaxed = true)
        every { rootService.currentGame } returns mockGame
        every { rootService.networkService } returns networkService
        val message = SelectWildlifeMessage(2)
        val refreshable = mockk<Refreshable>(relaxed = true)
        networkService.addRefreshable(refreshable)
        rootService.networkService.onSelectWildlifeReceived(message)
        verify { refreshable.refreshAfterOpponentSelectedWildLifeToken(2) }
    }
    /**
     * tests if sending a nature token use works
     */
    @Test
    fun testSendUseNatureToken() {
         val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)
        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)
        networkService.sendUseNatureToken()

        verify {
            mockClient.sendGameActionMessage(withArg { message ->
                val sentMessage = message as UseNatureTokenMessage
                assertNotNull(sentMessage)
            })
        }
    }

    /**
     * tests if receiving a UseNatureTokenMessage works
     */
    @Test
    fun testOnUseNatureTokenReceived() {
        val startTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val coordinate = Coordinate(0, 0)
        // Give the user 2 nature tokens by default so paid swaps are testable
        val user = User(name = "TestUser", natureToken = 2)
        user.board.placedHabitatTiles[coordinate] = startTile

        val selectedTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val action = ActionBuilder(
            userStates = UserStateChange(
                oldState = user,
                newState = user
            ),
            selection = ActionSelectionBuilder(
                usedNatureToken = 0,
                habitatTileIndex = 0,
                wildlifeTokenIndex = 0,
                habitatTile = selectedTile,
                swappedWildLifeTokens = mutableListOf(),
                wildlifeToken = WildLifeToken(WildLifeTokenType.BEAR)
            ),
            collections = CollectionState(
                oldWildLifeCollection = Stack(),
                newWildLifeCollection = Stack()
            )
        )

        // Pre-populate predictable items for wildlife token swap scenarios
        val marketTokens = mutableMapOf(
            0 to WildLifeToken(WildLifeTokenType.BEAR),
            1 to WildLifeToken(WildLifeTokenType.BEAR),
            2 to WildLifeToken(WildLifeTokenType.BEAR),
            3 to WildLifeToken(WildLifeTokenType.FOX)
        )

        val bag = Stack<WildLifeToken>()
        bag.push(WildLifeToken(WildLifeTokenType.SALMON))
        bag.push(WildLifeToken(WildLifeTokenType.ELK))
        bag.push(WildLifeToken(WildLifeTokenType.HAWK))

        val game = CascadiaGame(
            userList = listOf(user),
            displayedWildLifeToken = marketTokens,
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = listOf()
        ).apply {
            this.currentAction = action
            this.wildLifeCollection = bag
        }

        game.undoableHistory.add(action.build())

        rootService = RootService()
        rootService.currentGame = game

        rootService.networkService.onUseNatureTokenReceived()
        assertEquals(1, game.userList[game.currentUser].natureToken)
    }
    /**
     * tests if sending a SelectHabitatTileMessage works
     */
    @Test
    fun testSendSelectHabitat() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)
        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)


        networkService.sendSelectHabitat(1)
        verify {
            mockClient.sendGameActionMessage(withArg { message ->
                val sentMessage = message as SelectHabitatTileMessage
                assertEquals(1, sentMessage.habitatShopIndex)
            })
        }
    }

    /**
     * tests if a player joining is processed
     */
    @Test
    fun testOnPlayerJoined() {
        every { rootService.networkService } returns networkService
        rootService.networkService.onPlayerJoined("John", true)
        assertEquals(1, rootService.networkService.players.size)
        assertEquals("John", rootService.networkService.players[0].name)
        assertEquals(UserType.LOCAL_PLAYER, rootService.networkService.players[0].type)
    }

    /**
     * tests if receiving a SelectHabitatTileMessage is processed
     */
    @Test
    fun testOnSelectHabitatReceived() {
        val mockGame = mockk<CascadiaGame>(relaxed = true)
        every { rootService.currentGame } returns mockGame
        every { rootService.networkService } returns networkService
        val message = SelectHabitatTileMessage(1)
        val refreshable = mockk<Refreshable>(relaxed = true)
        networkService.addRefreshable(refreshable)
        rootService.networkService.onSelectHabitatReceived(message)
        verify { refreshable.refreshAfterOpponentSelectedHabitatTile(1) }

    }
    /**
     * tests if sendRotation throws correctly
     */
    @Test
    fun testSendRotationThrow() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.DISCONNECTED)

        assertThrows<IllegalStateException> { networkService.sendRotation(60) }
    }
    /**
     * tests if sending a RotationMessage works
     */
    @Test
    fun testSendRotation() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)
        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        networkService.sendRotation(60)
        verify {
            mockClient.sendGameActionMessage(withArg { message ->
                val sentMessage = message as RotationMessage
                assertEquals(60, sentMessage.habitatRotation)
            })
        }
    }

    /**
     * tests if OnRotationReceived properly throw
     */
    @Test
    fun testOnRotationReceivedThrows() {
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)
        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)
        assertThrows<IllegalStateException> { networkService.onRotationReceived(RotationMessage(60)) }
    }

    /**
     * tests if receiving a RotationMessage works
     */
    @Test
    fun testOnRotationReceived() {
        val startTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val coordinate = Coordinate(0, 0)
        val user = User(name = "TestUser", natureToken = 2)
        user.board.placedHabitatTiles[coordinate] = startTile

        val selectedTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val action = ActionBuilder(
            userStates = UserStateChange(
                oldState = user,
                newState = user
            ),
            selection = ActionSelectionBuilder(
                habitatTileIndex = 0,
                wildlifeTokenIndex = 0,
                habitatTile = selectedTile,
                wildlifeToken = WildLifeToken(WildLifeTokenType.BEAR)
            ),
        )

        // Pre-populate predictable items for wildlife token swap scenarios
        val marketTokens = mutableMapOf(
            0 to WildLifeToken(WildLifeTokenType.BEAR),
            1 to WildLifeToken(WildLifeTokenType.BEAR),
            2 to WildLifeToken(WildLifeTokenType.BEAR),
            3 to WildLifeToken(WildLifeTokenType.FOX)
        )

        val bag = Stack<WildLifeToken>()
        bag.push(WildLifeToken(WildLifeTokenType.SALMON))
        bag.push(WildLifeToken(WildLifeTokenType.ELK))
        bag.push(WildLifeToken(WildLifeTokenType.HAWK))

        val game = CascadiaGame(
            userList = listOf(user),
            displayedWildLifeToken = marketTokens,
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = listOf()
        ).apply {
            this.currentAction = action
            this.wildLifeCollection = bag
        }


        rootService = RootService()
        rootService.currentGame = game


        val message = RotationMessage(2)
        val tile = HabitatTile(
            edges=mutableListOf(
                HabitatTileType.FORESTS
            )
        )
        action.selection.habitatTile = tile
        game.currentAction = action
        rootService.networkService.updateConnectionState(ConnectionState.WAITING_FOR_PLAYER_TURN)
        rootService.networkService.onRotationReceived(message)
        assertEquals(2, tile.rotation)
    }


    /**
     * Tests that startNewHostedGame throws when not in WAITING_FOR_GUEST state
     */
    @Test
    fun startNewHostedGameThrowsWhenNotInWaitingForGuestState(){
        assertThrows<IllegalStateException> {
            networkService.startNewHostedGame(
                listOf(User(name = "Player1")),
                listOf(
                    entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
                    entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
                    entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX),
                    entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
                    entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON)
                )
            )
        }
    }


    /**
     * Tests that startNewHostedGame starts a game and sends GameInitMessage
     */
    @Test
    fun startNewHostedGameStartsAndSends(){
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true

        stateField.set(networkService, ConnectionState.WAITING_FOR_GUEST)


        val clientField = NetworkService::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(networkService, mockClient)

        val users = listOf(
            User(name = "Player1", type = UserType.LOCAL_PLAYER),
            User(name = "Player2", type = UserType.ONLINE_PLAYER)
        )

        val scoringCards = listOf(
            entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            entity.ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.FOX),
            entity.ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK)
        )

        val realRootService = RootService()
        val realNetworkService = spyk(NetworkService(realRootService))

        realNetworkService.addRefreshable(mockRefreshable)

        val realStateField = NetworkService::class.java.getDeclaredField("connectionState")
        realStateField.isAccessible = true
        realStateField.set(realNetworkService, ConnectionState.WAITING_FOR_GUEST)

        val realClientField = NetworkService::class.java.getDeclaredField("client")
        realClientField.isAccessible = true
        realClientField.set(realNetworkService, mockClient)

        realNetworkService.startNewHostedGame(users, scoringCards)

        assertNotNull(realRootService.currentGame)
        assertEquals(2, realRootService.currentGame!!.userList.size)

        verify(exactly = 1) {mockClient.sendGameActionMessage(any<GameInitMessage>())}

    }

    /**
     * Tests that setupPlayerStarterTiles places 3 tiles at correct coordinate
     *
     */
    @Test
    fun setupPlayerStarterTilesPlaceTiles(){
        val board = entity.UserBoard()
        val tiles = listOf(
            HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS),
                availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
            ),
            HabitatTile(
                edges = mutableListOf(HabitatTileType.MOUNTAINS),
                availableWildLifeToken = listOf(WildLifeTokenType.ELK)
            ),
            HabitatTile(
                edges = mutableListOf(HabitatTileType.RIVERS),
                availableWildLifeToken = listOf(WildLifeTokenType.SALMON)
            )
        )




        val method = NetworkService::class.java.getDeclaredMethod(
            "setupPlayerStarterTiles",
            entity.UserBoard::class.java,
            List::class.java
        )

        method.isAccessible = true
        method.invoke(networkService, board, tiles)

        assertNotNull(board.placedHabitatTiles[Coordinate(0, 0)])
        assertNotNull(board.placedHabitatTiles[Coordinate(0, 1)])
        assertNotNull(board.placedHabitatTiles[Coordinate(-1, 1)])
        assertEquals(3, board.placedHabitatTiles.size)

    }


    /**
     * Tests that startNewJoinedGame throws when not in WAITING_FOR_INIT state
     *
     */
    @Test
    fun startNewJoinedGameThrowsWhenNotWaitingState(){
        val message = GameInitMessage(
            tileStack = (0..99).toList(),
            scoringCards = listOf(false, false, false, true, true),
            players = listOf(
                NetPlayer(name = "Player1", startingTileID = 0),
                NetPlayer(name = "Player2", startingTileID = 1)
            ),
            wildlifeBag = List(100) { NetWildlife.BEAR}
        )

        assertThrows<IllegalStateException> { networkService.startNewJoinedGame(message, "Player1", "Player2") }
    }


    /**
     * Tests that startNewJoinedGame correctly sets up game when in WAITING_FOR_INIT
     */
    @Test
    fun startNewJoinedGameSetsUpCorrectly(){
        // Step 1: start a real game as host to get a valid GameInitMessage
        val hostRootService = RootService()
        val hostNetworkService = spyk(NetworkService(hostRootService))

        val hostStateField = NetworkService::class.java.getDeclaredField("connectionState")
        hostStateField.isAccessible = true
        hostStateField.set(hostNetworkService, ConnectionState.WAITING_FOR_GUEST)

        val hostClientField = NetworkService::class.java.getDeclaredField("client")
        hostClientField.isAccessible = true
        hostClientField.set(hostNetworkService, mockClient)

        val players = listOf(User(name = "Player1"), User(name = "Player2"))
        val cards = listOf(
            entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            entity.ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            entity.ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            entity.ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.FOX)
        )

        // capture the GameInitMessage sent by host
        var capturedMessage: GameInitMessage? = null
        every { mockClient.sendGameActionMessage(any<GameInitMessage>()) } answers {
            capturedMessage = firstArg()
        }

        hostNetworkService.startNewHostedGame(players, cards)
        val initMessage = checkNotNull(capturedMessage) { "Host did not send GameInitMessage" }

        // Step 2: use the real message to start joined game
        val guestRootService = RootService()
        val guestNetworkService = spyk(NetworkService(guestRootService))
        guestNetworkService.addRefreshable(mockRefreshable)

        val guestStateField = NetworkService::class.java.getDeclaredField("connectionState")
        guestStateField.isAccessible = true
        guestStateField.set(guestNetworkService, ConnectionState.WAITING_FOR_INIT)

        guestNetworkService.startNewJoinedGame(initMessage, "Player1", "Player2")

        // assertions
        assertNotNull(guestRootService.currentGame)
        val game = guestRootService.currentGame!!

        assertEquals("Player1", game.userList[0].name)
        assertEquals(UserType.LOCAL_PLAYER, game.userList[0].type)
        assertEquals("Player2", game.userList[1].name)
        assertEquals(UserType.ONLINE_PLAYER, game.userList[1].type)

        assertEquals(ConnectionState.WAITING_FOR_PLAYER_TURN, guestNetworkService.connectionState)
        verify(exactly = 1) { mockRefreshable.refreshAfterGameStart() }

        assertEquals(4, game.displayedHabitatTiles.size)
        assertEquals(4, game.displayedWildLifeToken.size)
        assertEquals(5, game.scoringCards.size)
        assertEquals(20, game.natureToken)
    }


    /**
     * Tests sendSelectAction throws when not in SELECTING state
     */
    @Test
    fun sendSelectActionThrowsWhenNotInSelectingState(){
        assertThrows<IllegalStateException>{
            networkService.sendSelectAction(0, 0, false)
        }
    }
    /**
     * Tests sendSelectAction throws when indices differ and no nature token used
     */
    @Test
    fun sendSelectActionThrowsWhenIndicesDiffer(){
        val stateField = NetworkService::class.java.getDeclaredField("connectionState")
        stateField.isAccessible = true
        stateField.set(networkService, ConnectionState.SELECTING)

        assertThrows<IllegalArgumentException>{
            networkService.sendSelectAction(0, 1, false)
        }
    }




}


package service

import entity.HabitatTile
import entity.HabitatTileType
import entity.User
import entity.WildLifeToken
import entity.WildLifeTokenType
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertEquals


/**
 * This class tests the functionality of the [AbstractRefreshingService] class.
 */
class AbstractRefreshingServiceTest {

    /**
     * This refreshable is initialized in the [setUp] function hence it is a late-initialized property.
     */
    private lateinit var testRefreshable: Refreshable

    /**
     * This abstractRefreshingService is initialized in the [setUp] function hence it is a late-initialized property.
     */
    private lateinit var abstractRefreshingService: AbstractRefreshingService

    /**
     * Initialize service to set up the test environment. This function is executed before every test.
     */
    @BeforeTest
    fun setUp() {
        testRefreshable = object : Refreshable {
            override fun refreshAfterGameStart() = Unit

            override fun refreshAfterGameEnd() = Unit

            override fun refreshAfterRotateHabitatTile(habitatTile: HabitatTile) = Unit

            override fun refreshAfterSwapWildLifeToken() = Unit

            override fun refreshAfterUndo(success: Boolean) = Unit

            override fun refreshAfterRedo(success: Boolean) = Unit

            override fun refreshAfterCheckOverPopulation(isOverpopulated: Boolean) = Unit

            override fun refreshAfterTurn() = Unit

            override fun refreshAfterPauseGame() = Unit

            override fun refreshAfterContinueGame() = Unit

            override fun refreshAfterUserJoined(user: User) = Unit

            override fun refreshAfterUserLeft(user: User) = Unit

            override fun refreshAfterSaveGame(filePath: String) = Unit

            override fun refreshAfterLoadGame() = Unit

            override fun refreshAfterPlaceHabitatTile(habitatTile: HabitatTile, posX: Int, posY: Int) = Unit

            override fun refreshAfterPlaceWildLifeToken(wildLifeToken: WildLifeToken, habitatTile: HabitatTile) = Unit

            override fun refreshAfterConnectionStateChanged(newState: ConnectionState) = Unit

            override fun refreshConnectionState(state: ConnectionState) = Unit

            override fun refreshAfterNatureTokenUsed() = Unit
            override fun refreshAfterGameConfigUpdated(players: List<User>, scoreCards: List<Boolean>) = Unit
            override fun refreshAfterOpponentSelectedHabitatTile(habitatTileIndex: Int) = Unit
            override fun refreshAfterOpponentSelectedWildLifeToken(wildLifeTokenIndex: Int) = Unit
            override fun refreshAfterWipeWildlife() = Unit
            override fun refreshAfterChatMessageReceived(message: String) = Unit
            override fun refreshAfterJoinSuccessful() = Unit
            override fun refreshAfterHostSuccessful() = Unit
        }



        abstractRefreshingService = object : AbstractRefreshingService() {}
        abstractRefreshingService.addRefreshable(testRefreshable)
    }

    /**
     * Tests if the refreshable is notified and is the correct one.
     */
    @Test
    fun testIfRefreshableIsNotified() {
        var refreshWasCalled = false
        var isTestRefreshable = false

        /**
         * This function is used to test if the refresh method was called on the test refreshable.
         */
        fun Refreshable.refreshForTesting() {
            refreshWasCalled = true
            isTestRefreshable = this === testRefreshable
        }

        abstractRefreshingService.onAllRefreshables { this.refreshForTesting() }

        assertTrue(
            refreshWasCalled,
            "The refresh method should have been called."
        )
        assertTrue(
            isTestRefreshable,
            "The refresh method should have been called on the test refreshable."
        )
    }


    /**
     * Tests that all overridden refreshable methods are called correctly.
     */
    @Test
    fun testAllRefreshableMethodsAreCalled() {
        val called = mutableSetOf<String>()

        val r = object : Refreshable {
            override fun refreshAfterGameStart() {
                called.add("GameStart")
            }

            override fun refreshAfterGameEnd() {
                called.add("GameEnd")
            }

            override fun refreshAfterRotateHabitatTile(habitatTile: HabitatTile) {
                called.add("RotateTile")
            }

            override fun refreshAfterSwapWildLifeToken() {
                called.add("SwapToken")
            }

            override fun refreshAfterUndo(success: Boolean) {
                called.add("Undo")
            }

            override fun refreshAfterRedo(success: Boolean) {
                called.add("Redo")
            }

            override fun refreshAfterCheckOverPopulation(isOverpopulated: Boolean) {
                called.add("OverPop")
            }

            override fun refreshAfterTurn() {
                called.add("Turn")
            }

            override fun refreshAfterPauseGame() {
                called.add("Pause")
            }

            override fun refreshAfterContinueGame() {
                called.add("Continue")
            }

            override fun refreshAfterUserJoined(user: User) {
                called.add("UserJoined")
            }

            override fun refreshAfterUserLeft(user: User) {
                called.add("UserLeft")
            }

            override fun refreshAfterSaveGame(filePath: String) {
                called.add("Save")
            }

            override fun refreshAfterLoadGame() {
                called.add("Load")
            }

            override fun refreshAfterPlaceHabitatTile(habitatTile: HabitatTile, posX: Int, posY: Int) {
                called.add("PlaceTile")
            }

            override fun refreshAfterPlaceWildLifeToken(wildLifeToken: WildLifeToken, habitatTile: HabitatTile) {
                called.add("PlaceToken")
            }

            override fun refreshConnectionState(state: ConnectionState) {
                called.add("ConnState")
            }

            override fun refreshAfterConnectionStateChanged(newState: ConnectionState) {
                called.add("ConnChanged")
            }

            override fun refreshAfterNatureTokenUsed() {
                called.add("NatureToken")
            }

            override fun refreshAfterGameConfigUpdated(players: List<User>, scoreCards: List<Boolean>) {
                called.add("ConfigUpdated")
            }

            override fun refreshAfterOpponentSelectedHabitatTile(habitatTileIndex: Int) {
                called.add("OpponentTile")
            }

            override fun refreshAfterOpponentSelectedWildLifeToken(wildLifeTokenIndex: Int) {
                called.add("OpponentToken")
            }

            override fun refreshAfterWipeWildlife() {
                called.add("WipeWildlife")
            }

            override fun refreshAfterChatMessageReceived(message: String) {
                called.add("Chat")
            }

            override fun refreshAfterJoinSuccessful() {
                called.add("Join")
            }

            override fun refreshAfterHostSuccessful() {
                called.add("Host")
            }
        }

        abstractRefreshingService.addRefreshable(r)

        val tile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val token = WildLifeToken(WildLifeTokenType.BEAR)
        val user = User("test")

        abstractRefreshingService.onAllRefreshables { refreshAfterGameStart() }
        abstractRefreshingService.onAllRefreshables { refreshAfterGameEnd() }
        abstractRefreshingService.onAllRefreshables { refreshAfterRotateHabitatTile(tile) }
        abstractRefreshingService.onAllRefreshables { refreshAfterSwapWildLifeToken() }
        abstractRefreshingService.onAllRefreshables { refreshAfterUndo(true) }
        abstractRefreshingService.onAllRefreshables { refreshAfterRedo(true) }
        abstractRefreshingService.onAllRefreshables { refreshAfterCheckOverPopulation(true) }
        abstractRefreshingService.onAllRefreshables { refreshAfterTurn() }
        abstractRefreshingService.onAllRefreshables { refreshAfterPauseGame() }
        abstractRefreshingService.onAllRefreshables { refreshAfterContinueGame() }
        abstractRefreshingService.onAllRefreshables { refreshAfterUserJoined(user) }
        abstractRefreshingService.onAllRefreshables { refreshAfterUserLeft(user) }
        abstractRefreshingService.onAllRefreshables { refreshAfterSaveGame(
            "test.txt") }
        abstractRefreshingService.onAllRefreshables { refreshAfterLoadGame() }
        abstractRefreshingService.onAllRefreshables { refreshAfterPlaceHabitatTile(tile,
            0, 0) }
        abstractRefreshingService.onAllRefreshables { refreshAfterPlaceWildLifeToken(token,
            tile) }
        abstractRefreshingService.onAllRefreshables { refreshConnectionState(ConnectionState.DISCONNECTED) }
        abstractRefreshingService.onAllRefreshables { refreshAfterConnectionStateChanged(
            ConnectionState.CONNECTED) }
        abstractRefreshingService.onAllRefreshables { refreshAfterNatureTokenUsed() }
        abstractRefreshingService.onAllRefreshables { refreshAfterGameConfigUpdated(
            listOf(), listOf()) }
        abstractRefreshingService.onAllRefreshables { refreshAfterOpponentSelectedHabitatTile(
            0) }
        abstractRefreshingService.onAllRefreshables { refreshAfterOpponentSelectedWildLifeToken(
            0) }
        abstractRefreshingService.onAllRefreshables { refreshAfterWipeWildlife() }
        abstractRefreshingService.onAllRefreshables { refreshAfterChatMessageReceived("hello") }
        abstractRefreshingService.onAllRefreshables { refreshAfterJoinSuccessful() }
        abstractRefreshingService.onAllRefreshables { refreshAfterHostSuccessful() }

        assertEquals(26, called.size)


    }


    /**
     * Tests that all default refreshable implementations are executed
     */
    @Test
    fun testDefaultRefreshableImplementations() {
        val defaultRefreshable = object : Refreshable {}
        abstractRefreshingService.addRefreshable(defaultRefreshable)
        val tile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val token = WildLifeToken(WildLifeTokenType.BEAR)
        val user = User("test")

        abstractRefreshingService.onAllRefreshables { refreshAfterGameStart() }
        abstractRefreshingService.onAllRefreshables { refreshAfterGameEnd() }
        abstractRefreshingService.onAllRefreshables { refreshAfterRotateHabitatTile(tile) }
        abstractRefreshingService.onAllRefreshables { refreshAfterSwapWildLifeToken() }
        abstractRefreshingService.onAllRefreshables { refreshAfterUndo(true) }
        abstractRefreshingService.onAllRefreshables { refreshAfterUndo(false) }
        abstractRefreshingService.onAllRefreshables { refreshAfterRedo(true) }
        abstractRefreshingService.onAllRefreshables { refreshAfterRedo(false) }
        abstractRefreshingService.onAllRefreshables { refreshAfterCheckOverPopulation(true) }
        abstractRefreshingService.onAllRefreshables { refreshAfterCheckOverPopulation(false) }
        abstractRefreshingService.onAllRefreshables { refreshAfterTurn() }
        abstractRefreshingService.onAllRefreshables { refreshAfterPauseGame() }
        abstractRefreshingService.onAllRefreshables { refreshAfterContinueGame() }
        abstractRefreshingService.onAllRefreshables { refreshAfterUserJoined(user) }
        abstractRefreshingService.onAllRefreshables { refreshAfterUserLeft(user) }
        abstractRefreshingService.onAllRefreshables { refreshAfterSaveGame("test.txt") }
        abstractRefreshingService.onAllRefreshables { refreshAfterLoadGame() }
        abstractRefreshingService.onAllRefreshables { refreshAfterPlaceHabitatTile(tile, 0, 0) }
        abstractRefreshingService.onAllRefreshables { refreshAfterPlaceWildLifeToken(token, tile) }
        abstractRefreshingService.onAllRefreshables { refreshConnectionState(ConnectionState.DISCONNECTED) }
        abstractRefreshingService.onAllRefreshables { refreshAfterConnectionStateChanged(
            ConnectionState.CONNECTED) }
        abstractRefreshingService.onAllRefreshables { refreshAfterNatureTokenUsed() }
        abstractRefreshingService.onAllRefreshables { refreshAfterGameConfigUpdated(
            listOf(), listOf()) }
        abstractRefreshingService.onAllRefreshables { refreshAfterOpponentSelectedHabitatTile(0) }
        abstractRefreshingService.onAllRefreshables { refreshAfterOpponentSelectedWildLifeToken(0) }
        abstractRefreshingService.onAllRefreshables { refreshAfterWipeWildlife() }
        abstractRefreshingService.onAllRefreshables { refreshAfterChatMessageReceived("hello") }
        abstractRefreshingService.onAllRefreshables { refreshAfterJoinSuccessful() }
        abstractRefreshingService.onAllRefreshables { refreshAfterHostSuccessful() }

    }

    /**
     * Tests that RootService.addRefreshable adds the refreshable to all services
     */

    @Test
    fun testRootServiceAddRefreshable() {
        val rootService = RootService()
        var called = false

        val refreshable = object : Refreshable {
            override fun refreshAfterGameStart() {
                called = true
            }
        }



        rootService.addRefreshable(refreshable)

        rootService.gameService.onAllRefreshables { refreshAfterGameStart() }
        assertTrue(called, "Refreshable should have been called on gameService.")

        called = false
        rootService.playerActionService.onAllRefreshables { refreshAfterGameStart() }
        assertTrue(called, "Refreshable should have been called on playerActionService.")

        called = false
        rootService.botService.onAllRefreshables { refreshAfterGameStart() }
        assertTrue(called, "Refreshable should have been called on botService.")

        called = false
        rootService.networkService.onAllRefreshables { refreshAfterGameStart() }
        assertTrue(called, "Refreshable should have been called on networkService.")

    }

    /**
     * Tests that [RootService.addRefreshables] adds multiple refreshables to all connected services.
     */
    @Test
    fun `addRefreshables should add multiple refreshables to all services`() {
        val rootService = RootService()

        var firstRefreshableCalled = false
        var secondRefreshableCalled = false

        val refreshable1 = object : Refreshable {
            override fun refreshAfterGameStart() {
                firstRefreshableCalled = true
            }
        }

        val refreshable2 = object : Refreshable {
            override fun refreshAfterGameStart() {
                secondRefreshableCalled = true
            }
        }

        rootService.addRefreshables(refreshable1, refreshable2)

        rootService.gameService.onAllRefreshables {
            refreshAfterGameStart()
        }

        assertTrue(
            firstRefreshableCalled,
            "The first refreshable should have been called."
        )
        assertTrue(
            secondRefreshableCalled,
            "The second refreshable should have been called."
        )
    }
}

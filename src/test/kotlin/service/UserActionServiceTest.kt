package service

import entity.ActionBuilder
import entity.CascadiaGame
import entity.Coordinate
import entity.HabitatTile
import entity.HabitatTileType
import entity.User
import entity.WildLifeToken
import entity.WildLifeTokenType
import entity.GameState
import entity.UserType
import entity.action.ActionSelectionBuilder
import entity.action.CollectionState
import entity.action.UserStateChange
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import tools.aqua.bgw.util.Stack
import kotlin.test.*

/**
 * This class tests the functionality of the [UserActionService] class.
 */
class UserActionServiceTest {

    private lateinit var rootService: RootService

    /**
     * Sets up the test environment before each test.
     * Generates a fully integrated environment for both tile placement and token swaps.
     */
    @BeforeTest
    fun setup() {
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

    }

    /**
     * Tests if habitat tile can be placed on a valid position
     * adjacent to an existing tile at (0,0)
     */
    @Test
    fun testPlaceHabitatTileValid() {
        // Place tile on (1,0)
        val game = checkNotNull(rootService.currentGame)
        val selectedHabitatTile = checkNotNull(game.currentAction.selection.habitatTile)
        val currentUser = game.userList[0]

        rootService.playerActionService.placeHabitatTile(selectedHabitatTile, 1, 0)

        assertTrue(Coordinate(1, 0) in currentUser.board.placedHabitatTiles)
    }


    /**
     * Tests if an exception is thrown when trying to place a habitat tile
     * on a position that is already occupied
     */
    @Test
    fun testPlaceHabitatTileAlreadyOccupied() {
        val game = checkNotNull(rootService.currentGame)
        val selectedHabitatTile = checkNotNull(game.currentAction.selection.habitatTile)

        rootService.playerActionService.placeHabitatTile(selectedHabitatTile, 1, 0)
        assertThrows<IllegalArgumentException> {
            rootService.playerActionService.placeHabitatTile(selectedHabitatTile, 1, 0)
        }
    }

    /**
     * Tests if an exception is thrown when trying to pace a habitat tile
     * on a position that is not adjacent to any existing tile
     */
    @Test
    fun testPlaceHabitatTileNotAdjacent() {
        val game = checkNotNull(rootService.currentGame)
        val selectedHabitatTile = checkNotNull(game.currentAction.selection.habitatTile)

        assertThrows<IllegalArgumentException> {
            rootService.playerActionService.placeHabitatTile(selectedHabitatTile, 10, 10)
        }
    }

    /**
     * Tests if an exception is thrown when trying to place a habitat tile
     * when no game is currently running
     */
    @Test
    fun testPlaceHabitatTileNoGame() {
        val game = checkNotNull(rootService.currentGame)
        val selectedHabitatTile = checkNotNull(game.currentAction.selection.habitatTile)

        rootService.currentGame = null
        assertThrows<IllegalStateException> {
            rootService.playerActionService.placeHabitatTile(selectedHabitatTile, 1, 0)
        }

    }

    /**
     * Tests if an exception is thrown when trying to place a habitat tile
     * when no action has been made yet
     */
    @Test
    fun testPlaceHabitatTileNoAction() {
        val game = checkNotNull(rootService.currentGame)
        val currentAction = game.currentAction

        currentAction.selection.habitatTile = null
        currentAction.selection.habitatTileIndex = null

        assertThrows<NullPointerException> {
            rootService.playerActionService.placeHabitatTile(
                currentAction.selection.habitatTile!!, 1, 0
            )
        }
    }

    /**
     * Tests if a free wildlife token swap is successfully processed when selecting
     * exactly three matching tokens, locking the free swap flag without spending nature tokens.
     */
    @Test
    fun testSwapWildLifeTokenSuccessFreeSwap() {
        val game = checkNotNull(rootService.currentGame)
        val player = game.userList[0]

        // Slots 0, 1, 2 are all BEARs -> free 3-of-a-kind match
        rootService.playerActionService.swapWildLifeToken(listOf(0, 1, 2))

        assertTrue(
            player.hasSwappedThree,
            "Free swap tracking flag must shift to true."
        )
        assertEquals(
            2, player.natureToken,
            "Tokens must remain untouched on a free swap action."
        )
        assertEquals(
            3, game.currentAction.selection.swappedWildLifeTokens.size,
            "History logs must document 3 items."
        )

        var finalBagCount = 0
        while (!game.wildLifeCollection.isEmpty()) {
            game.wildLifeCollection.pop()
            finalBagCount++
        }
        assertEquals(
            3, finalBagCount,
            "The bag must still contain exactly 3 tokens after a 3-token swap cycle."
        )
    }

    /**
     * Tests if a paid wildlife token swap correctly deducts a nature token from the active player
     * and accurately logs the cost inside the action builder history.
     */
    @Test
    fun testSwapWildLifeTokenSuccessPaidSwap() {
        val game = checkNotNull(rootService.currentGame)
        val player = game.userList[0]

        // Slot 3 contains a FOX -> Single swap costs 1 token
        rootService.playerActionService.swapWildLifeToken(listOf(3))

        assertFalse(
            player.hasSwappedThree,
            "Paid actions shouldn't trip the free tracking flag."
        )
        assertEquals(
            1, player.natureToken,
            "Wallet balance must reflect a 1-token deduction."
        )
        assertEquals(
            1, game.currentAction.selection.usedNatureToken,
            "Action builder must log 1 token spent."
        )

        var finalBagCount = 0
        while (!game.wildLifeCollection.isEmpty()) {
            game.wildLifeCollection.pop()
            finalBagCount++
        }
        assertEquals(
            3, finalBagCount,
            "The bag must still contain exactly 3 tokens after a paid single-token swap cycle."
        )
    }

    /**
     * Tests if an exception is thrown when passing a market index that is out of bounds
     * of the standard display market layout.
     */
    @Test
    fun testSwapWildLifeTokenThrowsOnOutOfBoundsIndex() {
        // Slot 4 doesn't exist in a standard market row (0..3)
        assertThrows<IllegalArgumentException> {
            rootService.playerActionService.swapWildLifeToken(listOf(1, 4))
        }
    }

    /**
     * Tests if an exception is thrown when duplicate indices are supplied to prevent
     * redundant or malicious multi-swap exploits on a single market slot.
     */
    @Test
    fun testSwapWildLifeTokenThrowsOnDuplicateIndices() {
        // Exploit guard: Can't choose slot 0 multiple times in one swap call
        assertThrows<IllegalArgumentException> {
            rootService.playerActionService.swapWildLifeToken(listOf(0, 0))
        }
    }

    /**
     * Tests if an exception is thrown when a player attempts a paid single token swap
     * but has zero nature tokens to cover the currency requirement.
     */
    @Test
    fun testSwapWildLifeTokenThrowsOnInsufficientTokens() {
        val game = checkNotNull(rootService.currentGame)
        game.userList[0].natureToken = 0 // Explicitly drain wallet balance for this test execution frame

        // Swapping a single FOX (slot 3) costs 1 token, which fails
        assertThrows<IllegalArgumentException> {
            rootService.playerActionService.swapWildLifeToken(listOf(3))
        }
    }

    /**
     * Tests that [UserActionService.continueGame] correctly updates the game state to
     * [GameState.WAIT_FOR_TURN] and successfully triggers the appropriate UI refresh broadcast.
     */
    @Test
    fun testContinueGameSuccess() {
        val game = checkNotNull(rootService.currentGame)

        // Place the game into an alternate state to check if it gets reset
        game.state = GameState.PAUSE

        var refreshCalled = false
        val observer = object : Refreshable {
            override fun refreshAfterContinueGame() {
                refreshCalled = true
            }
        }
        rootService.playerActionService.addRefreshable(observer)

        // Invoke the resume turn play execution frame
        rootService.playerActionService.continueGame()

        // Ensure state is waiting for input and interface layer notified
        assertEquals(
            GameState.WAIT_FOR_TURN, game.state,
            "Game state should change to WAIT_FOR_TURN."
        )
        assertTrue(
            refreshCalled,
            "UI observers must receive the refreshAfterContinueGame broadcast."
        )
    }

    /**
     * Tests that [UserActionService.continueGame] correctly throws an [IllegalStateException]
     * if invoked when no active game session is running.
     */
    @Test
    fun testContinueGameThrowsWhenNoActiveGame() {
        rootService.currentGame = null

        // Attempting to resume a missing session must fail
        assertThrows<IllegalStateException> {
            rootService.playerActionService.continueGame()
        }
    }

    /**
     * Tests if the game can be paused successfully.
     */
    @Test
    fun testPauseGameSuccess() {
        val game = checkNotNull(rootService.currentGame)
        // Pause the game
        rootService.playerActionService.pauseGame()
        // Assert that the game state is now PAUSE
        assertEquals(
            GameState.PAUSE, game.state,
            "The game state should be PAUSE after the call."
        )

    }

    /**
     * Tests that an exception is thrown when attempting to pause the game
     * while no game is currently active.
     */
    @Test
    fun testPauseGameNoGame() {
        rootService.currentGame = null
        assertThrows<IllegalStateException> {
            rootService.playerActionService.pauseGame()

        }
    }

    /**
     * Tests that an exception is thrown when attempting to rotate a habitat tile
     * while no game instance exists.
     */

    @Test

    fun testRotateHabitatTileNoGame() {
        val game = checkNotNull(rootService.currentGame)
        val habitatTile = checkNotNull(game.currentAction.selection.habitatTile)
        // Remove the game instance
        rootService.currentGame = null
        // Verify that an IllegalStateException is thrown
        assertThrows<IllegalStateException> {
            rootService.playerActionService.rotateHabitatTile(habitatTile)

        }

    }

    // ────────────── UNDO TESTS ──────────────

    /**
     * Tests if undo correctly restores the previous user state after a human action.
     */
    @Test
    fun testUndoRestoresPreviousState() {
        val game = checkNotNull(rootService.currentGame)
        val user = game.userList[0]

        val oldUser = User(name = "TestUser", natureToken = 0)
        oldUser.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val newUser = User(name = "TestUser", natureToken = 2)
        newUser.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val undoableAction = ActionBuilder(
            userStates = UserStateChange(
                oldState = oldUser,
                newState = newUser
            ),
            selection = ActionSelectionBuilder(
                usedNatureToken = 2,
                habitatTileIndex = 0,
                habitatTile = HabitatTile(
                    edges = mutableListOf(HabitatTileType.FORESTS),
                    availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
                ),
                swappedWildLifeTokens = mutableListOf(),
                wildlifeToken = WildLifeToken(WildLifeTokenType.BEAR),
                wildlifeTokenIndex = 0
            ),
            collections = CollectionState(
                oldWildLifeCollection = Stack(),
                newWildLifeCollection = Stack()
            )
        ).build()

        game.undoableHistory.clear()
        game.undoableHistory.add(undoableAction)

        val result = rootService.playerActionService.undo()

        assertTrue(result)
        assertEquals(0, user.natureToken, "User's natureToken should be restored to 0")
        assertEquals(1, game.redoableHistory.size, "Redo history should have one action")
        assertTrue(game.undoableHistory.isEmpty(), "Undo history should be empty")
    }

    /**
     * Tests if undo sets game state to WAIT_FOR_TURN.
     */
    @Test
    fun testUndoSetsStateToWaitForTurn() {
        val game = checkNotNull(rootService.currentGame)
        game.state = GameState.MOVING

        rootService.playerActionService.undo()

        assertEquals(GameState.WAIT_FOR_TURN, game.state)
    }

    /**
     * Tests if undo returns false when the undo history is empty.
     */
    @Test
    fun testUndoReturnsFalseWhenHistoryEmpty() {
        val game = checkNotNull(rootService.currentGame)
        game.undoableHistory.clear()

        val result = rootService.playerActionService.undo()

        assertFalse(result)
    }

    /**
     * Tests if an exception is thrown when trying to undo without a running game.
     */
    @Test
    fun testUndoNoGame() {
        rootService.currentGame = null
        assertThrows<IllegalStateException> {
            rootService.playerActionService.undo()
        }
    }

    /**
     * Tests if an exception is thrown when trying to undo in a network game.
     */
    @Test
    fun testUndoInNetworkGame() {
        val onlineUser = User(name = "Online", type = UserType.ONLINE_PLAYER)
        val localUser = User(name = "Local", type = UserType.LOCAL_PLAYER)

        rootService.currentGame = CascadiaGame(
            userList = listOf(localUser, onlineUser),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = listOf()
        ).apply {
            this.currentAction = checkNotNull(rootService.currentGame).currentAction
        }

        assertThrows<IllegalStateException> {
            rootService.playerActionService.undo()
        }
    }

    /**
     * Tests if an exception is thrown when trying to undo in a pure-bot game.
     */
    @Test
    fun testUndoInPureBotGame() {
        val bot1 = User(name = "Bot1", type = UserType.RANDOM_BOT)
        val bot2 = User(name = "Bot2", type = UserType.PROFESSIONAL_BOT)

        rootService.currentGame = CascadiaGame(
            userList = listOf(bot1, bot2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = listOf()
        ).apply {
            this.currentAction = checkNotNull(rootService.currentGame).currentAction
        }

        assertThrows<IllegalStateException> {
            rootService.playerActionService.undo()
        }
    }

    /**
     * Tests if undo returns used nature tokens back to the global pool.
     */
    @Test
    fun testUndoReturnsNatureTokensToPool() {
        val game = checkNotNull(rootService.currentGame)

        game.natureToken = 18
        val oldUser = User(name = "TestUser", natureToken = 0)
        oldUser.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val newUser = User(name = "TestUser", natureToken = 2)
        newUser.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val undoableAction = ActionBuilder(
            userStates = UserStateChange(
                oldState = oldUser,
                newState = newUser
            ),
            selection = ActionSelectionBuilder(
                usedNatureToken = 2,
                habitatTileIndex = 0,
                habitatTile = HabitatTile(
                    edges = mutableListOf(HabitatTileType.FORESTS),
                    availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
                ),
                swappedWildLifeTokens = mutableListOf(),
                wildlifeToken = WildLifeToken(WildLifeTokenType.BEAR),
                wildlifeTokenIndex = 0
            ),
            collections = CollectionState(
                oldWildLifeCollection = Stack(),
                newWildLifeCollection = Stack()
            )
        ).build()
        game.undoableHistory.clear()
        game.undoableHistory.add(undoableAction)

        rootService.playerActionService.undo()

        assertEquals(
            20, game.natureToken,
            "Nature tokens should be returned to global pool"
        )
    }

// ────────────── REDO TESTS ──────────────

    /**
     * Tests if redo correctly reapplies a previously undone action.
     */
    @Test
    fun testRedoReappliesAction() {
        val game = checkNotNull(rootService.currentGame)

        rootService.playerActionService.undo()
        val result = rootService.playerActionService.redo()

        assertTrue(result)
        assertEquals(1, game.undoableHistory.size, "Undo history should have action back")
        assertTrue(game.redoableHistory.isEmpty(), "Redo history should be empty")
    }

    /**
     * Tests if redo returns false when the redo history is empty.
     */
    @Test
    fun testRedoReturnsFalseWhenHistoryEmpty() {
        val game = checkNotNull(rootService.currentGame)
        game.redoableHistory.clear()

        val result = rootService.playerActionService.redo()

        assertFalse(result)
    }

    /**
     * Tests if an exception is thrown when trying to redo without a running game.
     */
    @Test
    fun testRedoNoGame() {
        rootService.currentGame = null
        assertThrows<IllegalStateException> {
            rootService.playerActionService.redo()
        }
    }

    /**
     * Tests if an exception is thrown when trying to redo in a network game.
     */
    @Test
    fun testRedoInNetworkGame() {
        val onlineUser = User(name = "Online", type = UserType.ONLINE_PLAYER)
        val localUser = User(name = "Local", type = UserType.LOCAL_PLAYER)

        rootService.currentGame = CascadiaGame(
            userList = listOf(localUser, onlineUser),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = listOf()
        ).apply {
            this.currentAction = checkNotNull(rootService.currentGame).currentAction
        }

        assertThrows<IllegalStateException> {
            rootService.playerActionService.redo()
        }
    }

    // ────────────── PLACE WILDLIFE TOKEN TESTS ──────────────

    /**
     * Tests that a wildlife token is successfully placed on a valid tile.
     */
    @Test
    fun testPlaceWildLifeTokenSuccess() {
        val game = checkNotNull(rootService.currentGame)
        val tile = game.userList[0].board.placedHabitatTiles[Coordinate(0, 0)]!!

        val token = game.displayedWildLifeToken[0]!!
        rootService.playerActionService.placeWildLifeToken(token, tile)
        assertEquals(
            WildLifeTokenType.BEAR, tile.placedWildLifeToken?.type,
            "Token should be placed on the tile."
        )
    }

    /**
     * Tests that placing a token on a keystone tile grants the current user a nature token
     * and decrements the shared pool.
     */
    @Test
    fun testPlaceWildLifeTokenKeystoneGrantsNatureToken() {
        val game = checkNotNull(rootService.currentGame)
        val keystoneTile = HabitatTile(
            keyStone = true,
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )
        game.userList[0].board.placedHabitatTiles[Coordinate(1, 0)] = keystoneTile
        game.natureToken = 20
        val natureBefore = game.userList[0].natureToken

        val token = game.displayedWildLifeToken[0]!!
        rootService.playerActionService.placeWildLifeToken(token, keystoneTile)

        assertEquals(
            natureBefore + 1,
            game.userList[0].natureToken,
            "Player should receive one nature token for placing on a keystone tile."
        )
        assertEquals(
            19, game.natureToken, "Shared nature token pool should decrease by one."
        )
    }

    /**
     * Tests that no nature token is granted when the shared pool is empty,
     * even on a keystone tile.
     */
    @Test
    fun testPlaceWildLifeTokenKeystoneNoGrantWhenPoolEmpty() {
        val game = checkNotNull(rootService.currentGame)
        val keystoneTile = HabitatTile(
            keyStone = true,
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )
        game.userList[0].board.placedHabitatTiles[Coordinate(1, 0)] = keystoneTile
        game.natureToken = 0
        val natureBefore = game.userList[0].natureToken

        val token = game.displayedWildLifeToken[0]!!
        rootService.playerActionService.placeWildLifeToken(token, keystoneTile)

        assertEquals(
            natureBefore,
            game.userList[0].natureToken,
            "Player should not receive a nature token when the pool is empty."
        )
    }

    /**
     * Tests that an exception is thrown when the tile already has a token placed on it.
     */
    @Test
    fun testPlaceWildLifeTokenThrowsWhenTileOccupied() {
        val game = checkNotNull(rootService.currentGame)
        val tile = game.userList[0].board.placedHabitatTiles[Coordinate(0, 0)]!!

        val token = game.displayedWildLifeToken[0]!!
        rootService.playerActionService.placeWildLifeToken(token, tile)

        assertThrows<IllegalArgumentException> {
            val token2 = game.displayedWildLifeToken.values.first()
            rootService.playerActionService.placeWildLifeToken(token2, tile)

        }
    }

    /**
     * Tests that an exception is thrown when the token type is not supported by the tile.
     */
    @Test
    fun testPlaceWildLifeTokenThrowsWhenTokenNotSupported() {
        val game = checkNotNull(rootService.currentGame)
        val tile = game.userList[0].board.placedHabitatTiles[Coordinate(0, 0)]!!

        val foxToken = game.displayedWildLifeToken[3]!!

        assertThrows<IllegalArgumentException> {
            rootService.playerActionService.placeWildLifeToken(foxToken, tile)
        }
    }

    /**
     * Tests that an exception is thrown when no game is currently active.
     */
    @Test
    fun testPlaceWildLifeTokenThrowsWhenNoGame() {
        val game = checkNotNull(rootService.currentGame)
        val tile = game.userList[0].board.placedHabitatTiles[Coordinate(0, 0)]!!

        rootService.currentGame = null

        assertThrows<IllegalStateException> {
            rootService.playerActionService.placeWildLifeToken(
                WildLifeToken(
                    WildLifeTokenType.BEAR
                ), tile
            )
        }
    }

    /**
     * Verifies that [UserActionService.saveGame] correctly serializes an active game state,
     * including board positions, supply stacks, and action histories.
     */
    @Test
    fun testSaveGame() {
        val service = rootService.playerActionService
        val tempFile = java.io.File.createTempFile("cascadia_save", ".txt").apply { deleteOnExit() }

        // Mutate setup objects slightly to force alternative branch coverage (Token vs NONE)
        val game = rootService.currentGame!!
        game.userList[0].board.placedHabitatTiles[Coordinate(0, 0)]?.placedWildLifeToken =
            WildLifeToken(WildLifeTokenType.BEAR)

        // Execute Save
        val savedPath = service.saveGame(tempFile.absolutePath)

        assertEquals(tempFile.absolutePath, savedPath)
        assertTrue(tempFile.exists())

        val lines = tempFile.readLines()
        assertTrue(lines.contains("currentUser=0"))
        assertTrue(lines.contains("tilePlaced=BEAR")) // Verifies token presence branch
        assertTrue(lines.contains("tokenStackCount=3")) // Verifies populated stack branch
        assertTrue(lines.contains("undoHistoryCount=1")) // Verifies history loop iteration

        // Error path validation guard lines
        rootService.currentGame = null
        assertThrows<IllegalStateException> { service.saveGame(tempFile.absolutePath) }
    }

    /** A save made from the pause menu must contain a resumable game state. */
    @Test
    fun testSavePausedGamePersistsStateBeforePause() {
        val service = rootService.playerActionService
        val tempFile = java.io.File.createTempFile("cascadia_paused_save", ".txt").apply { deleteOnExit() }
        val stateBeforePause = checkNotNull(rootService.currentGame).state

        service.pauseGame()
        service.saveGame(tempFile.absolutePath)

        assertTrue(tempFile.readLines().contains("state=$stateBeforePause"))
    }

    /**
     * Verifies that [UserActionService.loadGame] successfully parses a valid structured plain-text
     * snapshot payload to reconstruct a complete game container, builds the turn tracking state,
     * and correctly flags file validation path constraints.
     */
    @Test
    fun testLoadGame() {
        val service = rootService.playerActionService
        val tempFile = java.io.File.createTempFile("cascadia_load", ".txt").apply { deleteOnExit() }

        // A highly condensed, minimal save payload designed to execute every parsing line and loop context
        val minimalValidSavePayload = """
            currentUser=0
            natureToken=12
            state=WAIT_FOR_TURN
            gamePlaySpeed=1
            userCount=1
            userName=Alice
            userType=LOCAL_PLAYER
            userNatureToken=2
            userHasSwappedThree=false
            boardTileCount=1
            coord=0,0
            tileData=0,false
            tileEdges=FORESTS
            tileAvailable=BEAR
            tilePlaced=NONE
            scoringCardCount=1
            scoringCard=BEAR,false
            marketTokenCount=1
            tokenEntry=0,BEAR
            marketTileCount=1
            tileEntry=0
            tileData=0,false
            tileEdges=FORESTS
            tileAvailable=BEAR
            tilePlaced=NONE
            tokenStackCount=1
            tokenStackData=BEAR
            tileStackCount=1
            tileData=0,false
            tileEdges=FORESTS
            tileAvailable=BEAR
            tilePlaced=NONE
            undoHistoryCount=0
            redoHistoryCount=0
        """.trimIndent()

        tempFile.writeText(minimalValidSavePayload)

        // Execute Load
        val loadedGame = service.loadGame(tempFile.absolutePath)

        assertNotNull(loadedGame)
        assertEquals(12, loadedGame.natureToken)
        assertEquals(GameState.WAIT_FOR_TURN, loadedGame.state)
        assertEquals("Alice", loadedGame.userList[0].name)
        assertEquals(WildLifeTokenType.BEAR, loadedGame.wildLifeCollection.peek().type)

        assertNotNull(loadedGame.currentAction)
        assertEquals("Alice", loadedGame.currentAction.userStates?.oldState!!.name)

        // Error path validation guard lines
        assertThrows<IllegalArgumentException> { service.loadGame("   ") }
        assertThrows<IllegalStateException> { service.loadGame("invalid/path/file.txt") }
    }

    /** Legacy saves stored PAUSE and must be resumed so a current bot can run. */
    @Test
    fun testLoadLegacyPausedGameAsResumableTurn() {
        val service = rootService.playerActionService
        val sourceFile = java.io.File.createTempFile("cascadia_source", ".txt").apply { deleteOnExit() }
        val legacyFile = java.io.File.createTempFile("cascadia_legacy_pause", ".txt").apply { deleteOnExit() }

        service.saveGame(sourceFile.absolutePath)
        legacyFile.writeText(
            sourceFile.readText().replace(Regex("(?m)^state=.*$"), "state=PAUSE")
        )

        val loadedGame = service.loadGame(legacyFile.absolutePath)

        assertEquals(GameState.WAIT_FOR_TURN, loadedGame.state)
    }


    /**
     * Tests setSimulationSpeed
     */
    @Test
    fun testSetSimulationSpeed() {
        rootService.playerActionService.setSimulationSpeed(3)
        assertEquals(3, rootService.currentGame!!.gamePlaySpeed)
    }

    /**
     * Tests setSimulationSpeed throws when speed is 0
     */
    @Test
    fun testSetSimulationSpeedZero() {
        assertThrows<IllegalArgumentException> { rootService.playerActionService.setSimulationSpeed(0) }
    }

    /**
     * Tests setSimulationSpeed throws when speed is negative
     */
    @Test
    fun testSetSimulationSpeedNegative() {
        assertThrows<IllegalArgumentException> { rootService.playerActionService.setSimulationSpeed(-2) }
    }


    /**
     * Tests that useNaturalTokenIfNeeded decreases nature token when slots are different
     */
    @Test
    fun testUseNaturalTokenIfNeededDifferentSlots() {
        val game = checkNotNull(rootService.currentGame)
        val currentPlayer = game.userList[0]
        val tokensBefore = currentPlayer.natureToken

        rootService.playerActionService.useNaturalTokenIfNeeded(0, 1)
        assertEquals(tokensBefore - 1, currentPlayer.natureToken)
    }

    /**
     * Tests that useNaturalTokenIfNeeded does not change nature token when slots are equal
     */
    @Test
    fun testUseNatureTokenIfNeededSameSlots() {
        val game = checkNotNull(rootService.currentGame)
        val currentPlayer = game.userList[0]

        val tokensBefore = currentPlayer.natureToken

        rootService.playerActionService.useNaturalTokenIfNeeded(0, 0)

        assertEquals(tokensBefore, currentPlayer.natureToken)
    }

    /**
     * Tests that useNaturalTokenIfNeeded throws when no game is running
     */
    @Test
    fun testUseNaturalTokenIfNeededNoGame() {
        rootService.currentGame = null

        assertThrows<IllegalStateException> {
            rootService.playerActionService.useNaturalTokenIfNeeded(0, 0)
        }
    }

    /**
     * Tests that useNaturalTokenIfNeeded does not affect network when no online player exists
     */
    @Test
    fun testUseNaturalTokenIfNeededNoNetwork() {
        val game = checkNotNull(rootService.currentGame)
        val currentPlayer = game.userList[0]
        val tokensBefore = currentPlayer.natureToken

        rootService.playerActionService.useNaturalTokenIfNeeded(0, 2)

        assertEquals(tokensBefore - 1, currentPlayer.natureToken)
    }


    /**
     * Tests that useNaturalTokenIfNeeded sends network message when online player exists
     */
    @Test
    fun testUseNaturalTokenIfNeededWithOnlinePlayer() {
        val game = checkNotNull(rootService.currentGame)


        val onlinePlayer = User(name = "OnlineUser", type = UserType.ONLINE_PLAYER)
        val newUserList = game.userList.toMutableList()
        newUserList.add(onlinePlayer)

        val newGame = CascadiaGame(
            userList = newUserList,
            displayedWildLifeToken = game.displayedWildLifeToken,
            displayedHabitatTiles = game.displayedHabitatTiles,
            scoringCards = game.scoringCards
        ).apply {
            this.currentAction = game.currentAction
            this.wildLifeCollection = game.wildLifeCollection
        }

        rootService.currentGame = newGame

        val tokensBefore = newGame.userList[0].natureToken

        rootService.playerActionService.useNaturalTokenIfNeeded(0, 1)

        assertEquals(tokensBefore - 1, newGame.userList[0].natureToken)


    }


    /**
     * Tests that rotateHabitatTile increases rotation by 1
     */
    @Test
    fun testRotateHabitatTile() {
        val tile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val rotationBefore = tile.rotation

        rootService.playerActionService.rotateHabitatTile(tile)

        assertEquals((rotationBefore + 1) % 6, tile.rotation)


    }


    /**
     * Tests that rotateHabitatTile wraps around from 5 to 0
     */
    @Test
    fun testRotateHabitatTileWrapAround() {
        val tile = HabitatTile(
            rotation = 5,
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        rootService.playerActionService.rotateHabitatTile(tile)

        assertEquals(0, tile.rotation)
    }

    /**
     * Tests that rotateHabitatTile throws when no game is running
     */
    @Test
    fun testRotateHabitatTileNoGame2() {
        rootService.currentGame = null

        val tile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        assertThrows<IllegalStateException> { rootService.playerActionService.rotateHabitatTile(tile) }
    }

    /**
     * Tests that rotateHabitatTile with online player triggers network branch
     */
    @Test
    fun testRotateHabitatTileWithOnlinePlayer() {
        val game = checkNotNull(rootService.currentGame)

        val onlinePlayer = User(name = "OnlineUser", type = UserType.ONLINE_PLAYER)

        val newGame = CascadiaGame(
            userList = listOf(game.userList[0], onlinePlayer),
            displayedWildLifeToken = game.displayedWildLifeToken,
            displayedHabitatTiles = game.displayedHabitatTiles,
            scoringCards = game.scoringCards
        ).apply {
            this.currentAction = game.currentAction
            this.wildLifeCollection = game.wildLifeCollection
        }

        newGame.currentUser = 0
        rootService.currentGame = newGame

        rootService.networkService.updateConnectionState(ConnectionState.PLACING)

        val tile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        rootService.playerActionService.rotateHabitatTile(tile)
        assertEquals(1, tile.rotation)
    }

    /**
     * Tests that rotateHabitatTile can be called 6 times and then return to original
     */
    @Test
    fun testRotateHabitatTileFullRotation() {
        val tile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )
        val originalRotation = tile.rotation

        repeat(6) {
            rootService.playerActionService.rotateHabitatTile(tile)
        }

        assertEquals(originalRotation, tile.rotation)
    }


    /**
     * Tests that loadAction correctly restores an action
     */
    @Test
    fun testLoadActionViaSaveLoad() {
        val game = checkNotNull(rootService.currentGame)
        val selectedTile = checkNotNull(game.currentAction.selection.habitatTile)
        rootService.playerActionService.placeHabitatTile(selectedTile, 1, 0)


        val filePath = "test_save_load_action.txt"
        rootService.playerActionService.saveGame(filePath)

        val loadedGame = rootService.playerActionService.loadGame(filePath)

        assertNotNull(loadedGame)
        assertEquals(game.userList.size, loadedGame.userList.size)

        //cleanup
        java.io.File(filePath).delete()


    }

    /**
     * Tests that loadAction restores undo history
     */
    @Test
    fun testLoadActionRestoresUndoHistory() {
        val game = checkNotNull(rootService.currentGame)
        val selectedTile = checkNotNull(game.currentAction.selection.habitatTile)

        rootService.playerActionService.placeHabitatTile(selectedTile, 1, 0)

        val undoCountBefore = game.undoableHistory.size
        val filePath = "test_save_undo_history.txt"

        rootService.playerActionService.saveGame(filePath)

        val loadedGame = rootService.playerActionService.loadGame(filePath)
        assertEquals(undoCountBefore, loadedGame.undoableHistory.size)
        java.io.File(filePath).delete()
    }


    /**
     * Tests that loadAction restores swapped wildlife tokens correctly
     */
    @Test
    fun testLoadActionRestoresSwappedTokens() {
        val game = checkNotNull(rootService.currentGame)
        rootService.playerActionService.swapWildLifeToken(listOf(0, 1))

        val filePath = "test_save_swapped_tokens.txt"
        rootService.playerActionService.saveGame(filePath)

        val loadedGame = rootService.playerActionService.loadGame(filePath)

        assertNotNull(loadedGame)
        assertEquals(game.undoableHistory.size, loadedGame.undoableHistory.size)

        //cleanup
        java.io.File(filePath).delete()
    }


    /**
     * Tests that load action restores nature token count correctly
     */

    @Test
    fun testLoadActionRestoresNatureToken() {
        val game = checkNotNull(rootService.currentGame)
        val originalNatureToken = game.natureToken

        val filePath = "test_save_nature_token.txt"
        rootService.playerActionService.saveGame(filePath)

        val loaded = rootService.playerActionService.loadGame(filePath)

        assertEquals(originalNatureToken, loaded.natureToken)

        //cleanup
        java.io.File(filePath).delete()
    }


    /**
     * Tests that placeHabitatTile triggers network branch when online palyer exists
     */
    @Test
    fun testPlaceHabitatTileNetworkBranch() {
        val game = checkNotNull(rootService.currentGame)

        val onlinePlayer = User(name = "OnlineUser", type = UserType.ONLINE_PLAYER)
        val newGame = CascadiaGame(
            userList = listOf(game.userList[0], onlinePlayer),
            displayedWildLifeToken = game.displayedWildLifeToken,
            displayedHabitatTiles = game.displayedHabitatTiles,
            scoringCards = game.scoringCards
        ).apply {
            this.currentAction = game.currentAction
            this.wildLifeCollection = game.wildLifeCollection
        }

        rootService.currentGame = newGame

        val selectedTile = checkNotNull(game.currentAction.selection.habitatTile)

        rootService.playerActionService.placeHabitatTile(selectedTile, 1, 0)
        assertTrue(Coordinate(1, 0) in newGame.userList[0].board.placedHabitatTiles)

    }


    /**
     * Tests that placeWildLifeToken triggers network branch when online player exists
     */
    @Test
    fun testPlaceWildLifeTokenNetworkBranch() {
        val game = checkNotNull(rootService.currentGame)
        val onlinePlayer = User(name = "OnlineUser", type = UserType.ONLINE_PLAYER)

        val selectedTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )
        game.displayedHabitatTiles[0] = selectedTile
        game.currentAction.selection.habitatTile = selectedTile
        game.currentAction.selection.habitatTileIndex = 0

        game.userList[0].board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val newGame = CascadiaGame(
            userList = listOf(game.userList[0], onlinePlayer),
            displayedWildLifeToken = game.displayedWildLifeToken,
            displayedHabitatTiles = game.displayedHabitatTiles,
            scoringCards = game.scoringCards
        ).apply {
            this.currentAction = game.currentAction
            this.wildLifeCollection = game.wildLifeCollection
        }
        newGame.currentUser = 0
        newGame.state = GameState.WAIT_FOR_MOVE
        rootService.currentGame = newGame


        rootService.networkService.updateConnectionState(ConnectionState.PLACING)
        rootService.playerActionService.placeHabitatTile(selectedTile, 1, 0)


        rootService.networkService.updateConnectionState(ConnectionState.SELECTING)

        val user = newGame.userList[0]
        val tile = user.board.placedHabitatTiles[Coordinate(1, 0)]!!
        val token = newGame.displayedWildLifeToken.values.first()

        rootService.playerActionService.placeWildLifeToken(token, tile)

        assertNotNull(tile.placedWildLifeToken)
    }


    /**
     * Tests undo skips bot turns and goes back to last human turn.
     */
    @Test
    fun testUndoSkipsBotTurns() {
        val game = checkNotNull(rootService.currentGame)

        val humanUser = game.userList[0]
        val botUser = User(name = "Bot", type = UserType.PROFESSIONAL_BOT)

        val selectedTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val action = ActionBuilder(
            userStates = UserStateChange(
                oldState = humanUser,
                newState = humanUser
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

        val newGame = CascadiaGame(
            userList = listOf(humanUser, botUser),
            displayedWildLifeToken = game.displayedWildLifeToken,
            displayedHabitatTiles = game.displayedHabitatTiles,
            scoringCards = game.scoringCards
        ).apply {
            this.currentAction = action
            this.wildLifeCollection = game.wildLifeCollection
        }

        val humanAction = action.build()
        val botAction = action.build()

        newGame.undoableHistory.add(humanAction)
        newGame.undoableHistory.add(botAction)

        newGame.currentUser = 1

        rootService.currentGame = newGame

        val result = rootService.playerActionService.undo()

        assertTrue(result)
        assertEquals(UserType.LOCAL_PLAYER, newGame.userList[newGame.currentUser].type)

    }


    /**
     * Tests redo skips bot turns and goes forward to next human turn
     */
    @Test
    fun testRedoSkipsBotTurns() {
        val game = checkNotNull(rootService.currentGame)

        val humanUser = game.userList[0]
        val botUser = User(name = "Bot", type = UserType.PROFESSIONAL_BOT)

        val selectedTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )

        val action = ActionBuilder(
            userStates = UserStateChange(
                oldState = humanUser,
                newState = humanUser
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


        val newGame = CascadiaGame(
            userList = listOf(humanUser, botUser),
            displayedWildLifeToken = game.displayedWildLifeToken,
            displayedHabitatTiles = game.displayedHabitatTiles,
            scoringCards = game.scoringCards
        ).apply {
            this.currentAction = action
            this.wildLifeCollection = game.wildLifeCollection
        }

        val humanAction = action.build()
        val botAction = action.build()

        newGame.redoableHistory.add(humanAction)
        newGame.redoableHistory.add(botAction)
        newGame.redoableHistory.add(humanAction)
        newGame.currentUser = 0

        rootService.currentGame = newGame

        val result = rootService.playerActionService.redo()

        assertTrue(result)
        assertEquals(UserType.LOCAL_PLAYER, newGame.userList[newGame.currentUser].type)

    }


    /**
     * test that placeHabitatTile sends network messages
     */
    @Test
    fun testPlaceHabitatTileNetworkBranchSendsMessages() {
        val game = checkNotNull(rootService.currentGame)
        val onlinePlayer = User(name = "OnlineUser", type = UserType.ONLINE_PLAYER)


        val selectedTile = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )
        game.displayedHabitatTiles[0] = selectedTile  // manually add!

        game.currentAction.selection.habitatTile = selectedTile

        game.currentAction.selection.habitatTileIndex = 0



        game.userList[0].board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(WildLifeTokenType.BEAR)
        )


        val newGame = CascadiaGame(
            userList = listOf(game.userList[0], onlinePlayer),
            displayedWildLifeToken = game.displayedWildLifeToken,
            displayedHabitatTiles = game.displayedHabitatTiles,
            scoringCards = game.scoringCards
        ).apply {
            this.currentAction = game.currentAction
            this.wildLifeCollection = game.wildLifeCollection
        }
        newGame.currentUser = 0
        newGame.state = GameState.WAIT_FOR_MOVE
        rootService.currentGame = newGame

        rootService.playerActionService.placeHabitatTile(selectedTile, 1, 0)

        assertTrue(Coordinate(1, 0) in newGame.userList[0].board.placedHabitatTiles)
    }


}

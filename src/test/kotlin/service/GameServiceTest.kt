package service

import entity.*
import entity.action.ActionSelectionBuilder
import entity.action.CollectionState
import entity.action.UserStateChange
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.aqua.bgw.util.Stack
import kotlin.test.*

/** Tests for [GameService] covering game setup, turn progression, scoring, and edge cases. */
class GameServiceTest {

    private lateinit var rootService: RootService
    private lateinit var gameService: GameService
    private lateinit var testRefreshable: TestRefreshable


    /**
     * A mock observer to verify that the service layer
     * triggers the GUI scene refresh signals.
     */
    class TestRefreshable : Refreshable {
        var refreshAfterGameStartCalled = false
        var refreshAfterTurnCalled = false
        var refreshAfterGameEndCalled = false


        override fun refreshAfterGameStart() {
            refreshAfterGameStartCalled = true
        }

        override fun refreshAfterTurn() {
            refreshAfterTurnCalled = true
        }

        override fun refreshAfterGameEnd() {
            refreshAfterGameEndCalled = true
        }

    }

    /** Initialises [RootService], [GameService], and a [TestRefreshable] before each test. */
    @BeforeEach
    fun setUp() {
        // 1. Fix: RootService instantiates its own GameService as a 'val'.
        // We reference it directly instead of trying to overwrite it.
        rootService = RootService()
        gameService = rootService.gameService

        testRefreshable = TestRefreshable()
        gameService.addRefreshable(testRefreshable)
    }

    /**
     * Helper to fill currentAction with all mandatory fields so that
     * nextUser() and other methods that call .build() work correctly.
     */
    private fun fillCurrentAction(game: CascadiaGame) {
        val currentUser = game.userList[game.currentUser]

        game.currentAction = ActionBuilder(
            userStates = UserStateChange(
                oldState = currentUser,
                newState = currentUser
            ),
            selection = ActionSelectionBuilder(
                usedNatureToken = 0,
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
        )
    }

    /** Verifies that a new game is correctly initialized for three players. */
    @Test
    fun testStartNewGameSuccessWithThreePlayers() {
        val players = listOf(User("Alice"), User("Bob"), User("Charlie"))
        val cards = listOf(
            ScoringCard(wildLife = WildLifeTokenType.BEAR),
            ScoringCard(wildLife = WildLifeTokenType.ELK),
            ScoringCard(wildLife = WildLifeTokenType.SALMON),
            ScoringCard(wildLife = WildLifeTokenType.HAWK),
            ScoringCard(wildLife = WildLifeTokenType.FOX)
        )

        gameService.startNewGame(players, cards)

        val game = assertNotNull(rootService.currentGame)

        assertEquals(GameState.WAIT_FOR_TURN, game.state)
        assertEquals(0, game.currentUser)
        assertEquals(96, game.wildLifeCollection.size)

        val expectedTiles = 20 * players.size + 3 - 4
        assertEquals(expectedTiles, game.habitatTileCollection.size)

        val freshPlayers = players.map { User(it.name) }
        val freshRoot = RootService()
        freshRoot.gameService.startNewGame(freshPlayers, cards)

        val freshGame = assertNotNull(freshRoot.currentGame)

        assertEquals(4, freshGame.displayedHabitatTiles.size)
        assertEquals(4, freshGame.displayedWildLifeToken.size)

        freshPlayers.forEach { assertStarterBoard(it) }

        assertTrue(testRefreshable.refreshAfterGameStartCalled)
    }

    private fun assertStarterBoard(player: User) {
        val board = player.board

        assertEquals(3, board.placedHabitatTiles.size)
        assertTrue(Coordinate(0, 0) in board.placedHabitatTiles)
        assertTrue(Coordinate(0, 1) in board.placedHabitatTiles)
        assertTrue(Coordinate(-1, 1) in board.placedHabitatTiles)
    }


    /** Verifies that starting a game with fewer than two players throws [IllegalStateException]. */
    @Test
    fun testStartNewGameThrowsOnInvalidPlayerCount() {
        val invalidPlayers = listOf(User("LonelyPlayer"))
        val validCards = listOf(
            ScoringCard(wildLife = WildLifeTokenType.BEAR),
            ScoringCard(wildLife = WildLifeTokenType.ELK),
            ScoringCard(wildLife = WildLifeTokenType.SALMON),
            ScoringCard(wildLife = WildLifeTokenType.HAWK),
            ScoringCard(wildLife = WildLifeTokenType.FOX)
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            gameService.startNewGame(invalidPlayers, validCards)
        }
        assertTrue(exception.message!!.contains("Cascadia requires between 2 and 4 players."))
    }

    /** Verifies that starting a game with an incorrect number of scoring cards throws [IllegalStateException]. */
    @Test
    fun testStartNewGameThrowsOnInvalidScoringCardCount() {
        val validPlayers = listOf(User("Alice"), User("Bob"))
        val invalidCards = listOf(
            ScoringCard(wildLife = WildLifeTokenType.BEAR),
            ScoringCard(wildLife = WildLifeTokenType.ELK),
            ScoringCard(wildLife = WildLifeTokenType.SALMON),
            ScoringCard(wildLife = WildLifeTokenType.HAWK)
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            gameService.startNewGame(validPlayers, invalidCards)
        }
        assertTrue(exception.message!!.contains("Exactly 5 scoring cards must be provided."))
    }

    // ────────────── CHECK OVERPOPULATION TESTS ──────────────

    /**
     * A helper that initializes a fresh game (via startNewGame) and then
     * resets display/supply so each overpopulation test can fill them itself.
     */
    private fun setupGameForOverpopulation(): CascadiaGame {
        val players = listOf(User("Alice"), User("Bob"))
        val cards = listOf(
            ScoringCard(wildLife = WildLifeTokenType.BEAR),
            ScoringCard(wildLife = WildLifeTokenType.ELK),
            ScoringCard(wildLife = WildLifeTokenType.SALMON),
            ScoringCard(wildLife = WildLifeTokenType.HAWK),
            ScoringCard(wildLife = WildLifeTokenType.FOX)
        )
        gameService.startNewGame(players, cards)

        val game = checkNotNull(rootService.currentGame)
        // Reset display + supply so we control them fully in tests
        game.displayedWildLifeToken.clear()
        while (!game.wildLifeCollection.isEmpty()) {
            game.wildLifeCollection.pop()
        }
        return game
    }

    /** Verifies that no tokens are removed when no wildlife type exceeds three in the display. */
    @Test
    fun testNoOverpopulation() {
        val game = setupGameForOverpopulation()
        game.displayedWildLifeToken[0] = WildLifeToken(WildLifeTokenType.BEAR)
        game.displayedWildLifeToken[1] = WildLifeToken(WildLifeTokenType.FOX)
        game.displayedWildLifeToken[2] = WildLifeToken(WildLifeTokenType.HAWK)
        game.displayedWildLifeToken[3] = WildLifeToken(WildLifeTokenType.ELK)

        val result = gameService.checkOverPopulation()

        assertTrue(result.isEmpty(), "Result should be empty when no overpopulation")
    }


    /** Verifies that four identical tokens in the display trigger overpopulation removal. */
    @Test
    fun testFourIdenticalTokens() {
        val game = setupGameForOverpopulation()
        game.displayedWildLifeToken[0] = WildLifeToken(WildLifeTokenType.HAWK)
        game.displayedWildLifeToken[1] = WildLifeToken(WildLifeTokenType.HAWK)
        game.displayedWildLifeToken[2] = WildLifeToken(WildLifeTokenType.HAWK)
        game.displayedWildLifeToken[3] = WildLifeToken(WildLifeTokenType.HAWK)

        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.BEAR))
        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.FOX))
        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.SALMON))
        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.ELK))

        val result = gameService.checkOverPopulation()

        assertEquals(listOf(0, 1, 2, 3), result, "All 4 indices should be affected")
        for (i in 0..3) {
            assertTrue(
                game.displayedWildLifeToken[i]?.type != WildLifeTokenType.HAWK,
                "Index $i should no longer be HAWK"
            )
        }
    }

    /** Verifies that the game ends when the habitat tile supply is exhausted. */
    @Test
    fun testGameEndsWhenSupplyEmpty() {
        val game = setupGameForOverpopulation()
        game.displayedWildLifeToken[0] = WildLifeToken(WildLifeTokenType.BEAR)
        game.displayedWildLifeToken[1] = WildLifeToken(WildLifeTokenType.BEAR)
        game.displayedWildLifeToken[2] = WildLifeToken(WildLifeTokenType.BEAR)
        game.displayedWildLifeToken[3] = WildLifeToken(WildLifeTokenType.BEAR)
        // Supply is empty (cleared in setupGameForOverpopulation)

        gameService.checkOverPopulation()

        assertEquals(GameState.END, game.state, "Game should end when supply runs out")
    }

    /** Verifies that overpopulation is re-checked recursively after each cleanup. */
    @Test
    fun testRecursiveCheckOnRepeatedOverpopulation() {
        val game = setupGameForOverpopulation()
        game.displayedWildLifeToken[0] = WildLifeToken(WildLifeTokenType.BEAR)
        game.displayedWildLifeToken[1] = WildLifeToken(WildLifeTokenType.BEAR)
        game.displayedWildLifeToken[2] = WildLifeToken(WildLifeTokenType.BEAR)
        game.displayedWildLifeToken[3] = WildLifeToken(WildLifeTokenType.BEAR)

        // Supply forces another overpopulation — 4x FOX drawn next
        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.HAWK))
        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.FOX))
        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.FOX))
        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.FOX))
        game.wildLifeCollection.push(WildLifeToken(WildLifeTokenType.FOX))

        val result = gameService.checkOverPopulation()

        assertEquals(
            4, result.size,
            "All 4 indices should be affected after recursive check"
        )
    }

    /**
     * Tests if an exception is thrown when no game is currently running.
     */
    @Test
    fun testCheckOverPopulationNoGame() {
        rootService.currentGame = null
        assertFailsWith<IllegalStateException> {
            gameService.checkOverPopulation()
        }
    }

    // ────────────── NEXT USER TESTS ──────────────

    /**
     * Tests that nextUser advances currentUser to the next index and sets state to WAIT_FOR_TURN.
     */
    @Test
    fun testNextUserAdvancesToNextPlayer() {
        val players = listOf(User("Alice"), User("Bob"), User("Charlie"))
        val cards = listOf(
            ScoringCard(wildLife = WildLifeTokenType.BEAR),
            ScoringCard(wildLife = WildLifeTokenType.ELK),
            ScoringCard(wildLife = WildLifeTokenType.SALMON),
            ScoringCard(wildLife = WildLifeTokenType.HAWK),
            ScoringCard(wildLife = WildLifeTokenType.FOX)
        )
        gameService.startNewGame(players, cards)
        val game = checkNotNull(rootService.currentGame)
        game.currentUser = 0
        fillCurrentAction(game)
        gameService.nextUser()

        assertEquals(
            1, game.currentUser,
            "currentUser should advance to index 1."
        )
        assertEquals(
            GameState.WAIT_FOR_TURN, game.state,
            "State should be WAIT_FOR_TURN after advancing."
        )
    }

    /**
     * Tests that nextUser wraps around to the first player after the last player's turn.
     */
    @Test
    fun testNextUserWrapsAroundToFirstPlayer() {
        val players = listOf(User("Alice"), User("Bob"), User("Charlie"))
        val cards = listOf(
            ScoringCard(wildLife = WildLifeTokenType.BEAR),
            ScoringCard(wildLife = WildLifeTokenType.ELK),
            ScoringCard(wildLife = WildLifeTokenType.SALMON),
            ScoringCard(wildLife = WildLifeTokenType.HAWK),
            ScoringCard(wildLife = WildLifeTokenType.FOX)
        )
        gameService.startNewGame(players, cards)
        val game = checkNotNull(rootService.currentGame)
        game.currentUser = players.size - 1
        fillCurrentAction(game)
        gameService.nextUser()

        assertEquals(0, game.currentUser, "currentUser should wrap around to index 0.")
    }

    /**
     * Tests that nextUser ends the game when the habitat tile stack is empty.
     */
    @Test
    fun testNextUserEndsGameWhenHabitatTilesEmpty() {
        val players = listOf(User("Alice"), User("Bob"))
        val cards = listOf(
            ScoringCard(wildLife = WildLifeTokenType.BEAR),
            ScoringCard(wildLife = WildLifeTokenType.ELK),
            ScoringCard(wildLife = WildLifeTokenType.SALMON),
            ScoringCard(wildLife = WildLifeTokenType.HAWK),
            ScoringCard(wildLife = WildLifeTokenType.FOX)
        )
        gameService.startNewGame(players, cards)
        val game = checkNotNull(rootService.currentGame)

        while (!game.habitatTileCollection.isEmpty()) {
            game.habitatTileCollection.pop()
        }
        fillCurrentAction(game)
        gameService.nextUser()

        assertEquals(
            GameState.END, game.state,
            "Game should end when habitat tile stack is empty."
        )
        assertTrue(
            testRefreshable.refreshAfterGameEndCalled,
            "refreshAfterGameEnd should be triggered."
        )
    }

    /**
     * Tests that nextUser triggers the refreshAfterNextUser refresh signal on a normal turn change.
     */
    @Test
    fun testNextUserTriggersRefresh() {
        val players = listOf(User("Alice"), User("Bob"))
        val cards = listOf(
            ScoringCard(wildLife = WildLifeTokenType.BEAR),
            ScoringCard(wildLife = WildLifeTokenType.ELK),
            ScoringCard(wildLife = WildLifeTokenType.SALMON),
            ScoringCard(wildLife = WildLifeTokenType.HAWK),
            ScoringCard(wildLife = WildLifeTokenType.FOX)
        )
        gameService.startNewGame(players, cards)
        val game = checkNotNull(rootService.currentGame)
        fillCurrentAction(game)
        gameService.nextUser()

        assertTrue(testRefreshable.refreshAfterTurnCalled, "refreshAfterTurn should be triggered.")
    }

    /**
     * Tests that nextUser throws when no game is currently active.
     */
    @Test
    fun testNextUserThrowsWhenNoGame() {
        rootService.currentGame = null
        assertFailsWith<IllegalStateException> {
            gameService.nextUser()
        }
    }

    //-------------------Evaluate Scores TESTS-------------------
    private fun setupEvaluateScoresGame(): Pair<User, User> {
        val user1 = User(name = "User1", natureToken = 2)
        val user2 = User(name = "user2", natureToken = 1)

        val b1 = user1.board.placedHabitatTiles

        // Bear pairs Type A: 2 isolated pairs should be 11 points
        b1[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )
        b1[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )
        b1[Coordinate(3, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )
        b1[Coordinate(4, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )


        // Elk line of 3 Type A: 9 points
        b1[Coordinate(0, 2)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        b1[Coordinate(1, 2)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        b1[Coordinate(2, 2)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )

        // Salmon chain of 4 Type A: 12 points
        b1[Coordinate(0, 4)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.RIVERS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )
        b1[Coordinate(1, 4)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.RIVERS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )
        b1[Coordinate(2, 4)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.RIVERS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )
        b1[Coordinate(3, 4)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.RIVERS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )

        // Hawks Type B: 2 isolated with line of sight = 5 points
        b1[Coordinate(0, 6)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.MOUNTAINS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.HAWK)
        )
        b1[Coordinate(1, 6)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.MOUNTAINS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = null
        )
        b1[Coordinate(2, 6)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.MOUNTAINS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = null
        )
        b1[Coordinate(3, 6)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.MOUNTAINS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.HAWK)
        )

        // FOX Type B: 1 pair of bears adjacent = 3
        b1[Coordinate(0, 8)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.FOX)
        )
        b1[Coordinate(1, 8)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )
        b1[Coordinate(0, 7)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )


        // Forest corridor user1 with 4 tiles
        b1[Coordinate(5, 0)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf())
        b1[Coordinate(6, 0)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf())
        b1[Coordinate(7, 0)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf())
        b1[Coordinate(8, 0)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf())


        // Mountain corridor user1: 3 Tiles
        b1[Coordinate(5, 2)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.MOUNTAINS), availableWildLifeToken = listOf())
        b1[Coordinate(6, 2)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.MOUNTAINS), availableWildLifeToken = listOf())
        b1[Coordinate(7, 2)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.MOUNTAINS), availableWildLifeToken = listOf())

        val b2 = user2.board.placedHabitatTiles
        b2[Coordinate(0, 0)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf())
        b2[Coordinate(1, 0)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf())
        b2[Coordinate(0, 2)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.MOUNTAINS), availableWildLifeToken = listOf())
        b2[Coordinate(1, 2)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.MOUNTAINS), availableWildLifeToken = listOf())
        b2[Coordinate(2, 2)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.MOUNTAINS), availableWildLifeToken = listOf())
        b2[Coordinate(3, 2)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.MOUNTAINS), availableWildLifeToken = listOf())
        b2[Coordinate(4, 2)] =
            HabitatTile(edges = mutableListOf(HabitatTileType.MOUNTAINS), availableWildLifeToken = listOf())


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.FOX)
        )

        val game = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }

        rootService.currentGame = game
        return Pair(user1, user2)

    }


    /** Verifies that evaluateScores throws [IllegalStateException] when no game is active. */
    @Test
    fun testEvaluateScoreNoGame() {
        rootService.currentGame = null
        assertFailsWith<IllegalStateException> {
            rootService.gameService.evaluateScores()
        }
    }

    /** Verifies that evaluateScores throws [IllegalStateException] when game state is not END. */
    @Test
    fun testEvaluateScoreWrongState() {
        val user1 = User(name = "User1", natureToken = 2)
        val user2 = User(name = "user2", natureToken = 1)

        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.FOX)
        )

        rootService.gameService.startNewGame(listOf(user1, user2), scoringCards)

        rootService.currentGame!!.state = GameState.WAIT_FOR_TURN
        assertFailsWith<IllegalStateException> {
            gameService.evaluateScores()
        }
    }

    /** Verifies bear scoring using type A scoring card. */
    @Test
    fun testBearScoreTypeA() {
        val (user1, _) = setupEvaluateScoresGame()
        gameService.evaluateScores()
        val bearCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.BEAR }
        assertEquals(11, user1.scorePad.pointsByWildLifeToken[bearCard])
    }

    /** Verifies elk scoring using type A scoring card. */
    @Test
    fun testElkScoreTypeA() {
        val (user1, _) = setupEvaluateScoresGame()
        gameService.evaluateScores()
        val elkCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.ELK }
        assertEquals(9, user1.scorePad.pointsByWildLifeToken[elkCard])
    }

    /** Verifies salmon scoring using type A scoring card. */
    @Test
    fun testSalmonScoreTypeA() {
        val (user1, _) = setupEvaluateScoresGame()
        gameService.evaluateScores()
        val salmonCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.SALMON }
        assertEquals(12, user1.scorePad.pointsByWildLifeToken[salmonCard])
    }

    /** Verifies hawk scoring using type B scoring card. */
    @Test
    fun testHawkScoreTypeB() {
        val (user1, _) = setupEvaluateScoresGame()
        gameService.evaluateScores()
        val hawkCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.HAWK }
        assertEquals(5, user1.scorePad.pointsByWildLifeToken[hawkCard])
    }

    /** Verifies fox scoring using type B scoring card. */
    @Test
    fun testFoxScoreTypeB() {
        val (user1, _) = setupEvaluateScoresGame()
        gameService.evaluateScores()
        val foxCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.FOX }
        assertEquals(3, user1.scorePad.pointsByWildLifeToken[foxCard])
    }

    /** Verifies habitat corridor bonus scores are calculated correctly. */
    @Test
    fun testHabitatScoring() {
        val (user1, user2) = setupEvaluateScoresGame()
        gameService.evaluateScores()
        assertTrue(user1.scorePad.pointsByHabitatTiles.map { it.value }.sum() > 0)
        assertTrue(user2.scorePad.pointsByHabitatTiles.map { it.value }.sum() > 0)
        assertEquals(6, user1.scorePad.bonusPoints)
        assertEquals(2, user2.scorePad.bonusPoints)
    }

    /** Verifies that unused nature tokens contribute one point each at game end. */
    @Test
    fun testNatureTokenScoring() {
        val (user1, user2) = setupEvaluateScoresGame()
        gameService.evaluateScores()
        assertEquals(2, user1.scorePad.pointsByNatureToken)
        assertEquals(1, user2.scorePad.pointsByNatureToken)

    }

    /** Verifies that calling evaluateScores multiple times does not change the result. */
    @Test
    fun testEvaluateScoresIdempotent() {
        val (user1, _) = setupEvaluateScoresGame()
        gameService.evaluateScores()
        val firstTotal = user1.scorePad.totalPoints
        gameService.evaluateScores()
        assertEquals(firstTotal, user1.scorePad.totalPoints)
    }

    /** Verifies habitat bonus tie-breaking for two players with equal corridor lengths. */
    @Test
    fun testHabitatBonusTie() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")

        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )
        user1.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )

        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )
        user2.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )

        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON)
        )


        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()

        assertEquals(1, user1.scorePad.bonusPoints)
        assertEquals(1, user2.scorePad.bonusPoints)
    }

    /** Verifies bear scoring using type B scoring card with a simple grouping. */
    @Test
    fun testBearScoreTypeBSimple() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")

        // 3 bears in a triangle
        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )


        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )


        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val bearCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.BEAR }
        assertEquals(0, user1.scorePad.pointsByWildLifeToken[bearCard])
    }

    /** Verifies elk scoring using type B scoring card. */
    @Test
    fun testElkScoreTypeB() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")

        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        user1.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        user1.board.placedHabitatTiles[Coordinate(0, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        user1.board.placedHabitatTiles[Coordinate(1, -1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )


        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )

        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )

        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()

        val elkCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.ELK }
        assertEquals(13, user1.scorePad.pointsByWildLifeToken[elkCard])

    }


    /** Verifies salmon scoring using type B scoring card. */
    @Test

    fun testSalmonScoreTypeB() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")

        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.RIVERS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )

        user1.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.RIVERS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )

        user1.board.placedHabitatTiles[Coordinate(2, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.RIVERS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )


        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )

        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )


        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()

        val salmonCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.SALMON }

        assertEquals(9, user1.scorePad.pointsByWildLifeToken[salmonCard])

    }

    /** Verifies hawk scoring using type A scoring card. */
    @Test
    fun testHawkScoreTypeA() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")


        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.MOUNTAINS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.HAWK)
        )
        user1.board.placedHabitatTiles[Coordinate(3, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.MOUNTAINS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.HAWK)
        )

        user1.board.placedHabitatTiles[Coordinate(6, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.MOUNTAINS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.HAWK)
        )


        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()

        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )

        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val hawkCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.HAWK }

        assertEquals(8, user1.scorePad.pointsByWildLifeToken[hawkCard])

    }

    /** Verifies fox scoring using type A scoring card. */
    @Test
    fun testFoxScoreTypeA() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")



        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.FOX)
        )

        user1.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )

        user1.board.placedHabitatTiles[Coordinate(0, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )

        user1.board.placedHabitatTiles[Coordinate(-1, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )

        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES), availableWildLifeToken = listOf()

        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val foxCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.FOX }

        assertEquals(3, user1.scorePad.pointsByWildLifeToken[foxCard])


    }

    /** Verifies fox type B scoring when the fox is adjacent to two distinct animal pairs. */
    @Test
    fun testFoxScoreTypeBTwoPairs() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")



        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.FOX)
        )

        user1.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )

        user1.board.placedHabitatTiles[Coordinate(0, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )

        user1.board.placedHabitatTiles[Coordinate(-1, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        user1.board.placedHabitatTiles[Coordinate(-1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )

        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val foxCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.FOX }

        assertEquals(5, user1.scorePad.pointsByWildLifeToken[foxCard])
    }

    /** Verifies that a salmon with three or more neighbors breaks the chain and is excluded from scoring. */
    @Test
    fun testSalmonChainInterrupted() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")

        for (i in 0..4) {
            user1.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.RIVERS),
                availableWildLifeToken = listOf(),
                placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
            )
        }

        user1.board.placedHabitatTiles[Coordinate(3, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.RIVERS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )

        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )

        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        rootService.gameService.evaluateScores()


        val salmonCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.SALMON }
        val actualUser1 = rootService.currentGame!!.userList[0]


        assertEquals(13, actualUser1.scorePad.pointsByWildLifeToken[salmonCard])

    }

    /** Verifies that a hawk without line of sight to another hawk scores zero points. */
    @Test
    fun testHawkNoLineOfSight() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")



        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.MOUNTAINS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.HAWK)
        )


        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val hawkCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.HAWK }

        assertEquals(0, user1.scorePad.pointsByWildLifeToken[hawkCard])

    }

    /** Verifies that elk groups not meeting type A shape requirements score zero. */
    @Test
    fun testElkInvalidGroupTypeA() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")



        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )

        user1.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        user1.board.placedHabitatTiles[Coordinate(1, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )


        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val elkCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.ELK }

        assertEquals(7, user1.scorePad.pointsByWildLifeToken[elkCard])


    }

    /** Verifies habitat corridor bonuses are awarded correctly in a three-player game. */
    @Test
    fun testHabitatBonusThreePlayers() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")
        val user3 = User(name = "User3")

        for (i in 0..3) {
            user1.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS)
            )
        }

        for (i in 0..1) {
            user2.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS)
            )
        }
        user3.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS)
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2, user3),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        rootService.gameService.evaluateScores()


        assertEquals(3, user1.scorePad.bonusPoints)
        assertEquals(1, user2.scorePad.bonusPoints)
        assertEquals(0, user3.scorePad.bonusPoints)

    }


    /** Verifies habitat bonus tie rules in a three-player game when two players are tied for first. */
    @Test
    fun testHabitatBonusTwoPlayersTieThreePlayers() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")
        val user3 = User(name = "User3")

        for (user in listOf(user1, user2)) {
            for (i in 0..2) {
                user.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                    edges = mutableListOf(HabitatTileType.FORESTS)
                )
            }
        }
        user3.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS)
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2, user3),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        rootService.gameService.evaluateScores()


        assertEquals(2, user1.scorePad.bonusPoints)
        assertEquals(2, user2.scorePad.bonusPoints)
        assertEquals(0, user3.scorePad.bonusPoints)

    }

    /** Verifies habitat bonus when all three players are tied for the largest corridor. */
    @Test
    fun testHabitatBonusThreePlayersTie() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")
        val user3 = User(name = "User3")

        for (user in listOf(user1, user2, user3)) {
            user.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS)
            )
            user.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS)
            )
        }


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2, user3),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        rootService.gameService.evaluateScores()


        assertEquals(1, user1.scorePad.bonusPoints)
        assertEquals(1, user2.scorePad.bonusPoints)
        assertEquals(1, user3.scorePad.bonusPoints)

    }

    /** Verifies habitat corridor bonuses are awarded correctly in a four-player game. */
    @Test
    fun testHabitatBonusFourPlayers() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")
        val user3 = User(name = "User3")
        val user4 = User(name = "User4")

        for (i in 0..3) {
            user1.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS)
            )
        }

        for (i in 0..1) {
            user2.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS)
            )
        }
        user3.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS)
        )
        user4.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS)
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2, user3, user4),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        rootService.gameService.evaluateScores()

        assertEquals(3, user1.scorePad.bonusPoints)
        assertEquals(1, user2.scorePad.bonusPoints)
        assertEquals(0, user3.scorePad.bonusPoints)
        assertEquals(0, user4.scorePad.bonusPoints)

    }

    /** Verifies that no second-place bonus is awarded when first place is tied. */
    @Test
    fun testHabitatBonusNoSecondPlace() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")
        val user3 = User(name = "User3")

        for (i in 0..3) {
            user1.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS)
            )
        }


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2, user3),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        rootService.gameService.evaluateScores()


        assertEquals(3, user1.scorePad.bonusPoints)
        assertEquals(0, user2.scorePad.bonusPoints)
        assertEquals(0, user3.scorePad.bonusPoints)

    }

    /** Verifies second-place habitat bonus when two players are tied for second place. */
    @Test
    fun testHabitatBonusSecondPlaceTie() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")
        val user3 = User(name = "User3")

        for (i in 0..3) {
            user1.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS)
            )
        }

        for (user in listOf(user2, user3)) {
            for (i in 0..1) {
                user.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                    edges = mutableListOf(HabitatTileType.FORESTS)
                )

            }
        }


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2, user3),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        rootService.gameService.evaluateScores()


        assertEquals(3, user1.scorePad.bonusPoints)
        assertEquals(0, user2.scorePad.bonusPoints)
        assertEquals(0, user3.scorePad.bonusPoints)

    }

    /** Verifies hawk type A scoring with nine or more hawks on the board. */
    @Test
    fun testHawkScoreTypeANinePlusHawks() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")

        for (i in 0..8) {
            user1.board.placedHabitatTiles[Coordinate(i * 3, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.MOUNTAINS),
                availableWildLifeToken = listOf(),
                placedWildLifeToken = WildLifeToken(WildLifeTokenType.HAWK)
            )
        }


        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()

        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )

        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val hawkCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.HAWK }

        assertEquals(26, user1.scorePad.pointsByWildLifeToken[hawkCard])

    }

    /** Verifies fox type B scoring when the fox is adjacent to three distinct animal pairs. */
    @Test
    fun testFoxScoreTypeBThreePairs() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")



        user1.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.FOX)
        )

        user1.board.placedHabitatTiles[Coordinate(1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )

        user1.board.placedHabitatTiles[Coordinate(0, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
        )

        user1.board.placedHabitatTiles[Coordinate(-1, 1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        user1.board.placedHabitatTiles[Coordinate(-1, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.ELK)
        )
        user1.board.placedHabitatTiles[Coordinate(0, -1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )
        user1.board.placedHabitatTiles[Coordinate(1, -1)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.PRAIRIES),
            availableWildLifeToken = listOf(),
            placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
        )

        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val foxCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.FOX }

        assertEquals(7, user1.scorePad.pointsByWildLifeToken[foxCard])
    }

    /** Verifies salmon type A scoring for chains longer than four salmon. */
    @Test
    fun testSalmonScoreTypeALongChain() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")


        for (i in 0..6) {
            user1.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.RIVERS),
                availableWildLifeToken = listOf(),
                placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
            )
        }

        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val salmonCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.SALMON }

        assertEquals(25, user1.scorePad.pointsByWildLifeToken[salmonCard])
    }

    /** Verifies salmon type B scoring for long salmon chains. */
    @Test
    fun testSalmonScoreTypeBLongChain() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")


        for (i in 0..4) {
            user1.board.placedHabitatTiles[Coordinate(i, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.RIVERS),
                availableWildLifeToken = listOf(),
                placedWildLifeToken = WildLifeToken(WildLifeTokenType.SALMON)
            )
        }

        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = true, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val salmonCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.SALMON }

        assertEquals(17, user1.scorePad.pointsByWildLifeToken[salmonCard])
    }

    /** Verifies bear type A scoring with four bear pairs present. */
    @Test
    fun testBearScoreTypeAFourPairs() {
        val user1 = User(name = "User1")
        val user2 = User(name = "User2")


        for (i in 0..3) {
            user1.board.placedHabitatTiles[Coordinate(i * 4, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS),
                availableWildLifeToken = listOf(),
                placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
            )
            user1.board.placedHabitatTiles[Coordinate(i * 4 + 1, 0)] = HabitatTile(
                edges = mutableListOf(HabitatTileType.FORESTS),
                availableWildLifeToken = listOf(),
                placedWildLifeToken = WildLifeToken(WildLifeTokenType.BEAR)
            )
        }

        user2.board.placedHabitatTiles[Coordinate(0, 0)] = HabitatTile(
            edges = mutableListOf(HabitatTileType.FORESTS), availableWildLifeToken = listOf()
        )


        val scoringCards = listOf(
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.BEAR),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.ELK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.SALMON),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.HAWK),
            ScoringCard(isTypeB = false, wildLife = WildLifeTokenType.FOX)
        )





        rootService.currentGame = CascadiaGame(
            userList = listOf(user1, user2),
            displayedWildLifeToken = mutableMapOf(),
            displayedHabitatTiles = mutableMapOf(),
            scoringCards = scoringCards,
        ).apply {
            state = GameState.END
        }


        gameService.evaluateScores()


        val bearCard = rootService.currentGame!!.scoringCards.first { it.wildLife == WildLifeTokenType.BEAR }

        assertEquals(27, user1.scorePad.pointsByWildLifeToken[bearCard])
    }


}
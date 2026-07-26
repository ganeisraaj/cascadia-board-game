package service

import entity.ActionBuilder
import entity.Coordinate
import entity.HabitatTile
import entity.HabitatTileType
import entity.ScorePad
import entity.User
import entity.UserBoard
import entity.UserType
import entity.WildLifeToken
import entity.WildLifeTokenType
import entity.action.ActionSelectionBuilder
import entity.action.UserStateChange
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals


/** Tests for extension functions on [Coordinate] and [ActionBuilder]. */
class ExtensionsTest {

    /** Verifies that [Coordinate.getNeighbors] returns the correct six neighbors. */
    @Test
    fun testCorrectCoordinates() {
        val coordinate = Coordinate(0, 0)

        val listOfNeighbors = listOf(
            Coordinate(1, 0),
            Coordinate(1, -1),
            Coordinate(0, -1),
            Coordinate(-1, 0),
            Coordinate(-1, 1),
            Coordinate(0, 1)
        )

        assertEquals(listOfNeighbors, coordinate.getNeighbors())
    }
    /** Verifies that an empty [ActionBuilder] is not complete and throws on build. */
    @Test
    fun testActionBuilderNotCompleteAndNotReady() {
        val actionBuilder = ActionBuilder()

        assertEquals(false, actionBuilder.isComplete())
        assertThrows<IllegalStateException> { actionBuilder.build() }
    }

    /** Verifies that a fully populated [ActionBuilder] is complete and builds without error. */
    @Test
    fun testActionBuilderCompleteAndReady() {
        val actionBuilder = ActionBuilder(
            userStates = UserStateChange(
                oldState = User(
                    name = "Test player",
                    natureToken = 2,
                    type = UserType.LOCAL_PLAYER,
                    scorePad = ScorePad(),
                    board = UserBoard()
                ),
                newState = User(
                    name = "Test player",
                    natureToken = 1,
                    type = UserType.LOCAL_PLAYER,
                    scorePad = ScorePad(),
                    board = UserBoard()
                )
            ),
            selection = ActionSelectionBuilder(
                usedNatureToken = 0,
                habitatTileIndex = 1,
                wildlifeTokenIndex = 1,
                habitatTile = HabitatTile(
                    edges = mutableListOf(
                        HabitatTileType.FORESTS,
                        HabitatTileType.FORESTS,
                        HabitatTileType.FORESTS,
                        HabitatTileType.PRAIRIES,
                        HabitatTileType.PRAIRIES,
                        HabitatTileType.PRAIRIES
                    )
                ),
                wildlifeToken = WildLifeToken(WildLifeTokenType.BEAR)
            )
        )

        assertEquals(true, actionBuilder.isComplete())
        assertDoesNotThrow { actionBuilder.build() }
    }

}
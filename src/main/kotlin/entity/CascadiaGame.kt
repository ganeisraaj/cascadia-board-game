package entity

import tools.aqua.bgw.util.Stack

/**
 * Represents the current state of a Cascadia instance.
 *
 * @property currentUser Index of the active [User].
 * @property userList [User]s participating in the game.
 * @property natureToken Available nature tokens.
 * @property currentAction The [ActionBuilder] which is filled up during the move performed by the current user.
 * @property displayedWildLifeToken Currently displayed wildlife tokens.
 * @property displayedHabitatTiles Currently displayed [HabitatTile]s.
 * @property state Current state of [GameState].
 * @property habitatTileCollection Stack of remaining [HabitatTile]s.
 * @property undoableHistory Actions that can be undone.
 * @property redoableHistory Actions that can be redone.
 * @property scoringCards [ScoringCard]s used in the game.
 * @property wildLifeCollection Stack of remaining wildlife tokens.
 * @property gamePlaySpeed Simulation speed for games including only bots.
 */
class CascadiaGame(
    var currentUser: Int = 0,
    val userList: List<User>,
    var displayedWildLifeToken: MutableMap<Int, WildLifeToken>,
    var displayedHabitatTiles: MutableMap<Int, HabitatTile>,
    val undoableHistory: MutableList<Action> = mutableListOf(),
    val redoableHistory: MutableList<Action> = mutableListOf(),
    val scoringCards: List<ScoringCard>,

    ) {
    var natureToken: Int = 20
    var currentAction: ActionBuilder = ActionBuilder()
    var state: GameState = GameState.SETUP
    var habitatTileCollection: Stack<HabitatTile> = Stack()
    var wildLifeCollection: Stack<WildLifeToken> = Stack()
    var gamePlaySpeed: Int = 1

    /**
     * function for bot to copy game state
     */
    fun deepCopy(): CascadiaGame {
        return CascadiaGame(
            currentUser = currentUser,
            userList = userList.map { it.deepCopy() },
            displayedWildLifeToken = displayedWildLifeToken
                .mapValues { (_, token) -> WildLifeToken(token.type) }
                .toMutableMap(),
            displayedHabitatTiles = displayedHabitatTiles
                .mapValues { (_, tile) -> tile.deepCopy() }
                .toMutableMap(),
            undoableHistory = mutableListOf(),
            redoableHistory = mutableListOf(),
            scoringCards = scoringCards
        ).apply {
            habitatTileCollection = Stack<HabitatTile>().also { newStack ->
                habitatTileCollection.peekAll().forEach { tile ->
                    newStack.push(tile.deepCopy())
                }
            }

            wildLifeCollection = Stack<WildLifeToken>().also { newStack ->
                wildLifeCollection.peekAll().forEach { token ->
                    newStack.push(WildLifeToken(token.type))
                }
            }
        }
    }
}
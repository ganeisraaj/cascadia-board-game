package gui

import entity.User
import gui.components.ButtonCallbacks
import gui.components.CascadiaButton
import gui.components.CascadiaMenuScene
import gui.components.CascadiaTextField
import service.Refreshable
import service.RootService
import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ImageVisual

private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

/**
 * Scene to display standings of the players after the game has ended.
 * It is given an option to view all detailed scores to every player.
 *
 * @property rootService The current rootService instance.
 * @property onPressDetailedScore Callback method to switch to DetailedScoreScene from outside
 * @property onClickReturn Callback method to switch to MainMenuScene from outside
 */
class GameEndScene(
    val rootService: RootService,
    val onPressDetailedScore: (user: String) -> Unit = {},
    val onClickReturn: () -> Unit = {},
) : CascadiaMenuScene(),
    Refreshable {

    /**
     * button to navigate back to MainMenuScene.
     */
    private val returnButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Return",
        callbacks=ButtonCallbacks(onClick = { onClickReturn() })
    )

    private var userRanking: Pane<ComponentView> = createUserList(emptyList())

    init {
        background = ImageVisual("background_dirt.png")
        setSignText("Result")

        addComponents(returnButton)

        update()
    }

    /**
     * Update the ranking when a current game is ended so that ranking order and points are final.
     */
    override fun refreshAfterGameEnd() {
        update()
    }

    /**
     * Remove old ranking and add new ranking if a game is currently instantiated.
     */
    private fun update() {
        val currentGame = rootService.currentGame ?: return

        userRanking.clear()
        removeComponents(userRanking)

        userRanking = createUserList(currentGame.userList)
        addComponents(userRanking)
    }

    private fun createUserList(userList: List<User>) = Pane<ComponentView>(
        posX = SCENE_WIDTH / 2 - 720 / 2,
        posY = 450,
        width = 720,
        height = userList.size * 120,
    ).apply {

        val usersSortedByTotalPoints = userList.sortedByDescending { user -> user.scorePad.totalPoints }

        for ((index, user) in usersSortedByTotalPoints.withIndex()) {
            add(renderUser(120 * index, user, index))
        }
    }

    private fun renderUser(posY: Int, user: User, seeding: Int): Pane<ComponentView> {

        val placementIcon = when (seeding) {
            0 -> "\uD83E\uDD47"
            1 -> "\uD83E\uDD48"
            2 -> "\uD83E\uDD49"
            else -> "\uD83C\uDFC5"
        }

        val placementLabel = Label(
            posX = 10,
            posY = -5,
            width = 100,
            height = 100,
            text = placementIcon,
            font = Font(size = 56),
            alignment = Alignment.CENTER
        )

        val playerLabel = CascadiaTextField(width = 600, initialText = "${user.name} [${user.scorePad.totalPoints}]")

        val loopIcon = CascadiaButton(
            posX = 600 + 20,
            posY = 0,
            width = 100,
            image = ImageVisual("loop.png", 626 - 64, 626 - 64, 32, 32),
            callbacks=ButtonCallbacks(onClick = { onPressDetailedScore(user.name) }))

        return Pane<ComponentView>(posY = posY, width = 720, height = 100).apply {
            addAll(playerLabel, placementLabel, loopIcon)
        }
    }

}
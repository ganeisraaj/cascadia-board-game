package gui

import entity.ScoringCard
import entity.WildLifeTokenType
import gui.components.ButtonCallbacks
import gui.components.CascadiaButton
import gui.components.CascadiaMenuScene
import service.Refreshable
import service.RootService
import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.gamecomponentviews.CardView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.visual.ImageVisual
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

/**
 * Menu scene to display all previously saved games and load.
 * When game is loaded, game scene should be called.
 *
 * @property rootService The current rootService instance.
 * @property onClickResume Callback method to switch scene to GameScene from outside
 * @property onClickSave Callback method to show notification whether the game was saved successfully
 * @property onClickQuit Callback method to exit game from outside
 *
 */
class PauseScene(
    val rootService: RootService,
    val onClickResume: () -> Unit = {},
    val onClickSave: () -> Unit = {},
    val onClickQuit: () -> Unit = {},
) : CascadiaMenuScene(),
    Refreshable {

    private val resumeButton = CascadiaButton(
        posX = SCENE_WIDTH / 2 - 800 / 2,
        posY = 450,
        initialText = "Resume",
        callbacks=ButtonCallbacks(onClick = { handleResume() })
    )

    private val saveButton = CascadiaButton(
        posX = SCENE_WIDTH / 2 - 800 / 2,
        posY = 560,
        initialText = "Save",
        callbacks=ButtonCallbacks(onClick = { handleSaveGame() })
    )

    private val quitButton = CascadiaButton(
        posX = SCENE_WIDTH / 2 - 800 / 2,
        posY = 670,
        initialText = "Quit",
        callbacks= ButtonCallbacks(onClick = { onClickQuit() })
    )

    private var scoreCardSet: Pane<ComponentView> = createScoreCardSet(emptyList())

    init {
        background = ImageVisual("background_dirt.png")
        setSignText("Paused ❚❚")

        addComponents(resumeButton, saveButton, quitButton)
    }

    /**
     * Update the scoreCards when a current game is so that selected cards are final.
     */
    override fun refreshAfterGameStart() {
        update()
    }

    /**
     * Update the scoreCards when a current game is so that selected cards are final.
     */
    override fun refreshAfterLoadGame() {
        update()
    }

    private fun handleResume() {
        rootService.playerActionService.continueGame()

        onClickResume()
    }

    private fun handleSaveGame() {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        val timestamp = LocalDateTime.now().format(formatter)
        val folder = File("saves/")

        if (!folder.exists()) {
            folder.mkdirs()
        }

        rootService.playerActionService.saveGame("${folder.path}/$timestamp")

        onClickSave()
    }

    /**
     * Remove old scoreCards and add new ones if a game is currently instantiated.
     */
    private fun update() {
        val currentGame = rootService.currentGame ?: return
        val scoreCards = currentGame.scoringCards

        scoreCardSet.clear()
        removeComponents(scoreCardSet)

        scoreCardSet = createScoreCardSet(scoreCards)
        addComponents(scoreCardSet)
    }

    private fun createScoreCardSet(scoreCards: List<ScoringCard>) = Pane<ComponentView>(
        posX = SCENE_WIDTH / 2 - 1480 / 2,
        posY = SCENE_HEIGHT - 180 - 60,
        width = 1480,
        height = 180
    ).apply {
        for (scoreCard in scoreCards) {
            add(
                createScoreCard(
                    wildLifeTokenTypeIndex = WildLifeTokenType.entries.indexOf(scoreCard.wildLife)
                ).apply {
                    if (scoreCard.isTypeB) flip()
                }
            )
        }
    }

    private fun createScoreCard(wildLifeTokenTypeIndex: Int) = CardView(
        posX = wildLifeTokenTypeIndex * 300,
        width = 280,
        height = 180,
        front = ImageVisual(
            "scorecards/scorecard_" +
                    "${WildLifeTokenType.entries[wildLifeTokenTypeIndex].toString().lowercase()}_" +
                    "b.png"
        ),
        back = ImageVisual(
            "scorecards/scorecard_" +
                    "${WildLifeTokenType.entries[(wildLifeTokenTypeIndex + 1) % 5].toString().lowercase()}_" +
                    "a.png"
        )

    ).apply {
        onMouseEntered = {
            scale(1.2)
        }

        onMouseExited = {
            scale(1.0)
        }

    }

}
package gui

import gui.components.ButtonCallbacks
import gui.components.CascadiaButton
import gui.components.CascadiaMenuScene
import service.Refreshable
import service.RootService


private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

/**
 * The scene that will be shown once the player chooses the Online option from the main menu.
 * It is only there to allow the player to choose between hosting an online game or choosing one
 *
 * @property rootService The current rootService instance.
 *
 */
class OnlineGameMenuScene(
    val rootService: RootService,
    val onClickHost: () -> Unit = {},
    val onClickJoin: () -> Unit = {},
    val onClickBack: () -> Unit = {}
) :
    CascadiaMenuScene(), Refreshable {

    private val hostGameButton = CascadiaButton(
        posX = SCENE_WIDTH / 2 - 800 / 2,
        posY = 450,
        initialText = "Host game",
        callbacks=ButtonCallbacks(onClick = { onClickHost() }))

    private val joinGameButton = CascadiaButton(
        posX = SCENE_WIDTH / 2 - 800 / 2,
        posY = 570,
        initialText = "join game",
        callbacks= ButtonCallbacks(onClick = { onClickJoin() })
    )

    private val backButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Back",
        callbacks=ButtonCallbacks( onClick = { onClickBack() })
    )

    init {
        addComponents(hostGameButton, joinGameButton, backButton)
    }
}
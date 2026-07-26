package gui

import gui.components.ButtonCallbacks
import gui.components.CascadiaButton
import gui.components.CascadiaMenuScene
import service.Refreshable
import service.RootService

private const val SCENE_HEIGHT = 1080


/**
 *The main menu scene that will be the first shown scene once the application has been started.
 *
 * @property rootService, the RootService object that the scene will be bound to
 *
 **/
class MainMenuScene(
    val rootService: RootService,
    val onClickLocal: () -> Unit = {},
    val onClickOnline: () -> Unit = {},
    val onClickLoadGame: () -> Unit = {},
    val onClickQuit: () -> Unit = {}
) : CascadiaMenuScene(), Refreshable {

    private val localButton =
        CascadiaButton(posX = 560, posY = 450, callbacks = ButtonCallbacks(onClick = { onClickLocal() }))
    private val onlineButton =
        CascadiaButton(posX = 560, posY = 570, callbacks = ButtonCallbacks(onClick = { onClickOnline() }))
    private val loadGameButton =
        CascadiaButton(posX = 560, posY = 690, callbacks = ButtonCallbacks({ onClickLoadGame() }))
    private val quitButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Quit",
        callbacks = ButtonCallbacks(onClick = { onClickQuit() })
    )

    init {
        addComponents(localButton, onlineButton, loadGameButton, quitButton)
        localButton.setText("Local Game")
        onlineButton.setText("Online Game")
        loadGameButton.setText("Saved Games")
        quitButton.setText("Quit")
    }
}
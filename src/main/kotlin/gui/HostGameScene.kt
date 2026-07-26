package gui

import entity.User
import entity.UserType
import gui.components.CascadiaButton
import gui.components.CascadiaClickBox
import gui.components.CascadiaInputField
import gui.components.CascadiaMenuScene
import gui.components.ButtonCallbacks
import service.Refreshable
import service.RootService
import tools.aqua.bgw.core.Color

private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

/**
 * The game that will be shown when the player decides to host an online game
 * It gives them the options to choose their Username, put it in a custom Session ID if wanted and configure scorecards
 *
 * @property rootService, the RootService object it is bound to
 * @property onClickBack Callback method to handle click back from outside.
 * @property onClickStart Callback method to handle click start from outside.
 */
class HostGameScene(
    val rootService: RootService,
    val onClickBack: () -> Unit = {},
    val onClickStart: (localUser: User) -> Unit = {},
) :
    CascadiaMenuScene(), Refreshable {

    private val usernameInputField = CascadiaInputField(
        posX = SCENE_WIDTH / 2 - 820 / 2,
        posY = 450,
        width = 700,
        label = "Username",
        initialPrompt = "Enter username",
    )

    private val userTypeBox = CascadiaClickBox(
        posX = SCENE_WIDTH / 2 - 820 / 2 + 720,
        posY = 490,
        options = listOf(
            Pair("\uD81A\uDE06", Color("#5ff587")),
            Pair("R", Color("#f5e85f")),
            Pair("P", Color("#f55f5f"))
        )
    )

    private val sessionIDInputField = CascadiaInputField(
        posX = SCENE_WIDTH / 2 - 820 / 2,
        posY = 610,
        width = 700,
        label = "Custom SessionID",
        initialPrompt = "Enter ID"
    )

    private val backButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Back",
        callbacks=ButtonCallbacks(onClick = { handleClickBack() })
    )

    private val startButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Join",
        callbacks=ButtonCallbacks(onClick = { handleClickStart() })
    )

    private val userTypeMap = mapOf(
        "R" to UserType.RANDOM_BOT,
        "P" to UserType.PROFESSIONAL_BOT,
        "\uD81A\uDE06" to UserType.LOCAL_PLAYER
    )

    init {
        addComponents(
            backButton,
            startButton,
            usernameInputField,
            sessionIDInputField,
            userTypeBox
        )

        backButton.setText("Back")
        startButton.setText("Join")
    }

    private fun handleClickStart() {

        // Invalid username?
        if (usernameInputField.getText().isBlank()) {
            usernameInputField.setPrompt("Error: Invalid username")
            return
        }

        val networkService = rootService.networkService

        val username = usernameInputField.getText()
        val sessionID: String? = sessionIDInputField.getText().ifBlank { null }

        networkService.hostGame("wildlife", username, sessionID)

        onClickStart(
            User(
                name = username,
                type = userTypeMap[userTypeBox.selectedOption.first] ?: UserType.LOCAL_PLAYER,
            )
        )

        usernameInputField.setText("")
        usernameInputField.setPrompt("Enter username")

        sessionIDInputField.setText("")
        sessionIDInputField.setPrompt("Enter ID")

        userTypeBox.selectedOption = userTypeBox.options[0]
    }

    private fun handleClickBack() {
        val networkService = rootService.networkService
        networkService.disconnect()

        onClickBack()

        usernameInputField.setText("")
        usernameInputField.setPrompt("Enter username")

        sessionIDInputField.setText("")
        sessionIDInputField.setPrompt("Enter ID")

        userTypeBox.selectedOption = userTypeBox.options[0]
    }

}
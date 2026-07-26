package gui

import entity.User
import entity.UserType
import gui.components.ButtonCallbacks
import gui.components.CascadiaButton
import gui.components.CascadiaClickBox
import gui.components.CascadiaInputField
import gui.components.CascadiaMenuScene
import service.ConnectionState
import service.Refreshable
import service.RootService
import tools.aqua.bgw.core.Color

private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

private val userTypeOptions =
    listOf(
        Pair("\uD81A\uDE06", Color("#5ff587")),
        Pair("R", Color("#f5e85f")),
        Pair("P", Color("#f55f5f"))
    )

/**
 * Scene to be shown if a [entity.User] wants to join an online game.
 *
 * @property rootService The current rootService instance.
 * @property onClickJoin Callback method to handle click on "join"-button outside of this class.
 * @property onClickBack Callback method to handle click on "back"-button outside of this class.
 */
class JoinGameScene(
    val rootService: RootService,
    val onClickJoin: (localUser: User) -> Unit = {},
    val onClickBack: () -> Unit = {}
) : CascadiaMenuScene(),
    Refreshable {

    private val userNameInput = CascadiaInputField(
        posX = SCENE_WIDTH / 2 - 780 / 2,
        posY = 450,
        width = 700,
        label = "Username",
        initialPrompt = "Enter username"
    )

    private val userTypeBox = CascadiaClickBox(
        posX = SCENE_WIDTH / 2 + 700 / 2,
        posY = 490,
        options = userTypeOptions,
    )

    private val sessionInput = CascadiaInputField(
        posX = SCENE_WIDTH / 2 - 780 / 2,
        posY = 550 + 60,
        width = 700,
        label = "SessionID",
        initialPrompt = "Enter ID"
    )

    private val backButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Back",
        callbacks = ButtonCallbacks(onClick = { handleClickBack() })
    )

    private val joinButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Join",
        callbacks = ButtonCallbacks(onClick = { handleClickJoin() })
    )

    private val userTypeMap = mapOf(
        "R" to UserType.RANDOM_BOT,
        "P" to UserType.PROFESSIONAL_BOT,
        "\uD81A\uDE06" to UserType.LOCAL_PLAYER
    )

    init {
        addComponents(userNameInput, userTypeBox, sessionInput, backButton, joinButton)
    }

    private fun handleClickBack() {
        val networkService = rootService.networkService
        networkService.disconnect()

        onClickBack()

        userNameInput.setText("")
        userNameInput.setPrompt("Enter username")

        sessionInput.setText("")
        sessionInput.setPrompt("Enter ID")

        userTypeBox.selectedOption = userTypeBox.options[0]
    }

    private fun handleClickJoin() {

        if (userNameInput.getText().isBlank()) {
            userNameInput.setText("")
            userNameInput.setPrompt("Error: Invalid username")
            return
        }

        if (sessionInput.getText().isBlank()) {
            sessionInput.setText("")
            sessionInput.setPrompt("Error: Invalid ID")
            return
        }

        val networkService = rootService.networkService
        val username = userNameInput.getText()
        val sessionID = sessionInput.getText()

        networkService.joinGame("wildlife", username, sessionID)

        networkService.onPlayerJoined(username, true)

        onClickJoin(
            User(
                name = username,
                type = userTypeMap[userTypeBox.selectedOption.first] ?: UserType.LOCAL_PLAYER,
            )
        )

        userNameInput.setText("")
        userNameInput.setPrompt("Enter username")

        sessionInput.setText("")
        sessionInput.setPrompt("Enter ID")

        userTypeBox.selectedOption = userTypeBox.options[0]
    }

    override fun refreshConnectionState(state: ConnectionState) {
        println("ConnectionState: ${state.name}")
    }

    override fun refreshAfterConnectionStateChanged(newState: ConnectionState) {
        println("ConnectionState: ${newState.name}")
    }

}
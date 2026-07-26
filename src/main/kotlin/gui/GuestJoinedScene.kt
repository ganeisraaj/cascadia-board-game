package gui

import entity.User
import entity.UserType
import gui.components.ButtonCallbacks
import gui.components.CascadiaButton
import gui.components.CascadiaMenuScene
import gui.components.CascadiaTextField
import service.ConnectionState
import service.Refreshable
import service.RootService

private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

/**
 * The scene that will be shown once a player has successfully joined an online session.
 * It displays the names of all the connected users and the host
 *
 * @property rootService The current rootService instance.
 * @property onClickLeave Callback method to handle player leave from outside.
 */
class GuestJoinedScene(
    val rootService: RootService,
    val onClickLeave: () -> Unit = {},
    val onClickScoreCards: () -> Unit = {}
) : CascadiaMenuScene(), Refreshable {

    private var userList: MutableList<User> = mutableListOf()

    private val player1TextField =
        CascadiaTextField(posX = SCENE_WIDTH / 2 - 800 / 2, posY = 450).apply { isVisible = false }
    private val player2TextField =
        CascadiaTextField(posX = SCENE_WIDTH / 2 - 800 / 2, posY = 570).apply { isVisible = false }
    private val player3TextField =
        CascadiaTextField(posX = SCENE_WIDTH / 2 - 800 / 2, posY = 690).apply { isVisible = false }
    private val player4TextField =
        CascadiaTextField(posX = SCENE_WIDTH / 2 - 800 / 2, posY = 810).apply { isVisible = false }

    private val playerTextFieldList = listOf(player1TextField, player2TextField, player3TextField, player4TextField)

    private val leaveButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Leave",
        callbacks = ButtonCallbacks(onClick = { handleClickLeave() })
    )

    private val scoreCardsButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Cards",
        callbacks = ButtonCallbacks(onClick = { onClickScoreCards() })
    )

    init {
        addComponents(
            player1TextField,
            player2TextField,
            player3TextField,
            player4TextField,
            leaveButton,
            scoreCardsButton
        )
    }

    /**
     * Reset all TextFields when the game is started.
     */
    override fun refreshAfterGameStart() {
        syncTextFieldsTo(emptyList())
    }

    private fun handleClickLeave() {
        rootService.networkService.disconnect()

        syncTextFieldsTo(emptyList())

        onClickLeave()
    }

    /**
     * Synchronizes all displayed users to the actual list of users joined.
     *
     * @param userList The list of users that are currently joined.
     */
    fun syncTextFieldsTo(userList: List<User>) {

        playerTextFieldList.forEach { it.isVisible = false }

        userList.forEachIndexed { index, user ->

            val suffix = when (user.type) {
                UserType.RANDOM_BOT -> "[COM/R]"
                UserType.PROFESSIONAL_BOT -> "[COM/P]"
                else -> ""
            }

            playerTextFieldList[index].apply {
                isVisible = true
                setText("${user.name} $suffix")
            }
        }
        this.userList = userList.toMutableList()
    }

    override fun refreshAfterUserJoined(user: User) {
        if (userList.map { it.name }.contains(user.name)) {
            userList.removeIf { it.name == user.name }
        }

        println("CALLED")

        userList.add(user)
        syncTextFieldsTo(userList)
    }

    override fun refreshAfterUserLeft(user: User) {
        if (!userList.map { it.name }.contains(user.name)) return

        userList.removeIf { it.name == user.name }
        syncTextFieldsTo(userList)
    }

    override fun refreshConnectionState(state: ConnectionState) {
        println("ConnectionState: ${state.name}")
    }

    override fun refreshAfterConnectionStateChanged(newState: ConnectionState) {
        println("ConnectionState: ${newState.name}")
    }

    override fun refreshAfterGameConfigUpdated(players: List<User>, scoreCards: List<Boolean>) {
        // Update Userlist
        userList = players.toMutableList()
        syncTextFieldsTo(userList)

    }

}
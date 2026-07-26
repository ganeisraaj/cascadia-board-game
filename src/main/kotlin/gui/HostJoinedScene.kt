package gui

import entity.ScoringCard
import entity.User
import entity.UserType
import entity.WildLifeTokenType
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
 * The scene that will be shown if someone decides to host a lobby and has successfully created the lobby
 * The host has the additional buttons shuffle and start, unlike a player who joined.
 *
 * @property rootService, the rootService object that the scene is bound to
 * @property onClickLeave Callback method to handle click back from outside.
 * @property onClickStart Callback method to handle click start from outside.
 */
class HostJoinedScene(
    val rootService: RootService,
    val onClickLeave: () -> Unit = {},
    val onClickStart: () -> Unit = {},
    val onClickScoreCards: () -> Unit = {},
) :
    CascadiaMenuScene(), Refreshable {

    private var userList = mutableListOf<User>()
    private val scoreCards = WildLifeTokenType.entries.associateWith { ScoringCard(false, it) }
        .toMutableMap()

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

    private val startButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Start",
        callbacks = ButtonCallbacks( onClick = { handleClickStart() })
    )

    private val shuffleButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150 - 120,
        width = 300,
        initialText = "Shuffle",
        callbacks = ButtonCallbacks(onClick = { shufflePlayers() }))

    private val setupScoreCardsButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150 - 120 - 120,
        width = 300,
        initialText = "Cards",
        callbacks = ButtonCallbacks(onClick = { onClickScoreCards() })
    )

    init {
        addComponents(
            player1TextField, player2TextField, player3TextField, player4TextField, leaveButton, startButton,
            shuffleButton, setupScoreCardsButton
        )
    }

    /**
     * Reset all TextFields when the game is started.
     */
    override fun refreshAfterGameStart() {
        syncTextFieldsTo(emptyList())
        userList.clear()
    }

    /**
     * Update the ScoreCards from SetupScoreCardsScene
     *
     * @param scoreCardsTypeB Map of all WildLifeTokens to its type, whether it is type B or not.
     */
    fun updateScoreCards(scoreCardsTypeB: Map<WildLifeTokenType, Boolean>) {
        scoreCardsTypeB.forEach { scoreCardsTypeB ->
            scoreCards[scoreCardsTypeB.key] = ScoringCard(scoreCardsTypeB.value, scoreCardsTypeB.key)
        }
    }

    private fun handleClickLeave() {
        rootService.networkService.disconnect()

        onClickLeave()

        syncTextFieldsTo(emptyList())
    }

    private fun handleClickStart() {
        // Are there at least 2 and at most 4 players?
        if (userList.size !in 2..4) return

        val networkService = rootService.networkService
        val scoreCards = scoreCards.map { it.value }

        networkService.startNewHostedGame(userList, scoreCards)

        onClickStart()
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

    private fun shufflePlayers() {
        userList.shuffle()

        rootService.networkService.sendGameConfigMessage(
            players = userList,
            scoreCards = scoreCards.values.map { it.isTypeB }
        )

        syncTextFieldsTo(userList)
    }

    override fun refreshAfterUserJoined(user: User) {
        if (userList.map { it.name }.contains(user.name)) {
            userList.removeIf { it.name == user.name }
        }

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

        // Update ScoreCards
        this.scoreCards.onEachIndexed { index, (wildLifeTokenType, card) ->
            if (card.isTypeB != scoreCards[index]) {
                this.scoreCards[wildLifeTokenType] = ScoringCard(scoreCards[index], wildLifeTokenType)
            }
        }
    }

}
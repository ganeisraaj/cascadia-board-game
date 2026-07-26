package gui

import entity.ScoringCard
import entity.User
import entity.UserType
import entity.WildLifeTokenType
import gui.components.*
import service.Refreshable
import service.RootService
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.visual.ImageVisual

private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

/**
 * the scene that will be shown if the player chooses to play a local game.
 * It will allow them to configure player names, playing order, scorecards and whether to add bots
 *
 * @property rootService, the RootService object that the scene is bound to
 */
class LocalGameMenuScene(
    val rootService: RootService,
    val onClickBack: () -> Unit = {},
    val onClickStart: () -> Unit = {},
    val onClickScoreCards: () -> Unit = {},
) :
    CascadiaMenuScene(), Refreshable {

    private var userList: MutableList<User> = mutableListOf()
    private val userTypeMap = mapOf(
        "R" to UserType.RANDOM_BOT,
        "P" to UserType.PROFESSIONAL_BOT
    )

    private val scoreCards = WildLifeTokenType.entries.associateWith { ScoringCard(false, it) }
        .toMutableMap()

    private val player1TextField =
        CascadiaTextField(
            posX = SCENE_WIDTH / 2 - 1040 / 2,
            posY = 570,
            width = 600
        ).apply { isVisible = false }

    private val player2TextField =
        CascadiaTextField(
            posX = SCENE_WIDTH / 2 - 1040 / 2,
            posY = 690,
            width = 600
        ).apply { isVisible = false }

    private val player3TextField =
        CascadiaTextField(
            posX = SCENE_WIDTH / 2 - 1040 / 2,
            posY = 810,
            width = 600
        ).apply { isVisible = false }

    private val player4TextField =
        CascadiaTextField(
            posX = SCENE_WIDTH / 2 - 1040 / 2,
            posY = 930,
            width = 600
        ).apply { isVisible = false }

    private val playerTextFieldList = listOf(
        player1TextField,
        player2TextField,
        player3TextField,
        player4TextField
    )

    private val player1DeleteButton =
        createDeleteButton(0).apply { isVisible = false }

    private val player2DeleteButton =
        createDeleteButton(1).apply { isVisible = false }

    private val player3DeleteButton =
        createDeleteButton(2).apply { isVisible = false }

    private val player4DeleteButton =
        createDeleteButton(3).apply { isVisible = false }

    private val playerDeleteButtonList = listOf(
        player1DeleteButton,
        player2DeleteButton,
        player3DeleteButton,
        player4DeleteButton
    )

    private val nameInputField =
        CascadiaInputField(
            posX = SCENE_WIDTH / 2 - 1040 / 2,
            posY = 410,
            width = 600,
            label = "Username",
            initialPrompt = "Enter username"
        )

    private val userTypeButton = CascadiaClickBox(
        posX = SCENE_WIDTH / 2 - 1040 / 2 + 620,
        posY = 450,
        options = listOf(
            Pair("\uD81A\uDE06", Color("#5ff587")),
            Pair("R", Color("#f5e85f")),
            Pair("P", Color("#f55f5f"))
        )
    )

    private val addPlayerButton = CascadiaButton(
        posX = SCENE_WIDTH / 2 - 1040 / 2 + 620 + 120,
        posY = 450,
        width = 300,
        initialText = "Add",
        callbacks=ButtonCallbacks(onClick = {
            addUser(
                name = nameInputField.getText(),
                userType = userTypeMap.getOrDefault(
                    userTypeButton.selectedOption.first,
                    UserType.LOCAL_PLAYER
                ),
            )
        })
    )

    private val shuffleButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150 - 120,
        width = 300,
        initialText = "Shuffle",
        callbacks=ButtonCallbacks(onClick = { shufflePlayers() })
    )


    private val backButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Back",
        callbacks=ButtonCallbacks(onClick = { handleClickBack() })
    )

    private val setupScoreCardsButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150 - 120 - 120,
        width = 300,
        initialText = "Cards",
        callbacks=ButtonCallbacks(onClick = { onClickScoreCards() })
    )

    private val startButton = CascadiaButton(
        posX = SCENE_WIDTH - 300 - 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Start",
        callbacks=ButtonCallbacks(onClick = { handleClickStart() })
    )

    init {
        addComponents(
            player1TextField,
            player2TextField,
            player3TextField,
            player4TextField,
            player1DeleteButton,
            player2DeleteButton,
            player3DeleteButton,
            player4DeleteButton,
            nameInputField,
            userTypeButton,
            addPlayerButton,
            shuffleButton,
            startButton,
            backButton,
            setupScoreCardsButton
        )

    }

    override fun refreshAfterGameStart() {
        userList.clear()
        syncTextFieldsTo(emptyList())

        nameInputField.setText("")
        nameInputField.setPrompt("Enter name")
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

    private fun handleClickBack() {
        userList.clear()

        syncTextFieldsTo(emptyList())

        nameInputField.setText("")
        nameInputField.setPrompt("Enter name")

        onClickBack()
    }

    private fun handleClickStart() {
        // Are there at least 2 and at most 4 players?
        if (userList.size !in 2..4) return

        rootService.gameService.startNewGame(
            users = userList.toList(),
            scoringCards = scoreCards.values.toList()
        )

        onClickStart()
    }

    private fun addUser(name: String, userType: UserType) {

        if (userList.size == 4) return

        if (name == "") {
            nameInputField.setText("")
            nameInputField.setPrompt("Error: Invalid username")
            return
        }

        if (name in userList.map { it.name }) return

        userList.add(User(name = name, type = userType))

        syncTextFieldsTo(userList)

        nameInputField.setText("")
        nameInputField.setPrompt("Enter username")

    }

    /**
     * Synchronizes all displayed users to the actual list of users joined.
     *
     * @param userList The list of users that are currently joined.
     */
    fun syncTextFieldsTo(userList: List<User>) {

        playerTextFieldList.forEach { it.isVisible = false }
        playerDeleteButtonList.forEach { it.isVisible = false }

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

            playerDeleteButtonList[index].isVisible = true
        }
        this.userList = userList.toMutableList()
    }

    private fun createDeleteButton(playerIndex: Int) = CascadiaButton(
        posX = SCENE_WIDTH / 2 - 1040 / 2 + 620,
        posY = playerTextFieldList[playerIndex].posY.toInt(),
        width = 100,
        image = ImageVisual("cross.png"),
        callbacks=ButtonCallbacks(onClick = {
            if (playerIndex < userList.size) {
                userList.removeAt(playerIndex)
                syncTextFieldsTo(userList)
            }
        })
    )

    private fun shufflePlayers() {
        userList.shuffle()

        syncTextFieldsTo(userList)
    }
}
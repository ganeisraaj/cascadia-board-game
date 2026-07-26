package gui

import entity.Coordinate
import entity.HabitatTile
import entity.UserType
import entity.WildLifeToken
import gui.components.*
import service.Refreshable
import service.RootService
import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.gamecomponentviews.HexagonView
import tools.aqua.bgw.components.gamecomponentviews.TokenView
import tools.aqua.bgw.components.layoutviews.CameraPane
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.event.DragEvent
import tools.aqua.bgw.visual.ImageVisual
import java.util.*
import kotlin.collections.set
import entity.GameState

private const val SCENE_WIDTH = 1920
private const val SCENE_HEIGHT = 1080

/**
 * Represents the game scene where the game is being played.
 *
 * @property rootService The current rootService instance.
 * @property onClickPause Callback method to switch scenes from outside.
 */
@Suppress("TooManyFunctions")
class GameScene(
    val rootService: RootService,
    val onClickPause: () -> Unit = {},
) : CascadiaGameScene(), Refreshable {
    private val botTimer = Timer("bot-turn-timer", true)
    private var pendingBotTask: TimerTask? = null
    val loader = CascadiaImageLoader()
    private val natureTokenBag = CascadiaBag(
        posX = 80,
        posY = SCENE_HEIGHT - 160 - 160,
        visual = ImageVisual("bag_naturetoken.png")
    ).apply {
        area.dropAcceptor = { dragEvent ->
            dragEvent.draggedComponent == inventory.natureToken
        }

        area.onDragDropped = { handleDropNatureToken() }
    }

    private val wildLifeTokenBag = CascadiaBag(
        posX = 260,
        posY = SCENE_HEIGHT - 160 - 40,
        visual = ImageVisual("bag_wildlifetoken.png")
    ).apply {
        area.dropAcceptor = { dragEvent ->
            dragEvent.draggedComponent in display.wildLifeVisualSlotMap.keysForward
        }

        area.onDragDropped = { dragEvent -> handleDropWildLifeTokenBag(dragEvent) }
    }

    private val swapButton = CascadiaButton(
        posX = 460,
        posY = SCENE_HEIGHT - 100 - 40,
        width = 200,
        initialText = "Swap",
        callbacks=ButtonCallbacks(onClick = { handleSwap() })
    )

    private val pauseButton = CascadiaButton(
        posX = 0,
        width = 100,
        initialText = "❚❚",
        callbacks=ButtonCallbacks(onClick = { handlePause() }),
    )

    private val localUndoButton = CascadiaButton(
        posX = 120,
        width = 100,
        initialText = "<-",
        callbacks=ButtonCallbacks(onClick = { handleUndo() })
    )

    private val localRedoButton = CascadiaButton(
        posX = 240,
        width = 100,
        initialText = "->",
        callbacks=ButtonCallbacks( onClick = { handleRedo() })
    )

    private val simulationSpeedDecreaseButton = CascadiaButton(
        posX = 120,
        width = 100,
        initialText = "<<",
            callbacks=ButtonCallbacks(  onClick = { handleSimulationSpeedDecrease() })
    )

    private val simulationSpeedIncreaseButton = CascadiaButton(
        posX = 240,
        width = 100,
        initialText = ">>",
                callbacks=ButtonCallbacks(  onClick = { handleSimulationSpeedIncrease() })
    )

    /**
     * Collects all local options which include to pause, undo and redo.
     * If it is an online game, this pane is not being added, so these options would not be executable.
     */
    private val localOptionsPane = Pane<CascadiaButton>(
        posX = SCENE_WIDTH - 400,
        posY = 60,
        width = 340,
        height = 100,
    )

    private val display = CascadiaDisplay(
        posX = SCENE_WIDTH / 2 - 450 / 2,
        posY = SCENE_HEIGHT - 220 - 40,
        width = 450,
        height = 220
    ).apply { zIndex = 1000 }

    private val inventory = CascadiaUserInventory(
        posX = SCENE_WIDTH - 225 - 80,
        posY = SCENE_HEIGHT - 115 - 40,
    )

    private val swapThreeEqualDialog = CascadiaDialog(
        SCENE_WIDTH / 2 - 800 / 2,
        SCENE_HEIGHT / 2 - 500 / 2,
        dialogTitle = "Swap WildLifeToken?",
        dialogText = "The given display seems to give you three equal WildLifeToken. " +
                "Do you want to swap all three voluntarily?",
        onAccept = { handleSwapThreeEqualVoluntarily() }
    ).apply { zIndex = 1000 }

    private val rotateDialog = CascadiaRotationDialog(
        SCENE_WIDTH / 2 - 800 / 2,
        SCENE_HEIGHT / 2 - 500 / 2,
        onConfirm = { habitatTile, coordinate, rotation ->
            handlePlaceHabitatTile(
                habitatTile,
                coordinate,
                rotation
            )
        }
    ).apply {
        zIndex = 2000
    }

    private val cameraPanes = mutableMapOf<String, CameraPane<CascadiaUserBoard>>()
    private val userBoards = mutableMapOf<String, CascadiaUserBoard>()
    private val currentWildLifeTokenSlotsToSwap = mutableListOf<Int>()

    init {

        addComponents(
            natureTokenBag,
            wildLifeTokenBag,
            localOptionsPane,
            display,
            inventory,
            swapThreeEqualDialog,
            swapButton,
            rotateDialog
        )

    }

    private fun createUserBoard() = CascadiaUserBoard(
        posX = 0,
        posY = 0,
        width = 2400,
        height = 1200,
        isValidHabitatTile = { displayHabitatTileVisual ->
            displayHabitatTileVisual in display.habitatTileVisualSlotMap.keysForward
        },
        isValidWildLifeToken = { displayWildLifeTokenVisual ->
            displayWildLifeTokenVisual in display.wildLifeVisualSlotMap.keysForward
        },
        onHabitatTileDropped = { coordinate, view ->
            handleDropHabitatTileUserBoard(coordinate, view)
        },
        onWildLifeTokenDropped = { coordinate, view ->
            handleDropWildLifeTokenUserBoard(coordinate, view)
        },
    )

    private fun createCameraPane(userBoard: CascadiaUserBoard) = CameraPane(
        posX = SCENE_WIDTH / 2 - 1600 / 2,
        posY = SCENE_HEIGHT / 2 - 800 / 2,
        width = 1600,
        height = 800,
        target = userBoard
    ).apply {
        zIndex = 0
        interactive = true
    }

    private fun handlePlaceHabitatTile(habitatTile: HabitatTile, coordinate: Coordinate, rotation: Int) {

        rootService.playerActionService.placeHabitatTile(
            habitatTile = habitatTile,
            posX = coordinate.q,
            posY = coordinate.r
        )

        repeat(rotation) {
            rootService.playerActionService.rotateHabitatTile(habitatTile)
        }

    }

    private fun handlePause() {
        rootService.playerActionService.pauseGame()

        val game = rootService.currentGame ?: return

        if (game.state == GameState.PAUSE) {
            onClickPause()
        }
    }

    private fun handleSimulationSpeedIncrease() {
        val currentGame = checkNotNull(rootService.currentGame)
        val speed = currentGame.gamePlaySpeed

        if (speed == 1) return

        rootService.playerActionService.setSimulationSpeed(speed - 1)
    }

    private fun handleSimulationSpeedDecrease() {
        val currentGame = checkNotNull(rootService.currentGame)
        val speed = currentGame.gamePlaySpeed

        if (speed == 5) return

        rootService.playerActionService.setSimulationSpeed(speed + 1)
    }

    private fun isUndoRedoPossible(): Boolean {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentAction = currentGame.currentAction

        if (currentAction.selection.wildlifeToken != null
            || currentAction.selection.habitatTile != null
            || currentAction.selection.usedNatureToken > 0
        ) return false

        if (!currentWildLifeTokenSlotsToSwap.isEmpty()) return false

        return true
    }

    private fun handleUndo() {
        val currentGame = checkNotNull(rootService.currentGame)

        if (!isUndoRedoPossible()) return
        if (currentGame.undoableHistory.isEmpty()) return

        rootService.playerActionService.undo()
    }

    private fun handleRedo() {
        val currentGame = checkNotNull(rootService.currentGame)

        if (!isUndoRedoPossible()) return
        if (currentGame.redoableHistory.isEmpty()) return

        rootService.playerActionService.redo()
    }

    private fun handleSwap() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentAction = currentGame.currentAction
        val currentUser = currentGame.userList[currentGame.currentUser]

        if (currentUser.natureToken <= 0) return
        if (currentUser.type != UserType.LOCAL_PLAYER) return
        if (currentAction.selection.wildlifeToken != null) return

        rootService.playerActionService.swapWildLifeToken(currentWildLifeTokenSlotsToSwap)
        currentWildLifeTokenSlotsToSwap.clear()

        return
    }

    private fun handleDropWildLifeTokenBag(dragEvent: DragEvent) {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val currentAction = currentGame.currentAction
        val wildLifeTokenEntity = display.wildLifeVisualEntityMap[dragEvent.draggedComponent as TokenView]
        val slot = display.wildLifeVisualSlotMap[display.wildLifeVisualEntityMap.backward(wildLifeTokenEntity)]

        if (currentUser.type != UserType.LOCAL_PLAYER) return
        if (currentAction.selection.wildlifeToken != null) return     // Swapping Token is only possible before placing

        // Swapping tokens
        if (currentAction.selection.habitatTileIndex == null) {
            if (currentUser.natureToken <= 0) return
            if (slot in currentWildLifeTokenSlotsToSwap) return

            currentWildLifeTokenSlotsToSwap += slot
            display.removeWildLifeTokenAt(slot)
            return
        }

        // Discarding token
        println("REPLACING ${currentGame.wildLifeCollection.size}")

        // Replace token of the display with the top one of the collection
        if (currentGame.wildLifeCollection.isNotEmpty()) {

            //!! HAS TO GO INTO SERVICE - FOR NOW THIS IS WORKING JUST FINE

            val topMostToken = currentGame.wildLifeCollection.pop()
            currentGame.displayedWildLifeToken[slot] = topMostToken

            display.wildLifeVisualSlotMap.backward(slot).visual =
                loader.wildLifeImageFor(topMostToken.type)

            println("Popped element: ${topMostToken.type}")

            currentAction.selection.wildlifeToken = wildLifeTokenEntity
            currentAction.selection.wildlifeTokenIndex = slot

            display.setWildLifeDraggability(false)

            if (currentAction.selection.wildlifeToken != null && currentAction.selection.habitatTile != null) {
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        rootService.gameService.nextUser()
                    }
                }, 1000)
            }

        }

    }

    /**
     * Handle drag wildLifeToken from display to wildLifeToken bag or userBoard.
     *
     * @param coordinate The coordinate of the board where the HabitatTile is dropped on.
     * @param wildLifeTokenVisual The dragged WildLifeToken from the display.
     */
    private fun handleDropWildLifeTokenUserBoard(
        coordinate: Coordinate,
        wildLifeTokenVisual: TokenView
    ) {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val currentAction = currentGame.currentAction
        val wildLifeTokenEntity = display.wildLifeVisualEntityMap[wildLifeTokenVisual]
        val habitatTileEntity = checkNotNull(currentUser.board.placedHabitatTiles[coordinate])

        if (currentAction.selection.wildlifeToken != null) return
        if (habitatTileEntity.placedWildLifeToken != null) return
        if (wildLifeTokenEntity.type !in habitatTileEntity.availableWildLifeToken) return

        if (currentAction.selection.habitatTile != null) {
            val habitatTileSlot = checkNotNull(currentAction.selection.habitatTileIndex)
            val wildLifeSlot = currentGame.displayedWildLifeToken.values.indexOf(wildLifeTokenEntity)

            if (habitatTileSlot != wildLifeSlot) {
                if (currentUser.natureToken <= 0) return

                rootService.playerActionService.useNaturalTokenIfNeeded(habitatTileSlot, wildLifeSlot)
            }

        }

        rootService.playerActionService.placeWildLifeToken(
            wildLifeToken = wildLifeTokenEntity,
            habitatTile = habitatTileEntity
        )

    }

    /**
     * Handle drag habitatTile from display to userBoard.
     *
     * @param coordinate The coordinate of the board where the HabitatTile is dropped on.
     * @param habitatTileVisual The dragged HabitatTile from the display.
     */
    private fun handleDropHabitatTileUserBoard(
        coordinate: Coordinate,
        habitatTileVisual: HexagonView
    ) {

        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val currentAction = currentGame.currentAction
        val habitatTileEntity = display.habitatVisualEntityMap[habitatTileVisual]
        val currentBoard = checkNotNull(userBoards[currentGame.userList[currentGame.currentUser].name])
        val neighbor = getNeighbors(coordinate.q, coordinate.r, currentBoard)

        if (currentAction.selection.habitatTile != null) return

        if (currentAction.selection.wildlifeToken != null) {
            val habitatTileSlot = currentGame.displayedHabitatTiles.values.indexOf(habitatTileEntity)
            val wildLifeSlot = currentAction.selection.wildlifeTokenIndex!!

            if (habitatTileSlot != wildLifeSlot) {
                if (currentUser.natureToken <= 0) return

                rootService.playerActionService.useNaturalTokenIfNeeded(habitatTileSlot, wildLifeSlot)
            }

        }

        val neighbourMap = mapOf(
            Coordinate(1, 0) to neighbor[0].visual,
            Coordinate(1, -1) to neighbor[1].visual,
            Coordinate(0, -1) to neighbor[2].visual,
            Coordinate(-1, 0) to neighbor[3].visual,
            Coordinate(-1, 1) to neighbor[4].visual,
            Coordinate(0, 1) to neighbor[5].visual
        )

        rotateDialog.fillGrid(
            placedHabitatTile = habitatTileEntity,
            neighbours = neighbourMap
        )

        rotateDialog.show(habitatTileEntity, coordinate)

    }

    private fun getNeighbors(q: Int, r: Int, userBoardVisual: CascadiaUserBoard): List<ComponentView> {
        val neighbors = mutableListOf<ComponentView>()
        val neighborOffsets = listOf(
            Pair(1, 0), Pair(1, -1), Pair(0, -1),
            Pair(-1, 0), Pair(-1, 1), Pair(0, 1)
        )
        for ((q1, r1) in neighborOffsets) {
            val neighborQ = q + q1
            val neighborR = r + r1
            val neighborComponent = userBoardVisual.grid[neighborQ, neighborR]
            if (neighborComponent != null) {
                neighbors.add(neighborComponent)
            } else {
                neighbors.add(HexagonView(visual = loader.backImageOfHabitatTile))
            }
        }
        return neighbors
    }

    /**
     * Handle drag natureToken from user's inventory to natureToken bag.
     */
    private fun handleDropNatureToken() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val currentAction = currentGame.currentAction

        if (currentUser.natureToken <= 0) return
        if (currentAction.selection.wildlifeToken != null) return

        rootService.playerActionService.swapWildLifeToken(emptyList())
        updateInventory()
    }

    private fun handleSwapThreeEqualVoluntarily() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val potentialOverpopulation =
            currentGame.displayedWildLifeToken.entries.groupBy { it.value.type }.values.firstOrNull { it.size == 3 }
                ?.map { it.key }

        if (currentUser.type != UserType.LOCAL_PLAYER) return
        if (currentUser.hasSwappedThree) return

        if (potentialOverpopulation == null) return

        rootService.playerActionService.swapWildLifeToken(potentialOverpopulation)

    }

    private fun setupLocalOptionsPane() {
        val currentGame = checkNotNull(rootService.currentGame)

        val isLocal = currentGame.userList.none { user -> user.type == UserType.ONLINE_PLAYER }
        val containsBot =
            currentGame.userList.any { user -> user.type in listOf(UserType.RANDOM_BOT, UserType.PROFESSIONAL_BOT) }
        val isOnlyBot =
            currentGame.userList.all { user -> user.type in listOf(UserType.RANDOM_BOT, UserType.PROFESSIONAL_BOT) }

        localOptionsPane.clear()

        if (!isLocal) return

        localOptionsPane.addAll(pauseButton)

        if (containsBot) {
            simulationSpeedDecreaseButton.posY = localUndoButton.posY + 120
            simulationSpeedIncreaseButton.posY = localRedoButton.posY + 120

            localOptionsPane.addAll(simulationSpeedIncreaseButton, simulationSpeedDecreaseButton)
        }
        if (isOnlyBot) {
            simulationSpeedDecreaseButton.posY = localUndoButton.posY
            simulationSpeedIncreaseButton.posY = localRedoButton.posY
        } else {
            localOptionsPane.addAll(localUndoButton, localRedoButton)
        }
    }

    private fun checkThreeEqual() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val potentialOverpopulation =
            currentGame.displayedWildLifeToken.entries.groupBy { it.value.type }.values.firstOrNull { it.size == 3 }
                ?.map { it.key }

        if (currentUser.type != UserType.LOCAL_PLAYER) return
        if (currentUser.hasSwappedThree) return
        if (potentialOverpopulation == null) return

        // Here we can say that there are definitely three equal WildLifeToken in the display.
        swapThreeEqualDialog.show()
    }

    private fun updateDisplayDraggability() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]

        if (currentUser.type == UserType.LOCAL_PLAYER) {
            display.setWildLifeDraggability(true)
            display.setHabitatTileDraggability(true)
        } else {
            display.setWildLifeDraggability(false)
            display.setHabitatTileDraggability(false)
        }
    }

    private fun updatePauseButton() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentAction = currentGame.currentAction

        if (!pauseButton.isVisible) return

        if (currentAction.selection.wildlifeToken == null && currentAction.selection.habitatTile == null) {
            pauseButton.isDisabled = false
            return
        }
        pauseButton.isDisabled = true
    }

    /**
     * Update displayed amounts on bags.
     */
    private fun updateBags() {
        val currentGame = checkNotNull(rootService.currentGame)

        // Setup NaturalToken and WildLifeToken bags
        natureTokenBag.setAmount(currentGame.natureToken)
        wildLifeTokenBag.setAmount(currentGame.wildLifeCollection.size)
    }

    /**
     * Make pane of current user visible and hide all the other users.
     */
    private fun updateCameraPanes() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]

        for (cameraPane in cameraPanes.values)
            removeComponents(cameraPane)

        val currentCameraPane = checkNotNull(cameraPanes[currentUser.name])
        addComponents(currentCameraPane)
    }

    /**
     * Update inventory values to current player ones.
     */
    private fun updateInventory() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentAction = currentGame.currentAction
        val currentUser = currentGame.userList[currentGame.currentUser]

        inventory.setNatureTokenAmount(currentUser.natureToken)
        inventory.setHabitatTileAmount(23 - currentUser.board.placedHabitatTiles.size)

        if (currentUser.type != UserType.LOCAL_PLAYER) {
            inventory.natureToken.isDraggable = false
            return
        }

        if (currentAction.selection.wildlifeToken != null) return

        if (currentUser.natureToken == 0) {
            inventory.natureToken.isDraggable = false
        } else {
            inventory.natureToken.isDraggable = true
        }
    }

    private fun updateHabitatTileDisplay() {
        val currentGame = checkNotNull(rootService.currentGame)
        val habitatTileDisplay = currentGame.displayedHabitatTiles

        for ((slot, habitatTile) in habitatTileDisplay) {
            display.placeHabitatTileAt(slot, habitatTile)
        }
    }

    private fun updateWildLifeTokenDisplay() {
        val currentGame = checkNotNull(rootService.currentGame)
        val wildLifeDisplay = currentGame.displayedWildLifeToken

        for ((slot, wildLifeToken) in wildLifeDisplay) {
            display.placeWildLifeTokenAt(slot, wildLifeToken)
        }
    }

    private fun updateSwapButton() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val currentAction = currentGame.currentAction

        if (currentUser.type != UserType.LOCAL_PLAYER) swapButton.isVisible = false
        else if (currentAction.selection.wildlifeToken != null) swapButton.isVisible = false
        else if (currentUser.natureToken <= 0) swapButton.isVisible = false
        else swapButton.isVisible = true

    }

    /**
     * Loading state of current game into all scene components.
     * Primarily for starting Blanco game or loading a saved game.
     */
    private fun loadCurrentGameState() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]

        updateBags()
        updateInventory()

        setSignText(currentUser.name)

        // First create a CameraPane with UserBoard for each player
        if (cameraPanes.isEmpty()) {
            for (user in currentGame.userList) {
                val userBoardVisual = createUserBoard()
                val userCameraPane = createCameraPane(userBoardVisual)

                userBoards[user.name] = userBoardVisual
                cameraPanes[user.name] = userCameraPane

            }
        }

        updateCameraPanes()

        // Add already placed HabitatTiles to users Board
        for (user in currentGame.userList) {
            val userBoard = user.board
            val userBoardVisual = checkNotNull(userBoards[user.name])

            for (component in userBoardVisual.grid.components) {
                userBoardVisual.grid.remove(component)

                if (component in userBoardVisual.habitatTilesMap.keysForward) {
                    val habitatTile = userBoardVisual.habitatTilesMap[component]

                    userBoardVisual.removeHabitatTile(habitatTile)
                }
            }

            for ((coordinate, habitatTile) in userBoard.placedHabitatTiles) {
                userBoardVisual.placeHabitatTileAt(habitatTile, coordinate)
            }
        }

        // Place initial HabitatTiles and WildLifeToken onto display
        updateHabitatTileDisplay()
        updateWildLifeTokenDisplay()

        updateDisplayDraggability()
        checkThreeEqual()
        updateSwapButton()
    }

    override fun refreshAfterGameStart() {
        resetAll()
        setupLocalOptionsPane()
        loadCurrentGameState()
        scheduleBotTurnIfNeeded()
    }

    override fun refreshAfterLoadGame() {
        resetAll()
        setupLocalOptionsPane()
        loadCurrentGameState()
        scheduleBotTurnIfNeeded()
    }

    override fun refreshAfterRotateHabitatTile(habitatTile: HabitatTile) {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val habitatTileVisual = checkNotNull(userBoards[currentUser.name]?.habitatTilesMap?.backward(habitatTile))

        habitatTileVisual.visual = loader.habitatTileImageFor(habitatTile)
    }

    override fun refreshAfterSwapWildLifeToken() {
        updateBags()
        updateInventory()
        updateWildLifeTokenDisplay()
        updateSwapButton()
    }

    override fun refreshAfterUndo(success: Boolean) {
        loadCurrentGameState()
        updatePauseButton()
    }

    override fun refreshAfterRedo(success: Boolean) {
        loadCurrentGameState()
        updatePauseButton()
    }

    override fun refreshAfterCheckOverPopulation(isOverpopulated: Boolean) {
        updateWildLifeTokenDisplay()
    }

    override fun refreshAfterTurn() {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]

        setSignText(currentUser.name)

        updateDisplayDraggability()
        updateBags()
        updateCameraPanes()
        updateInventory()
        checkThreeEqual()
        updateSwapButton()
        scheduleBotTurnIfNeeded()
        updatePauseButton()
    }

    /**
     * new add from bot
     * If the current player is a bot, starts its turn after a short delay.
     */
    private fun scheduleBotTurnIfNeeded() {
        val scheduledGame = rootService.currentGame ?: return

        if (scheduledGame.state != GameState.WAIT_FOR_TURN) return

        val scheduledUserIndex = scheduledGame.currentUser
        val currentUser = scheduledGame.userList[scheduledUserIndex]

        val isBot =
            currentUser.type == UserType.RANDOM_BOT ||
                    currentUser.type == UserType.PROFESSIONAL_BOT

        if (!isBot) return

        pendingBotTask?.cancel()

        val newBotTask = object : TimerTask() {
            override fun run() {
                val currentGame = rootService.currentGame ?: return

                if (currentGame !== scheduledGame) return

                if (currentGame.state != GameState.WAIT_FOR_TURN) return

                if (currentGame.currentUser != scheduledUserIndex) return

                rootService.botService.botTurnPerform()
            }
        }

        pendingBotTask = newBotTask
        botTimer.schedule(newBotTask, 500)

        updatePauseButton()
    }

    override fun refreshAfterPauseGame() {

        cancelBotTurn()
        // All components disabled
        for (component in components) {
            component.isDisabled = true
        }
    }

    override fun refreshAfterContinueGame() {
        // All components enabled
        for (component in components) {
            component.isDisabled = false
        }

        scheduleBotTurnIfNeeded()
    }

    override fun refreshAfterPlaceHabitatTile(habitatTile: HabitatTile, posX: Int, posY: Int) {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val currentAction = currentGame.currentAction
        val coordinate = Coordinate(posX, posY)
        val userBoardVisual = checkNotNull(userBoards[currentUser.name])
        val habitatTileVisual = display.habitatVisualEntityMap.backward(habitatTile)
        val displaySlot = display.habitatTileVisualSlotMap[habitatTileVisual]

        display.setHabitatTileDraggability(false)

        // Disable Draggability on wildLifeToken in other slots when user hat no natureToken to choose any combination
        if (currentUser.natureToken == 0) {
            for ((token, slot) in display.wildLifeVisualSlotMap.entries) {
                if (slot != displaySlot) token.isDraggable = false
            }
        }

        display.removeHabitatTileAt(displaySlot)
        userBoardVisual.placeHabitatTileAt(habitatTile, coordinate)

        updateInventory()
        updateHabitatTileDisplay()
        updatePauseButton()

        if (currentAction.selection.wildlifeToken != null && currentAction.selection.habitatTile != null) {
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    rootService.gameService.nextUser()
                }
            }, 1000)
        }

    }

    override fun refreshAfterPlaceWildLifeToken(wildLifeToken: WildLifeToken, habitatTile: HabitatTile) {
        val currentGame = checkNotNull(rootService.currentGame)
        val currentUser = currentGame.userList[currentGame.currentUser]
        val currentAction = currentGame.currentAction
        val coordinate = currentUser.board.placedHabitatTiles.entries.find { it.value == habitatTile }!!.key
        val userBoardVisual = checkNotNull(userBoards[currentUser.name])
        val wildLifeTokenVisual = display.wildLifeVisualEntityMap.backward(wildLifeToken)
        val displaySlot = display.wildLifeVisualSlotMap[wildLifeTokenVisual]

        display.setWildLifeDraggability(false)

        // Disable Draggability on habitatTile in other slots when user hat no natureToken to choose any combination
        if (currentUser.natureToken == 0) {
            for ((token, slot) in display.habitatTileVisualSlotMap.entries) {
                if (slot != displaySlot) token.isDraggable = false
            }
        }

        display.removeWildLifeTokenAt(displaySlot)
        userBoardVisual.updateAfterWildLifeTokenPlaced(coordinate)

        // User could have got a KeyStone, so amounts could have bag- and inventory amounts could have changed.
        updateInventory()
        updateBags()
        updateSwapButton()
        updateWildLifeTokenDisplay()
        updatePauseButton()

        if (
            currentUser.type == UserType.LOCAL_PLAYER &&
            currentAction.selection.wildlifeToken != null &&
            currentAction.selection.habitatTile != null
        ) {
            Timer(true).schedule(object : TimerTask() {
                override fun run() {
                    rootService.gameService.nextUser()
                }
            }, 1000)
        }
    }

    override fun refreshAfterNatureTokenUsed() {
        updateBags()
        updateInventory()
    }

    override fun refreshAfterOpponentSelectedHabitatTile(habitatTileIndex: Int) {
        // For now nothing to update
    }

    override fun refreshAfterOpponentSelectedWildLifeToken(wildLifeTokenIndex: Int) {
        // For now nothing to update
    }

    private fun resetAll() {
        removeComponents(cameraPanes.values)
        cameraPanes.clear()
        userBoards.clear()
        localOptionsPane.clear()
        currentWildLifeTokenSlotsToSwap.clear()
        swapThreeEqualDialog.hide()
        pauseButton.isDisabled = false

        for (component in components) {
            component.isDisabled = false
        }
    }

    /**
     * help-method for cancel bot turn
     */
    fun cancelBotTurn() {
        pendingBotTask?.cancel()
        pendingBotTask = null
        botTimer.purge()

        rootService.botService.cancelBotTurn()
    }
}
package gui

import service.Refreshable
import tools.aqua.bgw.core.BoardGameApplication
import service.RootService
import tools.aqua.bgw.util.Font
import java.util.Timer
import java.util.TimerTask

/**
 * Represents the main application for Cascadia.
 * The application initializes the [RootService] and displays the scenes.
 */
class CascadiaApplication : BoardGameApplication("Cascadia"), Refreshable {

    /**
     * The root service instance. This is used to call service methods and access the entity layer.
     */
    private val rootService: RootService = RootService()

    private val mainMenuScene: MainMenuScene = MainMenuScene(
        rootService,
        onClickLocal = { showGameScene(localGameScene) },
        onClickOnline = { showGameScene(onlineGameMenuScene) },
        onClickLoadGame = { showGameScene(loadSavedGameScene) },
        onClickQuit = { exit() }
    )

    private val loadSavedGameScene: LoadSavedGameScene = LoadSavedGameScene(
        rootService,
        onClickBack = { showGameScene(mainMenuScene) },
    )

    private val localGameScene: LocalGameMenuScene = LocalGameMenuScene(
        rootService,
        onClickBack = { showGameScene(mainMenuScene) },
        onClickScoreCards = { showGameScene(setupScoreCardsSceneLocal) },
    )

    private val onlineGameMenuScene: OnlineGameMenuScene = OnlineGameMenuScene(
        rootService,
        onClickBack = { showGameScene(mainMenuScene) },
        onClickJoin = { showGameScene(onlineJoinGameScene) },
        onClickHost = { showGameScene(onlineHostGameScene) }
    )

    private val setupScoreCardsSceneLocal: SetupScoreCardsScene = SetupScoreCardsScene(
        rootService,
        onClickApply = { scoreCardsTypeB ->
            localGameScene.updateScoreCards(scoreCardsTypeB)        // Passing ScoreCards to Menu scene for local game.
            showGameScene(localGameScene)
        }
    )

    private val setupScoreCardsSceneOnline: SetupScoreCardsScene = SetupScoreCardsScene(
        rootService,
        onClickApply = { scoreCardsTypeB ->
            onlineHostJoinedScene.updateScoreCards(scoreCardsTypeB)
            showGameScene(onlineHostJoinedScene)
        }
    )

    private val viewScoreCardsScene: ViewScoreCardsScene = ViewScoreCardsScene(
        rootService,
        onClickBack = { showGameScene(guestJoinedScene) }
    )

    private val onlineHostGameScene: HostGameScene = HostGameScene(
        rootService,
        onClickBack = { showGameScene(onlineGameMenuScene) }
    )

    private val onlineJoinGameScene: JoinGameScene = JoinGameScene(
        rootService,
        onClickBack = { showGameScene(onlineGameMenuScene) },
    )

    private val onlineHostJoinedScene: HostJoinedScene = HostJoinedScene(
        rootService,
        onClickLeave = { showGameScene(onlineHostGameScene) },
        onClickScoreCards = { showGameScene(setupScoreCardsSceneOnline) },
    )

    private val guestJoinedScene: GuestJoinedScene = GuestJoinedScene(
        rootService,
        onClickLeave = { showGameScene(onlineJoinGameScene) },
        onClickScoreCards = { showGameScene(viewScoreCardsScene) }
    )

    private val pauseScene: PauseScene = PauseScene(
        rootService,

        onClickQuit = {
            cascadiaGameScene.cancelBotTurn()
            showGameScene(mainMenuScene)
        },

        onClickSave = {
            cascadiaGameScene.cancelBotTurn()
            showGameScene(mainMenuScene)
        },

        onClickResume = {
            showGameScene(cascadiaGameScene)
        },
    )

    private val cascadiaGameScene: GameScene = GameScene(
        rootService,
        onClickPause = { showGameScene(pauseScene) },
    )

    private val endScene: GameEndScene = GameEndScene(
        rootService,
        onClickReturn = { showGameScene(mainMenuScene) },
        onPressDetailedScore = { user ->
            detailedScoreScene.printScoresOntoPaper(user)
            showGameScene(detailedScoreScene)
        },
    )

    private val detailedScoreScene: UserScoreOverviewScene = UserScoreOverviewScene(
        rootService,
        onClickBack = { showGameScene(endScene) },
    )

    private val loadingScene: LoadingScene = LoadingScene(rootService)

    init {
        loadFont("fonts/minecraft.ttf", "Minecraft Default", Font.FontWeight.NORMAL)

        val refreshable = listOf(
            this,
            mainMenuScene,
            loadSavedGameScene,
            localGameScene,
            onlineGameMenuScene,
            setupScoreCardsSceneLocal,
            setupScoreCardsSceneOnline,
            viewScoreCardsScene,
            onlineHostGameScene,
            onlineJoinGameScene,
            onlineHostJoinedScene,
            guestJoinedScene,
            pauseScene,
            cascadiaGameScene,
            endScene,
            detailedScoreScene
        )

        for (refreshable in refreshable) {
            rootService.gameService.addRefreshable(refreshable)
            rootService.playerActionService.addRefreshable(refreshable)
            rootService.networkService.addRefreshable(refreshable)
            rootService.botService.addRefreshable(refreshable)
        }


        this.showGameScene(mainMenuScene)

    }

    override fun refreshAfterGameStart() {
        showGameScene(loadingScene)

        Timer().schedule(object : TimerTask() {
            override fun run() {
                showGameScene(cascadiaGameScene)
            }
        }, 1000)
    }

    override fun refreshAfterGameEnd() {
        showGameScene(endScene)
    }

    override fun refreshAfterLoadGame() {
        showGameScene(loadingScene)

        Timer().schedule(object : TimerTask() {
            override fun run() {
                showGameScene(cascadiaGameScene)
            }
        }, 1000)
    }

    override fun refreshAfterPauseGame() {
        showGameScene(pauseScene)
    }

    override fun refreshAfterContinueGame() {
        showGameScene(cascadiaGameScene)
    }

    override fun refreshAfterJoinSuccessful() {
        showGameScene(guestJoinedScene)
    }

    override fun refreshAfterHostSuccessful() {
        showGameScene(onlineHostJoinedScene)
    }

}
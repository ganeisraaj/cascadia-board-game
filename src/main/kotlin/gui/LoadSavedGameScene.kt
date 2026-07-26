package gui

import entity.CascadiaGame
import gui.components.CascadiaButton
import gui.components.CascadiaMenuScene
import gui.components.CascadiaTextField
import gui.components.ButtonCallbacks
import service.Refreshable
import service.RootService
import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import java.io.File

private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

/**
 * Menu scene to display all previously saved games and load.
 * When game is loaded, game scene should be called.
 *
 * @property rootService The current rootService instance.
 * @property onLoadGame Callback method to switch scene to GameScene from outside
 * @property onClickBack Callback method to switch scene to MainMenuScene from outside
 */
class LoadSavedGameScene(
    val rootService: RootService,
    val onLoadGame: (cascadiaGame: CascadiaGame) -> Unit = {},
    val onClickBack: () -> Unit = {},
) : CascadiaMenuScene(),
    Refreshable {

    private val games = mutableMapOf<String, CascadiaGame>()

    /**
     * button to navigate back to MainMenuScene.
     */
    private val backButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Back",
        callbacks=ButtonCallbacks(onClick = { onClickBack() })
    )

    private val noGameFoundLabel = Label(
        posY = 800,
        width = width,
        height = 50,
        text = "Found no game!",
        font = Font(52, Color.WHITE, "Minecraft Default", Font.FontWeight.NORMAL),
    ).apply {
        isFocusable = false
    }

    /**
     * render all game-components as a list inside a pane
     */
    private val gameList = Pane<ComponentView>(
        posX = SCENE_WIDTH / 2 - 920 / 2,
        posY = 450,
        width = 920,
        height = games.size * 100
    )

    init {
        addComponents(backButton, noGameFoundLabel, gameList)

        onSceneShown = {
            update()
        }

    }

    private fun update() {
        gameList.clear()

        loadGames().onEachIndexed { index, name ->
            gameList.add(renderGame(120 * index, name))
        }

        noGameFoundLabel.isVisible = gameList.isEmpty()
    }

    /**
     * Loads all files that are actually files of json-type.
     *
     * @return List of file names.
     */
    private fun loadGames(): List<String> {
        val folder = File("saves/")

        if (!folder.exists()) {
            folder.mkdirs()
        }

        return folder.listFiles { it.isFile }?.map { it.name } ?: emptyList()

    }

    private fun renderGame(posY: Int, gameTitle: String): Pane<ComponentView> {
        val gameLabel = CascadiaTextField(0, posY = 0, width = 600, initialText = gameTitle)
        val loadButton = CascadiaButton(
            posX = 600 + 20,
            posY = 0, width = 300,
            initialText = "Load",
            callbacks=ButtonCallbacks(onClick = { handleLoadGame(gameTitle) }))

        return Pane<ComponentView>(posX = 0, posY = posY, width = 920, height = 80).apply {
            addAll(gameLabel, loadButton)
        }
    }

    private fun handleLoadGame(gameTitle: String) {
        println("LOAD GAME!!!")

        val loadedGame = rootService.playerActionService.loadGame("saves/$gameTitle")

        onLoadGame(loadedGame)
    }
}
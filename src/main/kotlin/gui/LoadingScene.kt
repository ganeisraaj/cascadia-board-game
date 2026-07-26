package gui

import gui.components.CascadiaMenuScene
import service.Refreshable
import service.RootService
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ImageVisual

/**
 * In between two Scenes to show that data is being currently loaded.
 *
 * @property rootService The current rootService instance.
 *
 */
class LoadingScene(
    val rootService: RootService
) : CascadiaMenuScene(), Refreshable {

    private val loadingLabel = Label(
        posY = 800,
        width = width,
        height = 50,
        text = "Loading...",
        font = Font(52, Color.WHITE, "Minecraft Default", Font.FontWeight.NORMAL),
    )

    init {
        background = ImageVisual("background_dirt.png")

        addComponents(loadingLabel)
    }

}
package gui.components

import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.BoardGameScene
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ImageVisual

/**
 * Main game scene for the Cascadia game.
 *
 * Reusable standardized game scene.
 *
 * This scene uses a static background and header.
 */
open class CascadiaGameScene : BoardGameScene(
    background = ImageVisual("background_dirt.png")
) {

    private val signImage = Label(
        posX = width / 2 - 600 / 2,
        posY = 0,
        width = 600,
        height = 300,
        visual = ImageVisual("sign_hanging.png")
    ).apply {
        zIndex = 100
    }

    /**
     * gray shadow behind text
     */
    private val signTextShadow = Label(
        posX = width / 2 - 600 / 2 + 4,
        posY = 60 + 4,
        width = 600,
        height = 300,
        text = "Cascadia",
        font = Font(84, Color("#3d3d3d"), "Minecraft Default", Font.FontWeight.NORMAL),
        alignment = Alignment.CENTER,
    ).apply { zIndex = 110 }

    private val signText = Label(
        posX = width / 2 - 600 / 2,
        posY = 60,
        width = 600,
        height = 300,
        text = "Cascadia",
        font = Font(84, Color.WHITE, "Minecraft Default", Font.FontWeight.NORMAL),
        alignment = Alignment.CENTER
    ).apply { zIndex = 110 }

    init {
        addComponents(signImage, signTextShadow, signText)
    }

    /**
     * update the text on the sign
     *
     * @param text the Text to be displayed onto the sign
     */
    fun setSignText(text: String) {
        signTextShadow.text = text
        signText.text = text
    }

}
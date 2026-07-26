package gui.components

import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.BoardGameScene
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ImageVisual

/**
 * Main menu scene of the Cascadia game.
 *
 * Reusable standardized menu scene.
 *
 * This scene uses a static background and header.
 *
 */
open class CascadiaMenuScene : BoardGameScene(
    background = ImageVisual("background_menu.png")
) {

    private val signImage = Label(
        posX = width / 2 - 600 / 2,
        posY = 0,
        width = 600,
        height = 300,
        visual = ImageVisual("sign_hanging.png")
    )

    /**
     * gray shadow behind text
     */
    private val signTextShadow = Label(
        posX = width / 2 - 600 / 2 + 4,
        posY = 60 + 4,
        width = 600,
        height = 300,
        text = "Cascadia",
        font = Font(92, Color("#3d3d3d"), "Minecraft Default", Font.FontWeight.NORMAL),
        alignment = Alignment.CENTER,
    )

    private val signText = Label(
        posX = width / 2 - 600 / 2,
        posY = 60,
        width = 600,
        height = 300,
        text = "Cascadia",
        font = Font(92, Color.WHITE, "Minecraft Default", Font.FontWeight.NORMAL),
        alignment = Alignment.CENTER
    )

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

    /**
     * The text displayed on the sign
     */
    fun getSignText() = signText.text


}
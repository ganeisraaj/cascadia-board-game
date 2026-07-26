package gui.components

import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual
import tools.aqua.bgw.visual.CompoundVisual
import tools.aqua.bgw.visual.ImageVisual

/**
 * A data class which contains the callbacks, so that detekt stops freaking out
 */
data class ButtonCallbacks(
    val onClick: (cascadiaButton: CascadiaButton) -> Unit = {},
    val onHovered: (cascadiaButton: CascadiaButton) -> Unit = {},
    val onUnhovered: (cascadiaButton: CascadiaButton) -> Unit = {}

)

/**
 * Clickable UI button with optional text, image, and hover/click callbacks.
 *
 * @property posX X position in parent container.
 * @property posY Y position in parent container.
 * @property width Button width in pixels (default 800).
 * @property height Button height in pixels (default 100).
 * @property initialText Displayed button text.
 * @property image Optional icon/image inside the button.
 * @property font Text font configuration (default Minecraft-style font).
 */
class CascadiaButton(
    posX: Int = 0,
    posY: Int = 0,
    width: Int = 800,
    height: Int = 100,
    val initialText: String = "",
    val image: ImageVisual? = null,
    val callbacks: ButtonCallbacks = ButtonCallbacks()
) : Pane<ComponentView>(
    posX = posX,
    posY = posY,
    width = width,
    height = height,
) {

    private val font = Font(52, Color.WHITE, "Minecraft Default", Font.FontWeight.NORMAL)
    private val hoveredFont = Font(font.size, Color("#fff187"), "Minecraft Default", font.fontWeight)
    private val imageVisual = ImageVisual("button.png")
    private val hoveredVisual = ColorVisual(Color.BLUE).apply { transparency = 0.1 }

    private val compoundVisual = CompoundVisual(
        imageVisual,
        hoveredVisual
    )

    /**
     * black border behind input field
     */
    private val border = Label(
        width = width,
        height = height,
        visual = ColorVisual(Color.BLACK)
    )

    /**
     * component to trigger onClick when being clicked.
     */
    private val buttonComponent = Label(
        posX = 4,
        posY = 5,
        width = width - 8,
        height = height - 10,
        visual = imageVisual
    )

    /**
     * gray shadow behind text
     */
    private val buttonTextShadow = Label(
        posX = 4,
        posY = 4,
        width = width,
        height = height,
        text = initialText,
        font = Font(font.size, Color("#3d3d3d"), "Minecraft Default", font.fontWeight),
        alignment = Alignment.CENTER,
    )

    /**
     * text displayed on the button
     */
    private val buttonText = Label(
        width = width,
        height = height,
        text = initialText,
        font = font,
        alignment = Alignment.CENTER,
    )

    /**
     * optional image to be displayed onto buttonComponent.
     */
    private val imageComponent = Label(
        posX = width / 2 - height / 2,
        width = height,
        height = height,
        alignment = Alignment.CENTER
    )

    init {

        onMouseEntered = {
            buttonComponent.visual = compoundVisual
            buttonText.font = hoveredFont
            callbacks.onHovered(this)
        }

        onMouseExited = {
            buttonComponent.visual = imageVisual
            buttonText.font = font
            callbacks.onUnhovered(this)
        }

        onMouseReleased = {
            buttonComponent.visual = imageVisual
            buttonText.font = font
            callbacks.onClick(this)
        }

        addAll(border, buttonComponent, buttonTextShadow, buttonText)

        if (image != null) {
            imageComponent.visual = image

            add(imageComponent)
        }

    }

    /**
     * update the text on the button
     *
     * @param text the Text to be displayed onto the button
     */
    fun setText(text: String) {
        buttonText.text = text
        buttonTextShadow.text = text
    }

    /**
     * return the current text displayed.
     */
    fun getText() = buttonText.text

}
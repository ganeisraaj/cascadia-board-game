package gui.components

import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.components.uicomponents.UIComponent
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual
import tools.aqua.bgw.visual.ImageVisual

/**
 * A non-editable text display field based on a centered label.
 *
 * Used to show text in the Cascadia UI with optional hover interactions.
 * Unlike an input field, this component is read-only.
 *
 * @property posX X position in the parent container.
 * @property posY Y position in the parent container.
 * @property width Width of the text field (default 800).
 * @property height Height of the text field (default 100).
 * @property initialText Displayed text content.
 * @property font Font used for rendering the text.
 * @property onHovered Called when the mouse enters the field.
 * @property onUnhovered Called when the mouse leaves the field.
 */
class CascadiaTextField(
    posX: Int = 0,
    posY: Int = 0,
    width: Int = 800,
    height: Int = 100,
    var font: Font = Font(52, Color.WHITE, "Minecraft Default", Font.FontWeight.NORMAL),
    val initialText: String = "",
    val onHovered: (cascadiaTextField: CascadiaTextField) -> Unit = {},
    val onUnhovered: (cascadiaTextField: CascadiaTextField) -> Unit = {}
) : Pane<UIComponent>(
    posX = posX,
    posY = posY,
    width = width,
    height = height,
) {

    private val defaultVisual = ImageVisual("button.png")

    private val border = Label(
        width = width,
        height = height,
        visual = ColorVisual(Color.BLACK)
    )

    /**
     * texture of the area
     */
    private val textFieldVisual = Label(
        posX = 4,
        posY = 5,
        height = height - 10,
        width = width - 8,
        visual = defaultVisual
    )

    /**
     * gray shadow behind text
     */
    private val fieldTextShadow = Label(
        posX = 4,
        posY = 4,
        width = width,
        height = height,
        text = initialText,
        font = Font(font.size, Color("#3d3d3d"), "Minecraft Default", font.fontWeight),
        alignment = Alignment.CENTER,
    )

    /**
     * text displayed on the field
     */
    private val fieldText = Label(
        width = width,
        height = height,
        text = initialText,
        font = font,
        alignment = Alignment.CENTER,
    )

    init {

        onMouseEntered = {
            onHovered(this)
        }

        onMouseExited = {
            onUnhovered(this)
        }

        addAll(border, textFieldVisual, fieldTextShadow, fieldText)

    }

    /**
     * update the text on the field
     *
     * @param text the Text to be displayed onto the field
     */
    fun setText(text: String) {
        fieldText.text = text
        fieldTextShadow.text = text
    }

    /**
     * return the current text displayed.
     */
    fun getText() = fieldText.text

}
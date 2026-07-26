package gui.components

import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.components.uicomponents.TextField
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.event.KeyCode
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual

/**
 * contains the input field callback values because detekt sucks
 */
data class InputFieldCallbacks(
    val onClick: (cascadiaInputField: CascadiaInputField) -> Unit = {},
    val onPressEnter: (cascadiaInputField: CascadiaInputField) -> Unit = {},
)

/**
 * A text input field for user text entry.
 *
 * Supports initial text, a label, and callbacks for click and Enter key events.
 * Typically used for forms or naming input within the Cascadia UI system.
 *
 * @property posX X position in the parent container.
 * @property posY Y position in the parent container.
 * @property width Width of the input field (default 800).
 * @property height Height of the input field (default 100).
 * @property initialText Pre-filled text shown in the field.
 * @property label Optional label displayed above the field (height + 40px).
 * @property font Font used for rendering the text.
 */
class CascadiaInputField(
    posX: Int = 0,
    posY: Int = 0,
    width: Int = 800,
    height: Int = 100,
    val initialPrompt: String = "",
    val initialText: String = "",
    val label: String = "",
    val callbacks: InputFieldCallbacks = InputFieldCallbacks()
) : Pane<ComponentView>(
    posX = posX,
    posY = posY,
    width = width,
    height = height,
    visual = ColorVisual(Color.TRANSPARENT)
) {

    private val font = Font(52, Color.WHITE, "Minecraft Default", Font.FontWeight.NORMAL)

    /**
     * optional label which indicates what has to be written into the text field.
     */
    private val labelComponent = Label(
        width = width,
        height = 40,
        text = label,
        font = Font(42, Color.WHITE, "Minecraft Default", Font.FontWeight.NORMAL),
        alignment = Alignment.BOTTOM_LEFT,
        visual = ColorVisual(Color.TRANSPARENT)
    )

    /**
     * black border behind input field
     */
    private val border = Label(
        width = width,
        height = height,
        visual = ColorVisual(Color.WHITE)
    )

    /**
     * padding from left border to add space between border and text
     */
    private val textFieldLeftPaddingPane = Pane<TextField>(
        posX = 5,
        posY = 5,
        height = height - 10,
        width = width - 10,
        visual = ColorVisual(Color.BLACK)
    )

    /**
     * field where user can enter text.
     * Displayed onto inputImage.
     */
    private val inputTextField = TextField(
        posX = 16,
        height = height - 16,
        width = width - 48,
        text = initialText,
        font = font,
        prompt = initialPrompt,
        visual = ColorVisual(Color.BLACK)
    )

    init {

        if (label.isNotBlank()) {
            this.height += 40

            border.posY = labelComponent.height
            textFieldLeftPaddingPane.posY += labelComponent.height

            add(labelComponent)
        }

        textFieldLeftPaddingPane.add(inputTextField)

        addAll(border, textFieldLeftPaddingPane)
    }

    /**
     * update the text on the input
     *
     * @param text the Text to be displayed into the input
     */
    fun setText(text: String) {
        inputTextField.text = text
    }

    /**
     * return the current text displayed.
     */
    fun getText() = inputTextField.text

    /**
     * update the text on the input
     *
     * @param text the Text to be displayed into the input
     */
    fun setPrompt(text: String) {
        inputTextField.prompt = text
    }

    /**
     * return the current text displayed.
     */
    fun getPrompt() = inputTextField.prompt

}
package gui.components

import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Button
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual

/**
 * A click box with multiple options.
 *
 * Displays currently selected option by text and color.
 *
 * @property posX X position in the parent container (default 0).
 * @property posY Y position in the parent container (default 0).
 * @property width Width of the click box in pixels (default 100).
 * @property height Height of the click box in pixels (default 100).
 * @property font Font used for rendering option text.
 * @property options List of selectable options as (text, color) pairs.
 * @property onClick Callback invoked with the selected option text.
 */
class CascadiaClickBox(
    posX: Int = 0,
    posY: Int = 0,
    width: Int = 100,
    height: Int = 100,
    val font: Font = Font(42, Color.BLACK, "Minecraft Default", Font.FontWeight.NORMAL),
    val options: List<Pair<String, Color>>,
    val onClick: (selectedOption: String) -> Unit = {},
) :
    Pane<ComponentView>(posX = posX, posY = posY, width = width, height = height) {

    /**
     * saves the current selected option.
     */
    var selectedOption = options[0]

    private val border = Label(
        width = width,
        height = height,
        visual = ColorVisual(Color.BLACK)
    )

    private val box = Button(
        posX = 5,
        posY = 5,
        width = width - 10,
        height = height - 10,
        font = font,
        text = selectedOption.first,
        visual = ColorVisual(selectedOption.second),
        alignment = Alignment.CENTER
    )

    init {

        box.onMouseReleased = {
            selectedOption = options[(options.indexOf(selectedOption) + 1) % options.size]

            box.text = selectedOption.first
            box.visual = ColorVisual(selectedOption.second)

            onClick(selectedOption.first)
        }

        addAll(border, box)
    }

}
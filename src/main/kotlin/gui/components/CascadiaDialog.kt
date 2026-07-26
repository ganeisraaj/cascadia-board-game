package gui.components

import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.components.uicomponents.TextArea
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual
import tools.aqua.bgw.visual.Visual

/**
 * Represents a dialog pane to ask the user for a decision.
 *
 * @property posX X position in parent container.
 * @property posY Y position in parent container.
 * @property dialogWidth Dialog width in pixels (default 800).
 * @property dialogHeight Dialog height in pixels (default 500).
 * @property dialogTitle Title of the dialog pane displayed at the top left.
 * @property dialogText Text of the dialog to explain the user the decision to make.
 * @property onReject Button which indicates decision "no".
 * @property onAccept Button which indicates decision "yes".
 */
class CascadiaDialog(
    posX: Int = 0,
    posY: Int = 0,
    val dialogWidth: Int = 816,
    val dialogHeight: Int = 516,
    val dialogTitle: String = "",
    val dialogText: String = "",
    val onReject: () -> Unit = {},
    val onAccept: () -> Unit = {},
) : Pane<ComponentView>(
    0,
    0,
    1920,
    1080,
    visual = ColorVisual(Color(0, 0, 0, 0.4))
) {

    private var isMinimized = false

    private val borderPane = Pane<ComponentView>(
        posX,
        posY,
        dialogWidth,
        dialogHeight,
        visual = ColorVisual(Color.DARK_GRAY)
    )

    private val dialogPane = Pane<ComponentView>(
        posX + 8,
        posY + 8,
        dialogWidth - 16,
        dialogHeight - 16,
        visual = ColorVisual(Color.GRAY)
    )

    private val titleLabel = Label(
        posX = 40,
        posY = 20,
        width = dialogWidth - 320,
        height = 80,
        text = dialogTitle,
        font = Font(52, Color.BLACK, "Minecraft Default", Font.FontWeight.NORMAL),
        alignment = Alignment.CENTER,
        visual = ColorVisual(Color.WHITE)
    )

    private val minimizeButton = CascadiaButton(
        posX = dialogWidth - 80 - 48 - 80 - 20,
        posY = 20,
        width = 80,
        height = 80,
        initialText = "-",
        callbacks = ButtonCallbacks(onClick = { minimizeDialog() })
    )

    private val closeButton = CascadiaButton(
        posX = dialogWidth - 80 - 48,
        posY = 20,
        width = 80,
        height = 80,
        initialText = "✘",
        callbacks = ButtonCallbacks(onClick = { closeDialog() })
    )

    private val textField = TextArea(
        posX = 40,
        posY = 120,
        width = dialogWidth - 80,
        height = 250,
        visual = Visual.EMPTY,
        text = dialogText,
        font = Font(52, Color.BLACK, "Minecraft Default", Font.FontWeight.NORMAL),
    ).apply {
        isReadonly = true
    }

    private val rejectButton = CascadiaButton(
        posX = 40,
        posY = dialogHeight - 100 - 40,
        width = 340,
        height = 100,
        initialText = "No",
        callbacks = ButtonCallbacks(onClick = {
            hide()

            onReject()
        })
    )

    private val acceptButton = CascadiaButton(
        posX = dialogWidth - 340 - 48,
        posY = dialogHeight - 100 - 40,
        width = 340,
        height = 100,
        initialText = "Yes",
        callbacks = ButtonCallbacks(onClick = {
            hide()

            onAccept()
        })
    )

    init {
        dialogPane.addAll(titleLabel, minimizeButton, closeButton, textField, rejectButton, acceptButton)

        addAll(borderPane, dialogPane)

        hide()
    }

    /**
     * Makes the component visible and enables user interaction.
     *
     * This method sets the visibility state to visible and removes the disabled state.
     */
    fun show() {
        isVisible = true
        isDisabled = false
    }

    /**
     * Hides the component and disables user interaction.
     *
     * This method sets the visibility state to hidden and prevents user interaction
     * while the component is not visible.
     */
    fun hide() {
        isVisible = false
        isDisabled = true
    }

    private fun minimizeDialog() {

        val relevantComponents = listOf(textField, rejectButton, acceptButton)

        if (!isMinimized) {

            for (component in relevantComponents) {
                component.isVisible = false
                component.isDisabled = true
            }

            borderPane.height = 120.0 + 16
            dialogPane.height = 120.0
            isMinimized = true
            minimizeButton.setText("<>")

            return
        }

        for (component in relevantComponents) {
            component.isVisible = true
            component.isDisabled = false
        }

        borderPane.height = dialogHeight.toDouble() + 16
        dialogPane.height = dialogHeight.toDouble()
        isMinimized = false
        minimizeButton.setText("-")
    }

    private fun closeDialog() {
        if (isMinimized) minimizeDialog()

        hide()

        onReject()
    }

}
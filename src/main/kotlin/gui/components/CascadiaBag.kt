package gui.components

import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.container.Area
import tools.aqua.bgw.components.gamecomponentviews.TokenView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.Visual

/**
 * Represents a bag where items can be dragged to.
 *
 * @property posX The horizontal position of the bag.
 * @property posY The vertical position of the bag.
 * @property width The Width of the bag.
 * @property height The Height of the bag.
 * @property visual The texture of the bag.
 */
class CascadiaBag(
    posX: Int,
    posY: Int,
    width: Int = 160,
    height: Int = 160,
    visual: Visual = Visual.EMPTY
) : Pane<ComponentView>(
    posX,
    posY,
    width,
    height
) {

    val area = Area<TokenView>(
        0,
        0,
        width,
        height,
        visual = visual
    ).apply {
        onDragGestureEntered = { _ ->
            opacity = 0.7
        }

        onDragGestureExited = { _ ->
            opacity = 1.0
        }
    }

    private val amountLabel = Label(
        posX = width / 2 - 50 / 2,
        posY = height / 2 - 40 / 2 + 30,
        width = 50,
        height = 40,
        text = "20",
        font = Font(48, Color.WHITE, "Minecraft Default")
    )

    init {

        addAll(area, amountLabel)

        scale(1.2)

        zIndex = 100

    }

    /**
     * Update the visualized amount of the items inside the bag.
     *
     * @param amount The amount to be applied.
     */
    fun setAmount(amount: Int) {
        amountLabel.text = "$amount"
    }

}
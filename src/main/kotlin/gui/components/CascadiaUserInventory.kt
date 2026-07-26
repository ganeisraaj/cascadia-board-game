package gui.components

import gui.CascadiaImageLoader
import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.gamecomponentviews.HexagonView
import tools.aqua.bgw.components.gamecomponentviews.TokenView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ImageVisual
import tools.aqua.bgw.visual.Visual

private const val SCENE_WIDTH = 1920
private const val SCENE_HEIGHT = 1080

/**
 * Displays the user's inventory in the Cascadia game.
 *
 * @property onNatureTokenDragSuccess Callback invoked when a nature token is successfully dragged
 * from the inventory.
 */
class CascadiaUserInventory(
    posX: Int = 0,
    posY: Int = 0,
    width: Int = 225,
    height: Int = 115,
    visual: Visual = ImageVisual("inventory.png", 180, 90),
    val onNatureTokenDragSuccess: () -> Unit = {},
) : Pane<ComponentView>(
    posX, posY, width, height, visual
) {

    private val imageLoader = CascadiaImageLoader()

    private val inventoryTitle = Label(
        posX = SCENE_WIDTH - 225 - 80 - 50,
        posY = SCENE_HEIGHT - 115 - 40 - 100,
        width = 180,
        height = 110,
        text = "Inventory",
        font = Font(32, Color.BLACK, "Minecraft Default"),
        visual = ImageVisual("sign.png")
    )

    val natureToken = TokenView(
        posX = 10,
        posY = 5,
        width = 100,
        height = 115,
        visual = ImageVisual("emerald.png", 140, 155, offsetX = 40, offsetY = 50)
    ).apply {
        isDraggable = true

        onDragGestureEnded = { _, success ->
            if (success) {
                onNatureTokenDragSuccess()
            }
        }
    }

    private val natureTokenAmount = Label(
        posX = 55,
        posY = 65,
        width = 50,
        height = 40,
        text = "20",
        font = Font(48, Color.WHITE, "Minecraft Default")
    )

    private val habitatTiles = HexagonView(
        posX = 127,
        posY = 12,
        size = 45,
        visual = imageLoader.backImageOfHabitatTile
    )

    private val habitatTileAmount = Label(
        posX = 170,
        posY = 65,
        width = 50,
        height = 40,
        text = "20",
        font = Font(48, Color.WHITE, "Minecraft Default")
    )

    init {
        addAll(
            natureToken,
            natureTokenAmount,
            habitatTiles,
            habitatTileAmount,
            inventoryTitle
        )
    }

    /**
     * Update the visualized amount of the natureToken.
     *
     * @param amount The amount to be applied.
     */
    fun setNatureTokenAmount(amount: Int) {
        natureTokenAmount.text = "$amount"
    }

    /**
     * Update the visualized amount of the habitatTiles.
     *
     * @param amount The amount to be applied.
     */
    fun setHabitatTileAmount(amount: Int) {
        habitatTileAmount.text = "$amount"
    }

}
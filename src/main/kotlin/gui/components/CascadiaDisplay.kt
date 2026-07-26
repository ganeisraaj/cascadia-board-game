package gui.components

import entity.HabitatTile
import entity.WildLifeToken
import gui.CascadiaImageLoader
import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.gamecomponentviews.HexagonView
import tools.aqua.bgw.components.gamecomponentviews.TokenView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.util.BidirectionalMap
import tools.aqua.bgw.visual.ImageVisual
import tools.aqua.bgw.visual.Visual

private val slotOffsetX = mapOf(
    0 to 11,
    1 to 125,
    2 to 239,
    3 to 351,
)

private val slotOffsetY = mapOf(
    0 to 10,
    1 to 116
)

/**
 * Represents the display where the current wildLifeToken- and habitatTile display are shown.
 *
 * @property posX The horizontal position of the display.
 * @property posY The vertical position of the display.
 * @property width The Width of the display.
 * @property height The Height of the display.
 */
class CascadiaDisplay(
    posX: Int = 0,
    posY: Int = 0,
    width: Int = 450,
    height: Int = 300,
) : Pane<ComponentView>(
    posX = posX,
    posY = posY,
    width = width,
    height = height,
    visual = ImageVisual("inventory.png")
) {

    private val imageLoader = CascadiaImageLoader()

    val wildLifeVisualEntityMap = BidirectionalMap<TokenView, WildLifeToken>()
    val habitatVisualEntityMap = BidirectionalMap<HexagonView, HabitatTile>()
    val habitatTileVisualSlotMap = BidirectionalMap<HexagonView, Int>()
    val wildLifeVisualSlotMap = BidirectionalMap<TokenView, Int>()

    init {
        initializeDisplay()
    }

    /**
     * Fill up the display by adding a token to every index.
     */
    private fun initializeDisplay() {
        for (slotPosX in 0..3) {
            val visualWildLifeToken = TokenView(
                posX = slotOffsetX[slotPosX]!!,
                posY = slotOffsetY[0]!!,
                width = 90,
                height = 90,
                visual = Visual.EMPTY
            ).apply {
                isDraggable = false  // For testing
            }

            val visualHabitatTile = HexagonView(
                posX = slotOffsetX[slotPosX]!!,
                posY = slotOffsetY[1]!!,
                size = 50,
                visual = Visual.EMPTY
            ).apply {
                isDraggable = false
            }

            habitatTileVisualSlotMap[visualHabitatTile] = slotPosX
            wildLifeVisualSlotMap[visualWildLifeToken] = slotPosX

            addAll(visualWildLifeToken, visualHabitatTile)
        }
    }

    /**
     * Place a visual representation of a [HabitatTile] on the display at a specific slot.
     *
     * @param slotPosX The slot of the display.
     * @param habitatTile The habitatTile being placed.
     */
    fun placeHabitatTileAt(slotPosX: Int, habitatTile: HabitatTile) {
        require(slotPosX in 0..3)

        val visualHabitatTile = habitatTileVisualSlotMap.backward(slotPosX)

        visualHabitatTile.visual = imageLoader.habitatTileImageFor(habitatTile)

        habitatVisualEntityMap[visualHabitatTile] = habitatTile
    }

    /**
     * Place a visual representation of a [WildLifeToken] on the display at a specific slot.
     *
     * @param slotPosX The slot of the display.
     * @param wildLifeToken The wildLifeToken being placed.
     */
    fun placeWildLifeTokenAt(slotPosX: Int, wildLifeToken: WildLifeToken) {
        require(slotPosX in 0..3)

        val occupiedField = wildLifeVisualSlotMap.backward(slotPosX)

        occupiedField.visual = imageLoader.wildLifeImageFor(wildLifeToken.type)

        wildLifeVisualEntityMap[occupiedField] = wildLifeToken
    }

    /**
     * Clear the given slot of the wildLifeToken display.
     *
     * @param slotPosX The slot being cleared.
     */
    fun removeWildLifeTokenAt(slotPosX: Int) {
        require(slotPosX in 0..3)

        val visualWildLifeToken = wildLifeVisualSlotMap.backward(slotPosX)

        visualWildLifeToken.visual = Visual.EMPTY
        visualWildLifeToken.onDragGestureEnded = { _, _ -> }

        wildLifeVisualEntityMap.removeForward(visualWildLifeToken)
    }

    /**
     * Clear the given slot of the habitatTile display.
     *
     * @param slotPosX The slot being cleared.
     */
    fun removeHabitatTileAt(slotPosX: Int) {
        require(slotPosX in 0..3)

        val visualHabitatTile = habitatTileVisualSlotMap.backward(slotPosX)

        visualHabitatTile.visual = Visual.EMPTY
        visualHabitatTile.onDragGestureEnded = { _, _ -> }

        habitatVisualEntityMap.removeForward(visualHabitatTile)
    }

    /**
     * Update draggability of all wildLifeToken.
     *
     * @param isDraggable The Value which will be set to all wildLifeToken in the display.
     */
    fun setWildLifeDraggability(isDraggable: Boolean) {
        for (wildLifeToken in wildLifeVisualSlotMap.keysForward) {
            wildLifeToken.isDraggable = isDraggable
        }
    }

    /**
     * Update draggability of all habitatTiles.
     *
     * @param isDraggable The Value which will be set to all habitatTiles in the display.
     */
    fun setHabitatTileDraggability(isDraggable: Boolean) {
        for (habitatTile in habitatTileVisualSlotMap.keysForward) {
            habitatTile.isDraggable = isDraggable
        }
    }
}
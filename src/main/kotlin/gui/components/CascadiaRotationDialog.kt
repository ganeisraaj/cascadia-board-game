package gui.components

import entity.Coordinate
import entity.HabitatTile
import gui.CascadiaImageLoader
import service.getNeighbors
import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.container.HexagonGrid
import tools.aqua.bgw.components.gamecomponentviews.HexagonView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.core.HexOrientation
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual
import tools.aqua.bgw.visual.Visual

/**
 * Dialog for selecting the rotation of a habitat tile in the Cascadia game.
 *
 * @property dialogWidth Width of the dialog window in pixels.
 * @property dialogHeight Height of the dialog window in pixels.
 * @property onConfirm Callback invoked when the rotation is confirmed with the selected habitat tile,
 * the corresponding coordinate, and the chosen rotation.
 */
class CascadiaRotationDialog(
    posX: Int = 0,
    posY: Int = 0,
    val dialogWidth: Int = 816,
    val dialogHeight: Int = 600,
    val onConfirm: (habitatTile: HabitatTile, coordinate: Coordinate, rotation: Int) -> Unit = { _, _, _ -> }
) : Pane<ComponentView>(
    0,
    0,
    1920,
    1080,
    visual = ColorVisual(Color(0, 0, 0, 0.4))
) {

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
        width = dialogWidth - 240,
        height = 80,
        text = "Configure HabitatTile",
        font = Font(52, Color.BLACK, "Minecraft Default", Font.FontWeight.NORMAL),
        alignment = Alignment.CENTER,
        visual = ColorVisual(Color.WHITE)
    ).apply { zIndex = 100 }

    private val rotationButton = CascadiaButton(
        posX = 40,
        posY = dialogHeight - 100 - 40,
        width = 340,
        height = 100,
        initialText = "Rotate",
        callbacks=ButtonCallbacks(onClick = { handleRotate() })
    )
    private val confirmationButton = CascadiaButton(
        posX = dialogWidth - 340 - 48,
        posY = dialogHeight - 100 - 40,
        width = 340,
        height = 100,
        initialText = "Confirm",
        callbacks=ButtonCallbacks(onClick = { handleConfirm() })
    )

    private val previewGrid = HexagonGrid<HexagonView>(
        posX = 200,
        posY = 90,
        coordinateSystem = HexagonGrid.CoordinateSystem.AXIAL,
        orientation = HexOrientation.POINTY_TOP,
    )
    private val closeButton = CascadiaButton(
        posX = dialogWidth - 80 - 48,
        posY = 20,
        width = 80,
        height = 80,
        initialText = "✘",
        callbacks=ButtonCallbacks( onClick = { hide() })
    )

    private val imageLoader = CascadiaImageLoader()
    private var habitatTileEntity: HabitatTile? = null
    private var coordinate: Coordinate? = null
    private var rotationValue = 0
    private val coordinateList = listOf((0 to -1), (1 to -1), (-1 to 0), (0 to 0), (1 to 0), (-1 to 1), (0 to 1))

    init {

        dialogPane.addAll(
            titleLabel,
            previewGrid,
            rotationButton,
            confirmationButton,
            closeButton,
        )

        addAll(borderPane, dialogPane)

        for (coord in coordinateList) {
            previewGrid[coord.first, coord.second] = HexagonView(size = 80, visual = ColorVisual(Color.TRANSPARENT))

        }

        hide()
    }

    private fun handleConfirm() {
        onConfirm(
            checkNotNull(habitatTileEntity),
            checkNotNull(coordinate),
            rotationValue
        )

        hide()
    }

    private fun handleRotate() {
        previewGrid[0, 0]?.rotate(60)
        rotationValue++

    }

    /**
     * Fills the preview grid with the placed habitat tile and its neighboring tiles.
     *
     * The placed habitat tile is displayed at the center of the grid, while neighboring
     * positions are filled with their corresponding visuals or a default habitat tile back image
     * if no neighbor exists.
     *
     * @param placedHabitatTile Habitat tile that is placed in the center of the preview grid.
     * @param neighbours Map containing the coordinates and visuals of the neighboring tiles.
     */
    fun fillGrid(placedHabitatTile: HabitatTile, neighbours: Map<Coordinate, Visual>) {
        previewGrid[0, 0]?.visual = imageLoader.habitatTileImageFor(placedHabitatTile)
        previewGrid[0, 0]?.rotation = 0.0

        for (neighbourCoordinate in Coordinate(0, 0).getNeighbors()) {
            if (neighbours[neighbourCoordinate] != null) {
                val neighbour = checkNotNull(previewGrid[neighbourCoordinate.q, neighbourCoordinate.r])

                neighbour.visual = checkNotNull(neighbours[neighbourCoordinate])
            } else {
                previewGrid[neighbourCoordinate.q, neighbourCoordinate.r]?.visual = imageLoader.backImageOfHabitatTile
            }
        }
    }

    /**
     * Displays the habitat tile and updates the associated state.
     *
     * Resets the visibility and enabled state of the element and assigns
     * the provided habitat tile and its coordinate.
     *
     * @param habitatTile The habitat tile to display.
     * @param coordinate The position of the habitat tile on the game board.
     */
    fun show(habitatTile: HabitatTile, coordinate: Coordinate) {
        isVisible = true
        isDisabled = false

        this.habitatTileEntity = habitatTile
        this.coordinate = coordinate
    }

    /**
     * Hides the habitat tile and resets its state.
     *
     * Resets the preview rotation, disables visibility, clears the assigned
     * habitat tile and coordinate, and restores the default rotation value.
     */
    fun hide() {
        previewGrid[0, 0]?.rotation = 0.0

        isVisible = false
        isDisabled = true

        habitatTileEntity = null
        coordinate = null
        rotationValue = 0
    }

}
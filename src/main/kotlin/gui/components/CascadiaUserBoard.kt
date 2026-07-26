package gui.components

import entity.Coordinate
import entity.HabitatTile
import gui.CascadiaImageLoader
import service.getNeighbors
import tools.aqua.bgw.components.container.HexagonGrid
import tools.aqua.bgw.components.gamecomponentviews.HexagonView
import tools.aqua.bgw.components.gamecomponentviews.TokenView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.core.HexOrientation
import tools.aqua.bgw.util.BidirectionalMap
import tools.aqua.bgw.visual.Visual

/**
 * User board to place habitatTiles and wildLifeTokens.
 *
 * @property posX X position in parent container.
 * @property posY Y position in parent container.
 * @property width Button width in pixels (default 1000).
 * @property height Button height in pixels (default 600).
 * @property visual The Background.
 * @property isValidHabitatTile The drop acceptor for HabitatTiles.
 * @property isValidWildLifeToken The drop acceptor for WildLifeToken.
 * @property onHabitatTileDropped Callback method that is being called, if a HabitatTile is successfully dropped.
 * @property onWildLifeTokenDropped Callback method taht is being called, if a WildLifeToken is successfully dropped.
 */
class CascadiaUserBoard(
    posX: Int = 0,
    posY: Int = 0,
    width: Int = 1000,
    height: Int = 600,
    visual: Visual = Visual.EMPTY,
    private val isValidHabitatTile: (HexagonView) -> Boolean = { true },
    private val isValidWildLifeToken: (TokenView) -> Boolean = { true },
    val onHabitatTileDropped: (Coordinate, HexagonView) -> Unit = { _, _ -> },
    val onWildLifeTokenDropped: (Coordinate, TokenView) -> Unit = { _, _ -> },
) : Pane<HexagonGrid<HexagonView>>(
    posX,
    posY,
    width,
    height,
    visual
) {
    private val imgLoader = CascadiaImageLoader()

    val habitatTilesMap = BidirectionalMap<HexagonView, HabitatTile>()

    val grid = HexagonGrid<HexagonView>(
        posX = width / 2 - 200 / 2,
        posY = height / 2 - 100,
        coordinateSystem = HexagonGrid.CoordinateSystem.AXIAL,
        orientation = HexOrientation.POINTY_TOP,
    )

    val starterTileCoordinates = listOf(
        Coordinate(0, 0),
        Coordinate(0, 1),
        Coordinate(-1, 1)
    )

    init {
        add(grid)
    }

    /**
     * Updates the habitat tile visual after a wildlife token has been placed.
     *
     * Refreshes the tile image based on the associated habitat tile and whether
     * the tile is a starter tile. Disables further drag-and-drop interactions
     * for the updated tile.
     *
     * @param coordinate The coordinate of the habitat tile that was updated.
     */
    fun updateAfterWildLifeTokenPlaced(coordinate: Coordinate) {
        val habitatTileVisual = checkNotNull(grid[coordinate.q, coordinate.r])
        val habitatTileEntity = habitatTilesMap[habitatTileVisual]
        val starterTile = coordinate in starterTileCoordinates

        habitatTileVisual.visual = imgLoader.habitatTileImageFor(habitatTileEntity, starterTile)

        habitatTileVisual.dropAcceptor = { false }
        habitatTileVisual.onDragDropped = {}
    }

    /**
     * Places a habitat tile at the specified coordinate and creates its visual representation.
     *
     * Creates a new hexagon view for the habitat tile, configures its drag-and-drop
     * behavior for wildlife tokens, stores the mapping between the visual component
     * and the habitat tile entity, and adds the tile to the grid.
     *
     * @param habitatTile The habitat tile to place on the board.
     * @param coordinate The coordinate where the habitat tile should be placed.
     */
    fun placeHabitatTileAt(habitatTile: HabitatTile, coordinate: Coordinate) {
        val starterTile = coordinate in starterTileCoordinates

        val habitatTileVisual = HexagonView(
            size = 80,
            visual = imgLoader.habitatTileImageFor(habitatTile, starterTile),
            orientation = HexOrientation.POINTY_TOP
        ).apply {
            dropAcceptor = { event ->
                event.draggedComponent is TokenView
                        && isValidWildLifeToken(event.draggedComponent as TokenView)
            }

            onDragDropped = { event ->
                onWildLifeTokenDropped(coordinate, event.draggedComponent as TokenView)
            }

        }
        habitatTilesMap[habitatTileVisual] = habitatTile

        grid[coordinate.q, coordinate.r] = habitatTileVisual

        createNeighbours(coordinate)
    }

    /**
     * Removes the visual representation of a habitat tile from the grid.
     *
     * Finds the corresponding visual component for the given habitat tile,
     * removes it from the grid, and clears the mapping between the entity
     * and its visual representation.
     *
     * @param habitatTile The habitat tile to remove from the board.
     */
    fun removeHabitatTile(habitatTile: HabitatTile) {
        val habitatTileVisual = habitatTilesMap.backward(habitatTile)

        grid.remove(habitatTileVisual)
        habitatTilesMap.removeBackward(habitatTile)
    }

    /**
     * Creates visual placeholders for all empty neighboring positions of a habitat tile.
     *
     * For each neighboring coordinate, a new hexagon view is created if the position
     * is not already occupied. The created placeholders are configured to accept
     * valid habitat tile drops and provide visual feedback during drag interactions.
     * After creating the neighbors, the grid is centered.
     *
     * @param coordinate The coordinate used as the reference point for creating neighbors.
     */
    fun createNeighbours(coordinate: Coordinate) {
        val neighboursToCreate = coordinate.getNeighbors().toMutableList()
        for (neighbour in neighboursToCreate) {
            if (grid[neighbour.q, neighbour.r] != null) {
                continue
            }
            val habitatTileVisual = HexagonView(
                size = 80,
                visual = imgLoader.backImageOfHabitatTile,
                orientation = HexOrientation.POINTY_TOP
            ).apply {
                dropAcceptor = { dragEvent ->
                    dragEvent.draggedComponent is HexagonView
                            && isValidHabitatTile(dragEvent.draggedComponent as HexagonView)
                }
                onDragDropped = { dragEvent ->
                    onHabitatTileDropped(neighbour, dragEvent.draggedComponent as HexagonView)
                }
                onDragGestureEntered = { _ ->
                    opacity = 0.7
                }

                onDragGestureExited = { _ ->
                    opacity = 1.0
                }
            }
            grid[neighbour.q, neighbour.r] = habitatTileVisual
        }

        centerGrid()

    }

    private fun centerGrid() {
        grid.posX = posX + width / 2 - grid.width / 2
        grid.posY = posY + height / 2 - grid.height / 2
    }
}
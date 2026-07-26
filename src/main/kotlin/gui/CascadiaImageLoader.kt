package gui

import entity.HabitatTile
import entity.HabitatTileType
import entity.WildLifeTokenType
import tools.aqua.bgw.visual.CompoundVisual
import tools.aqua.bgw.visual.ImageVisual

private const val HABITAT_FILE = "habitat_textures/textures.png"
private const val AVAILABLE_WILDLIFE_FILE = "habitat_textures/wildlife_textures.png"

private const val IMG_HEIGHT = 256
private const val IMG_WIDTH = 256

/**
 * Provides access to all habitat tile textures given in habitatTextures/textures.png.
 * Each tile is given in 256x256 Pixels.
 */
class CascadiaImageLoader {

    /**
     * the back image of a habitat tile if it is not flipped.
     */
    val backImageOfHabitatTile =
        ImageVisual(
            path = HABITAT_FILE,
            width = IMG_WIDTH,
            height = IMG_HEIGHT,
            offsetX = 3 * IMG_WIDTH,
            offsetY = 4 * IMG_HEIGHT
        )

    /**
     * provides offsets for each habitatTile in the game.
     */
    private val habitatOffsets = mapOf(
        setOf<HabitatTileType>() to (3 to 3),
        setOf(HabitatTileType.WETLANDS) to (0 to 0),
        setOf(HabitatTileType.WETLANDS, HabitatTileType.FORESTS) to (1 to 0),
        setOf(HabitatTileType.WETLANDS, HabitatTileType.MOUNTAINS) to (2 to 0),
        setOf(HabitatTileType.WETLANDS, HabitatTileType.PRAIRIES) to (3 to 0),

        setOf(HabitatTileType.RIVERS) to (0 to 1),
        setOf(HabitatTileType.RIVERS, HabitatTileType.FORESTS) to (1 to 1),
        setOf(HabitatTileType.RIVERS, HabitatTileType.PRAIRIES) to (2 to 1),
        setOf(HabitatTileType.RIVERS, HabitatTileType.WETLANDS) to (3 to 1),

        setOf(HabitatTileType.PRAIRIES) to (0 to 2),
        setOf(HabitatTileType.PRAIRIES, HabitatTileType.FORESTS) to (1 to 2),
        setOf(HabitatTileType.PRAIRIES, HabitatTileType.MOUNTAINS) to (2 to 2),

        setOf(HabitatTileType.MOUNTAINS) to (0 to 3),
        setOf(HabitatTileType.MOUNTAINS, HabitatTileType.RIVERS) to (1 to 3),

        setOf(HabitatTileType.FORESTS) to (0 to 4),
        setOf(HabitatTileType.FORESTS, HabitatTileType.MOUNTAINS) to (1 to 4),
    )

    /**
     * provides offsets for each wildLife combination possible in the game.
     */
    private val availableWildLifeOffsets = mapOf(
        setOf<WildLifeTokenType>() to (0 to 5),
        setOf(WildLifeTokenType.ELK) to (1 to 0),
        setOf(WildLifeTokenType.ELK, WildLifeTokenType.FOX) to (1 to 1),
        setOf(WildLifeTokenType.ELK, WildLifeTokenType.FOX, WildLifeTokenType.HAWK) to (3 to 3),
        setOf(WildLifeTokenType.ELK, WildLifeTokenType.FOX, WildLifeTokenType.SALMON) to (3 to 4),
        setOf(WildLifeTokenType.ELK, WildLifeTokenType.HAWK) to (1 to 2),
        setOf(WildLifeTokenType.ELK, WildLifeTokenType.HAWK, WildLifeTokenType.SALMON) to (3 to 2),
        setOf(WildLifeTokenType.ELK, WildLifeTokenType.SALMON) to (1 to 3),

        setOf(WildLifeTokenType.FOX) to (2 to 0),
        setOf(WildLifeTokenType.FOX, WildLifeTokenType.HAWK) to (2 to 1),
        setOf(WildLifeTokenType.FOX, WildLifeTokenType.HAWK, WildLifeTokenType.SALMON) to (4 to 1),
        setOf(WildLifeTokenType.FOX, WildLifeTokenType.SALMON) to (2 to 2),
        setOf(WildLifeTokenType.FOX, WildLifeTokenType.HAWK) to (2 to 1),

        setOf(WildLifeTokenType.HAWK) to (3 to 0),
        setOf(WildLifeTokenType.HAWK, WildLifeTokenType.SALMON) to (3 to 1),
        setOf(WildLifeTokenType.SALMON) to (4 to 0),

        setOf(WildLifeTokenType.BEAR) to (0 to 0),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.FOX) to (0 to 1),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.HAWK) to (0 to 2),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.HAWK, WildLifeTokenType.SALMON) to (2 to 3),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.SALMON) to (0 to 3),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.ELK) to (0 to 4),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.ELK, WildLifeTokenType.FOX) to (1 to 4),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.ELK, WildLifeTokenType.HAWK) to (2 to 4),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.ELK, WildLifeTokenType.SALMON) to (3 to 4),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.FOX, WildLifeTokenType.HAWK) to (4 to 4),
        setOf(WildLifeTokenType.BEAR, WildLifeTokenType.FOX, WildLifeTokenType.SALMON) to (5 to 4),
    )

    private val starterTilesRotation = mapOf(
        setOf(HabitatTileType.MOUNTAINS) to 0,
        setOf(HabitatTileType.RIVERS, HabitatTileType.PRAIRIES) to 5,
        setOf(HabitatTileType.FORESTS, HabitatTileType.WETLANDS) to 1,
        setOf(HabitatTileType.WETLANDS) to 0,
        setOf(HabitatTileType.PRAIRIES, HabitatTileType.MOUNTAINS) to 5,
        setOf(HabitatTileType.RIVERS, HabitatTileType.FORESTS) to 4,
        setOf(HabitatTileType.PRAIRIES) to 0,
        setOf(HabitatTileType.FORESTS, HabitatTileType.MOUNTAINS) to 5,
        setOf(HabitatTileType.WETLANDS, HabitatTileType.RIVERS) to 1,
        setOf(HabitatTileType.RIVERS) to 0,
        setOf(HabitatTileType.MOUNTAINS, HabitatTileType.WETLANDS) to 2,
        setOf(HabitatTileType.PRAIRIES, HabitatTileType.FORESTS) to 4,
        setOf(HabitatTileType.FORESTS) to 0,
        setOf(HabitatTileType.WETLANDS, HabitatTileType.PRAIRIES) to 5,
        setOf(HabitatTileType.MOUNTAINS, HabitatTileType.RIVERS) to 4
    )

    /**
     * renders the 256x256-texture for the given habitatTile
     *
     * @param habitatTile logical [HabitatTile]
     *
     * @return [CompoundVisual] of habitat and wildLife textures.
     */
    fun habitatTileImageFor(habitatTile: HabitatTile, starterTile: Boolean = false): CompoundVisual {
        val habitatVisual = checkNotNull(habitatOffsets[habitatTile.edges.toSet()])
        val wildLifeVisual = checkNotNull(availableWildLifeOffsets[habitatTile.availableWildLifeToken.toSet()])
        val keyStoneVisual = if (habitatTile.keyStone) ImageVisual(
            "emerald.png",
            width = 620,
            height = 730,
            offsetX = -200,
            offsetY = -500
        ) else ImageVisual("emerald.png", 0, 0)

        var tokenImage = ImageVisual(
            path = AVAILABLE_WILDLIFE_FILE,
            width = IMG_WIDTH,
            height = IMG_HEIGHT,
            offsetX = wildLifeVisual.first * IMG_WIDTH,
            offsetY = wildLifeVisual.second * IMG_HEIGHT
        )

        if (habitatTile.placedWildLifeToken != null) {

            val placedWildLifeToken = checkNotNull(habitatTile.placedWildLifeToken)

            val wildLifeOffset = checkNotNull(availableWildLifeOffsets[setOf(placedWildLifeToken.type)]).first

            tokenImage = ImageVisual(
                path = AVAILABLE_WILDLIFE_FILE,
                width = 128,
                height = 128,
                offsetX = wildLifeOffset * IMG_WIDTH + 64,
                offsetY = 64
            )
        }

        val initialRotation = if (starterTile) parseRotationFromEdges(habitatTile) * 60 else 0

        return CompoundVisual(
            ImageVisual(
                path = HABITAT_FILE,
                width = IMG_WIDTH,
                height = IMG_HEIGHT,
                offsetX = habitatVisual.first * IMG_WIDTH,
                offsetY = habitatVisual.second * IMG_HEIGHT,
                rotation = 180 + initialRotation + habitatTile.rotation * 60
            ),
            tokenImage,
            keyStoneVisual
        )
    }

    /**
     * renders the 128x128-texture for the given wildLifeToken
     *
     * @param wildLifeToken logical [WildLifeTokenType]
     *
     * @return [ImageVisual] of wildLifeToken texture
     */
    fun wildLifeImageFor(wildLifeToken: WildLifeTokenType): ImageVisual {
        val offsetMap = mapOf(
            WildLifeTokenType.SALMON to 0,
            WildLifeTokenType.HAWK to 1,
            WildLifeTokenType.BEAR to 2,
            WildLifeTokenType.ELK to 3,
            WildLifeTokenType.FOX to 4
        )

        val wildLifeOffset = checkNotNull(offsetMap[wildLifeToken])

        return ImageVisual("wildlife_textures/textures.png", 128, 128, wildLifeOffset * 128, 0)
    }

    private fun parseRotationFromEdges(habitatTile: HabitatTile): Int {
        val edgesSet = habitatTile.edges.toSet()

        return starterTilesRotation[edgesSet] ?: 0
    }

}
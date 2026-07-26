package entity

/**
 * Represents a specific location on a hexagonal grid using Axial Coordinates (q = column, r = row).
 */
data class Coordinate(
    val q: Int,
    val r: Int
)
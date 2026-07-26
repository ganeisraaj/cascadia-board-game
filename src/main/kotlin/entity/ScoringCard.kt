package entity

/**
 * Represents a scoring card used to evaluate wildlife scoring.
 *
 * @property isTypeB Indicates whether this is a Type B scoring card (else Type A).
 * @property wildLife The [WildLifeTokenType] associated with this scoring card.
 */
class ScoringCard(
    val isTypeB: Boolean = false,
    val wildLife: WildLifeTokenType
)
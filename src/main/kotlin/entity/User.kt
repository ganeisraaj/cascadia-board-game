package entity

/**
 * Represents a [User] in the game.
 *
 * @property name [User]'s name.
 * @property natureToken Number of nature tokens the player has.
 * @property type Type of user.
 * @property scorePad [User]'s scoring information.
 * @property board [User]'s board.
 * @property hasSwappedThree Indication whether the [User] has swapped three equal NatureToken voluntarily
 */
class User(
    val name: String,
    var natureToken: Int = 0,
    val type: UserType = UserType.LOCAL_PLAYER,
    val scorePad: ScorePad = ScorePad(),
    val board: UserBoard = UserBoard(),
    var hasSwappedThree: Boolean = false
) {

    /**
     * Creates a deep copy of this user with independent copies of mutable objects.
     *
     * @return A new user instance containing the same values and deeply copied mutable properties.
     */
    fun deepCopy() = User(
        name = name,
        natureToken = natureToken,
        type = type,
        scorePad = scorePad.deepCopy(),
        board = board.deepCopy(),
        hasSwappedThree = hasSwappedThree
    )
}
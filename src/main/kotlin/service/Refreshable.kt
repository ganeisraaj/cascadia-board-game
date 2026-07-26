package service

import entity.HabitatTile
import entity.User
import entity.WildLifeToken

/**
 * This interface provides a mechanism for the service layer classes to communicate
 * (usually to the GUI classes) that certain changes have been made to the entity
 * layer, so that the user interface can be updated accordingly.
 *
 * Default (empty) implementations are provided for all methods, so that implementing
 * GUI classes only need to react to events relevant to them.
 *
 * @see AbstractRefreshingService
 */
interface Refreshable {
    /**
     * perform refreshes that are necessary after a new game has been created and started
     */
    fun refreshAfterGameStart() {}

    /**
     * perform refreshes that are necessary after the currently running game has been finished
     */
    fun refreshAfterGameEnd() {}

    /**
     * perform refreshes that are necessary after the player has chosen to rotate a tile
     * @param habitatTile, the habitat tile that has been rotated
     */
    fun refreshAfterRotateHabitatTile(habitatTile: HabitatTile) {}

    /**
     * perform refreshes that are necessary after wildlife tokens have been swapped either due to any kind of
     * overpopulation or a nature token swap from the player
     */
    fun refreshAfterSwapWildLifeToken() {}

    /**
     * perform refreshes that are necessary after the player has triggered an undo
     * @param success, a boolean that indicates if the undo was successful
     */
    fun refreshAfterUndo(success: Boolean) {}

    /**
     * perform refreshes that are necessary after the player has triggered a redo
     * @param success, a boolean that indicates if the redo was successful
     */
    fun refreshAfterRedo(success: Boolean) {}

    /**
     * perform refreshes that are necessary after the game has checked for an overpopulation
     * @param isOverpopulated, a boolean that indicates the result of the check
     */
    fun refreshAfterCheckOverPopulation(isOverpopulated: Boolean) {}

    /**
     * perform refreshes that are necessary after a users´ turn has ended
     */
    fun refreshAfterTurn() {}

    /**
     * perform refreshes that are necessary after the game has been paused by a player
     */
    fun refreshAfterPauseGame() {}

    /**
     * perform refreshes that are necessary after the player has chosen to return to the game after pausing
     */
    fun refreshAfterContinueGame() {}

    /**
     * perform refreshes that are necessary after a user has joined an online game
     * @param user, the user that has joined
     */
    fun refreshAfterUserJoined(user: User) {}

    /**
     * perform refreshes that are necessary after a user has left an online game
     * @param user, the user that left
     */
    fun refreshAfterUserLeft(user: User) {}

    /**
     * perform refreshes that are necessary after the user has saved the current game
     * @param filePath, the file path of where the game is saved
     */
    fun refreshAfterSaveGame(filePath: String) {}

    /**
     * perform refreshes that are necessary after a game has loaded
     */
    fun refreshAfterLoadGame() {}

    /**
     * perform refreshes that are necessary after a user places a tile
     * @param habitatTile, the tile that has been placed
     * @param posX, the x-coordinate where the tile was placed
     * @param posY, the y-coordinate where the tile was placed
     */
    fun refreshAfterPlaceHabitatTile(habitatTile: HabitatTile, posX: Int, posY: Int) {}

    /**
     * perform refreshes that are necessary after a user places a wildlife token
     * @param wildLifeToken, the type of token that was placed
     * @param habitatTile, the habitatTile that the token was placed on
     */
    fun refreshAfterPlaceWildLifeToken(wildLifeToken: WildLifeToken, habitatTile: HabitatTile) {}

    /**
     * refreshes the network connection status with the given information
     *
     * @param state the information to show
     */
    fun refreshConnectionState(state: ConnectionState) {}

    /**
     * perform refreshes that are necessary after the connection state has changed.
     * @param newState The new connection state.
     */
    fun refreshAfterConnectionStateChanged(newState: ConnectionState) {}

    /**
     * refreshes corresponding gui elements to update displayed content referring to nature tokens.
     */
    fun refreshAfterNatureTokenUsed() {}

    /**
     * refreshes corresponding gui elements to update diplayed game configuration if it is being updated.
     */
    fun refreshAfterGameConfigUpdated(players: List<User>, scoreCards: List<Boolean>) {}

    /**
     * refreshes corresponding gui elements if an opponent has placed a habitatTile into their board.
     *
     * @param habitatTileIndex The index of the habitatTile in the display.
     */
    fun refreshAfterOpponentSelectedHabitatTile(habitatTileIndex: Int) {}

    /**
     * refreshes corresponding gui elements if an opponent has placed a wildLifeToken onto a habitatTile of their board.
     *
     * @param wildLifeTokenIndex The index of the wildLifeToken in the display.
     */
    fun refreshAfterOpponentSelectedWildLifeToken(wildLifeTokenIndex: Int) {}

    /**
     * refreshes corresponding gui elements if the wildLifeToken are wiped and renewed.
     */
    fun refreshAfterWipeWildlife () {}

    /**
     * refreshes corresponding gui elements after a chat message is received.
     */
    fun refreshAfterChatMessageReceived(message: String) {}

    /**
     * refreshes scene switch when join to an online match was successful.
     */
    fun refreshAfterJoinSuccessful() {}

    /**
     * refreshes scene switch when hosting an online match was successful.
     */
    fun refreshAfterHostSuccessful() {}
}
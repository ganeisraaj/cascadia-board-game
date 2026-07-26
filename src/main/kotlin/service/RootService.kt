package service

import entity.CascadiaGame
import service.network.NetworkService
import service.bot.BotService


/**
 * The root service class is responsible for managing services and the entity layer reference.
 * This class acts as a central hub for every other service within the application.
 */
class RootService {
    val gameService = GameService(this)
    val playerActionService = UserActionService(this)
    val botService = BotService(this)
    val networkService = NetworkService(this)
    var currentGame: CascadiaGame? = null

    /**
     * Adds the provided newRefreshable to all services connected
     * to this root service
     */
    fun addRefreshable(newRefreshable: Refreshable) {
        gameService.addRefreshable(newRefreshable)
        playerActionService.addRefreshable(newRefreshable)
        botService.addRefreshable(newRefreshable)
        networkService.addRefreshable(newRefreshable)
    }

    /**
     * Adds each of the provided newRefreshables to all services
     * connected to this root service
     */
    fun addRefreshables(vararg newRefreshable: Refreshable) {
        newRefreshable.forEach { addRefreshable(it) }
    }
}
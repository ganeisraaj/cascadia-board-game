package service.bot
import entity.BotAction

/**
 * Strategy interface for bot
 *
 * Implementation define how a bot selects its action from a list of legal actions
 */
interface BotStrategy {
    /**
     * Selects one action from the given list of legal actions
     *
     * @param legalActions All legal actions available to the bot
     * @return The selected [BotAction] to execute
     */
    fun chooseAction(legalActions: List<BotAction>): BotAction
}
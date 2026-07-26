package service.bot
import entity.BotAction


/**
 * A bot strategy that randomly selects one action from the list of legal actions
 *

 */
class RandomStrategy : BotStrategy {

    /**
     * Randomly selects one action from the given list of legal actions
     *
     * @param legalActions All legal actions available to the bot
     * @return A randomly selected [BotAction]
     */
    override fun chooseAction(legalActions: List<BotAction>): BotAction {
        require(legalActions.isNotEmpty()) { "Legal actions must not be empty." }
        return legalActions.random()
    }
}
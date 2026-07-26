package service.bot

import entity.BotAction
import entity.CascadiaGame
import kotlin.math.ln
import kotlin.math.sqrt


/**
 * represents one node in the MCTS tree used
 *
 * Each node stores the action that led to the state,
 * its parent node, the game state at this point, and the list
 * of actions not yet explored from this node
 *
 *
 * @property action the [BotAction] that led to this node
 * @property parent The parent note in the MCTS tree
 * @property unUsedAction Actions that have not yet been expanded into child nodes
 * @property gameState A deep copy of the game state after [action] was applied
 */


class MCTSNode(
    val action: BotAction?,
    val parent: MCTSNode?,
    val unUsedAction: MutableList<BotAction>,
    val gameState: CascadiaGame
){

    val children: MutableList<MCTSNode> = mutableListOf()

    var visits: Int = 0

    var totalScore: Double = 0.0

    /**
     * returns true if all possible actions from this node habe been
     * expanded into child nodes
     */
    fun isFullyExpanded(): Boolean = unUsedAction.isEmpty()

    /**
     * Selects the best child node using the UCB1 formula
     *
     * @return the child node with the highest UCB1 value
     */
    fun bestChild(): MCTSNode {
        return children.maxBy { child->if(child.visits == 0){ Double.POSITIVE_INFINITY}
        else {
            val exploitation = child.totalScore / child.visits
            val exploration = 1.41*sqrt(ln(this.visits.toDouble()) / child.visits)
            exploitation + exploration
        }}
    }

}
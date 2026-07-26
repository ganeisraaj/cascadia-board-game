package gui

import entity.User
import entity.WildLifeTokenType
import gui.components.ButtonCallbacks
import gui.components.CascadiaButton
import gui.components.CascadiaMenuScene
import service.ConnectionState
import service.Refreshable
import service.RootService
import tools.aqua.bgw.animation.FlipAnimation
import tools.aqua.bgw.components.gamecomponentviews.CardView
import tools.aqua.bgw.util.BidirectionalMap
import tools.aqua.bgw.visual.ImageVisual
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.random.Random

private const val SCENE_HEIGHT = 1080

/**
 * Represents a scene to view ScoreCards for a game.
 *
 * @property rootService The current rootService instance.
 */
class ViewScoreCardsScene(
    val rootService: RootService,
    val onClickBack: () -> Unit = {}
) : CascadiaMenuScene(), Refreshable {

    private val scoreCardIsTypeB = WildLifeTokenType.entries.associateWith {
        false
    }.toMutableMap()

    private val scoreCardViews = BidirectionalMap<CardView, CascadiaButton>().apply {
        WildLifeTokenType.entries.forEach { type ->
            val card = createScoreCard(type)
            val button = createScoreCardButton()

            this[card] = button
        }
    }

    private val backButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Back",
        callbacks = ButtonCallbacks(onClick = { onClickBack() })
    )

    init {

        addScoreCardViews()
        addComponents(backButton)

    }

    private fun createScoreCard(type: WildLifeTokenType) =
        CardView(
            posX = 0,
            posY = 0,
            width = 300,
            height = 200,
            front = ImageVisual("scorecards/scorecard_${type.toString().lowercase()}_a.png"),
            back = ImageVisual("scorecards/scorecard_${type.toString().lowercase()}_b.png")
        ).apply {
            showFront()
        }

    private fun createScoreCardButton() =
        CascadiaButton(
            posX = 0,
            posY = 220,
            width = 300,
            height = 100,
            initialText = "A",
            callbacks = ButtonCallbacks(onClick = {})
        ).apply {
            isDisabled = true
        }

    private fun reset() {
        for ((card, button) in scoreCardViews.entries) {
            card.showFront()
            button.setText("A")
        }
        scoreCardIsTypeB.all { false }
    }

    private fun addScoreCardViews() {

        scoreCardViews.entries.forEachIndexed { index, (cardView, button) ->
            cardView.posX = 1920 / 2 - 1580 / 2 + index * 320.toDouble()
            cardView.posY = 1080 / 2 - 100.toDouble()

            button.posX = 1920 / 2 - 1580 / 2 + index * 320.toDouble()
            button.posY = 1080 / 2 - 100 + 220.toDouble()

            addComponents(cardView, button)
        }
    }

    override fun refreshAfterGameConfigUpdated(players: List<User>, scoreCards: List<Boolean>) {
        // Update ScoreCards
        scoreCardIsTypeB.onEachIndexed { index, (tokenType, _) ->
            val isTypeB = scoreCards[index]
            val view = scoreCardViews.entries.toList()[index].first
            val button = scoreCardViews.entries.toList()[index].second

            scoreCardIsTypeB[tokenType] = scoreCards[index]

            if (isTypeB) {
                view.showBack()
                button.setText("B")
            } else {
                view.showFront()
                button.setText("A")
            }
        }
    }

    override fun refreshAfterGameStart() {
        reset()
    }

}
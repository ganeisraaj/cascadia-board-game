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

/**
 * Represents a scene to configure and setup ScoreCards for a game.
 *
 * @property rootService The current rootService instance.
 * @property onClickApply Callback method to handle clicking on apply from outside.
 */
class SetupScoreCardsScene(
    val rootService: RootService,
    val onClickApply: (scoreCardsTypeB: Map<WildLifeTokenType, Boolean>) -> Unit = {}
) : CascadiaMenuScene(), Refreshable {

    private val scoreCardIsTypeB = WildLifeTokenType.entries.associateWith {
        false
    }.toMutableMap()

    private val scoreCardViews = BidirectionalMap<CardView, CascadiaButton>().apply {
        WildLifeTokenType.entries.forEach { type ->
            val card = createScoreCard(type)
            val button = createScoreCardButton(
                wildLifeTokenType = type,
                card = card
            )

            this[card] = button
        }
    }

    private val shuffleButton = CascadiaButton(
        posX = 1920 / 2 - 1580 / 2,
        posY = 1080 / 2 - 100 + 220 + 120,
        width = 1580 / 2 - 10,
        initialText = "Shuffle"
    ).apply {
        onMouseReleased = {
            shuffleScoreCards()
        }
    }

    private val applyButton = CascadiaButton(
        posX = 1920 / 2 + 10,
        posY = 1080 / 2 - 100 + 220 + 120,
        width = 1580 / 2 - 10,
        initialText = "Apply"
    ).apply {
        onMouseReleased = {
            handleClickApply()
        }
    }

    init {

        addScoreCardViews()
        addComponents(shuffleButton, applyButton)

    }

    private fun handleClickApply() {
        val networkService = rootService.networkService

        onClickApply(scoreCardIsTypeB)

        if (networkService.connectionState != ConnectionState.WAITING_FOR_GUEST) return

        networkService.sendGameConfigMessage(
            players = networkService.players.toList(),
            scoreCards = scoreCardIsTypeB.values.toList()
        )
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

    private fun createScoreCardButton(wildLifeTokenType: WildLifeTokenType, card: CardView) =
        CascadiaButton(
            posX = 0,
            posY = 220,
            width = 300,
            height = 100,
            initialText = "A",
            callbacks = ButtonCallbacks(onClick = { handleScoreCardTypeClick(wildLifeTokenType, card, it) })
        )

    private fun reset() {
        for ((card, button) in scoreCardViews.entries) {
            card.showFront()
            button.setText("A")
        }
        scoreCardIsTypeB.all { false }
    }

    private fun handleScoreCardTypeClick(wildLifeTokenType: WildLifeTokenType, card: CardView, button: CascadiaButton) {
        val front = card.currentSide == CardView.CardSide.FRONT

        if (front) {
            button.setText("B")
            scoreCardIsTypeB[wildLifeTokenType] = true
        } else {
            button.setText("A")
            scoreCardIsTypeB[wildLifeTokenType] = false
        }

        playAnimation(animateFlip(card, front))
        card.flip()
    }

    private fun shuffleScoreCards() {
        scoreCardViews.entries.forEach { (_, button) ->
            if (Random.nextBoolean()) button.callbacks.onClick.invoke(button)
        }
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

    private fun animateFlip(card: CardView, front: Boolean) =
        FlipAnimation(
            card,
            fromVisual = if (front) card.frontVisual else card.backVisual,
            toVisual = if (front) card.backVisual else card.frontVisual,
            duration = 150
        )

    override fun refreshAfterGameConfigUpdated(players: List<User>, scoreCards: List<Boolean>) {
        // Update ScoreCards
        scoreCardIsTypeB.onEachIndexed { index, (tokenType, isTypeB) ->
            scoreCardIsTypeB[tokenType] = scoreCards[index]

            val view = scoreCardViews.entries.toList()[index].first
            val button = scoreCardViews.entries.toList()[index].second

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
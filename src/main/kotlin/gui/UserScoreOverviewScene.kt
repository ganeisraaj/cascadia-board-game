package gui

import entity.HabitatTileType
import entity.WildLifeTokenType
import gui.components.ButtonCallbacks
import gui.components.CascadiaButton
import gui.components.CascadiaMenuScene
import service.Refreshable
import service.RootService
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ImageVisual

private const val SCENE_HEIGHT = 1080
private const val SCENE_WIDTH = 1920

/**
 * Represents a scene with all points of a player in detail
 *
 * @property rootService, the RootService object that the scene is bound to
 * @property onClickBack Callback method to handle clicking back from outside.
 */
class UserScoreOverviewScene(
    val rootService: RootService,
    val onClickBack: () -> Unit = {}
) : CascadiaMenuScene(), Refreshable {

    private val wildLifeNatureScorePaper = Pane<Label>(
        posX = SCENE_WIDTH / 2 - (296 * 3 + 40) / 2,
        posY = SCENE_HEIGHT - 600 - 100,
        width = 296 * 1.5,
        height = 342 * 1.5,
        visual = ImageVisual("scorepad_wildlife_wool.png")
    )

    private val pointsByBear = Label(
        posX = 130,
        posY = 35,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val pointsByElk = Label(
        posX = 130,
        posY = 100,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default")
    )

    private val pointsBySalmon = Label(
        posX = 130,
        posY = 170,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default")
    )

    private val pointsByHawk = Label(
        posX = 130,
        posY = 235,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default")
    )

    private val pointsByFox = Label(
        posX = 130,
        posY = 300,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default")
    )

    private val pointsWildLifeTotal = Label(
        posX = 130,
        posY = 370,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default")
    )

    private val habitatAndSumScorePaper = Pane<Label>(
        posX = SCENE_WIDTH / 2 - 296 * 3 / 2 + 296 * 1.5 + 40,
        posY = SCENE_HEIGHT - 600 - 100,
        width = 296 * 1.5,
        height = 342 * 1.5,
        visual = ImageVisual("scorepad_habitat_wool.png")
    )

    private val pointsByMountains = Label(
        posX = 130,
        posY = 25,
        width = 280,
        height = 60,
        text = "- / -",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val pointsByForest = Label(
        posX = 130,
        posY = 90,
        width = 280,
        height = 60,
        text = "- / -",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val pointsByPrairies = Label(
        posX = 130,
        posY = 155,
        width = 280,
        height = 60,
        text = "- / -",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val pointsByWetlands = Label(
        posX = 130,
        posY = 225,
        width = 280,
        height = 60,
        text = "- / -",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val pointsByRiver = Label(
        posX = 130,
        posY = 300,
        width = 280,
        height = 60,
        text = "- / -",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val totalPointsByHabitat = Label(
        posX = 130,
        posY = 370,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val pointsByNatureToken = Label(
        posX = 130,
        posY = 435,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val totalPoints = Label(
        posX = 130,
        posY = 435,
        width = 280,
        height = 60,
        text = "-",
        font = Font(48, Color.WHITE, "Minecraft Default"),
    )

    private val backButton = CascadiaButton(
        posX = 80,
        posY = SCENE_HEIGHT - 150,
        width = 300,
        initialText = "Back",
        callbacks = ButtonCallbacks(onClick = { onClickBack() })
    )

    init {

        background = ImageVisual("background_dirt.png")

        wildLifeNatureScorePaper.addAll(
            pointsByBear,
            pointsByElk,
            pointsBySalmon,
            pointsByHawk,
            pointsByFox,
            pointsWildLifeTotal,
            pointsByNatureToken
        )

        habitatAndSumScorePaper.addAll(
            pointsByMountains,
            pointsByForest,
            pointsByPrairies,
            pointsByWetlands,
            pointsByRiver,
            totalPointsByHabitat,
            totalPoints
        )

        addComponents(wildLifeNatureScorePaper, habitatAndSumScorePaper, backButton)
    }

    /**
     * Updates all label components and fills them with points.
     *
     * @param username The username of the user whose points should be printed.
     */
    fun printScoresOntoPaper(username: String) {
        val currentGame = checkNotNull(rootService.currentGame)
        val user = checkNotNull(currentGame.userList.find { it.name == username })
        val pad = user.scorePad
        val wildLifeScores =
            currentGame.scoringCards.associate { it.wildLife to checkNotNull(pad.pointsByWildLifeToken[it]) }
        val bearPoints = wildLifeScores[WildLifeTokenType.BEAR] ?: 0
        val elkPoints = wildLifeScores[WildLifeTokenType.ELK] ?: 0
        val salmonPoints = wildLifeScores[WildLifeTokenType.SALMON] ?: 0
        val hawkPoints = wildLifeScores[WildLifeTokenType.HAWK] ?: 0
        val foxPoints = wildLifeScores[WildLifeTokenType.FOX] ?: 0

        val mountainPoints = user.scorePad.pointsByHabitatTiles[HabitatTileType.MOUNTAINS] ?: 0
        val forestPoints = user.scorePad.pointsByHabitatTiles[HabitatTileType.FORESTS] ?: 0
        val prairiePoints = user.scorePad.pointsByHabitatTiles[HabitatTileType.PRAIRIES] ?: 0
        val wetlandPoints = user.scorePad.pointsByHabitatTiles[HabitatTileType.WETLANDS] ?: 0
        val riverPoints = user.scorePad.pointsByHabitatTiles[HabitatTileType.RIVERS] ?: 0

        setSignText(user.name)

        pointsByBear.text = bearPoints.toString()
        pointsByElk.text = elkPoints.toString()
        pointsBySalmon.text = salmonPoints.toString()
        pointsByHawk.text = hawkPoints.toString()
        pointsByFox.text = foxPoints.toString()

        pointsByMountains.text = mountainPoints.toString()
        pointsByForest.text = forestPoints.toString()
        pointsByPrairies.text = prairiePoints.toString()
        pointsByWetlands.text = wetlandPoints.toString()
        pointsByRiver.text = riverPoints.toString()

        pointsWildLifeTotal.text = (bearPoints + elkPoints + elkPoints + salmonPoints + foxPoints).toString()
        totalPointsByHabitat.text = "${mountainPoints + forestPoints + prairiePoints + wetlandPoints + riverPoints}"
        pointsByNatureToken.text = pad.pointsByNatureToken.toString()
        totalPoints.text = user.scorePad.totalPoints.toString()
    }

}
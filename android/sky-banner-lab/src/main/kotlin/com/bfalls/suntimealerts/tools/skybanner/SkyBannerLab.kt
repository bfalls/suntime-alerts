package com.bfalls.suntimealerts.tools.skybanner

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SkyFacingMode
import com.bfalls.suntimealerts.alarm.domain.service.MoonArcPositionCalculator
import com.bfalls.suntimealerts.alarm.domain.service.MoonEphemeris
import com.bfalls.suntimealerts.alarm.domain.service.MoonTimesCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunArcPositionCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.ActionListener
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFormattedTextField
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerDateModel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class PreviewState(
    val now: ZonedDateTime,
    val coordinate: Coordinate,
    val skyFacingMode: SkyFacingMode = SkyFacingMode.SOUTH_FACING
)

fun main() {
    SwingUtilities.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        SkyBannerLabFrame().isVisible = true
    }
}

private class SkyBannerLabFrame : JFrame("Suntime Alerts Sky Banner Lab") {
    private val previewPanel = SkyBannerPanel()
    private val dateTimeSpinner = JSpinner(
        SpinnerDateModel(Date(), null, null, java.util.Calendar.MINUTE)
    )
    private val zoneField = JComboBox(TimeZone.getAvailableIDs().sorted().toTypedArray())
    private val latitudeField = JTextField("39.7392", 10)
    private val longitudeField = JTextField("-104.9903", 10)
    private val statusLabel = JLabel(" ")

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout(12, 12)
        minimumSize = Dimension(900, 420)

        zoneField.selectedItem = ZoneId.systemDefault().id
        configureDateTimeSpinner()

        add(buildControls(), BorderLayout.NORTH)
        add(previewPanel, BorderLayout.CENTER)
        add(statusLabel.apply { border = BorderFactory.createEmptyBorder(0, 12, 12, 12) }, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(null)
        refreshPreview()
    }

    private fun configureDateTimeSpinner() {
        val editor = JSpinner.DateEditor(dateTimeSpinner, "yyyy-MM-dd HH:mm")
        dateTimeSpinner.editor = editor
        (editor.textField as JFormattedTextField).columns = 16
    }

    private fun buildControls(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 0, 12)
        }
        val gc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            ipadx = 4
            ipady = 4
        }

        fun addRow(row: Int, label: String, component: java.awt.Component) {
            gc.gridx = 0
            gc.gridy = row
            gc.weightx = 0.0
            panel.add(JLabel(label), gc)
            gc.gridx = 1
            gc.weightx = 1.0
            panel.add(component, gc)
        }

        addRow(0, "Date/Time", dateTimeSpinner)
        addRow(1, "Time Zone", zoneField)
        addRow(2, "Latitude", latitudeField)
        addRow(3, "Longitude", longitudeField)

        val buttonRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JButton("Now").apply {
                addActionListener {
                    dateTimeSpinner.value = Date()
                    refreshPreview()
                }
            })
            add(JButton("Tomorrow").apply {
                addActionListener {
                    val zoneId = selectedZoneId()
                    val next = Instant.ofEpochMilli((dateTimeSpinner.value as Date).time)
                        .atZone(zoneId)
                        .plusDays(1)
                    dateTimeSpinner.value = Date.from(next.toInstant())
                    refreshPreview()
                }
            })
            add(JButton("Refresh").apply {
                addActionListener { refreshPreview() }
            })
        }
        addRow(4, "Actions", buttonRow)

        val refreshListener = ActionListener { refreshPreview() }
        latitudeField.addActionListener(refreshListener)
        longitudeField.addActionListener(refreshListener)
        zoneField.addActionListener(refreshListener)
        dateTimeSpinner.addChangeListener { refreshPreview() }

        return panel
    }

    private fun refreshPreview() {
        runCatching {
            val coordinate = Coordinate(
                latitude = latitudeField.text.trim().toDouble(),
                longitude = longitudeField.text.trim().toDouble()
            )
            val zoneId = selectedZoneId()
            val selectedInstant = Instant.ofEpochMilli((dateTimeSpinner.value as Date).time)
            val previewState = PreviewState(
                now = selectedInstant.atZone(zoneId),
                coordinate = coordinate
            )
            previewPanel.state = previewState
            val phase = MoonEphemeris.moonPhase(previewState.now)
            statusLabel.text =
                "Moon illumination ${(phase.illumination01 * 100.0).roundToInt()}% | " +
                    if (phase.isWaxing) "Waxing" else "Waning"
        }.onFailure { error ->
            statusLabel.text = "Invalid input: ${error.message}"
        }
    }

    private fun selectedZoneId(): ZoneId {
        return ZoneId.of(zoneField.selectedItem as String)
    }
}

private class SkyBannerPanel : JPanel() {
    var state: PreviewState? = null
        set(value) {
            field = value
            repaint()
        }

    private val sunImage = loadImage("/sun.png")
    private val moonImage = loadImage("/moon_full.png")
    private val sunTimesCalculator = SunTimesCalculator()

    init {
        preferredSize = Dimension(860, 220)
        background = Color(0xE8EEF7)
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val previewState = state
        if (previewState == null) {
            drawPlaceholder(g)
            g.dispose()
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()
        val date = LocalDate.of(
            previewState.now.year,
            previewState.now.month,
            previewState.now.dayOfMonth
        )
        val sunTimes = sunTimesCalculator.calculateSunTimes(date, previewState.coordinate, previewState.now.zone)
        val moonWindow = MoonTimesCalculator.computeWindow(
            previewState.now,
            previewState.coordinate.latitude,
            previewState.coordinate.longitude
        )
        val moonPhase = MoonEphemeris.moonPhase(previewState.now)

        val dayLengthMinutes = if (sunTimes.sunrise != null && sunTimes.sunset != null) {
            Duration.between(sunTimes.sunrise, sunTimes.sunset).toMinutes()
        } else {
            12L * 60L
        }
        val horizonY = height * 0.75f
        val sunArcHeight = height * 0.45f * SunArcPositionCalculator.computeArcScale(dayLengthMinutes).toFloat()
        val moonArcHeight = calculateMoonArcHeight(moonWindow.maxAltDeg, horizonY, height)
        val horizontalPadding = width * 0.1f
        val sunPosition = SunArcPositionCalculator.computeSunXY(
            t = SunArcPositionCalculator.computeSunT(previewState.now, sunTimes.sunrise, sunTimes.sunset),
            width = width,
            horizonY = horizonY,
            arcHeight = sunArcHeight,
            horizontalPadding = horizontalPadding,
            skyFacingMode = previewState.skyFacingMode
        )
        val moonPosition = MoonArcPositionCalculator.computeMoonXY(
            now = previewState.now,
            rise = moonWindow.rise,
            set = moonWindow.set,
            width = width,
            horizonY = horizonY,
            arcHeight = moonArcHeight,
            horizontalPadding = horizontalPadding,
            skyFacingMode = previewState.skyFacingMode
        )
        val hasSunTimes = sunTimes.sunrise != null && sunTimes.sunset != null
        val isDay = hasSunTimes && sunPosition.isDay

        drawBackground(g, width, height, horizonY, isDay, hasSunTimes, previewState.now.toLocalDate().toEpochDay())

        g.color = Color(255, 255, 255, 64)
        g.stroke = BasicStroke(2f)
        g.drawLine(0, horizonY.roundToInt(), width.roundToInt(), horizonY.roundToInt())

        if (moonPosition.isUp && moonWindow.rise != null && moonWindow.set != null) {
            val moonDiameter = width.coerceAtMost(height) * 0.10f
            drawMoonPhase(
                g = g,
                centerX = moonPosition.x,
                centerY = moonPosition.y,
                diameter = moonDiameter.coerceIn(24f, 44f),
                illumination01 = moonPhase.illumination01.toFloat(),
                isWaxing = moonPhase.isWaxing,
                alpha = if (isDay) 0.65f else 0.95f
            )
        }

        if (isDay) {
            val sunDiameter = width.coerceAtMost(height) * 0.12f
            val size = sunDiameter.coerceIn(28f, 48f).roundToInt()
            g.drawImage(
                sunImage,
                (sunPosition.x - size / 2f).roundToInt(),
                (sunPosition.y - size / 2f).roundToInt(),
                size,
                size,
                null
            )
        }

        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 16)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm z").apply {
            timeZone = TimeZone.getTimeZone(previewState.now.zone)
        }.format(Date.from(previewState.now.toInstant()))
        g.drawString(
            "Preview $stamp  @ ${"%.4f".format(previewState.coordinate.latitude)}, ${"%.4f".format(previewState.coordinate.longitude)}",
            16,
            24
        )
        g.dispose()
    }

    private fun drawBackground(
        g: Graphics2D,
        width: Float,
        height: Float,
        horizonY: Float,
        isDay: Boolean,
        hasSunTimes: Boolean,
        starSeed: Long
    ) {
        val paint = when {
            isDay -> GradientPaint(0f, 0f, Color(0x64, 0xB5, 0xF6), 0f, height, Color(0xBB, 0xDE, 0xFB))
            hasSunTimes -> GradientPaint(0f, 0f, Color(0x0D, 0x1B, 0x2A), 0f, height, Color(0x00, 0x12, 0x19))
            else -> GradientPaint(0f, 0f, Color(0xD7, 0xE2, 0xEE), 0f, height, Color(0xB4, 0xC1, 0xCE))
        }
        g.paint = paint
        g.fillRect(0, 0, width.roundToInt(), height.roundToInt())

        if (hasSunTimes && !isDay) {
            val random = java.util.Random(starSeed)
            repeat(60) {
                val x = random.nextFloat() * width
                val y = random.nextFloat() * (horizonY * 0.9f)
                val radius = 1f + random.nextFloat() * 2f
                g.color = Color(255, 255, 255, (80 + random.nextInt(120)).coerceAtMost(200))
                g.fill(Ellipse2D.Float(x, y, radius, radius))
            }
        }
    }

    private fun drawMoonPhase(
        g: Graphics2D,
        centerX: Float,
        centerY: Float,
        diameter: Float,
        illumination01: Float,
        isWaxing: Boolean,
        alpha: Float
    ) {
        val topLeftX = centerX - diameter / 2f
        val topLeftY = centerY - diameter / 2f
        val circle = Ellipse2D.Float(topLeftX, topLeftY, diameter, diameter)
        val originalClip = g.clip
        g.clip = circle

        g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha * 0.1f)
        g.drawImage(moonImage, topLeftX.roundToInt(), topLeftY.roundToInt(), diameter.roundToInt(), diameter.roundToInt(), null)

        g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (0.88f * alpha).coerceAtMost(1f))
        g.color = Color(0, 0, 0, 235)
        g.fill(circle)

        val litArea = buildMoonLitArea(topLeftX, topLeftY, diameter, illumination01.coerceIn(0f, 1f), isWaxing)
        g.clip = litArea
        g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha)
        g.drawImage(moonImage, topLeftX.roundToInt(), topLeftY.roundToInt(), diameter.roundToInt(), diameter.roundToInt(), null)

        val rimWidth = minimumVisibleSliver(diameter, illumination01)
        if (illumination01 < 0.08f && rimWidth > 0f) {
            g.color = Color(255, 245, 210, 170)
            val x = if (isWaxing) {
                topLeftX + diameter - rimWidth
            } else {
                topLeftX
            }
            g.fill(Rectangle2D.Float(x, topLeftY + 1f, rimWidth, diameter - 2f))
        }

        g.clip = originalClip
        g.composite = java.awt.AlphaComposite.SrcOver
    }

    private fun buildMoonLitArea(
        topLeftX: Float,
        topLeftY: Float,
        diameter: Float,
        illumination01: Float,
        isWaxing: Boolean
    ): Area {
        val circle = Area(Ellipse2D.Float(topLeftX, topLeftY, diameter, diameter))
        if (illumination01 <= 0f) return Area()
        if (illumination01 >= 1f) return circle

        val centerX = topLeftX + diameter / 2f
        val phaseDelta = (illumination01 - 0.5f) * 2f
        val ovalWidth = (diameter * abs(phaseDelta)).coerceAtLeast(0.001f)
        val oval = Area(
            Ellipse2D.Float(
                centerX - ovalWidth / 2f,
                topLeftY,
                ovalWidth,
                diameter
            )
        )

        val halfRect = if (isWaxing) {
            Rectangle2D.Float(centerX, topLeftY, diameter / 2f, diameter)
        } else {
            Rectangle2D.Float(topLeftX, topLeftY, diameter / 2f, diameter)
        }
        val halfMoon = Area(circle).apply { intersect(Area(halfRect)) }

        return if (illumination01 < 0.5f) {
            halfMoon.apply { subtract(oval) }
        } else {
            halfMoon.apply { add(oval) }
        }
    }

    private fun drawPlaceholder(g: Graphics2D) {
        g.color = Color(230, 236, 242)
        g.fillRect(0, 0, width, height)
        g.color = Color.DARK_GRAY
        g.drawString("Enter inputs to preview the sky banner.", 16, 24)
    }
}

private fun calculateMoonArcHeight(
    moonMaxAltDeg: Double,
    horizonY: Float,
    height: Float
): Float {
    val topPadding = height * 0.08f
    val maxArcSpan = (horizonY - topPadding).coerceAtLeast(height * 0.2f)
    val altitudeRad = Math.toRadians(moonMaxAltDeg.coerceIn(0.0, 90.0))
    val altitudeFactor = sin(altitudeRad).toFloat().coerceIn(0.25f, 1.1f)
    val desiredArcHeight = maxArcSpan * altitudeFactor
    val minArcHeight = height * 0.2f
    val maxArcHeight = maxArcSpan * 1.05f
    return desiredArcHeight.coerceIn(minArcHeight, maxArcHeight)
}

private fun minimumVisibleSliver(diameter: Float, illumination01: Float): Float {
    if (illumination01 <= 0f || illumination01 >= 0.08f) return 0f
    val base = diameter * illumination01
    return base.coerceAtLeast(1.25f).coerceAtMost(diameter * 0.08f)
}

private fun loadImage(resourcePath: String): BufferedImage {
    val stream: InputStream = SkyBannerLabFrame::class.java.getResourceAsStream(resourcePath)
        ?: error("Missing resource $resourcePath")
    stream.use {
        return ImageIO.read(it)
    }
}

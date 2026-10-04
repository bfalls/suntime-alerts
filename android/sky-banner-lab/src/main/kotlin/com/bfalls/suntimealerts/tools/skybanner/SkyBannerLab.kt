package com.bfalls.suntimealerts.tools.skybanner

import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SkyFacingMode
import com.bfalls.suntimealerts.alarm.domain.service.MoonArcPositionCalculator
import com.bfalls.suntimealerts.alarm.domain.service.MoonPhaseMask
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import com.bfalls.suntimealerts.alarm.domain.service.SkyBackgroundModel
import com.bfalls.suntimealerts.alarm.domain.service.SkyBackgroundSpec
import com.bfalls.suntimealerts.alarm.domain.service.SunArcPositionCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunXY
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.LinearGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.awt.image.RescaleOp
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFormattedTextField
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.SpinnerDateModel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class PreviewState(
    val now: ZonedDateTime,
    val coordinate: Coordinate,
    val skyFacingMode: SkyFacingMode = SkyFacingMode.SOUTH_FACING,
    val softMoonEdgeEnabled: Boolean = true,
    val moonBrightnessAdjustment: Float = 0f,
    val visualTuning: VisualTuning = VisualTuning(),
    val infoIconCircleEnabled: Boolean = true
)

internal data class VisualTuning(
    val cloudPatches: Float = 0.18f,
    val horizonHaze: Float = 0.75f,
    val hillShadow: Float = 0.25f,
    val groundGradient: Float = 0.75f
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
    private val softMoonEdgeCheckbox = JCheckBox("Soft moon phase edge", true)
    private val infoIconCircleCheckbox = JCheckBox("Category icon circles", true)
    private val advanceTimeCheckbox = JCheckBox("Advance preview time", false)
    private var moonBrightnessAdjustment = 0f
    private val moonBrightnessLabel = JLabel()
    private val cloudPatchesSlider = JSlider(0, 100, 18)
    private val horizonHazeSlider = JSlider(0, 150, 75)
    private val hillShadowSlider = JSlider(0, 100, 25)
    private val groundGradientSlider = JSlider(0, 100, 75)
    private val statusLabel = JLabel(" ")

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout(12, 12)
        minimumSize = Dimension(900, 620)

        zoneField.selectedItem = ZoneId.systemDefault().id
        configureDateTimeSpinner()

        add(buildControls(), BorderLayout.NORTH)
        add(previewPanel, BorderLayout.CENTER)
        add(statusLabel.apply { border = BorderFactory.createEmptyBorder(0, 12, 12, 12) }, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(null)
        refreshPreview()
        var lastClockTick = System.nanoTime()
        Timer(1000) {
            val tick = System.nanoTime()
            val elapsedMillis = (tick - lastClockTick) / 1_000_000
            lastClockTick = tick
            if (advanceTimeCheckbox.isSelected) {
                dateTimeSpinner.value = Date((dateTimeSpinner.value as Date).time + elapsedMillis)
            }
        }.start()
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
        addRow(1, "Adjust date", buildDateTimeNudgeControls("Year", "Month", "Day"))
        addRow(2, "Adjust time", buildDateTimeNudgeControls("Hour", "Minute"))
        addRow(3, "Time Zone", zoneField)
        addRow(4, "Latitude", latitudeField)
        addRow(5, "Longitude", longitudeField)
        addRow(6, "Moon", softMoonEdgeCheckbox)
        addRow(7, "Brightness", buildMoonBrightnessControls())
        addRow(8, "Cloud patches", buildPercentSlider(cloudPatchesSlider))
        addRow(9, "Horizon haze", buildPercentSlider(horizonHazeSlider, maxPercent = 150))
        addRow(10, "Hill shadow", buildPercentSlider(hillShadowSlider))
        addRow(11, "Ground gradient", buildPercentSlider(groundGradientSlider))

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
        addRow(12, "Info overlay", JPanel().apply {
            add(infoIconCircleCheckbox)
            add(advanceTimeCheckbox)
        })
        addRow(13, "Actions", buttonRow)

        val refreshListener = ActionListener { refreshPreview() }
        latitudeField.addActionListener(refreshListener)
        longitudeField.addActionListener(refreshListener)
        zoneField.addActionListener(refreshListener)
        softMoonEdgeCheckbox.addActionListener(refreshListener)
        infoIconCircleCheckbox.addActionListener(refreshListener)
        dateTimeSpinner.addChangeListener { refreshPreview() }
        listOf(
            cloudPatchesSlider,
            horizonHazeSlider,
            hillShadowSlider,
            groundGradientSlider
        ).forEach { slider ->
            slider.addChangeListener { refreshPreview() }
        }

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
                coordinate = coordinate,
                softMoonEdgeEnabled = softMoonEdgeCheckbox.isSelected,
                moonBrightnessAdjustment = moonBrightnessAdjustment,
                visualTuning = currentVisualTuning(),
                infoIconCircleEnabled = infoIconCircleCheckbox.isSelected
            )
            previewPanel.state = previewState
            val phase = requireNotNull(previewPanel.metrics).moonPhase
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

    private fun buildDateTimeNudgeControls(vararg components: String): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            components.forEach { component ->
                val repeatMs = if (component == "Minute") 50 else 300
                add(JLabel("$component "))
                add(repeatingButton("-", repeatMs = repeatMs) { adjustDateTime(component, -1L) })
                add(repeatingButton("+", repeatMs = repeatMs) { adjustDateTime(component, 1L) })
                add(JLabel("  "))
            }
        }
    }

    private fun adjustDateTime(component: String, delta: Long) {
        val zoneId = selectedZoneId()
        val current = Instant.ofEpochMilli((dateTimeSpinner.value as Date).time).atZone(zoneId)
        val adjusted = when (component) {
            "Year" -> current.plusYears(delta)
            "Month" -> current.plusMonths(delta)
            "Day" -> current.plusDays(delta)
            "Hour" -> current.plusHours(delta)
            "Minute" -> current.plusMinutes(delta)
            else -> current
        }
        dateTimeSpinner.value = Date.from(adjusted.toInstant())
        refreshPreview()
    }

    private fun buildMoonBrightnessControls(): JPanel {
        updateMoonBrightnessLabel()
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(repeatingButton("-") { adjustMoonBrightness(-0.1f) })
            add(repeatingButton("+") { adjustMoonBrightness(0.1f) })
            add(JButton("Reset").apply {
                addActionListener {
                    moonBrightnessAdjustment = 0f
                    updateMoonBrightnessLabel()
                    refreshPreview()
                }
            })
            add(moonBrightnessLabel)
        }
    }

    private fun repeatingButton(label: String, repeatMs: Int = 300, action: () -> Unit): JButton {
        return JButton(label).apply {
            var repeatTimer: Timer? = null

            fun stopRepeating() {
                repeatTimer?.stop()
                repeatTimer = null
            }

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    if (!isEnabled || !SwingUtilities.isLeftMouseButton(event)) return
                    action()
                    stopRepeating()
                    repeatTimer = Timer(repeatMs) { action() }.apply {
                        initialDelay = 300
                        start()
                    }
                }

                override fun mouseReleased(event: MouseEvent) {
                    stopRepeating()
                }

                override fun mouseExited(event: MouseEvent) {
                    stopRepeating()
                }
            })
        }
    }

    private fun adjustMoonBrightness(delta: Float) {
        moonBrightnessAdjustment = (moonBrightnessAdjustment + delta).coerceIn(-0.5f, 1.0f)
        updateMoonBrightnessLabel()
        refreshPreview()
    }

    private fun updateMoonBrightnessLabel() {
        val percent = (moonBrightnessAdjustment * 100f).roundToInt()
        moonBrightnessLabel.text = "  ${if (percent >= 0) "+" else ""}$percent%"
    }

    private fun buildPercentSlider(slider: JSlider, maxPercent: Int = 100): JPanel {
        val valueLabel = JLabel()
        fun updateLabel() {
            valueLabel.text = "  ${slider.value}%"
        }
        updateLabel()
        slider.majorTickSpacing = 25
        slider.paintTicks = true
        slider.paintLabels = maxPercent > 100
        slider.addChangeListener { updateLabel() }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(slider)
            add(valueLabel)
        }
    }

    private fun currentVisualTuning(): VisualTuning {
        return VisualTuning(
            cloudPatches = cloudPatchesSlider.value / 100f,
            horizonHaze = horizonHazeSlider.value / 100f,
            hillShadow = hillShadowSlider.value / 100f,
            groundGradient = groundGradientSlider.value / 100f
        )
    }
}

internal class SkyBannerPanel : JPanel() {
    private val infoCalculator = SkyInfoCalculator()
    private val infoOverlay = SkyInfoLabOverlay()
    internal var metrics: SkyInfoMetrics? = null
        private set
    var onOpenAdvancedInfo: ((SkyInfoMetrics) -> Unit)? = null
    var state: PreviewState? = null
        set(value) {
            field = value
            metrics = value?.let { infoCalculator.calculate(it.now, it.coordinate) }
            metrics?.let { infoOverlay.update(it, System.nanoTime() / 1_000_000) }
            getAccessibleContext().accessibleDescription = metrics?.let {
                com.bfalls.suntimealerts.alarm.presentation.ui.SkyInfoMessages.forMetrics(it).joinToString(". ") { message -> message.text }
            }
            repaint()
        }

    private val sunImage = loadImage("/sun.png")
    private val moonImage = loadImage("/moon_full.png")
    private var lastOverlayFrame: SkyInfoFadeFrame? = null
    private val overlayTimer = Timer(16) {
        if (isShowing) {
            val frame = infoOverlay.frame(System.nanoTime() / 1_000_000)
            if (frame != lastOverlayFrame) {
                lastOverlayFrame = frame
                repaint()
            }
        }
    }

    init {
        preferredSize = Dimension(860, 220)
        background = Color(0xE8EEF7)
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(event) && event.y >= height - 48) {
                    metrics?.let { onOpenAdvancedInfo?.invoke(it) }
                }
            }
        })
    }

    override fun addNotify() { super.addNotify(); overlayTimer.start() }
    override fun removeNotify() { overlayTimer.stop(); super.removeNotify() }

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
        val infoMetrics = requireNotNull(metrics)
        val sunTimes = infoMetrics.today
        val moonWindow = infoMetrics.moonWindow
        val moonPhase = infoMetrics.moonPhase

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
        val sunAltitudeDeg = infoMetrics.sunPosition.altitudeDeg
        val skyBackground = SkyBackgroundModel.compute(sunAltitudeDeg, hasSunTimes)
        val isDay = hasSunTimes && sunPosition.isDay

        drawBackground(
            g = g,
            width = width,
            height = height,
            horizonY = horizonY,
            skyBackground = skyBackground,
            sunPosition = sunPosition,
            starSeed = previewState.now.toLocalDate().toEpochDay(),
            tuning = previewState.visualTuning
        )

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

        if (moonPosition.isUp && moonWindow.rise != null && moonWindow.set != null) {
            val moonDiameter = width.coerceAtMost(height) * 0.10f
            drawMoonPhase(
                g = g,
                centerX = moonPosition.x,
                centerY = moonPosition.y,
                diameter = moonDiameter.coerceIn(24f, 44f),
                illumination01 = moonPhase.illumination01.toFloat(),
                isWaxing = moonPhase.isWaxing,
                litDirectionRadians = MoonPhaseMask.litDirectionRadians(
                    sunAltAz = infoMetrics.sunPosition,
                    moonAltAz = infoMetrics.moonPosition
                ),
                softEdgeEnabled = previewState.softMoonEdgeEnabled,
                brightnessAdjustment = previewState.moonBrightnessAdjustment,
                alpha = 0.65f + 0.30f * skyBackground.starAlpha
            )
        }

        drawSkyLandscape(
            g = g,
            width = width,
            height = height,
            horizonY = horizonY,
            skyBackground = skyBackground,
            tuning = previewState.visualTuning
        )

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
        g.font = Font("SansSerif", Font.PLAIN, 12)
        g.drawString(
            "Sky ${skyBackground.phase} | Sun altitude ${"%.1f".format(sunAltitudeDeg)}°",
            16,
            42
        )
        infoOverlay.paint(g, this.width, this.height, previewState.infoIconCircleEnabled, System.nanoTime() / 1_000_000)
        g.dispose()
    }

    private fun drawBackground(
        g: Graphics2D,
        width: Float,
        height: Float,
        horizonY: Float,
        skyBackground: SkyBackgroundSpec,
        sunPosition: SunXY,
        starSeed: Long,
        tuning: VisualTuning
    ) {
        g.paint = LinearGradientPaint(
            0f,
            0f,
            0f,
            height,
            skyBackground.gradientStops.map { it.position }.toFloatArray(),
            skyBackground.gradientStops.map { Color(it.color, true) }.toTypedArray()
        )
        g.fillRect(0, 0, width.roundToInt(), height.roundToInt())

        skyBackground.sunGlow?.let { glow ->
            val radius = width.coerceAtMost(height) * glow.radiusScale
            g.paint = RadialGradientPaint(
                sunPosition.x,
                sunPosition.y,
                radius,
                floatArrayOf(0f, 0.45f, 1f),
                arrayOf(
                    Color(glow.color, true).withAlpha(glow.alpha),
                    Color(glow.color, true).withAlpha(glow.alpha * 0.32f),
                    Color(glow.color, true).withAlpha(0f)
                )
            )
            g.fillRect(0, 0, width.roundToInt(), height.roundToInt())
        }

        if (skyBackground.starAlpha > 0f) {
            val random = java.util.Random(starSeed)
            repeat(60) {
                val x = random.nextFloat() * width
                val y = random.nextFloat() * (horizonY * 0.9f)
                val radius = 1f + random.nextFloat() * 2f
                val alpha = ((80 + random.nextInt(120)).coerceAtMost(200) * skyBackground.starAlpha)
                    .roundToInt()
                    .coerceIn(0, 255)
                g.color = Color(255, 255, 255, alpha)
                g.fill(Ellipse2D.Float(x, y, radius, radius))
            }
        }

        drawCloudPatches(g, width, horizonY, tuning.cloudPatches)
    }

    private fun drawSkyLandscape(
        g: Graphics2D,
        width: Float,
        height: Float,
        horizonY: Float,
        skyBackground: SkyBackgroundSpec,
        tuning: VisualTuning
    ) {
        drawHillLayer(
            g = g,
            width = width,
            height = height,
            horizonY = horizonY,
            profile = SkyBackgroundModel.farHillProfile(),
            paint = Color(skyBackground.landscape.farHillColor, true),
            verticalShift = height * 0.018f
        )
        drawHillLayer(
            g = g,
            width = width,
            height = height,
            horizonY = horizonY,
            profile = SkyBackgroundModel.nearHillProfile(),
            paint = buildGroundPaint(
                height = height,
                horizonY = horizonY,
                color = Color(skyBackground.landscape.groundColor, true),
                amount = tuning.groundGradient
            ),
            verticalShift = height * 0.045f
        )
        drawHillLayer(
            g = g,
            width = width,
            height = height,
            horizonY = horizonY,
            profile = SkyBackgroundModel.nearHillProfile(),
            paint = buildGroundPaint(
                height = height,
                horizonY = horizonY,
                color = Color(skyBackground.landscape.nearHillColor, true),
                amount = tuning.groundGradient
            ),
            verticalShift = 0f
        )
        if (tuning.hillShadow > 0f) {
            drawHillShadow(
                g = g,
                width = width,
                height = height,
                horizonY = horizonY,
                amount = tuning.hillShadow
            )
        }
        val hazeAlpha = skyBackground.landscape.hazeAlpha * tuning.horizonHaze
        if (hazeAlpha > 0f) {
            g.paint = LinearGradientPaint(
                0f,
                horizonY - height * 0.08f,
                0f,
                horizonY + height * 0.10f,
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf(
                    Color(skyBackground.landscape.hazeColor, true).withAlpha(0f),
                    Color(skyBackground.landscape.hazeColor, true).withAlpha(hazeAlpha),
                    Color(skyBackground.landscape.hazeColor, true).withAlpha(0f)
                )
            )
            g.fillRect(
                0,
                (horizonY - height * 0.08f).roundToInt(),
                width.roundToInt(),
                (height * 0.18f).roundToInt()
            )
        }
    }

    private fun drawHillLayer(
        g: Graphics2D,
        width: Float,
        height: Float,
        horizonY: Float,
        profile: List<com.bfalls.suntimealerts.alarm.domain.service.SkyHorizonPoint>,
        paint: Paint,
        verticalShift: Float
    ) {
        if (profile.isEmpty()) return
        val path = buildHillLinePath(width, height, horizonY, profile, verticalShift)
        path.lineTo(width * 1.05, height.toDouble())
        path.lineTo(width * -0.05, height.toDouble())
        path.closePath()
        g.paint = paint
        g.fill(path)
    }

    private fun buildHillLinePath(
        width: Float,
        height: Float,
        horizonY: Float,
        profile: List<com.bfalls.suntimealerts.alarm.domain.service.SkyHorizonPoint>,
        verticalShift: Float
    ): Path2D.Float {
        val path = Path2D.Float()
        val points = profile.map { point ->
            java.awt.geom.Point2D.Float(
                point.x01 * width,
                horizonY + point.yOffset01 * height + verticalShift
            )
        }
        path.moveTo(points.first().x.toDouble(), points.first().y.toDouble())
        if (points.size == 1) {
            path.lineTo(points.first().x.toDouble(), points.first().y.toDouble())
        } else {
            for (index in 1 until points.lastIndex) {
                val control = points[index]
                val next = points[index + 1]
                val endX = (control.x + next.x) / 2f
                val endY = (control.y + next.y) / 2f
                path.quadTo(
                    control.x.toDouble(),
                    control.y.toDouble(),
                    endX.toDouble(),
                    endY.toDouble()
                )
            }
            val penultimate = points[points.lastIndex - 1]
            val last = points.last()
            path.quadTo(
                penultimate.x.toDouble(),
                penultimate.y.toDouble(),
                last.x.toDouble(),
                last.y.toDouble()
            )
        }
        return path
    }

    private fun buildGroundPaint(height: Float, horizonY: Float, color: Color, amount: Float): Paint {
        if (amount <= 0f) return color
        return LinearGradientPaint(
            0f,
            horizonY,
            0f,
            height,
            floatArrayOf(0f, 1f),
            arrayOf(
                color.scaleBrightness(1f + 0.18f * amount),
                color.scaleBrightness(1f - 0.28f * amount)
            )
        )
    }

    private fun drawHillShadow(
        g: Graphics2D,
        width: Float,
        height: Float,
        horizonY: Float,
        amount: Float
    ) {
        g.paint = LinearGradientPaint(
            0f,
            horizonY,
            0f,
            height,
            floatArrayOf(0f, 0.55f, 1f),
            arrayOf(
                Color(0, 0, 0, (34f * amount).roundToInt().coerceIn(0, 255)),
                Color(0, 0, 0, (16f * amount).roundToInt().coerceIn(0, 255)),
                Color(0, 0, 0, 0)
            )
        )
        g.fillRect(0, horizonY.roundToInt(), width.roundToInt(), (height - horizonY).roundToInt())
    }

    private fun drawCloudPatches(g: Graphics2D, width: Float, horizonY: Float, amount: Float) {
        if (amount <= 0f) return
        val random = java.util.Random(20261003L)
        repeat((18 * amount).roundToInt().coerceAtLeast(1)) {
            val patchWidth = width * (0.14f + random.nextFloat() * 0.18f)
            val patchHeight = horizonY * (0.035f + random.nextFloat() * 0.055f)
            val x = random.nextFloat() * (width + patchWidth) - patchWidth * 0.5f
            val y = horizonY * (0.10f + random.nextFloat() * 0.58f)
            val alpha = (0.045f + random.nextFloat() * 0.075f) * amount
            g.paint = RadialGradientPaint(
                x + patchWidth * 0.5f,
                y + patchHeight * 0.5f,
                patchWidth * 0.55f,
                floatArrayOf(0f, 0.58f, 1f),
                arrayOf(
                    Color(255, 255, 255).withAlpha(alpha),
                    Color(245, 250, 255).withAlpha(alpha * 0.34f),
                    Color(245, 250, 255).withAlpha(0f)
                )
            )
            g.fill(Ellipse2D.Float(x, y, patchWidth, patchHeight))
        }
    }

    private fun drawMoonPhase(
        g: Graphics2D,
        centerX: Float,
        centerY: Float,
        diameter: Float,
        illumination01: Float,
        isWaxing: Boolean,
        litDirectionRadians: Float?,
        softEdgeEnabled: Boolean,
        brightnessAdjustment: Float,
        alpha: Float
    ) {
        val topLeftX = centerX - diameter / 2f
        val topLeftY = centerY - diameter / 2f
        val circle = Ellipse2D.Float(topLeftX, topLeftY, diameter, diameter)
        val originalClip = g.clip
        g.clip = circle

        val litArea = buildMoonLitArea(
            topLeftX,
            topLeftY,
            diameter,
            illumination01.coerceIn(0f, 1f),
            isWaxing,
            litDirectionRadians
        )
        if (softEdgeEnabled) {
            drawMoonSoftEdgeLayers(
                g = g,
                topLeftX = topLeftX,
                topLeftY = topLeftY,
                diameter = diameter,
                illumination01 = illumination01,
                isWaxing = isWaxing,
                litDirectionRadians = litDirectionRadians,
                brightnessAdjustment = brightnessAdjustment,
                alpha = alpha
            )
        }
        g.clip = litArea
        g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha)
        drawMoonImage(
            g = g,
            topLeftX = topLeftX,
            topLeftY = topLeftY,
            diameter = diameter,
            brightnessAdjustment = brightnessAdjustment
        )

        g.clip = originalClip
        g.composite = java.awt.AlphaComposite.SrcOver
    }

    private fun drawMoonSoftEdgeLayers(
        g: Graphics2D,
        topLeftX: Float,
        topLeftY: Float,
        diameter: Float,
        illumination01: Float,
        isWaxing: Boolean,
        litDirectionRadians: Float?,
        brightnessAdjustment: Float,
        alpha: Float
    ) {
        MoonPhaseMask.softEdgeLayers(illumination01.coerceIn(0f, 1f)).forEach { layer ->
            g.clip = buildMoonLitArea(
                topLeftX,
                topLeftY,
                diameter,
                layer.illumination01,
                isWaxing,
                litDirectionRadians
            )
            g.composite = java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER,
                alpha * layer.alphaMultiplier
            )
            drawMoonImage(
                g = g,
                topLeftX = topLeftX,
                topLeftY = topLeftY,
                diameter = diameter,
                brightnessAdjustment = brightnessAdjustment
            )
        }
    }

    private fun drawMoonImage(
        g: Graphics2D,
        topLeftX: Float,
        topLeftY: Float,
        diameter: Float,
        brightnessAdjustment: Float
    ) {
        val size = diameter.roundToInt().coerceAtLeast(1)
        val x = topLeftX.roundToInt()
        val y = topLeftY.roundToInt()
        val brightness = (1f + brightnessAdjustment).coerceIn(0.5f, 2.0f)
        if (brightness == 1f) {
            g.drawImage(moonImage, x, y, size, size, null)
            return
        }

        val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val scaledGraphics = scaled.createGraphics()
        scaledGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        scaledGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        scaledGraphics.drawImage(moonImage, 0, 0, size, size, null)
        scaledGraphics.dispose()

        val operation = RescaleOp(
            floatArrayOf(brightness, brightness, brightness, 1f),
            floatArrayOf(0f, 0f, 0f, 0f),
            null
        )
        g.drawImage(operation.filter(scaled, null), x, y, null)
    }

    private fun buildMoonLitArea(
        topLeftX: Float,
        topLeftY: Float,
        diameter: Float,
        illumination01: Float,
        isWaxing: Boolean,
        litDirectionRadians: Float?
    ): Area {
        val points = MoonPhaseMask.litDiscPolygon(illumination01, isWaxing, litDirectionRadians)
        if (points.isEmpty()) return Area()
        val path = Path2D.Float()
        points.forEachIndexed { index, point ->
            val x = topLeftX + point.x01 * diameter
            val y = topLeftY + point.y01 * diameter
            if (index == 0) {
                path.moveTo(x.toDouble(), y.toDouble())
            } else {
                path.lineTo(x.toDouble(), y.toDouble())
            }
        }
        path.closePath()
        return Area(path)
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

private fun Color.withAlpha(alpha: Float): Color {
    return Color(red, green, blue, (255f * alpha.coerceIn(0f, 1f)).roundToInt())
}

private fun Color.scaleBrightness(scale: Float): Color {
    return Color(
        (red * scale).roundToInt().coerceIn(0, 255),
        (green * scale).roundToInt().coerceIn(0, 255),
        (blue * scale).roundToInt().coerceIn(0, 255),
        alpha
    )
}

private fun loadImage(resourcePath: String): BufferedImage {
    val stream: InputStream = SkyBannerLabFrame::class.java.getResourceAsStream(resourcePath)
        ?: error("Missing resource $resourcePath")
    stream.use {
        return ImageIO.read(it)
    }
}

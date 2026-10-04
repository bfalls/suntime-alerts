package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import kotlinx.coroutines.delay

internal const val SKY_INFO_ROTATION_MILLIS = SkyInfoPresentation.ROTATION_MILLIS

/** A fixed overlay; the sky and terrain geometry are deliberately independent. */
@Composable
fun SkyInfoOverlay(
    metrics: SkyInfoMetrics,
    modifier: Modifier = Modifier,
    showIconCircle: Boolean = true,
    // Connect navigation here when the advanced-information screen is added.
    onOpenAdvancedInfo: ((SkyInfoMetrics) -> Unit)? = null
) {
    val messages = remember(metrics) { SkyInfoMessages.forMetrics(metrics) }
    val ids = messages.map { it.id }
    var selectedId by remember(metrics.period) { mutableStateOf(ids.first()) }
    val selected = messages.firstOrNull { it.id == selectedId } ?: messages.first()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(metrics.period, ids, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (selectedId !in ids) selectedId = ids.first()
            while (true) {
                delay(SKY_INFO_ROTATION_MILLIS)
                selectedId = ids[(ids.indexOf(selectedId).coerceAtLeast(0) + 1) % ids.size]
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("sky_info_overlay")
            .then(if (onOpenAdvancedInfo != null) Modifier.clickable(onClickLabel = "View advanced sun and moon information") {
                onOpenAdvancedInfo(metrics)
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = selected,
            contentKey = { it.id },
            transitionSpec = {
                (fadeIn(tween(SkyInfoPresentation.FADE_IN_MILLIS, delayMillis = SkyInfoPresentation.FADE_IN_DELAY_MILLIS))
                    togetherWith fadeOut(tween(SkyInfoPresentation.FADE_OUT_MILLIS)))
                    .using(null)
            },
            label = "Sky information fade",
            modifier = Modifier.fillMaxSize()
        ) { message ->
            val style = iconStyle(message.category)
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Box(
                    modifier = Modifier.size(28.dp)
                        .then(if (showIconCircle) Modifier.background(Color(SkyInfoPresentation.iconCircleArgb), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(19.dp))
                }
                Text(
                    text = message.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = Shadow(Color(0xB300180E), Offset(0f, 1f), 3f)
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

private data class SkyInfoIconStyle(val icon: ImageVector, val color: Color)

private fun iconStyle(category: SkyInfoCategory): SkyInfoIconStyle {
    val icon = when (category) {
        SkyInfoCategory.SUNRISE, SkyInfoCategory.SUNSET, SkyInfoCategory.TWILIGHT -> Icons.Outlined.WbTwilight
        SkyInfoCategory.DAYLIGHT -> Icons.Outlined.WbSunny
        SkyInfoCategory.DAYLIGHT_GAIN -> Icons.AutoMirrored.Outlined.TrendingUp
        SkyInfoCategory.DAYLIGHT_LOSS -> Icons.AutoMirrored.Outlined.TrendingDown
        SkyInfoCategory.GOLDEN_HOUR, SkyInfoCategory.BLUE_HOUR -> Icons.Outlined.CameraAlt
        SkyInfoCategory.SUN_POSITION -> Icons.Outlined.Explore
        SkyInfoCategory.DARKNESS -> Icons.Outlined.DarkMode
        SkyInfoCategory.MOON_PHASE, SkyInfoCategory.MOON_EVENT -> Icons.Outlined.NightsStay
    }
    return SkyInfoIconStyle(icon, Color(category.colorArgb))
}

package com.example.runthebusiness.ui.cafe

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.R
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.data.local.CafeOrderScene
import com.example.runthebusiness.data.local.TimedCafeEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private enum class CafeView(@field:DrawableRes val art: Int, val description: String) {
    ROOM(R.drawable.cafe_scene_room, "Minimalist café dining room with a sage service counter"),
    COFFEE(R.drawable.cafe_scene_coffee, "Espresso machine at the coffee bar"),
    KITCHEN(R.drawable.cafe_scene_kitchen, "Stainless steel kitchen prep area"),
    DELIVERY(R.drawable.cafe_scene_delivery, "Produce crates at the café delivery entrance"),
    PAYMENT(R.drawable.cafe_scene_payment, "Accounts desk and supplier invoices")
}

private data class SceneCue(val event: TimedCafeEvent, val views: List<CafeView>)

@Composable
fun CafeScene(
    name: String,
    time: GameTime,
    playing: Boolean,
    latestOrder: CafeOrderScene?,
    latestDelivery: TimedCafeEvent?,
    latestPayment: TimedCafeEvent?,
    isOpen: Boolean,
    tables: Int,
    seats: Int,
    activeGuests: Int,
    readyDishes: Int,
    totalDishes: Int,
    bankCents: Long,
    cashCents: Long,
    hasUrgentBills: Boolean,
    modifier: Modifier = Modifier
) {
    var view by remember { mutableStateOf(CafeView.ROOM) }
    val cues = buildList {
        if (latestPayment != null && latestPayment.time.day == time.day && latestPayment.time <= time) {
            add(SceneCue(latestPayment, listOf(CafeView.PAYMENT)))
        }
        if (latestDelivery != null && latestDelivery.time.day == time.day && latestDelivery.time <= time) {
            add(SceneCue(latestDelivery, listOf(CafeView.DELIVERY)))
        }
        if (isOpen && latestOrder != null && time.totalMinutes - latestOrder.event.time.totalMinutes in 0L..29L) {
            val shots = buildList {
                if (latestOrder.drinks) add(CafeView.COFFEE)
                if (latestOrder.food) add(CafeView.KITCHEN)
            }
            if (shots.isNotEmpty()) add(SceneCue(latestOrder.event, shots))
        }
    }
    val latestCues by rememberUpdatedState(cues)
    val shownEvents = remember { mutableMapOf<CafeView, String>() }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        if (view != CafeView.ROOM) delay(1_500)
        view = CafeView.ROOM
        while (true) {
            val cue = snapshotFlow {
                latestCues.firstOrNull { shownEvents[it.views.first()] != it.event.id }
            }.first { it != null } ?: continue
            shownEvents[cue.views.first()] = cue.event.id
            for (shot in cue.views) {
                view = shot
                delay(2_600)
            }
            view = CafeView.ROOM
            delay(1_400)
        }
    }
    val activity = when (view) {
        CafeView.ROOM -> if (isOpen) "$activeGuests guests · $tables tables · $seats seats" else "Dining room closed"
        CafeView.COFFEE -> "Coffee order"
        CafeView.KITCHEN -> "Kitchen order"
        CafeView.DELIVERY -> "Delivery received"
        CafeView.PAYMENT -> "Automatic bill payment"
    }

    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Text(if (isOpen) "Open" else "Closed", style = MaterialTheme.typography.labelMedium,
                        color = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Day ${time.day}", style = MaterialTheme.typography.labelMedium)
                    Text("%02d:%02d".format(Locale.ROOT, time.hour, time.minute),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                }
            }
            Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clipToBounds()) {
                Crossfade(targetState = view, animationSpec = tween(260), label = "Café view") { shot ->
                    SceneFrame(
                        view = shot,
                        hour = time.hour,
                        active = shot == view && (shot != CafeView.ROOM || isOpen && activeGuests > 0),
                        guestPresent = isOpen && activeGuests > 0,
                        playing = playing
                    )
                }
                Text(activity, Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Color(0xCC121C22), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SceneMetric("Menu ready", "$readyDishes / $totalDishes", Modifier.weight(1f))
                SceneMetric("Bank", sceneMoney(bankCents), Modifier.weight(1f))
                SceneMetric("Cash", sceneMoney(cashCents), Modifier.weight(1f))
            }
            if (hasUrgentBills) {
                Text("Overdue bills affecting service", Modifier.padding(start = 14.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SceneMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
    }
}

private fun sceneMoney(cents: Long): String = "€%,.0f".format(Locale.ROOT, cents / 100.0)

@Composable
private fun SceneFrame(view: CafeView, hour: Int, active: Boolean, guestPresent: Boolean, playing: Boolean) {
    val moving = active && playing
    val phase = remember { Animatable(0f) }
    LaunchedEffect(active, playing) {
        if (!active) phase.snapTo(0f)
        if (moving) {
            while (true) {
                withFrameNanos { }
                val remainingMillis = ((1f - phase.value) * 7_200).toInt()
                phase.animateTo(1f, tween(remainingMillis, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(view.art), contentDescription = view.description,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer {
                val drift = sin(phase.value * (2 * PI)).toFloat()
                scaleX = 1.035f
                scaleY = 1.035f
                translationX = drift * size.width * 0.009f
            })
        if (view == CafeView.ROOM) {
            RoomGuest(guestPresent, playing, Modifier.matchParentSize())
        }
        val shade = when (hour) {
            in 0..6, in 21..23 -> 0.45f
            in 7..8, in 18..20 -> 0.17f
            else -> 0f
        }
        if (shade > 0f) {
            Box(Modifier.matchParentSize().background(Color(0xFF111C35).copy(alpha = shade)))
        }
        if (active) {
            when (view) {
                CafeView.COFFEE -> CoffeeSteam(phase, Modifier.matchParentSize())
                CafeView.KITCHEN -> SceneLight(phase, Color(0xFFFAECC9), 0.69f, 0.62f, Modifier.matchParentSize())
                CafeView.DELIVERY -> SceneLight(phase, Color(0xFFFAECC9), 0.47f, 0.31f, Modifier.matchParentSize())
                CafeView.ROOM -> SceneLight(phase, Color(0xFFFAECC9), 0.20f, 0.68f, Modifier.matchParentSize())
                CafeView.PAYMENT -> SceneLight(phase, Color(0xFFFAECC9), 0.55f, 0.42f, Modifier.matchParentSize())
            }
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0.68f to Color.Transparent, 1f to Color(0x440A151C)
        )))
    }
}

@Composable
private fun CoffeeSteam(phase: Animatable<Float, *>, modifier: Modifier) {
    Canvas(modifier) {
        repeat(3) { index ->
            val lift = (phase.value * 2f + index / 3f) % 1f
            val x = size.width * (0.50f + sin(lift * (2 * PI) + index).toFloat() * 0.014f)
            val y = size.height * (0.55f - 0.12f * lift)
            val w = size.width * (0.012f + 0.031f * lift)
            val h = size.height * (0.05f + 0.043f * lift)
            val opacity = sin(lift * PI).toFloat() * 0.17f
            drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = opacity), Color.Transparent),
                center = Offset(x, y), radius = w), topLeft = Offset(x - w, y - h / 2), size = Size(w * 2, h))
        }
    }
}

@Composable
private fun SceneLight(phase: Animatable<Float, *>, tint: Color, x: Float, y: Float, modifier: Modifier) {
    Canvas(modifier) {
        val shift = sin(phase.value * (2 * PI)).toFloat()
        val center = Offset(size.width * (x + shift * 0.04f), size.height * y)
        drawRect(Brush.radialGradient(listOf(tint.copy(alpha = 0.07f + 0.02f * shift), Color.Transparent),
            center = center, radius = size.width * 0.28f))
    }
}

@Composable
private fun RoomGuest(present: Boolean, playing: Boolean, modifier: Modifier) {
    val visibility = remember { Animatable(if (present) 1f else 0f) }
    LaunchedEffect(present, playing) {
        if (playing) visibility.animateTo(if (present) 1f else 0f, tween(650))
    }
    BoxWithConstraints(modifier) {
        Image(
            painterResource(R.drawable.cafe_scene_guest),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(x = maxWidth * 0.08f, y = -maxHeight * 0.08f)
                .height(maxHeight * 0.56f)
                .aspectRatio(0.40f, matchHeightConstraintsFirst = true)
                .graphicsLayer {
                    alpha = visibility.value
                    translationX = (1f - visibility.value) * -size.width * 0.28f
                }
        )
    }
}

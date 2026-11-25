package com.example.runthebusiness.ui.cafe

import com.example.runthebusiness.ui.components.cafeLabel
import androidx.compose.ui.res.stringResource
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
import com.example.runthebusiness.ui.theme.CafeAppearance
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.data.local.CafeOrderScene
import com.example.runthebusiness.data.local.TimedCafeEvent
import com.example.runthebusiness.ui.components.CafeLedgerOptions
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private enum class CafeView(val label: String, @field:DrawableRes val art: Int, @field:androidx.annotation.StringRes val description: Int) {
    ROOM("Room", R.drawable.cafe_scene_room, R.string.scene_room_description),
    COFFEE("Coffee", R.drawable.cafe_scene_coffee, R.string.scene_coffee_description),
    KITCHEN("Kitchen", R.drawable.cafe_scene_kitchen, R.string.scene_kitchen_description),
    DELIVERY("Delivery", R.drawable.cafe_scene_delivery, R.string.scene_delivery_description),
    PAYMENT("Office", R.drawable.cafe_scene_payment, R.string.scene_office_description)
}

private data class SceneCue(val event: TimedCafeEvent, val views: List<CafeView>)

@Composable
fun CafeScene(
    name: String,
    appearance: CafeAppearance,
    time: GameTime,
    speed: Int,
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
    onOpenOperations: (Int) -> Unit,
    onOpenFinance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playing = speed > 0
    var automaticView by remember { mutableStateOf(CafeView.ROOM) }
    var pinnedView by rememberSaveable { mutableStateOf<CafeView?>(null) }
    val view = pinnedView ?: automaticView
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
    var lastServiceView by remember { mutableStateOf<CafeView?>(null) }
    LaunchedEffect(speed, pinnedView) {
        if (pinnedView != null) {
            automaticView = CafeView.ROOM
            return@LaunchedEffect
        }
        if (!playing) return@LaunchedEffect
        val roomHold = when (speed) {
            5 -> 4_800L
            20 -> 4_000L
            else -> 1_400L
        }
        if (automaticView != CafeView.ROOM) delay(1_500)
        automaticView = CafeView.ROOM
        while (true) {
            val cue = snapshotFlow {
                latestCues.firstOrNull { shownEvents[it.views.first()] != it.event.id }
            }.first { it != null } ?: continue
            shownEvents[cue.views.first()] = cue.event.id
            val service = cue.views.first() in setOf(CafeView.COFFEE, CafeView.KITCHEN)
            val shots = if (speed >= 5 && service) {
                listOf(cue.views.firstOrNull { it != lastServiceView } ?: cue.views.first())
            } else cue.views
            for (shot in shots) {
                automaticView = shot
                if (service) lastServiceView = shot
                delay(when {
                    !service -> 2_600L
                    speed == 5 -> 3_200L
                    speed == 20 -> 2_200L
                    else -> 2_600L
                })
            }
            automaticView = CafeView.ROOM
            delay(roomHold)
        }
    }
    val activity = when (view) {
        CafeView.ROOM -> if (isOpen) stringResource(R.string.room_guest_counts, activeGuests, tables, seats) else stringResource(R.string.room_closed)
        CafeView.COFFEE -> if (pinnedView == null) stringResource(R.string.coffee_order) else stringResource(R.string.coffee_bar)
        CafeView.KITCHEN -> if (pinnedView == null) stringResource(R.string.kitchen_order) else cafeLabel("Kitchen")
        CafeView.DELIVERY -> if (pinnedView == null) stringResource(R.string.delivery_received) else stringResource(R.string.receiving_entrance)
        CafeView.PAYMENT -> if (pinnedView == null) stringResource(R.string.automatic_bill_payment) else cafeLabel("Office")
    }

    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(if (isOpen) R.string.open else R.string.closed), style = MaterialTheme.typography.labelMedium,
                        color = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.day_number, time.day), style = MaterialTheme.typography.labelMedium)
                    Text("%02d:%02d".format(Locale.ROOT, time.hour, time.minute),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                }
            }
            Box(Modifier.fillMaxWidth().aspectRatio(2f).clipToBounds()) {
                Crossfade(targetState = view, animationSpec = tween(260), label = "Café view") { shot ->
                    SceneFrame(
                        view = shot,
                        hour = time.hour,
                        active = shot == view && if (shot == CafeView.ROOM) isOpen else pinnedView == null,
                        sceneArt = if (appearance == CafeAppearance.ART_DECO) {
                            when (shot) {
                                CafeView.ROOM -> if (isOpen && activeGuests >= 4) R.drawable.cafe_deco_busy
                                    else R.drawable.cafe_deco_room
                                CafeView.COFFEE -> R.drawable.cafe_deco_coffee
                                CafeView.KITCHEN -> R.drawable.cafe_deco_kitchen
                                CafeView.DELIVERY -> R.drawable.cafe_deco_delivery
                                CafeView.PAYMENT -> R.drawable.cafe_deco_office
                            }
                        } else if (shot != CafeView.ROOM) shot.art else when {
                            !isOpen -> R.drawable.cafe_scene_room
                            activeGuests >= maxOf(5, seats / 2) -> R.drawable.cafe_scene_room_busy
                            activeGuests >= 3 -> R.drawable.cafe_scene_room_steady
                            else -> R.drawable.cafe_scene_room_quiet
                        },
                        playing = playing
                    )
                }
                Text(activity, Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Color(0xCC121C22), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            CafeLedgerOptions(CafeView.entries.map { it.label }, view.label, revealSelected = false) { label ->
                pinnedView = CafeView.entries.first { it.label == label }
            }
            val destination = when (view) {
                CafeView.ROOM -> null
                CafeView.COFFEE -> "Menu prices"
                CafeView.KITCHEN -> "Inventory"
                CafeView.DELIVERY -> "Purchasing"
                CafeView.PAYMENT -> "Bills"
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = pinnedView == null,
                    onClick = { pinnedView = null },
                    label = { Text(stringResource(R.string.auto_camera)) }
                )
                if (destination != null) {
                    TextButton(onClick = {
                        when (view) {
                            CafeView.COFFEE -> onOpenOperations(0)
                            CafeView.KITCHEN -> onOpenOperations(2)
                            CafeView.DELIVERY -> onOpenOperations(1)
                            CafeView.PAYMENT -> onOpenFinance()
                            CafeView.ROOM -> Unit
                        }
                    }) { Text(cafeLabel(destination)) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SceneMetric(stringResource(R.string.menu_ready), "$readyDishes / $totalDishes", Modifier.weight(1f))
                SceneMetric(stringResource(R.string.bank), sceneMoney(bankCents), Modifier.weight(1f))
                SceneMetric(stringResource(R.string.cash), sceneMoney(cashCents), Modifier.weight(1f))
            }
            if (hasUrgentBills) {
                Text(stringResource(R.string.overdue_service), Modifier.padding(start = 14.dp, bottom = 10.dp),
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
private fun SceneFrame(view: CafeView, hour: Int, active: Boolean, @DrawableRes sceneArt: Int, playing: Boolean) {
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
        Crossfade(targetState = sceneArt, animationSpec = tween(720), label = "Dining room occupancy",
            modifier = Modifier.fillMaxSize()) { frame ->
            Image(painterResource(frame), contentDescription = stringResource(view.description),
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer {
                    val drift = sin(phase.value * (2 * PI)).toFloat()
                    scaleX = 1.035f
                    scaleY = 1.035f
                    translationX = drift * size.width * 0.009f
                })
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

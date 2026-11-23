package com.example.runthebusiness.ui.cafe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.min

private val wall = Color(0xFFEDD8B9)
private val floor = Color(0xFFB9805C)
private val wood = Color(0xFF75462F)
private val brass = Color(0xFFE4B76C)
private val leaf = Color(0xFF527C61)
private val linen = Color(0xFFF8E9D2)

/** Lightweight, resolution-independent game scene. No bitmap assets or animation timers.
 * The scene represents the real saved café state; it does not simulate customers.
 */
@Composable
fun CafeScene(
    name: String,
    day: Int,
    hour: Int,
    minute: Int,
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
    Column(modifier.background(Color(0xFF2F211C), RoundedCornerShape(22.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("MAISON CAFÉ", color = brass, style = MaterialTheme.typography.labelMedium)
                Text(name, color = linen, style = MaterialTheme.typography.titleLarge)
            }
            Column {
                Text("DAY $day", color = brass, style = MaterialTheme.typography.labelMedium)
                Text("%02d:%02d".format(Locale.ROOT, hour, minute),
                    color = linen, style = MaterialTheme.typography.titleLarge)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val sx = size.width / 360f
            val sy = size.height / 220f
            fun point(x: Float, y: Float) = Offset(x * sx, y * sy)
            fun block(color: Color, x: Float, y: Float, w: Float, h: Float, radius: Float = 0f) {
                drawRoundRect(color, point(x, y), Size(w * sx, h * sy),
                    androidx.compose.ui.geometry.CornerRadius(radius * min(sx, sy)))
            }
            block(wall, 0f, 0f, 360f, 76f)
            block(floor, 0f, 76f, 360f, 144f)
            for (i in 0..8) {
                drawLine(Color(0x33FFF2DA), point(i * 45f, 76f), point(i * 45f - 35f, 220f), 1.5f)
            }
            block(Color(0xFF3C615C), 20f, 9f, 83f, 60f, 5f)
            block(Color(0xFFA9D3C7), 26f, 15f, 71f, 48f, 3f)
            block(Color(0xFFFFF1CB), 56f, 15f, 3f, 48f)
            block(Color(0xFFFFF1CB), 26f, 36f, 71f, 3f)
            block(wood, 223f, 42f, 127f, 53f, 7f)
            block(Color(0xFFCF9A6C), 218f, 36f, 137f, 11f, 3f)
            block(Color(0xFF312B2B), 248f, 14f, 34f, 23f, 3f)
            block(brass, 254f, 20f, 22f, 3f, 1f)
            block(brass, 254f, 27f, 14f, 3f, 1f)
            // Espresso machine, cups and warm pendant lamps.
            block(Color(0xFF6D6C6A), 293f, 19f, 40f, 20f, 3f)
            block(linen, 297f, 12f, 10f, 9f, 2f)
            block(linen, 313f, 12f, 10f, 9f, 2f)
            for (x in listOf(130f, 185f, 298f)) {
                drawLine(wood, point(x, 0f), point(x, 19f), 2f)
                drawCircle(brass, 9f * sx, point(x, 22f))
                drawCircle(Color(0x66FFF3D1), 17f * sx, point(x, 22f))
            }
            val positions = listOf(Offset(74f, 125f), Offset(179f, 124f),
                Offset(100f, 190f), Offset(222f, 190f), Offset(310f, 156f))
            repeat(tables.coerceIn(0, positions.size)) { i ->
                val p = positions[i]
                val x = p.x
                val y = p.y
                block(Color(0x66502F22), x - 27f, y + 13f, 60f, 11f, 5f)
                block(wood, x - 5f, y + 5f, 10f, 26f, 2f)
                drawOval(Color(0xFFE9B785), point(x - 28f, y - 10f), Size(56f * sx, 29f * sy))
                drawOval(wood, point(x - 28f, y - 12f), Size(56f * sx, 29f * sy),
                    style = Stroke(2f * sx))
                drawCircle(linen, 5f * sx, point(x + 8f, y + 1f))
            }
            // Abstract customers: intentionally simple until the character-art pass.
            val guestPositions = listOf(
                Offset(52f, 111f), Offset(93f, 113f), Offset(155f, 108f),
                Offset(202f, 111f), Offset(81f, 175f), Offset(119f, 174f),
                Offset(203f, 175f), Offset(242f, 173f), Offset(296f, 141f),
                Offset(326f, 142f)
            )
            repeat(activeGuests.coerceIn(0, guestPositions.size)) { i ->
                val p = guestPositions[i]
                val jacket = if (i % 3 == 0) leaf else if (i % 3 == 1)
                    Color(0xFF9E5950) else Color(0xFF52708A)
                block(jacket, p.x - 8f, p.y + 10f, 16f, 18f, 6f)
                drawCircle(Color(0xFFD6A17C), 7f * sx, point(p.x, p.y + 6f))
                drawArc(wood, 190f, 160f, false,
                    point(p.x - 7f, p.y - 1f), Size(14f * sx, 13f * sy),
                    style = Stroke(3f * sx))
            }
            if (!isOpen) block(Color(0xAA261A17), 0f, 0f, 360f, 220f)
        }
        Row(Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isOpen) "● OPEN" else "● CLOSED",
                color = if (isOpen) Color(0xFF94D5A6) else Color(0xFFFFB6A6),
                style = MaterialTheme.typography.labelLarge)
            Text("$tables tables · $seats seats · $activeGuests guests",
                color = linen, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(color = Color(0xFF604438))
        Row(Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("AVAILABLE MENU", color = brass, style = MaterialTheme.typography.labelSmall)
                Text("$readyDishes / $totalDishes", color = linen,
                    style = MaterialTheme.typography.titleMedium)
            }
            Column {
                Text("BANK / CASH", color = brass, style = MaterialTheme.typography.labelSmall)
                Text("€%.0f / €%.0f".format(Locale.ROOT, bankCents / 100.0, cashCents / 100.0),
                    color = linen, style = MaterialTheme.typography.titleMedium)
            }
            if (hasUrgentBills) Text("!", color = Color(0xFFFFA08B),
                style = MaterialTheme.typography.headlineMedium)
        }
    }
}

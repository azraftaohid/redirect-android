package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DailyClickPoint
import com.example.ui.theme.MonoFontFamily
import kotlin.math.roundToInt

@Composable
fun DailyChart(
  points: List<DailyClickPoint>,
  modifier: Modifier = Modifier,
) {
  var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

  if (points.isEmpty()) {
    Text(
      text = "No daily data in this range.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = modifier.padding(vertical = 24.dp),
    )
    return
  }

  val maxClicks = remember(points) { (points.maxOfOrNull { it.clicks } ?: 0).coerceAtLeast(1) }
  val isBarMode = points.size <= 30

  val primaryColor = MaterialTheme.colorScheme.primary
  val activeHighlightColor = MaterialTheme.colorScheme.secondary
  val mutedLineColor = MaterialTheme.colorScheme.outlineVariant
  val surfaceColor = MaterialTheme.colorScheme.surface

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Scrubbing readout header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val activePoint = selectedIndex?.let { points.getOrNull(it) }
      if (activePoint != null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            text = "${activePoint.day}  ·  ${activePoint.clicks} clicks",
            style = MaterialTheme.typography.labelMedium.copy(
              fontFamily = MonoFontFamily,
              fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      } else {
        Text(
          text = "Tap or drag chart to inspect daily counts",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = "Max: ${points.maxOfOrNull { it.clicks } ?: 0}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(176.dp)
        .testTag("daily_clicks_chart")
        .pointerInput(points) {
          detectTapGestures { offset ->
            if (size.width > 0 && points.isNotEmpty()) {
              val idx = ((offset.x / size.width) * (points.size - 1))
                .roundToInt()
                .coerceIn(0, points.lastIndex)
              selectedIndex = idx
            }
          }
        }
        .pointerInput(points) {
          detectHorizontalDragGestures(
            onDragEnd = {},
            onHorizontalDrag = { change, _ ->
              if (size.width > 0 && points.isNotEmpty()) {
                val idx = ((change.position.x / size.width) * (points.size - 1))
                  .roundToInt()
                  .coerceIn(0, points.lastIndex)
                selectedIndex = idx
              }
            },
          )
        },
    ) {
      val w = size.width
      val h = size.height
      val topPad = 10.dp.toPx()
      val bottomPad = 6.dp.toPx()
      val chartH = (h - topPad - bottomPad).coerceAtLeast(1f)

      // Horizontal baseline & mid dashed gridlines
      val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
      drawLine(
        color = mutedLineColor,
        start = Offset(0f, topPad + chartH),
        end = Offset(w, topPad + chartH),
        strokeWidth = 1.dp.toPx(),
      )
      drawLine(
        color = mutedLineColor,
        start = Offset(0f, topPad + chartH * 0.5f),
        end = Offset(w, topPad + chartH * 0.5f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dashEffect,
      )
      drawLine(
        color = mutedLineColor,
        start = Offset(0f, topPad),
        end = Offset(w, topPad),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dashEffect,
      )

      if (isBarMode) {
        val count = points.size
        val slotWidth = w / count
        val barWidth = (slotWidth * 0.62f).coerceAtLeast(3.dp.toPx())
        points.forEachIndexed { idx, pt ->
          val ratio = pt.clicks.toFloat() / maxClicks.toFloat()
          val barH = if (pt.clicks == 0) 2.dp.toPx() else (ratio * chartH).coerceAtLeast(4.dp.toPx())
          val x = idx * slotWidth + (slotWidth - barWidth) / 2f
          val y = topPad + chartH - barH
          val isSelected = selectedIndex == idx
          val barColor = when {
            isSelected -> activeHighlightColor
            pt.clicks == 0 -> mutedLineColor
            else -> primaryColor
          }
          drawRoundRect(
            color = barColor,
            topLeft = Offset(x, y),
            size = Size(barWidth, barH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
          )
        }
      } else {
        val count = points.size
        val stepX = if (count > 1) w / (count - 1).toFloat() else w
        val linePath = Path()
        val areaPath = Path()

        val coords = points.mapIndexed { idx, pt ->
          val x = idx * stepX
          val y = topPad + chartH - (pt.clicks.toFloat() / maxClicks.toFloat()) * chartH
          Offset(x, y)
        }

        coords.forEachIndexed { idx, offset ->
          if (idx == 0) {
            linePath.moveTo(offset.x, offset.y)
            areaPath.moveTo(offset.x, topPad + chartH)
            areaPath.lineTo(offset.x, offset.y)
          } else {
            linePath.lineTo(offset.x, offset.y)
            areaPath.lineTo(offset.x, offset.y)
          }
        }
        areaPath.lineTo(coords.last().x, topPad + chartH)
        areaPath.close()

        drawPath(
          path = areaPath,
          brush = Brush.verticalGradient(
            colors = listOf(
              primaryColor.copy(alpha = 0.25f),
              primaryColor.copy(alpha = 0.02f),
            ),
            startY = topPad,
            endY = topPad + chartH,
          ),
        )
        drawPath(
          path = linePath,
          color = primaryColor,
          style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
          ),
        )

        selectedIndex?.let { idx ->
          coords.getOrNull(idx)?.let { pt ->
            drawLine(
              color = activeHighlightColor,
              start = Offset(pt.x, topPad),
              end = Offset(pt.x, topPad + chartH),
              strokeWidth = 1.dp.toPx(),
            )
            drawCircle(
              color = surfaceColor,
              radius = 5.dp.toPx(),
              center = pt,
            )
            drawCircle(
              color = primaryColor,
              radius = 3.5.dp.toPx(),
              center = pt,
            )
          }
        }
      }
    }

    // X-axis date labels (start / mid / end)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = points.first().day,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (points.size >= 3) {
        Text(
          text = points[points.size / 2].day,
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (points.size >= 2) {
        Text(
          text = points.last().day,
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

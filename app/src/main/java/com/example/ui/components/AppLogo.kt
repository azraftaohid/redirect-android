package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun AppLogoMark(
  modifier: Modifier = Modifier,
  size: Dp = 36.dp,
) {
  val containerColor = MaterialTheme.colorScheme.primary
  val glyphColor = MaterialTheme.colorScheme.onPrimary

  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    drawRoundRect(
      color = containerColor,
      cornerRadius = CornerRadius(w * 0.28f, h * 0.28f),
    )

    val strokeWidth = w * 0.105f
    // Forward slash /
    drawLine(
      color = glyphColor,
      start = Offset(w * 0.43f, h * 0.28f),
      end = Offset(w * 0.27f, h * 0.72f),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )

    // Chevron >
    val chevron = Path().apply {
      moveTo(w * 0.54f, h * 0.31f)
      lineTo(w * 0.75f, h * 0.50f)
      lineTo(w * 0.54f, h * 0.69f)
    }
    drawPath(
      path = chevron,
      color = glyphColor,
      style = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
      ),
    )
  }
}

@Composable
fun AppBrandHeader(
  modifier: Modifier = Modifier,
  subtitle: String? = null,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    AppLogoMark(size = 32.dp)
    Text(
      text = stringResource(R.string.app_name),
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface,
    )
    if (!subtitle.isNullOrBlank()) {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

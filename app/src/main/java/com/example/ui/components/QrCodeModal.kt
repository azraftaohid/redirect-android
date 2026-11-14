package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.LinkRecord
import com.example.domain.util.QrUtils
import com.example.domain.util.ValidationUtils
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QrCodeModal(
  link: LinkRecord,
  serverBaseUrl: String,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val shortUrl = remember(serverBaseUrl, link.slug) {
    ValidationUtils.buildShortUrl(serverBaseUrl, link.slug)
  }
  val previewBitmap = remember(shortUrl) {
    QrUtils.generateQrBitmap(shortUrl, sizePx = 512)
  }

  val saveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("image/png"),
  ) { uri ->
    if (uri != null) {
      scope.launch {
        val saved = withContext(Dispatchers.IO) {
          runCatching {
            val highRes = QrUtils.generateQrBitmap(shortUrl, sizePx = 1024)
            context.contentResolver.openOutputStream(uri)?.use { out ->
              highRes.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            highRes.recycle()
          }.isSuccess
        }
        Toast.makeText(
          context,
          if (saved) context.getString(R.string.qr_saved_success) else "Failed to save QR image",
          Toast.LENGTH_SHORT,
        ).show()
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.qr_title),
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = "/${link.slug}",
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.SemiBold,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(12.dp),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            bitmap = previewBitmap.asImageBitmap(),
            contentDescription = "QR code for $shortUrl",
            modifier = Modifier
              .size(196.dp)
              .testTag("qr_preview_image"),
          )
        }
        Text(
          text = shortUrl,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { saveLauncher.launch("qr-${link.slug}.png") },
            modifier = Modifier
              .weight(1f)
              .testTag("qr_save_png_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Download,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = stringResource(R.string.qr_save_png),
              modifier = Modifier.padding(start = 6.dp),
              maxLines = 1,
            )
          }
          Button(
            onClick = {
              scope.launch {
                val uri = withContext(Dispatchers.IO) {
                  val highRes = QrUtils.generateQrBitmap(shortUrl, sizePx = 1024)
                  val resultUri = QrUtils.writeQrPngToCache(context, link.slug, highRes)
                  highRes.recycle()
                  resultUri
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "image/png"
                  putExtra(Intent.EXTRA_STREAM, uri)
                  putExtra(Intent.EXTRA_TEXT, shortUrl)
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, shortUrl))
              }
            },
            modifier = Modifier
              .weight(1f)
              .testTag("qr_share_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = stringResource(R.string.qr_share_image),
              modifier = Modifier.padding(start = 6.dp),
              maxLines = 1,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("qr_close_button"),
      ) {
        Text(stringResource(R.string.qr_close))
      }
    },
  )
}

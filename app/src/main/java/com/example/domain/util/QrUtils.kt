package com.example.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object QrUtils {
  fun generateQrBitmap(content: String, sizePx: Int = 1024): Bitmap {
    val hints = mapOf(
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
      EncodeHintType.MARGIN to 2,
      EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
      val rowOffset = y * width
      for (x in 0 until width) {
        pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
      }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
      setPixels(pixels, 0, width, 0, 0, width, height)
    }
  }

  fun writeQrPngToCache(context: Context, slug: String, bitmap: Bitmap): Uri {
    val dir = File(context.cacheDir, "qr").apply { mkdirs() }
    val safeSlug = slug.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val file = File(dir, "redirect-$safeSlug.png")
    FileOutputStream(file).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }
}

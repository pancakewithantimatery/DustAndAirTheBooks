package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.IsbnUtils
import com.example.ui.theme.CyanAccent
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "BarcodeScanner"

@Composable
fun BarcodeScannerSection(
    onIsbnDetected: (String) -> Unit,
    onScannerError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val detectedLock = remember { AtomicBoolean(false) }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraRef?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {
            }
            cameraExecutor.shutdown()
        }
    }

    val laserTransition = rememberInfiniteTransition(label = "laserBeam")
    val laserProgress by laserTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .testTag("barcode_camera_preview")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(
                                    cameraExecutor,
                                    EanIsbnAnalyzer { validIsbn ->
                                        if (detectedLock.compareAndSet(false, true)) {
                                            vibrateShort(ctx)
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onIsbnDetected(validIsbn)
                                            }
                                        }
                                    }
                                )
                            }

                        cameraProvider.unbindAll()
                        cameraRef = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind CameraX barcode scanner", e)
                        onScannerError(e.localizedMessage ?: "Unable to start rear camera.")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Minimalist cyan laser viewfinder overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padX = size.width * 0.12f
            val padY = size.height * 0.2f
            val boxW = size.width - padX * 2f
            val boxH = size.height - padY * 2f
            val corner = 22.dp.toPx()
            val stroke = 2.5.dp.toPx()

            // Corner brackets
            val corners = listOf(
                // Top-left
                Offset(padX, padY) to Offset(padX + corner, padY),
                Offset(padX, padY) to Offset(padX, padY + corner),
                // Top-right
                Offset(padX + boxW, padY) to Offset(padX + boxW - corner, padY),
                Offset(padX + boxW, padY) to Offset(padX + boxW, padY + corner),
                // Bottom-left
                Offset(padX, padY + boxH) to Offset(padX + corner, padY + boxH),
                Offset(padX, padY + boxH) to Offset(padX, padY + boxH - corner),
                // Bottom-right
                Offset(padX + boxW, padY + boxH) to Offset(padX + boxW - corner, padY + boxH),
                Offset(padX + boxW, padY + boxH) to Offset(padX + boxW, padY + boxH - corner)
            )
            corners.forEach { (start, end) ->
                drawLine(
                    color = CyanAccent,
                    start = start,
                    end = end,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }

            // Animated horizontal scan laser line
            val laserY = padY + boxH * laserProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CyanAccent,
                        Color.White,
                        CyanAccent,
                        Color.Transparent
                    ),
                    startX = padX,
                    endX = padX + boxW
                ),
                start = Offset(padX + 8.dp.toPx(), laserY),
                end = Offset(padX + boxW - 8.dp.toPx(), laserY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Top overlay HUD: hint pill + torch toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xBB0D0F12),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Align EAN-13 book barcode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            IconButton(
                onClick = {
                    val next = !torchEnabled
                    torchEnabled = next
                    try {
                        cameraRef?.cameraControl?.enableTorch(next)
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xBB0D0F12))
                    .testTag("scanner_torch_button")
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (torchEnabled) "Turn flashlight off" else "Turn flashlight on",
                    tint = if (torchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

private class EanIsbnAnalyzer(
    private val onValidIsbn: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8
                ),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val width = image.width
            val height = image.height

            val decoded = decodeOrientation(data, width, height)
                ?: decodeRotated90(data, width, height)

            if (decoded != null) {
                val parsed = IsbnUtils.parseSingle(decoded)
                if (parsed.isValid) {
                    onValidIsbn(parsed.isbn13.ifEmpty { parsed.isbn10 })
                }
            }
        } catch (_: Exception) {
            // Frame had no readable barcode
        } finally {
            image.close()
        }
    }

    private fun decodeOrientation(yuv: ByteArray, width: Int, height: Int): String? {
        return try {
            val source = PlanarYUVLuminanceSource(
                yuv, width, height, 0, 0, width, height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decodeWithState(bitmap).text
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun decodeRotated90(yuv: ByteArray, width: Int, height: Int): String? {
        return try {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dstX = height - 1 - y
                    val dstY = x
                    rotated[dstY * height + dstX] = yuv[y * width + x]
                }
            }
            val source = PlanarYUVLuminanceSource(
                rotated, height, width, 0, 0, height, width, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decodeWithState(bitmap).text
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}

private fun vibrateShort(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(55L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(55L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(55L)
            }
        }
    } catch (_: Exception) {
    }
}

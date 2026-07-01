package com.example.calmsource.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerOverlay(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLumenTokens.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    DisposableEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f)),
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(LumenTokens.Space.md),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = t.colors.foreground)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LumenTokens.Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(LumenTokens.Space.xl))
            Text(
                text = "Scan TV sign-in code",
                style = LumenType.H2.toTextStyle(),
                color = t.colors.foreground,
            )
            Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
            Text(
                text = "Point your camera at the QR code on your television.",
                style = LumenType.Body.toTextStyle(),
                color = t.colors.mutedForeground,
            )
            Spacer(modifier = Modifier.height(LumenTokens.Space.lg))

            if (hasPermission) {
                val decodedOnce = remember { AtomicBoolean(false) }
                val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
                DisposableEffect(analyzerExecutor) {
                    onDispose { analyzerExecutor.shutdown() }
                }
                var cameraBound by remember(hasPermission) { mutableStateOf(false) }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    update = { previewView ->
                        if (cameraBound) return@AndroidView
                        cameraBound = true
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                if (decodedOnce.get()) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val buffer = imageProxy.planes.firstOrNull()?.buffer ?: run {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val data = ByteArray(buffer.remaining())
                                buffer.get(data)
                                val width = imageProxy.width
                                val height = imageProxy.height
                                val source = PlanarYUVLuminanceSource(
                                    data,
                                    width,
                                    height,
                                    0,
                                    0,
                                    width,
                                    height,
                                    false,
                                )
                                val bitmap = BinaryBitmap(HybridBinarizer(source))
                                runCatching {
                                    MultiFormatReader().decode(bitmap).text
                                }.getOrNull()?.let { value ->
                                    if (decodedOnce.compareAndSet(false, true)) {
                                        previewView.post { onResult(value) }
                                    }
                                }
                                imageProxy.close()
                            }
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            }
                        }, ContextCompat.getMainExecutor(context))
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Camera access is needed to scan the TV sign-in code.",
                        style = LumenType.Body.toTextStyle(),
                        color = t.colors.mutedForeground,
                    )
                    Spacer(modifier = Modifier.height(LumenTokens.Space.md))
                    TextButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    ) {
                        Text("Grant camera access", color = t.colors.brand)
                    }
                }
            }
        }
    }
}

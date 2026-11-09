package com.example.util

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonCyberGreen
import com.example.ui.theme.ObsidianBlack
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.util.EnumMap
import java.util.concurrent.Executors

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Triggers the real Android hardware BiometricPrompt (Fingerprint / Face sensor).
 * Strictly checks hardware presence and enrolled biometrics before unlocking.
 */
fun authenticateWithRealBiometrics(
    context: Context,
    title: String = "ZeroChat Donanım Kasa Kilidi",
    subtitle: String = "Gizli kasayı açmak için parmak izinizi sensöre okutun",
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK

    when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            onError("⚠️ Bu telefonda biyometrik / parmak izi sensörü bulunmuyor. Lütfen PIN kodunuzu kullanın.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            onError("⚠️ Parmak izi sensörü şu anda kullanılamıyor. Lütfen tekrar deneyin veya PIN girin.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onError("⚠️ Telefonda kayıtlı parmak izi yok! Önce telefon Ayarları > Güvenlik bölümünden parmak izi ekleyin.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
            onError("⚠️ Biyometrik doğrulama bu cihazda aktif değil. Lütfen PIN kodunuzu kullanın.")
            return
        }
        BiometricManager.BIOMETRIC_SUCCESS -> {
            // Proceed to real hardware prompt
        }
    }

    val activity = context.findFragmentActivity()
    if (activity == null) {
        onError("⚠️ Biyometrik pencere başlatılamadı. Lütfen PIN kodunuzu kullanın.")
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError("Biyometrik hata: $errString")
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("❌ Parmak izi eşleşmedi! Lütfen tekrar deneyin.")
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(authenticators)
        .setNegativeButtonText("PIN Kodu Kullan")
        .setConfirmationRequired(false)
        .build()

    try {
        prompt.authenticate(promptInfo)
    } catch (e: Exception) {
        Log.e("BiometricAuth", "Failed to show BiometricPrompt", e)
        onError("Biyometrik sensör başlatılamadı: ${e.localizedMessage}")
    }
}

/**
 * Encodes a real, standards-compliant QR Code Bitmap using ZXing QRCodeWriter.
 */
fun generateRealQrCodeBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) {
                    AndroidColor.BLACK
                } else {
                    AndroidColor.WHITE
                }
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        Log.e("QrGenerator", "Failed to generate QR bitmap", e)
        null
    }
}

/**
 * Live CameraX + ZXing QR Code Scanner Composable with runtime CAMERA permission,
 * Torch control, and Front/Back lens switching.
 */
@Composable
fun RealCameraQrScannerView(
    onQrCodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchOn by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var hasScanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permissionDeniedMessage = "QR kod taramak için kamera izni gereklidir."
        } else {
            permissionDeniedMessage = null
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianBlack),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NeonCyberGreen),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = NeonCyberGreen,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = permissionDeniedMessage ?: "Canlı QR Kod Okuyucu için Kamera İzni Gerekiyor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyberGreen,
                        contentColor = Color(0xFF002110)
                    )
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kamerayı Aç ve İzin Ver", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val reader = MultiFormatReader().apply {
                    val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
                        put(DecodeHintType.TRY_HARDER, true)
                        put(DecodeHintType.CHARACTER_SET, "UTF-8")
                    }
                    setHints(hints)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            if (!hasScanned) {
                                val decoded = decodeQrFromImageProxy(imageProxy, reader)
                                if (!decoded.isNullOrBlank()) {
                                    hasScanned = true
                                    mainExecutor.execute {
                                        onQrCodeDetected(decoded)
                                    }
                                }
                            }
                            imageProxy.close()
                        }
                    }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("QrScanner", "CameraX bind failed", e)
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, NeonCyberGreen, RoundedCornerShape(18.dp))
            .background(ObsidianBlack)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Targeting reticle frame overlay
        Box(
            modifier = Modifier
                .size(170.dp)
                .align(Alignment.Center)
                .border(2.dp, CyberCyan.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
        )

        // Top Camera Controls (Flash & Switch Camera)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = ObsidianBlack.copy(alpha = 0.75f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "🔴 CANLI QR TARAYICI AKTİF",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyberGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        val nextTorch = !isTorchOn
                        isTorchOn = nextTorch
                        boundCamera?.cameraControl?.enableTorch(nextTorch)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(ObsidianBlack.copy(alpha = 0.75f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Feneri Aç/Kapat",
                        tint = if (isTorchOn) NeonCyberGreen else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(ObsidianBlack.copy(alpha = 0.75f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Kamera Değiştir",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Surface(
            color = ObsidianBlack.copy(alpha = 0.78f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "Karşı düğümün QR kodunu mavi çerçeveye hizalayın",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private fun decodeQrFromImageProxy(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)

    val width = imageProxy.width
    val height = imageProxy.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    val luminance: ByteArray = if (pixelStride == 1 && rowStride == width) {
        data
    } else {
        val out = ByteArray(width * height)
        var inputOffset = 0
        for (y in 0 until height) {
            val outputOffset = y * width
            if (pixelStride == 1) {
                val len = minOf(width, data.size - inputOffset)
                if (len > 0) {
                    System.arraycopy(data, inputOffset, out, outputOffset, len)
                }
            } else {
                for (x in 0 until width) {
                    val idx = inputOffset + x * pixelStride
                    if (idx < data.size) {
                        out[outputOffset + x] = data[idx]
                    }
                }
            }
            inputOffset += rowStride
        }
        out
    }

    return try {
        val source = PlanarYUVLuminanceSource(
            luminance,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source)))).text
        }
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

/**
 * Full-screen or modal Real CameraX Photo Capture dialog for capturing encrypted photos in chat.
 */
@Composable
fun RealCameraPhotoCaptureDialog(
    onPhotoCapturedDataUri: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, lensFacing, hasCameraPermission) {
        if (!hasCameraPermission) return@DisposableEffect onDispose {}
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraCapture", "Failed to bind CameraX ImageCapture", e)
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(480.dp),
            shape = RoundedCornerShape(24.dp),
            color = CarbonSurface,
            border = BorderStroke(1.5.dp, NeonCyberGreen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📷 Şifreli Kamera Çekimi (AES-256)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyberGreen
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyberGreen,
                                contentColor = Color(0xFF002110)
                            )
                        ) {
                            Text("Kamera İznini Ver", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, CyberCyan, RoundedCornerShape(18.dp))
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val nextTorch = !isTorchOn
                                    isTorchOn = nextTorch
                                    boundCamera?.cameraControl?.enableTorch(nextTorch)
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(ObsidianBlack.copy(alpha = 0.75f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Fener",
                                    tint = if (isTorchOn) NeonCyberGreen else Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(ObsidianBlack.copy(alpha = 0.75f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Ön/Arka Kamera",
                                    tint = CyberCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (isCapturing) return@Button
                            isCapturing = true
                            val mainExec = ContextCompat.getMainExecutor(context)
                            imageCapture.takePicture(
                                mainExec,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        try {
                                            val bmp = image.toBitmap()
                                            val scaled = Bitmap.createScaledBitmap(
                                                bmp,
                                                640,
                                                (640f * bmp.height / bmp.width.coerceAtLeast(1)).toInt().coerceAtLeast(360),
                                                true
                                            )
                                            val baos = ByteArrayOutputStream()
                                            scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                                            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                            onPhotoCapturedDataUri("data:image/jpeg;base64,$b64")
                                        } catch (e: Exception) {
                                            Log.e("CameraCapture", "Bitmap encode failed", e)
                                        } finally {
                                            image.close()
                                            isCapturing = false
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraCapture", "Capture error", exception)
                                        isCapturing = false
                                    }
                                }
                            )
                        },
                        enabled = !isCapturing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("capture_encrypted_photo_btn")
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF002110),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fotoğrafı Çek ve AES-256 İle Şifrele", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

package com.example.util

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BurnCoralRed
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
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? FragmentActivity
}

/**
 * Verifies real hardware biometric sensor presence and enrolled fingerprints/biometrics,
 * then shows the OS BiometricPrompt dialog. Never unlocks if hardware or enrollment is missing.
 */
fun authenticateWithRealBiometrics(
    context: Context,
    title: String = "ZeroChat Kriptografik Kasa Kilidi",
    subtitle: String = "Gizli kasayı açmak için parmak izinizi veya biyometrik sensörü doğrulayın",
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(context)
    // Class 3 biometrics only, bound to a Keystore key (CryptoObject) so the unlock is
    // confirmed by secure hardware and not just by an app callback.
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG

    when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            // Hardware exists and user has enrolled biometrics
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
            onError("❌ Bu cihazda fiziksel parmak izi / biyometrik sensör donanımı bulunmuyor! Lütfen Kasa PIN kodunu kullanın.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            onError("⚠️ Biyometrik sensör donanımı şu anda kullanılamıyor. Lütfen PIN kodunuzu girin.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onError("🔒 Telefonunuzda kayıtlı parmak izi veya biyometrik kilit aktif değil! Önce telefon Ayarları > Güvenlik bölümünden parmak izi ekleyin.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
            onError("⚠️ Biyometrik sensör için güvenlik güncellemesi gerekiyor.")
            return
        }
        else -> {
            onError("❌ Biyometrik doğrulama bu cihazda aktif değil. Lütfen PIN kodunuzu kullanın.")
            return
        }
    }

    val activity = context.findFragmentActivity()
    if (activity == null) {
        onError("⚠️ Biyometrik doğrulama penceresi başlatılamadı (FragmentActivity bulunamadı).")
        return
    }

    val cipher = try {
        biometricVaultCipher()
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
        // A new fingerprint was enrolled: the old key is gone; require the PIN once.
        resetBiometricVaultKey()
        onError("Biyometrik kayıtlar değişti. Kasayı bir kez PIN ile açın.")
        return
    } catch (e: Exception) {
        onError("Biyometrik anahtar oluşturulamadı: ${e.message}")
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // The cipher only works if the TEE accepted the biometric for this key.
                val verified = try {
                    result.cryptoObject?.cipher?.doFinal(ByteArray(16)) != null
                } catch (_: Exception) {
                    false
                }
                if (verified) onSuccess() else onError("Biyometrik doğrulama donanım tarafından onaylanmadı")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError("Biyometrik doğrulama hatası: $errString")
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
        .build()

    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
}

private const val BIOMETRIC_VAULT_KEY = "zerochat_vault_biometric_key"

/** AES key that can only be used right after a strong biometric authentication. */
private fun biometricVaultCipher(): javax.crypto.Cipher {
    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val key = (keyStore.getKey(BIOMETRIC_VAULT_KEY, null) as? javax.crypto.SecretKey) ?: run {
        val generator = javax.crypto.KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            BIOMETRIC_VAULT_KEY,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, android.security.keystore.KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        generator.init(spec)
        generator.generateKey()
    }
    return javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(javax.crypto.Cipher.ENCRYPT_MODE, key)
    }
}

private fun resetBiometricVaultKey() {
    runCatching {
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(BIOMETRIC_VAULT_KEY)
    }
}

/**
 * Generates a real, standards-compliant ISO/IEC 18004 QR Code Bitmap using ZXing.
 */
fun generateRealQrCodeBitmap(
    content: String,
    sizePx: Int = 640,
    foregroundColor: Int = android.graphics.Color.rgb(0, 230, 118),
    backgroundColor: Int = android.graphics.Color.rgb(6, 10, 15)
): ImageBitmap? {
    val cleanContent = content.trim()
    if (cleanContent.isEmpty()) return null
    return try {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 1)
        }
        val bitMatrix = QRCodeWriter().encode(cleanContent, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        Log.e("ZeroChatQR", "Failed to generate real QR code", e)
        null
    }
}

/**
 * Decodes a QR code from a gallery image URI if the user scans an image from their phone.
 */
private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8"
                )
            )
        }
        try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source)))).text
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Live CameraX + ZXing QR Code Scanner Composable with Torch, Front/Back Lens toggle, and Gallery QR decode.
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
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchOn by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var statusText by remember { mutableStateOf("Kamerayı karşınızdaki kişinin ZeroChat QR koduna doğrultun...") }
    val hasDispatchedResult = remember { AtomicBoolean(false) }
    val qrScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            statusText = "⚠️ QR tarama için Kamera izni gereklidir."
        }
    }

    val galleryQrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            statusText = "Görsel taranıyor..."
            qrScope.launch {
                val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                        context.contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                        }?.let { decodeQrFromBitmap(it) }
                    }.getOrNull()
                }
                if (!decoded.isNullOrBlank()) {
                    onQrCodeDetected(decoded)
                } else {
                    statusText = "❌ Seçilen görselde geçerli bir QR kod bulunamadı."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianBlack,
        border = BorderStroke(1.5.dp, NeonCyberGreen),
        modifier = modifier.fillMaxWidth()
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Canlı Kamera ile QR Kod Tarama",
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonCyberGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        )
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kamerayı Aç", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            galleryQrPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        border = BorderStroke(1.dp, CyberCyan)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galeriden QR Seç", color = CyberCyan)
                    }
                }
            }
        } else {
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
                val analysisExecutor = Executors.newSingleThreadExecutor()
                val mainExecutor = ContextCompat.getMainExecutor(context)
                val disposed = AtomicBoolean(false)
                var boundUseCases: Array<androidx.camera.core.UseCase> = emptyArray()

                val listener = Runnable {
                    if (disposed.get()) return@Runnable
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val qrReader = MultiFormatReader().apply {
                            setHints(
                                mapOf(
                                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                    DecodeHintType.TRY_HARDER to true,
                                    DecodeHintType.CHARACTER_SET to "UTF-8"
                                )
                            )
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    val decoded = decodeQrFromImageProxy(imageProxy, qrReader)
                                    if (!decoded.isNullOrBlank() && hasDispatchedResult.compareAndSet(false, true)) {
                                        mainExecutor.execute {
                                            if (!disposed.get()) onQrCodeDetected(decoded)
                                            // Allow another scan if the caller rejected this one.
                                            previewView.postDelayed({ hasDispatchedResult.set(false) }, 2500L)
                                        }
                                    }
                                    imageProxy.close()
                                }
                            }

                        val selector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        boundUseCases = arrayOf(preview, imageAnalysis)
                        val cam = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageAnalysis
                        )
                        boundCamera = cam
                        isTorchOn = false
                    } catch (e: Exception) {
                        Log.e("ZeroChatQR", "CameraX bind failed", e)
                        statusText = "Kamera başlatılamadı: ${e.localizedMessage}"
                    }
                }

                cameraProviderFuture.addListener(listener, mainExecutor)

                onDispose {
                    disposed.set(true)
                    if (cameraProviderFuture.isDone && boundUseCases.isNotEmpty()) {
                        runCatching { cameraProviderFuture.get().unbind(*boundUseCases) }
                    }
                    analysisExecutor.shutdown()
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cyber Target Frame Overlay
                    Surface(
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, NeonCyberGreen.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .size(150.dp)
                            .align(Alignment.Center)
                    ) {}

                    // Top Camera Controls (Torch + Lens Switch + Gallery QR)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .align(Alignment.TopEnd),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = {
                                val nextTorch = !isTorchOn
                                isTorchOn = nextTorch
                                boundCamera?.cameraControl?.enableTorch(nextTorch)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(ObsidianBlack.copy(alpha = 0.72f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Fener",
                                tint = if (isTorchOn) NeonCyberGreen else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
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
                                .background(ObsidianBlack.copy(alpha = 0.72f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Kamera Değiştir",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                galleryQrPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(ObsidianBlack.copy(alpha = 0.72f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Galeriden QR Oku",
                                tint = NeonCyberGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun decodeQrFromImageProxy(image: ImageProxy, reader: MultiFormatReader): String? {
    return try {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val yuvBytes = if (pixelStride == 1 && rowStride == width) {
            data
        } else {
            val packed = ByteArray(width * height)
            for (y in 0 until height) {
                val rowStart = y * rowStride
                for (x in 0 until width) {
                    val idx = rowStart + x * pixelStride
                    if (idx < data.size) {
                        packed[y * width + x] = data[idx]
                    }
                }
            }
            packed
        }

        val source = PlanarYUVLuminanceSource(
            yuvBytes,
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
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { captureExecutor.shutdown() } }

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
        val disposed = AtomicBoolean(false)
        var boundPreview: Preview? = null

        val listener = Runnable {
            if (disposed.get()) return@Runnable
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                cameraProvider.unbind(imageCapture)
                boundPreview = preview
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraCapture", "CameraX bind failed", e)
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            disposed.set(true)
            if (cameraProviderFuture.isDone) {
                runCatching {
                    val provider = cameraProviderFuture.get()
                    boundPreview?.let { provider.unbind(it) }
                    provider.unbind(imageCapture)
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianBlack),
            border = BorderStroke(1.5.dp, NeonCyberGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = NeonCyberGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Şifreli Kamera Çekimi",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyberGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = BurnCoralRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!hasCameraPermission) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Fotoğraf çekip şifreli olarak göndermek için Kamera izni gereklidir.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            Text("Kamera İznini Ver", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(18.dp))
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
                                    val next = !isTorchOn
                                    isTorchOn = next
                                    boundCamera?.cameraControl?.enableTorch(next)
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(ObsidianBlack.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Flaş",
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
                                    .size(40.dp)
                                    .background(ObsidianBlack.copy(alpha = 0.7f), CircleShape)
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
                            // Decode and encode on a background executor; deliver on the main thread.
                            val mainExec = ContextCompat.getMainExecutor(context)
                            imageCapture.takePicture(
                                captureExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val dataUri = try {
                                            kotlinx.coroutines.runBlocking {
                                                encodeBitmapForSending(image.toBitmap(), image.imageInfo.rotationDegrees)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CameraCapture", "Bitmap encode failed", e)
                                            null
                                        } finally {
                                            image.close()
                                        }
                                        mainExec.execute {
                                            isCapturing = false
                                            if (dataUri != null) onPhotoCapturedDataUri(dataUri)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraCapture", "Capture error", exception)
                                        mainExec.execute { isCapturing = false }
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
                            Text("Fotoğrafı Çek ve Gönder", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real-time CameraX video preview box for P2P Encrypted Video Calls with live frame capture.
 */
@Composable
fun LiveVideoCallCameraPreview(
    modifier: Modifier = Modifier,
    onFrameCapturedBase64: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

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

    val currentOnFrame by rememberUpdatedState(onFrameCapturedBase64)

    DisposableEffect(lifecycleOwner, lensFacing, hasCameraPermission) {
        if (!hasCameraPermission) return@DisposableEffect onDispose {}
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val disposed = java.util.concurrent.atomic.AtomicBoolean(false)
        var boundUseCases: Array<androidx.camera.core.UseCase> = emptyArray()
        var lastFrameMillis = 0L

        val listener = Runnable {
            if (disposed.get()) return@Runnable
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val now = System.currentTimeMillis()
                            val callback = currentOnFrame
                            if (callback != null && (now - lastFrameMillis) >= VIDEO_FRAME_INTERVAL_MS) {
                                lastFrameMillis = now
                                try {
                                    val frame = encodeVideoFrame(imageProxy.toBitmap(), imageProxy.imageInfo.rotationDegrees)
                                    mainExecutor.execute { if (!disposed.get()) callback(frame) }
                                } catch (_: Exception) {
                                }
                            }
                            imageProxy.close()
                        }
                    }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                boundUseCases = arrayOf(preview, imageAnalysis)
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e("VideoCallCamera", "Failed to bind video call preview", e)
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            disposed.set(true)
            // Only unbind what this preview bound; never block the main thread on the future.
            if (cameraProviderFuture.isDone && boundUseCases.isNotEmpty()) {
                runCatching { cameraProviderFuture.get().unbind(*boundUseCases) }
            }
            analysisExecutor.shutdown()
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianBlack,
        border = BorderStroke(1.5.dp, CyberCyan),
        modifier = modifier
    ) {
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBlack)
                ) {
                    Text("Kamera İznini Aç", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                )
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            CameraSelector.LENS_FACING_BACK
                        } else {
                            CameraSelector.LENS_FACING_FRONT
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(ObsidianBlack.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Ön/Arka Kamera Değiştir",
                        tint = CyberCyan
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianBlack.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "🟢 Canlı Kamera",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyberGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Reads an image from a local content:// URI, scales it down, compresses as JPEG,
 * and returns a cross-device "data:image/jpeg;base64,..." payload.
 */
fun encodeUriToEncryptedDataUri(context: Context, uri: android.net.Uri): String? {
    return try {
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream)
        } ?: return null
        val maxDim = 640
        val w = rawBitmap.width.coerceAtLeast(1)
        val h = rawBitmap.height.coerceAtLeast(1)
        val scale = (maxDim.toFloat() / maxOf(w, h).toFloat()).coerceAtMost(1.0f)
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)
        val scaled = if (targetW != w || targetH != h) {
            Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
        } else {
            rawBitmap
        }
        val baos = ByteArrayOutputStream()
        var quality = 74
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        while (baos.size() > 110_000 && quality > 35) {
            baos.reset()
            quality -= 12
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        }
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    } catch (e: Exception) {
        Log.e("ZeroChatMedia", "Failed to encode URI to Base64 data URI", e)
        null
    }
}

/**
 * Decodes a "data:image/...;base64,..." string into a Compose ImageBitmap for cross-device rendering.
 */
fun decodeBase64DataUriToImageBitmap(dataUri: String): ImageBitmap? {
    if (!dataUri.startsWith("data:image", ignoreCase = true)) return null
    val b64Part = dataUri.substringAfter("base64,", "")
    if (b64Part.isBlank()) return null
    return try {
        val bytes = Base64.decode(b64Part, Base64.DEFAULT)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bmp?.asImageBitmap()
    } catch (e: Exception) {
        Log.e("ZeroChatMedia", "Failed to decode Base64 image", e)
        null
    }
}

/**
 * Reads up to maxBytes of a selected document URI and encodes it as Base64 for cross-device transfer.
 */
fun encodeDocumentUriToBase64(context: Context, uri: android.net.Uri, maxBytes: Int = 122_880): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(maxBytes)
            var totalRead = 0
            while (totalRead < maxBytes) {
                val read = stream.read(buffer, totalRead, maxBytes - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            if (totalRead > 0) {
                Base64.encodeToString(buffer.copyOf(totalRead), Base64.NO_WRAP)
            } else {
                ""
            }
        } ?: ""
    } catch (e: Exception) {
        Log.e("ZeroChatMedia", "Failed to encode document URI", e)
        ""
    }
}

private const val VIDEO_FRAME_INTERVAL_MS = 1_500L

/** Downscales, rotates upright and JPEG-encodes one camera frame as a data URI. */
private fun encodeVideoFrame(source: Bitmap, rotationDegrees: Int): String {
    val targetW = 240
    val targetH = (targetW.toFloat() * source.height / source.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
    val upright = if (rotationDegrees != 0) {
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
    } else {
        scaled
    }
    val out = ByteArrayOutputStream()
    upright.compress(Bitmap.CompressFormat.JPEG, 50, out)
    return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonCyberGreen
import com.example.ui.theme.ObsidianBlack
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

private const val PREFS_LOCAL_NODE = "zerochat_local_node_session"
private const val KEY_LOCAL_UID = "local_uid"
private const val KEY_LOCAL_HANDLE = "local_handle"

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Handle the user typed on the sign-in screen; used once when the profile is first created. */
fun getPreferredHandle(context: Context): String? =
    context.getSharedPreferences(PREFS_LOCAL_NODE, Context.MODE_PRIVATE)
        .getString(KEY_LOCAL_HANDLE, null)?.takeIf { it.isNotBlank() }

private fun savePreferredHandle(context: Context, handle: String) {
    context.getSharedPreferences(PREFS_LOCAL_NODE, Context.MODE_PRIVATE).edit()
        .putString(KEY_LOCAL_HANDLE, handle)
        .apply()
}

fun clearPreferredHandle(context: Context) {
    context.getSharedPreferences(PREFS_LOCAL_NODE, Context.MODE_PRIVATE).edit().clear().apply()
}

private suspend fun exchangeGoogleCredentialWithFirebase(
    context: Context,
    credential: Credential,
    preferredHandle: String,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val googleIdToken = googleIdTokenCredential.idToken
        val googleEmail = googleIdTokenCredential.id
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
        val firebaseUser = authResult.user
        if (firebaseUser != null) {
            val cleanPreferred = preferredHandle.trim()
                .removePrefix("@")
                .lowercase(Locale.ROOT)
                .replace("[^a-z0-9_]".toRegex(), "")
                .take(24)
            if (cleanPreferred.length >= 3) savePreferredHandle(context, cleanPreferred)
            onAuthSuccess()
        } else {
            onAuthError("Firebase oturumu oluşturulamadı. Lütfen tekrar deneyin.")
        }
    } else {
        onAuthError("Geçersiz Google kimlik bilgisi türü alındı.")
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }

    val clientId = context.getString(R.string.default_web_client_id)
    val activityContext = context.findActivity() ?: context
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activityContext, request)
            exchangeGoogleCredentialWithFirebase(
                context = context,
                credential = result.credential,
                preferredHandle = "",
                onAuthSuccess = onAuthSuccess,
                onAuthError = { onUnauthenticated() }
            )
        } catch (e: NoCredentialException) {
            onUnauthenticated()
        } catch (e: GetCredentialCancellationException) {
            onUnauthenticated()
        } catch (e: Exception) {
            Log.w("Auth", "Auto sign-in skipped: ${e.message}")
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    preferredHandle: String = "",
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = context.getString(R.string.default_web_client_id)
    val activityContext = context.findActivity() ?: context

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val primaryRequest = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activityContext, primaryRequest)
            exchangeGoogleCredentialWithFirebase(
                context = context,
                credential = result.credential,
                preferredHandle = preferredHandle,
                onAuthSuccess = onAuthSuccess,
                onAuthError = onAuthError
            )
        } catch (e: GetCredentialCancellationException) {
            Log.i("Auth", "User cancelled Google Sign-In")
            onAuthCancelled()
        } catch (primaryErr: GetCredentialException) {
            Log.w("Auth", "GetSignInWithGoogleOption failed (${primaryErr.message}), trying GetGoogleIdOption fallback")
            try {
                val fallbackGoogleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackGoogleIdOption)
                    .build()
                val fallbackResult = credentialManager.getCredential(activityContext, fallbackRequest)
                exchangeGoogleCredentialWithFirebase(
                    context = context,
                    credential = fallbackResult.credential,
                    preferredHandle = preferredHandle,
                        onAuthSuccess = onAuthSuccess,
                    onAuthError = onAuthError
                )
            } catch (cancelErr: GetCredentialCancellationException) {
                onAuthCancelled()
            } catch (fallbackErr: Exception) {
                Log.e("Auth", "Google Sign-In failed", fallbackErr)
                onAuthError("Google ile giriş yapılamadı: ${fallbackErr.localizedMessage ?: primaryErr.localizedMessage ?: "Bilinmeyen hata"}")
            }
        } catch (e: Exception) {
            Log.e("Auth", "Unexpected error during Google Sign-In", e)
            onAuthError("Giriş hatası: ${e.localizedMessage ?: "Lütfen internet bağlantınızı kontrol edin."}")
        }
    }
}

fun signOutZeroChat(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    context: Context? = null,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                context?.let { clearPreferredHandle(it) }
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var customHandleInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cyber_hero_1794168170099),
                    contentDescription = "ZeroChat Siber Güvenlik Kalkanı",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBlack.copy(alpha = 0.25f),
                                    ObsidianBlack.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = NeonCyberGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ZERO-KNOWLEDGE PROTOCOL v3.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyberGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ZeroChat",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.tagline_zero_knowledge),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.5.dp, NeonCyberGreen)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔐 Güvenli Oturum Aç",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyberGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Google hesabınızla giriş yapın. Şifreleme anahtarlarınız bu cihazda oluşturulur ve cihazdan çıkmaz; mesajlarınızı yalnızca sohbet ettiğiniz kişiler okuyabilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customHandleInput,
                        onValueChange = { customHandleInput = it },
                        placeholder = {
                            Text(
                                text = "İsteğe bağlı @kullanici_adi (örn: siber_kurt)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = null,
                                tint = CyberCyan
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_handle_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                preferredHandle = customHandleInput,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isLoading = false
                                }
                            )
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyberGreen,
                            contentColor = Color(0xFF002110)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF002110),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Google & Firestore Bağlanıyor...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.sign_in_with_google),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            FeatureSpecCard(
                icon = Icons.Default.Lock,
                title = "Gerçek AES-256-GCM Uçtan Uca Şifreleme",
                description = "Her mesaj cihazınızda şifrelenir; şifreli zarfları (Ciphertext & IV) istediğiniz an denetleyebilirsiniz."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureSpecCard(
                icon = Icons.Default.Key,
                title = "@kullanici_adi ile Küresel P2P Keşif",
                description = "Telefon numarası paylaşmadan kullanıcı adı veya canlı kamera QR kodu ile eşleşin ve anında şifreli mesajlaşın."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureSpecCard(
                icon = Icons.Default.VisibilityOff,
                title = "Biyometrik Kasa ve Süreli Mesajlar",
                description = "Gerçek parmak izi sensörü ile korunan gizli kasa ve okunduktan sonra kendini imha eden mesajlar."
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FeatureSpecCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NeonCyberGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

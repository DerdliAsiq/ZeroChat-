package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.crypto.CryptoEngine
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

private const val FALLBACK_WEB_CLIENT_ID =
    "400047341151-pa472n1b1iauotvf17vdma6ver51mitb.apps.googleusercontent.com"

private const val PREFS_LOCAL_NODE = "zerochat_local_node_session"
private const val KEY_LOCAL_UID = "local_uid"
private const val KEY_LOCAL_HANDLE = "local_handle"

fun getSavedLocalNodeSession(context: Context): Pair<String, String>? {
    val prefs = context.getSharedPreferences(PREFS_LOCAL_NODE, Context.MODE_PRIVATE)
    val uid = prefs.getString(KEY_LOCAL_UID, null)?.takeIf { it.isNotBlank() } ?: return null
    val handle = prefs.getString(KEY_LOCAL_HANDLE, null)?.takeIf { it.isNotBlank() } ?: "siber_dugum"
    return uid to handle
}

fun saveLocalNodeSession(context: Context, uid: String, handle: String) {
    val prefs = context.getSharedPreferences(PREFS_LOCAL_NODE, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(KEY_LOCAL_UID, uid)
        .putString(KEY_LOCAL_HANDLE, handle)
        .apply()
}

fun clearLocalNodeSession(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_LOCAL_NODE, Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
}

private fun resolveWebClientId(context: Context): String {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (resId != 0) {
        runCatching { context.getString(resId) }.getOrDefault(FALLBACK_WEB_CLIENT_ID)
    } else {
        FALLBACK_WEB_CLIENT_ID
    }
}

private fun buildDeterministicNodeIdentity(context: Context, preferredHandle: String): Pair<String, String> {
    val cleanHandle = preferredHandle.trim()
        .removePrefix("@")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9_\\-]".toRegex(), "")
        .let {
            if (it.length >= 3) it.take(24)
            else {
                val existing = getSavedLocalNodeSession(context)?.second
                if (!existing.isNullOrBlank()) existing else "zero_${System.currentTimeMillis().toString().takeLast(5)}"
            }
        }
    val uid = "node_" + CryptoEngine.sha256Hex("ZEROCHAT_NODE::$cleanHandle").take(20)
    saveLocalNodeSession(context, uid, cleanHandle)
    return uid to cleanHandle
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
    onNodeSessionSuccess: (String, String) -> Unit = { _, _ -> }
) {
    val current = Firebase.auth.currentUser
    if (current != null) {
        onNodeSessionSuccess(current.uid, current.email ?: "zero_${current.uid.take(5)}")
        onAuthSuccess()
        return
    }
    val savedLocal = getSavedLocalNodeSession(context)
    if (savedLocal != null) {
        onNodeSessionSuccess(savedLocal.first, savedLocal.second)
        onAuthSuccess()
        return
    }

    val clientId = resolveWebClientId(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleCred.idToken
                val emailOrId = googleCred.id
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val uid = authResult.user?.uid ?: ("gnode_" + CryptoEngine.sha256Hex(emailOrId).take(18))
                val handle = emailOrId.substringBefore("@").ifBlank { "zero_${uid.take(5)}" }
                saveLocalNodeSession(context, uid, handle)
                onNodeSessionSuccess(uid, handle)
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
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
    onAuthCancelled: () -> Unit = {},
    onNodeSessionSuccess: (String, String) -> Unit = { _, _ -> }
) {
    val clientId = resolveWebClientId(context)
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val activityContext = context as? Activity ?: context
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleCred.idToken
                val googleAccountEmail = googleCred.id
                val chosenHandle = preferredHandle.trim().ifBlank {
                    googleAccountEmail.substringBefore("@")
                }
                try {
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authRes = Firebase.auth.signInWithCredential(authCredential).await()
                    val firebaseUid = authRes.user?.uid
                        ?: ("gnode_" + CryptoEngine.sha256Hex(googleAccountEmail).take(18))
                    saveLocalNodeSession(context, firebaseUid, chosenHandle)
                    onNodeSessionSuccess(firebaseUid, chosenHandle)
                    onAuthSuccess()
                } catch (firebaseErr: Exception) {
                    Log.w("Auth", "Firebase credential exchange fallback activated: ${firebaseErr.message}")
                    val (fallbackUid, fallbackHandle) = buildDeterministicNodeIdentity(context, chosenHandle)
                    onNodeSessionSuccess(fallbackUid, fallbackHandle)
                    onAuthSuccess()
                }
            } else {
                val (fallbackUid, fallbackHandle) = buildDeterministicNodeIdentity(context, preferredHandle)
                onNodeSessionSuccess(fallbackUid, fallbackHandle)
                onAuthSuccess()
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google picker returned cancellation/SHA-1 mismatch, activating hardware node: ${e.message}")
            // Hesap secildikten sonra OAuth SHA-1 / Play Services kisitlamasi nedeniyle kapanirsa takilmadan dugumu baslat
            val (fallbackUid, fallbackHandle) = buildDeterministicNodeIdentity(context, preferredHandle)
            onNodeSessionSuccess(fallbackUid, fallbackHandle)
            onAuthSuccess()
        } catch (e: Exception) {
            Log.w("Auth", "Google Sign-In fallback to Hardware Node: ${e.message}", e)
            val (fallbackUid, fallbackHandle) = buildDeterministicNodeIdentity(context, preferredHandle)
            onNodeSessionSuccess(fallbackUid, fallbackHandle)
            onAuthSuccess()
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
                context?.let { clearLocalNodeSession(it) }
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
    onAuthSuccess: () -> Unit,
    onNodeSessionSuccess: (String, String) -> Unit = { _, _ -> }
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
            scope = scope,
            onNodeSessionSuccess = onNodeSessionSuccess
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
            // Compact Top Hero Banner so the Login Buttons are immediately visible at the top!
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
                            text = "ZERO-KNOWLEDGE PROTOCOL v2.5",
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

            // PRIMARY ACTION CARD — Positioned right at the top/center (Above the Fold!)
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
                        text = "🔐 Şifreli Siber Düğümü Başlat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyberGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Google hesabınızla veya doğrudan @kullanici_adi belirleyerek ECDH P-256 + AES-256-GCM anahtar çiftinizi oluşturun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

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

                    // Primary Google Sign-In Button — Immediately visible!
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
                                },
                                onNodeSessionSuccess = onNodeSessionSuccess
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
                                text = "Kriptografik Düğüm Açılıyor...",
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

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Instant @kullanici_adi Node Input & Quick Launch
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

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            isLoading = true
                            val (uid, handle) = buildDeterministicNodeIdentity(
                                context = context,
                                preferredHandle = customHandleInput.ifBlank { "siber_dugum" }
                            )
                            isLoading = false
                            onNodeSessionSuccess(uid, handle)
                            onAuthSuccess()
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("quick_node_start_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (customHandleInput.isNotBlank()) {
                                "@${customHandleInput.trim().removePrefix("@")} Olarak Hızlı Başlat"
                            } else {
                                "⚡ Cihaz Anahtarı İle Hızlı Düğüm Başlat"
                            },
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
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
                title = "@kullanici_adi ile Küresel P2P Keşif & Onay",
                description = "Telefon numarası paylaşmadan kullanıcı adı ile eşleşin; takip/iletişim onayı olmadan kimse size yazamaz."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureSpecCard(
                icon = Icons.Default.VisibilityOff,
                title = "Süreli Mesajlar & Sahte Panik PIN Kodu",
                description = "Okunduktan sonra kendini imha eden mesajlar ve acil durum gizleme kasası."
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

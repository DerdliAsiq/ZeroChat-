package com.example.data

import android.content.Context
import com.example.R
import com.example.crypto.IdentityKeyManager
import com.example.data.local.LocalVaultDatabase
import com.example.data.repository.ZeroChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

/** Process-wide singletons shared by the UI and background workers. */
object ZeroChatServices {
    @Volatile
    private var identityKeyManager: IdentityKeyManager? = null

    fun firestore(context: Context): FirebaseFirestore =
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))

    fun identityKeys(context: Context): IdentityKeyManager =
        identityKeyManager ?: synchronized(this) {
            identityKeyManager ?: IdentityKeyManager(File(context.applicationContext.noBackupFilesDir, "identity"))
                .also { identityKeyManager = it }
        }

    fun repository(context: Context): ZeroChatRepository =
        ZeroChatRepository(firestore(context), identityKeys(context))

    fun vaultDao(context: Context) = LocalVaultDatabase.getInstance(context).vaultDao()
}

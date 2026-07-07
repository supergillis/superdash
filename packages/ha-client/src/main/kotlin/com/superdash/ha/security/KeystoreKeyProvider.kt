package com.superdash.ha.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/** Wraps Tink's AndroidKeysetManager. The keyset is stored in SharedPreferences
 *  under [keysetName]; the keyset itself is encrypted with a master key in
 *  AndroidKeystore under [masterKeyAlias]. AES-256-GCM. */
class KeystoreKeyProvider(
    private val context: Context,
    private val keysetName: String = "superdash_token_keyset",
    private val masterKeyAlias: String = "android-keystore://superdash_master_key",
    private val sharedPrefName: String = "superdash_token_keyset_pref",
) {
    fun getOrCreateAead(): Aead {
        AeadConfig.register()
        val handle: KeysetHandle =
            AndroidKeysetManager
                .Builder()
                .withSharedPref(context, keysetName, sharedPrefName)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(masterKeyAlias)
                .build()
                .keysetHandle
        return handle.getPrimitive(Aead::class.java)
    }
}

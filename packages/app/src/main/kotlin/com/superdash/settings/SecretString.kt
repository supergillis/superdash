package com.superdash.settings

/** Encrypts and decrypts a stored string value at rest. */
internal interface SecretString {
    fun conceal(plain: String): String

    fun reveal(stored: String): String

    /** No-op passthrough. Used in tests and where encryption is unavailable. */
    object Identity : SecretString {
        override fun conceal(plain: String): String = plain

        override fun reveal(stored: String): String = stored
    }
}

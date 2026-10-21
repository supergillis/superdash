package com.superdash.settings

private const val CLEARTEXT_SCHEME_PREFIX = "http://"

fun isUnencryptedUrl(url: String?): Boolean =
    url.orEmpty().trim().startsWith(CLEARTEXT_SCHEME_PREFIX, ignoreCase = true)

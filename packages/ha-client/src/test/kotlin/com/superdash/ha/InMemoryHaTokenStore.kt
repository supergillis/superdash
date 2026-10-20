package com.superdash.ha

class InMemoryHaTokenStore(
    private var tokens: HaTokens? =
        HaTokens(accessToken = "access", refreshToken = "refresh", expiresAtEpochMs = Long.MAX_VALUE / 2),
) : HaTokenStoreLike {
    var lastSaved: HaTokens? = null
        private set

    override suspend fun load(): HaTokens? = tokens

    override suspend fun save(tokens: HaTokens) {
        this.tokens = tokens
        lastSaved = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}

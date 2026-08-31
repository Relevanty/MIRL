package com.personal.sleepalarm.domain.externalcontext

import java.time.Instant

/** Reserved integration boundary. Public trend/news feeds are deliberately disabled for now. */
enum class PublicSignalSource {
    GDELT,
    GOOGLE_TRENDS
}

sealed interface PublicSignalContextResult {
    data class Disabled(val source: PublicSignalSource) : PublicSignalContextResult
}

interface PublicSignalContextProvider {
    val source: PublicSignalSource
    suspend fun getContext(now: Instant): PublicSignalContextResult
}

class DisabledPublicSignalContextProvider(
    override val source: PublicSignalSource
) : PublicSignalContextProvider {
    override suspend fun getContext(now: Instant): PublicSignalContextResult =
        PublicSignalContextResult.Disabled(source)
}

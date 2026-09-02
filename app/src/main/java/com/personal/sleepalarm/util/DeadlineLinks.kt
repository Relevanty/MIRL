package com.personal.sleepalarm.util

import java.net.IDN
import java.net.URI
import java.util.Locale
import org.json.JSONArray

/** Shared validation for editing, restoring and opening deadline links. */
object DeadlineLinks {
    private val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val hostAndPortPattern = Regex(
        "^(?:localhost|(?:[^:/?#.]+\\.)+[^:/?#.]+):[0-9]+(?:[/?#]|$)",
        RegexOption.IGNORE_CASE
    )
    private val hostLabelPattern = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    /**
     * Only browser-safe HTTP(S) URLs are accepted; a bare domain gets HTTPS.
     * Encoded paths and query values are preserved instead of being encoded twice.
     * Credentials, whitespace and ambiguous backslashes are deliberately rejected.
     */
    fun normalize(input: String): String? {
        val value = input.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) {
            return null
        }
        val hasHttpScheme = value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
        if (!hasHttpScheme && schemePattern.containsMatchIn(value) &&
            !hostAndPortPattern.containsMatchIn(value)
        ) return null

        val candidate = when {
            hasHttpScheme -> value
            value.startsWith("//") -> "https:$value"
            else -> "https://$value"
        }
        return runCatching {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            if (scheme != "http" && scheme != "https") return null
            val authority = uri.rawAuthority ?: return null
            if (authority.contains('@')) return null
            val normalizedAuthority = normalizeAuthority(authority, uri) ?: return null
            val normalized = buildString {
                append(scheme).append("://").append(normalizedAuthority)
                append(uri.rawPath.orEmpty())
                uri.rawQuery?.let { append('?').append(it) }
                uri.rawFragment?.let { append('#').append(it) }
            }
            URI(normalized).toASCIIString()
        }.getOrNull()
    }

    fun encode(links: List<String>): String = JSONArray(
        links.mapNotNull(::normalize).distinct()
    ).toString()

    /** Old/corrupt backups and non-string values cannot become actionable links. */
    fun decode(json: String): List<String> = runCatching {
        val values = JSONArray(json)
        (0 until values.length()).mapNotNull { index ->
            (values.opt(index) as? String)?.let(::normalize)
        }.distinct()
    }.getOrDefault(emptyList())

    private fun normalizeAuthority(authority: String, uri: URI): String? {
        if (authority.startsWith('[')) {
            // java.net.URI validates the IPv6 literal without resolving it over the network.
            val host = uri.host ?: return null
            if (!host.startsWith('[') || '%' in host) return null
            val suffix = authority.substringAfter(']', missingDelimiterValue = "invalid")
            if (suffix.isNotEmpty() && (!suffix.startsWith(':') || !validPort(suffix.drop(1)))) {
                return null
            }
            return host.lowercase(Locale.ROOT) + suffix
        }

        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', missingDelimiterValue = "")
        if (':' in authority && !validPort(port)) return null
        val asciiHost = runCatching {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        }.getOrNull() ?: return null
        val domain = asciiHost.removeSuffix(".")
        if (domain.isEmpty() || domain.length > 253) return null
        val labels = domain.split('.')
        if (labels.any { !hostLabelPattern.matches(it) }) return null
        if (labels.all { label -> label.all(Char::isDigit) }) {
            if (labels.size != 4 || labels.any { (it.toIntOrNull() ?: 256) !in 0..255 }) return null
        } else if (labels.size < 2 && domain != "localhost") {
            return null
        }
        return asciiHost + if (port.isEmpty()) "" else ":$port"
    }

    private fun validPort(value: String): Boolean = value.isNotEmpty() &&
        value.all(Char::isDigit) && (value.toIntOrNull() ?: 0) in 1..65535
}

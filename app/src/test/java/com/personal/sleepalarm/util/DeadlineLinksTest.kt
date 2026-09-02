package com.personal.sleepalarm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeadlineLinksTest {
    @Test
    fun bareDomainsGetHttpsAndCanonicalHost() {
        assertEquals("https://example.com", DeadlineLinks.normalize("  Example.COM  "))
        assertEquals("https://www.example.com/path", DeadlineLinks.normalize("www.example.com/path"))
        assertEquals("https://example.com:8443/path", DeadlineLinks.normalize("example.com:8443/path"))
        assertEquals("https://example.com/a", DeadlineLinks.normalize("//example.com/a"))
        assertEquals("https://localhost:8080", DeadlineLinks.normalize("localhost:8080"))
    }

    @Test
    fun existingHttpAndEncodedComponentsArePreserved() {
        assertEquals(
            "http://example.com/a%2Fb?q=one%20two&next=https%3A%2F%2Fexample.org#part%202",
            DeadlineLinks.normalize("HTTP://Example.COM/a%2Fb?q=one%20two&next=https%3A%2F%2Fexample.org#part%202")
        )
    }

    @Test
    fun internationalDomainsAndPathsCanBeOpenedByABrowser() {
        assertEquals(
            "https://xn--e1afmkfd.xn--p1ai/%D0%B4%D0%B0%D1%82%D0%B0",
            DeadlineLinks.normalize("пример.рф/дата")
        )
        assertEquals("https://127.0.0.1:8080/", DeadlineLinks.normalize("127.0.0.1:8080/"))
        assertEquals("https://[::1]:8080/", DeadlineLinks.normalize("https://[::1]:8080/"))
    }

    @Test
    fun nonWebSchemesAndAmbiguousInputsAreRejected() {
        listOf(
            "", "just a name", "example", "javascript:alert(1)", "javascript:123",
            "file:///etc/passwd", "content://provider/1", "intent://example.com",
            "data:text/html,hi", "mailto:person@example.com", "ftp://example.com",
            "https:example.com", "http:///example.com", "https://", "https://user@example.com",
            "https://example.com\\@evil.com", "https://example.com/white space",
            "https://example.com/\nredirect", "https://example.com/%zz",
            "https://-bad.example", "https://bad_.example", "https://a..example",
            "https://999.0.0.1", "https://example.com:0", "https://example.com:65536",
            "https://example.com:", "https://example.com:notaport"
        ).forEach { value -> assertNull("Should reject $value", DeadlineLinks.normalize(value)) }
    }

    @Test
    fun jsonRoundTripPreservesLinkOrderAndRemovesDuplicates() {
        val encoded = DeadlineLinks.encode(
            listOf("example.com/a", "https://Example.com/a", "http://example.org/b", "", "javascript:alert(1)")
        )
        assertEquals(listOf("https://example.com/a", "http://example.org/b"), DeadlineLinks.decode(encoded))
    }

    @Test
    fun invalidJsonAndNonStringsCannotBecomeLinks() {
        listOf("", "{bad}", "null", "{}", "\"example.com\"").forEach {
            assertEquals(emptyList<String>(), DeadlineLinks.decode(it))
        }
        assertEquals(
            listOf("https://example.com"),
            DeadlineLinks.decode("[null,7,{},\"javascript:alert(1)\",\"example.com\"]")
        )
    }
}

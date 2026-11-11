package ru.homework.shortlinks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {
    @Test
    void acceptsHttpsUrlWithQuery() {
        assertEquals("https://example.org/path?a=1&b=2",
                UrlValidator.validate(" https://example.org/path?a=1&b=2 "));
    }

    @Test
    void acceptsHttpUrl() {
        assertEquals("http://example.org/", UrlValidator.validate("http://example.org/"));
    }

    @Test
    void rejectsFileProtocol() {
        assertThrows(IllegalArgumentException.class, () -> UrlValidator.validate("file:///etc/passwd"));
    }

    @Test
    void rejectsMissingHost() {
        assertThrows(IllegalArgumentException.class, () -> UrlValidator.validate("https:///path"));
    }

    @Test
    void rejectsControlCharacters() {
        assertThrows(IllegalArgumentException.class, () -> UrlValidator.validate("https://example.com/\nabc"));
    }

    @Test
    void rejectsWhitespaceInUrl() {
        assertThrows(IllegalArgumentException.class, () -> UrlValidator.validate("https://example.com/a b"));
    }

    @Test
    void rejectsTooLongUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("https://example.com/" + "a".repeat(2048)));
    }
}

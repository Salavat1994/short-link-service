package ru.homework.shortlinks.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Неизменяемая модель сокращенной ссылки. */
public record ShortLink(
        String code,
        UUID ownerId,
        String originalUrl,
        int maxClicks,
        int clicks,
        Instant createdAt,
        Instant expiresAt) {

    public ShortLink {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(originalUrl, "originalUrl");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (maxClicks < 1 || clicks < 0 || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Некорректное состояние ссылки.");
        }
    }

    public ShortLink withClicks(int newClicks) {
        return new ShortLink(code, ownerId, originalUrl, maxClicks, newClicks, createdAt, expiresAt);
    }

    public ShortLink withLimit(int newLimit) {
        return new ShortLink(code, ownerId, originalUrl, newLimit, clicks, createdAt, expiresAt);
    }

    public ShortLink withUrl(String newUrl) {
        return new ShortLink(code, ownerId, newUrl, maxClicks, clicks, createdAt, expiresAt);
    }
}

package ru.homework.shortlinks.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Уведомление сохраняется, даже если ссылка уже автоматически удалена. */
public record Notice(UUID ownerId, String code, NoticeType type, Instant occurredAt) {
    public Notice {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

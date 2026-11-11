package ru.homework.shortlinks.core;

/** Ожидаемая бизнес-ошибка, которую CLI выводит понятным текстом. */
public final class LinkException extends RuntimeException {
    public LinkException(String message) {
        super(message);
    }
}

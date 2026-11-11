package ru.homework.shortlinks.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Разрешены только абсолютные HTTP(S)-адреса без userinfo и управляющих символов. */
public final class UrlValidator {
    private static final int MAX_LENGTH = 2048;

    private UrlValidator() {
    }

    public static String validate(String value) {
        if (value == null) {
            throw new IllegalArgumentException("URL не задан.");
        }
        String url = value.trim();
        if (url.isEmpty() || url.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Длина URL должна быть от 1 до 2048 символов.");
        }
        for (int i = 0; i < url.length(); i++) {
            if (Character.isISOControl(url.charAt(i))) {
                throw new IllegalArgumentException("URL содержит управляющие символы.");
            }
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!"https".equals(scheme.toLowerCase(Locale.ROOT))
                    && !"http".equals(scheme.toLowerCase(Locale.ROOT)))) {
                throw new IllegalArgumentException("Разрешены только URL с http:// или https://.");
            }
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Укажите абсолютный URL с корректным доменом без userinfo.");
            }
            if (uri.getPort() > 65535 || uri.getPort() < -1) {
                throw new IllegalArgumentException("Некорректный порт URL.");
            }
            return url;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("URL имеет неверный синтаксис.", exception);
        }
    }
}

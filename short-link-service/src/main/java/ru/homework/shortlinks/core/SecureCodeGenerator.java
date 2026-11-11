package ru.homework.shortlinks.core;

import java.security.SecureRandom;

/** Восемь символов из набора Base62, без зависимости от исходного URL. */
public final class SecureCodeGenerator implements CodeGenerator {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String nextCode() {
        StringBuilder result = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }
}

package ru.homework.shortlinks.infra;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import ru.homework.shortlinks.ui.BrowserLauncher;

/** Открывает исходный HTTP(S)-адрес через системный браузер. */
public final class DesktopBrowser implements BrowserLauncher {
    @Override
    public void open(String originalUrl) throws IOException {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IOException("Открытие браузера не поддерживается в этой среде.");
            }
            Desktop.getDesktop().browse(URI.create(originalUrl));
        } catch (UnsupportedOperationException | SecurityException exception) {
            throw new IOException("Не удалось открыть браузер в этой среде.", exception);
        }
    }
}

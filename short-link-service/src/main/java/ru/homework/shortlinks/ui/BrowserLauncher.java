package ru.homework.shortlinks.ui;

import java.io.IOException;

@FunctionalInterface
public interface BrowserLauncher {
    void open(String originalUrl) throws IOException;
}

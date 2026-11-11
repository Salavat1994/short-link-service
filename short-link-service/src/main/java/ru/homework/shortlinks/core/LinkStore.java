package ru.homework.shortlinks.core;

import java.io.IOException;

/** Граница между бизнес-логикой и физическим хранением. */
public interface LinkStore {
    State load() throws IOException;

    void save(State state) throws IOException;
}

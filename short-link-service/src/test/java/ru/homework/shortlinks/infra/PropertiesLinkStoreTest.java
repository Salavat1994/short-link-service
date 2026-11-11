package ru.homework.shortlinks.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.homework.shortlinks.config.AppConfig;
import ru.homework.shortlinks.core.LinkService;
import ru.homework.shortlinks.core.SecureCodeGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesLinkStoreTest {
    @TempDir
    Path directory;

    @Test
    void persistsUserLinksAndClicksAfterRestart() throws IOException {
        Path path = directory.resolve("subdir/links.properties");
        AppConfig config = new AppConfig(3600, 3, 10, 5, "short.local/", path);
        LinkService first = new LinkService(new PropertiesLinkStore(path), config,
                Clock.systemUTC(), new SecureCodeGenerator());
        var created = first.create("https://example.org/", 2);
        first.follow(created.link().code());
        UUID owner = created.ownerId();

        LinkService restarted = new LinkService(new PropertiesLinkStore(path), config,
                Clock.systemUTC(), new SecureCodeGenerator());
        assertEquals(owner, restarted.currentUser());
        assertEquals(1, restarted.details(created.link().code()).clicks());
        assertEquals(1, restarted.myLinks().size());
    }

    @Test
    void storesAndRestoresNotifications() throws IOException {
        Path path = directory.resolve("links.properties");
        AppConfig config = new AppConfig(3600, 1, 10, 5, "short.local/", path);
        LinkService first = new LinkService(new PropertiesLinkStore(path), config,
                Clock.systemUTC(), new SecureCodeGenerator());
        first.follow(first.create("https://example.org", 1).link().code());

        LinkService second = new LinkService(new PropertiesLinkStore(path), config,
                Clock.systemUTC(), new SecureCodeGenerator());
        assertEquals(1, second.takeNotices().size());
        assertTrue(second.takeNotices().isEmpty());
    }

    @Test
    void corruptedFileFailsInsteadOfSilentlyResetting() throws IOException {
        Path path = directory.resolve("broken.properties");
        Files.writeString(path, "not=a=valid-state\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new PropertiesLinkStore(path).load());
        assertTrue(Files.readString(path).contains("not=a=valid-state"));
    }
}

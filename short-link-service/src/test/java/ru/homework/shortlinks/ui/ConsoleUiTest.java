package ru.homework.shortlinks.ui;

import org.junit.jupiter.api.Test;
import ru.homework.shortlinks.config.AppConfig;
import ru.homework.shortlinks.core.LinkService;
import ru.homework.shortlinks.core.State;
import ru.homework.shortlinks.core.LinkStore;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleUiTest {
    @Test
    void showsHelpAndHandlesUnknownCommand() throws IOException {
        String output = run("help\nunknown\nexit\n");
        assertTrue(output.contains("create <URL>"));
        assertTrue(output.contains("Неизвестная команда"));
    }

    @Test
    void cliCreatesAndShowsLink() throws IOException {
        String output = run("create https://example.com 2\nlist\nexit\n");
        assertTrue(output.contains("Создан UUID пользователя:"));
        assertTrue(output.contains("short.local/00000001"));
        assertTrue(output.contains("0/2"));
    }

    @Test
    void cliOpensBrowserAndBlocksWhenLimitReached() throws IOException {
        AtomicInteger launches = new AtomicInteger();
        LinkStore memory = new LinkStore() {
            State state = State.empty();
            public State load() { return state; }
            public void save(State value) { state = value; }
        };
        AppConfig config = new AppConfig(3600, 2, 5, 5, "short.local/", Path.of("unused"));
        LinkService service = new LinkService(memory, config, Clock.systemUTC(), () -> "abcdefgh");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String input = "create https://example.com 1\ngo abcdefgh\ngo abcdefgh\nnotifications\nexit\n";
        ConsoleUi ui = new ConsoleUi(service, url -> launches.incrementAndGet(),
                new BufferedReader(new StringReader(input)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        ui.run();
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(1, launches.get());
        assertTrue(output.contains("лимит достигнут"));
        assertTrue(output.contains("Лимит переходов исчерпан"));
        assertTrue(output.contains("исчерпан лимит переходов"));
    }

    @Test
    void pastedShortAddressIsAcceptedWithoutGo() throws IOException {
        String output = run("create https://example.com 2\nshort.local/00000001\nexit\n");
        assertTrue(output.contains("Открыт исходный адрес в браузере. Переходов: 1/2"));
    }

    private static String run(String script) throws IOException {
        LinkStore memory = new LinkStore() {
            State state = State.empty();
            public State load() { return state; }
            public void save(State value) { state = value; }
        };
        AppConfig config = new AppConfig(3600, 2, 5, 5, "short.local/", Path.of("unused"));
        AtomicInteger codes = new AtomicInteger();
        LinkService service = new LinkService(memory, config, Clock.systemUTC(),
                () -> String.format("%08d", codes.incrementAndGet()));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ConsoleUi(service, url -> {}, new BufferedReader(new StringReader(script)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8)).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }
}

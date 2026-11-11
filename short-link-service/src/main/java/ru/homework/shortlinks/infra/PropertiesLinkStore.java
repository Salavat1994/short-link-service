package ru.homework.shortlinks.infra;

import ru.homework.shortlinks.core.LinkStore;
import ru.homework.shortlinks.core.Notice;
import ru.homework.shortlinks.core.NoticeType;
import ru.homework.shortlinks.core.ShortLink;
import ru.homework.shortlinks.core.State;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Хранит цельный снимок как .properties, атомарно заменяя файл через temp + move. */
public final class PropertiesLinkStore implements LinkStore {
    private static final String VERSION = "1";
    private final Path path;

    public PropertiesLinkStore(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    @Override
    public State load() throws IOException {
        if (!Files.exists(path)) {
            return State.empty();
        }
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(reader);
        }
        try {
            if (!VERSION.equals(required(p, "format.version"))) {
                throw new IllegalArgumentException("Неподдерживаемая версия файла данных.");
            }
            Set<UUID> users = new HashSet<>();
            for (int i = 0; i < count(p, "users.count"); i++) {
                users.add(UUID.fromString(required(p, "user." + i)));
            }
            Map<String, ShortLink> links = new HashMap<>();
            for (int i = 0; i < count(p, "links.count"); i++) {
                String base = "link." + i + ".";
                ShortLink link = new ShortLink(
                        required(p, base + "code"),
                        UUID.fromString(required(p, base + "owner")),
                        required(p, base + "url"),
                        Integer.parseInt(required(p, base + "limit")),
                        Integer.parseInt(required(p, base + "clicks")),
                        Instant.parse(required(p, base + "created")),
                        Instant.parse(required(p, base + "expires")));
                if (!users.contains(link.ownerId()) || links.putIfAbsent(link.code(), link) != null) {
                    throw new IllegalArgumentException("Повторный код или неизвестный владелец в файле данных.");
                }
            }
            List<Notice> notices = new ArrayList<>();
            for (int i = 0; i < count(p, "notices.count"); i++) {
                String base = "notice." + i + ".";
                Notice notice = new Notice(
                        UUID.fromString(required(p, base + "owner")),
                        required(p, base + "code"),
                        NoticeType.valueOf(required(p, base + "type")),
                        Instant.parse(required(p, base + "at")));
                if (!users.contains(notice.ownerId())) {
                    throw new IllegalArgumentException("Неизвестный адресат уведомления.");
                }
                notices.add(notice);
            }
            String selectedUser = required(p, "session.user");
            UUID current = selectedUser.isEmpty() ? null : UUID.fromString(selectedUser);
            return new State(links, users, current, notices);
        } catch (RuntimeException exception) {
            throw new IOException("Файл данных повреждён или имеет неверный формат; данные не перезаписаны.", exception);
        }
    }

    @Override
    public void save(State state) throws IOException {
        Properties p = new Properties();
        p.setProperty("format.version", VERSION);
        p.setProperty("session.user", state.currentUser() == null ? "" : state.currentUser().toString());

        List<UUID> users = state.users().stream().sorted().toList();
        p.setProperty("users.count", Integer.toString(users.size()));
        for (int i = 0; i < users.size(); i++) {
            p.setProperty("user." + i, users.get(i).toString());
        }

        List<ShortLink> links = state.links().values().stream()
                .sorted(Comparator.comparing(ShortLink::code)).toList();
        p.setProperty("links.count", Integer.toString(links.size()));
        for (int i = 0; i < links.size(); i++) {
            String base = "link." + i + ".";
            ShortLink link = links.get(i);
            p.setProperty(base + "code", link.code());
            p.setProperty(base + "owner", link.ownerId().toString());
            p.setProperty(base + "url", link.originalUrl());
            p.setProperty(base + "limit", Integer.toString(link.maxClicks()));
            p.setProperty(base + "clicks", Integer.toString(link.clicks()));
            p.setProperty(base + "created", link.createdAt().toString());
            p.setProperty(base + "expires", link.expiresAt().toString());
        }

        p.setProperty("notices.count", Integer.toString(state.notices().size()));
        for (int i = 0; i < state.notices().size(); i++) {
            String base = "notice." + i + ".";
            Notice notice = state.notices().get(i);
            p.setProperty(base + "owner", notice.ownerId().toString());
            p.setProperty(base + "code", notice.code());
            p.setProperty(base + "type", notice.type().name());
            p.setProperty(base + "at", notice.occurredAt().toString());
        }

        Path parent = path.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "shortlinks-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                p.store(writer, "Short Link Service persistent state");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Отсутствует ключ данных " + key);
        }
        return value;
    }

    private static int count(Properties p, String key) {
        int value = Integer.parseInt(required(p, key));
        if (value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException("Недопустимое количество записей " + key);
        }
        return value;
    }
}

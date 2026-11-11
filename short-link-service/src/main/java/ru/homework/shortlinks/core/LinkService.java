package ru.homework.shortlinks.core;

import ru.homework.shortlinks.config.AppConfig;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Бизнес-операции; синхронизация защищает счётчик от конкурентного превышения лимита. */
public final class LinkService {
    private static final int MAX_GENERATION_ATTEMPTS = 32;

    private final LinkStore store;
    private final AppConfig config;
    private final Clock clock;
    private final CodeGenerator generator;
    private State state;

    public LinkService(LinkStore store, AppConfig config, Clock clock, CodeGenerator generator)
            throws IOException {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.state = Objects.requireNonNull(store.load(), "state");
        cleanupExpired();
    }

    public record CreatedLink(ShortLink link, UUID ownerId, boolean newUser) {
    }

    public record OpenResult(ShortLink link, boolean lastAllowedClick) {
    }

    public synchronized CreatedLink create(String originalUrl, Integer requestedLimit) throws IOException {
        cleanupExpired();
        String validUrl = UrlValidator.validate(originalUrl);
        int maxClicks = requestedLimit == null ? config.defaultClicks() : requestedLimit;
        validateClicks(maxClicks);

        UUID owner = state.currentUser();
        boolean newUser = owner == null;
        if (newUser) {
            owner = UUID.randomUUID();
        }

        String code = uniqueCode();
        Instant now = clock.instant();
        ShortLink link = new ShortLink(code, owner, validUrl, maxClicks, 0, now,
                now.plusSeconds(config.ttlSeconds()));

        var links = new HashMap<>(state.links());
        links.put(code, link);
        Set<UUID> users = new HashSet<>(state.users());
        users.add(owner);
        commit(new State(links, users, owner, state.notices()));
        return new CreatedLink(link, owner, newUser);
    }

    /** Разрешённый переход учитывается и сохраняется ДО обращения к браузеру. */
    public synchronized OpenResult follow(String shortLinkOrCode) throws IOException {
        String code = codeOf(shortLinkOrCode);
        ShortLink beforeCleanup = state.links().get(code);
        if (beforeCleanup != null && expired(beforeCleanup)) {
            cleanupExpired();
            throw new LinkException("Срок действия короткой ссылки истёк. Создайте новую ссылку.");
        }
        cleanupExpired();
        ShortLink link = state.links().get(code);
        if (link == null) {
            throw new LinkException("Ссылка не найдена: возможно, она удалена или срок действия истёк.");
        }
        if (link.clicks() >= link.maxClicks()) {
            throw new LinkException("Лимит переходов исчерпан. Ссылка недоступна; создайте новую.");
        }
        ShortLink updated = link.withClicks(link.clicks() + 1);
        var links = new HashMap<>(state.links());
        links.put(code, updated);
        var notices = new ArrayList<>(state.notices());
        boolean lastAllowed = updated.clicks() >= updated.maxClicks();
        if (lastAllowed) {
            notices.add(new Notice(link.ownerId(), code, NoticeType.CLICK_LIMIT_REACHED, clock.instant()));
        }
        commit(new State(links, state.users(), state.currentUser(), notices));
        return new OpenResult(updated, lastAllowed);
    }

    public synchronized ShortLink setLimit(String shortLinkOrCode, int limit) throws IOException {
        cleanupExpired();
        validateClicks(limit);
        ShortLink link = ownedLink(shortLinkOrCode);
        ShortLink updated = link.withLimit(limit);
        var links = new HashMap<>(state.links());
        links.put(updated.code(), updated);
        var notices = new ArrayList<>(state.notices());
        if (link.clicks() < link.maxClicks() && updated.clicks() >= updated.maxClicks()) {
            notices.add(new Notice(link.ownerId(), link.code(), NoticeType.CLICK_LIMIT_REACHED, clock.instant()));
        }
        commit(new State(links, state.users(), state.currentUser(), notices));
        return updated;
    }

    public synchronized ShortLink setUrl(String shortLinkOrCode, String originalUrl) throws IOException {
        cleanupExpired();
        ShortLink link = ownedLink(shortLinkOrCode);
        String validUrl = UrlValidator.validate(originalUrl);
        ShortLink updated = link.withUrl(validUrl);
        var links = new HashMap<>(state.links());
        links.put(updated.code(), updated);
        commit(state.withLinks(links));
        return updated;
    }

    public synchronized void delete(String shortLinkOrCode) throws IOException {
        cleanupExpired();
        ShortLink link = ownedLink(shortLinkOrCode);
        var links = new HashMap<>(state.links());
        links.remove(link.code());
        commit(state.withLinks(links));
    }

    public synchronized ShortLink details(String shortLinkOrCode) throws IOException {
        cleanupExpired();
        return ownedLink(shortLinkOrCode);
    }

    public synchronized List<ShortLink> myLinks() throws IOException {
        cleanupExpired();
        UUID owner = requireCurrentUser();
        return state.links().values().stream()
                .filter(link -> link.ownerId().equals(owner))
                .sorted(Comparator.comparing(ShortLink::createdAt).reversed()
                        .thenComparing(ShortLink::code))
                .toList();
    }

    public synchronized UUID currentUser() {
        return state.currentUser();
    }

    /** Новая сессия: UUID создастся только при первом create. */
    public synchronized void newUserSession() throws IOException {
        commit(state.withSession(null));
    }

    /** UUID выступает токеном владения: отдельной авторизации по ТЗ нет. */
    public synchronized void useExistingUser(UUID userId) throws IOException {
        if (userId == null || !state.users().contains(userId)) {
            throw new LinkException("Неизвестный UUID. Укажите UUID ранее созданного пользователя.");
        }
        commit(state.withSession(userId));
    }

    public synchronized List<Notice> takeNotices() throws IOException {
        cleanupExpired();
        UUID owner = requireCurrentUser();
        List<Notice> result = state.notices().stream()
                .filter(notice -> notice.ownerId().equals(owner))
                .toList();
        if (!result.isEmpty()) {
            List<Notice> remaining = state.notices().stream()
                    .filter(notice -> !notice.ownerId().equals(owner))
                    .toList();
            commit(state.withNotices(remaining));
        }
        return result;
    }

    /** Вызывается при старте, перед действиями и периодически фоновым планировщиком. */
    public synchronized int cleanupExpired() throws IOException {
        Instant now = clock.instant();
        var links = new HashMap<>(state.links());
        var notices = new ArrayList<>(state.notices());
        int removed = 0;
        for (ShortLink link : state.links().values()) {
            if (!now.isBefore(link.expiresAt())) {
                links.remove(link.code());
                notices.add(new Notice(link.ownerId(), link.code(), NoticeType.EXPIRED, now));
                removed++;
            }
        }
        if (removed > 0) {
            commit(new State(links, state.users(), state.currentUser(), notices));
        }
        return removed;
    }

    public String shortAddress(String code) {
        return config.prefix() + code;
    }

    private String uniqueCode() {
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String candidate = generator.nextCode();
            if (candidate != null && candidate.matches("[A-Za-z0-9]{8}")
                    && !state.links().containsKey(candidate)) {
                return candidate;
            }
        }
        throw new LinkException("Не удалось подобрать уникальный короткий код.");
    }

    private ShortLink ownedLink(String value) {
        UUID owner = requireCurrentUser();
        ShortLink link = state.links().get(codeOf(value));
        if (link == null) {
            throw new LinkException("Ссылка не найдена или уже удалена.");
        }
        if (!owner.equals(link.ownerId())) {
            throw new LinkException("Доступ запрещён: редактировать и удалять ссылку может только владелец.");
        }
        return link;
    }

    private UUID requireCurrentUser() {
        if (state.currentUser() == null) {
            throw new LinkException("Пользователь не выбран. Создайте ссылку или введите user-use <UUID>.");
        }
        return state.currentUser();
    }

    private boolean expired(ShortLink link) {
        return !clock.instant().isBefore(link.expiresAt());
    }

    private String codeOf(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Короткий код не задан.");
        }
        String candidate = value.trim();
        if (candidate.startsWith(config.prefix())) {
            candidate = candidate.substring(config.prefix().length());
        }
        if (!candidate.matches("[A-Za-z0-9]{8}")) {
            throw new IllegalArgumentException("Введите код из 8 символов или ссылку " + config.prefix() + "<код>.");
        }
        return candidate;
    }

    private void validateClicks(int value) {
        if (value < 1 || value > config.maxClicks()) {
            throw new IllegalArgumentException("Лимит переходов должен быть от 1 до " + config.maxClicks() + ".");
        }
    }

    private void commit(State nextState) throws IOException {
        store.save(nextState);
        state = nextState;
    }
}

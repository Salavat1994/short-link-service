package ru.homework.shortlinks.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.homework.shortlinks.config.AppConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LinkServiceTest {
    private static final String URL = "https://www.baeldung.com/java-9-http-client";
    private final AppConfig config = new AppConfig(3600, 3, 10, 5,
            "short.local/", Path.of("unused"));
    private final MemoryStore store = new MemoryStore();
    private final MutableClock clock = new MutableClock();
    private final AtomicInteger sequence = new AtomicInteger();
    private LinkService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new LinkService(store, config, clock,
                () -> String.format("%08d", sequence.incrementAndGet()));
    }

    @Test
    void firstCreationGeneratesUserUuid() throws IOException {
        LinkService.CreatedLink created = service.create(URL, 2);
        assertTrue(created.newUser());
        assertEquals(created.ownerId(), service.currentUser());
        assertEquals(2, created.link().maxClicks());
    }

    @Test
    void nextLinkUsesExistingUser() throws IOException {
        UUID user = service.create(URL, 2).ownerId();
        assertFalse(service.create("https://example.org/second", 3).newUser());
        assertEquals(user, service.currentUser());
    }

    @Test
    void differentUsersReceiveDistinctCodesForSameUrl() throws IOException {
        LinkService.CreatedLink first = service.create(URL, 2);
        service.newUserSession();
        LinkService.CreatedLink second = service.create(URL, 2);
        assertNotEquals(first.link().code(), second.link().code());
        assertNotEquals(first.ownerId(), second.ownerId());
    }

    @Test
    void oneUserCanOwnSeveralLinks() throws IOException {
        service.create(URL, 2);
        service.create("https://example.org", 2);
        assertEquals(2, service.myLinks().size());
    }

    @Test
    void usesDefaultClickLimit() throws IOException {
        assertEquals(3, service.create(URL, null).link().maxClicks());
    }

    @Test
    void rejectsInvalidUrlWithoutCreatingAccount() {
        assertThrows(IllegalArgumentException.class, () -> service.create("javascript:alert(1)", 2));
        assertNull(service.currentUser());
    }

    @Test
    void rejectsUrlWithCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("https://admin:secret@example.com/", 2));
    }

    @Test
    void rejectsLimitGreaterThanConfigured() {
        assertThrows(IllegalArgumentException.class, () -> service.create(URL, 11));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.create(URL, 0));
    }

    @Test
    void countsAllowedClicksAndDeniesNextOne() throws IOException {
        String code = service.create(URL, 2).link().code();
        assertFalse(service.follow(code).lastAllowedClick());
        assertTrue(service.follow(code).lastAllowedClick());
        assertThrows(LinkException.class, () -> service.follow(code));
        assertEquals(2, service.details(code).clicks());
    }

    @Test
    void anyoneCanFollowPublicShortCodeWithoutBeingLoggedIn() throws IOException {
        String code = service.create(URL, 2).link().code();
        service.newUserSession();
        assertEquals(URL, service.follow("short.local/" + code).link().originalUrl());
    }

    @Test
    void ownerCanChangeLimitWithoutResettingCount() throws IOException {
        String code = service.create(URL, 2).link().code();
        service.follow(code);
        assertEquals(5, service.setLimit(code, 5).maxClicks());
        assertEquals(1, service.details(code).clicks());
    }

    @Test
    void ownerCanChangeDestination() throws IOException {
        String code = service.create(URL, 2).link().code();
        service.setUrl(code, "https://example.com/new");
        assertEquals("https://example.com/new", service.follow(code).link().originalUrl());
    }

    @Test
    void strangerCannotEditLimit() throws IOException {
        String code = service.create(URL, 2).link().code();
        service.newUserSession();
        service.create("https://example.org", 2);
        assertThrows(LinkException.class, () -> service.setLimit(code, 5));
    }

    @Test
    void strangerCannotEditUrl() throws IOException {
        String code = service.create(URL, 2).link().code();
        service.newUserSession();
        service.create("https://example.org", 2);
        assertThrows(LinkException.class, () -> service.setUrl(code, "https://example.net"));
    }

    @Test
    void strangerCannotDeleteLink() throws IOException {
        String code = service.create(URL, 2).link().code();
        service.newUserSession();
        service.create("https://example.org", 2);
        assertThrows(LinkException.class, () -> service.delete(code));
    }

    @Test
    void ownerCanDeleteLink() throws IOException {
        String code = service.create(URL, 2).link().code();
        service.delete(code);
        assertTrue(service.myLinks().isEmpty());
        assertThrows(LinkException.class, () -> service.follow(code));
    }

    @Test
    void privateListingIsFilteredByOwner() throws IOException {
        UUID firstUser = service.create(URL, 2).ownerId();
        service.newUserSession();
        service.create("https://example.org", 2);
        assertEquals(1, service.myLinks().size());
        service.useExistingUser(firstUser);
        assertEquals(1, service.myLinks().size());
    }

    @Test
    void cleanupRemovesExpiredLinks() throws IOException {
        String code = service.create(URL, 2).link().code();
        clock.advance(Duration.ofHours(1));
        assertEquals(1, service.cleanupExpired());
        assertTrue(service.myLinks().isEmpty());
        assertThrows(LinkException.class, () -> service.follow(code));
    }

    @Test
    void expiresAtExactDeadline() throws IOException {
        String code = service.create(URL, 2).link().code();
        clock.advance(Duration.ofHours(1));
        assertTrue(assertThrows(LinkException.class, () -> service.follow(code))
                .getMessage().contains("истёк"));
    }

    @Test
    void limitNoticeSentExactlyOnce() throws IOException {
        String code = service.create(URL, 1).link().code();
        service.follow(code);
        assertThrows(LinkException.class, () -> service.follow(code));
        List<Notice> notices = service.takeNotices();
        assertEquals(1, notices.size());
        assertEquals(NoticeType.CLICK_LIMIT_REACHED, notices.get(0).type());
        assertTrue(service.takeNotices().isEmpty());
    }

    @Test
    void expiryNotificationSurvivesLinkRemoval() throws IOException {
        String code = service.create(URL, 2).link().code();
        clock.advance(Duration.ofHours(1));
        service.cleanupExpired();
        assertEquals(code, service.takeNotices().get(0).code());
    }

    @Test
    void loweringLimitBelowConsumedClicksBlocksLink() throws IOException {
        String code = service.create(URL, 3).link().code();
        service.follow(code);
        service.follow(code);
        service.setLimit(code, 1);
        assertThrows(LinkException.class, () -> service.follow(code));
        assertEquals(NoticeType.CLICK_LIMIT_REACHED, service.takeNotices().get(0).type());
    }

    @Test
    void raisingReachedLimitAllowsMoreClicks() throws IOException {
        String code = service.create(URL, 1).link().code();
        service.follow(code);
        service.setLimit(code, 2);
        assertTrue(service.follow(code).lastAllowedClick());
        assertEquals(2, service.details(code).clicks());
    }

    @Test
    void noticesAreIsolatedByOwner() throws IOException {
        UUID first = service.create(URL, 1).ownerId();
        service.follow("00000001");
        service.newUserSession();
        service.create("https://example.org", 2);
        assertTrue(service.takeNotices().isEmpty());
        service.useExistingUser(first);
        assertEquals(1, service.takeNotices().size());
    }

    @Test
    void collisionTriggersAnotherGeneration() throws IOException {
        service.create(URL, 2); // 00000001
        LinkService collidingService = new LinkService(store, config, clock, new CodeGenerator() {
            int calls;
            @Override
            public String nextCode() {
                return ++calls == 1 ? "00000001" : "abcdefgh";
            }
        });
        assertEquals("abcdefgh", collidingService.create("https://example.org", 2).link().code());
    }

    @Test
    void invalidUnknownUuidIsRejected() {
        assertThrows(LinkException.class, () -> service.useExistingUser(UUID.randomUUID()));
    }

    @Test
    void concurrentClicksNeverExceedLimit() throws Exception {
        String code = service.create(URL, 3).link().code();
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                calls.add(() -> {
                    try {
                        service.follow(code);
                        return true;
                    } catch (LinkException exception) {
                        return false;
                    }
                });
            }
            List<Future<Boolean>> results = pool.invokeAll(calls);
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successes++;
                }
            }
            assertEquals(3, successes);
            assertEquals(3, service.details(code).clicks());
        } finally {
            pool.shutdownNow();
        }
    }

    static class MemoryStore implements LinkStore {
        State state = State.empty();

        @Override
        public State load() {
            return state;
        }

        @Override
        public void save(State state) {
            this.state = state;
        }
    }

    static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-10-01T12:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

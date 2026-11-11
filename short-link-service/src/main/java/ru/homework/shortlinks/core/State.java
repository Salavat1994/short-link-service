package ru.homework.shortlinks.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Цельный снимок данных для атомарного сохранения. */
public record State(
        Map<String, ShortLink> links,
        Set<UUID> users,
        UUID currentUser,
        List<Notice> notices) {

    public State {
        links = Map.copyOf(links);
        users = Set.copyOf(users);
        notices = List.copyOf(notices);
        if (currentUser != null && !users.contains(currentUser)) {
            throw new IllegalArgumentException("Текущий UUID отсутствует среди пользователей.");
        }
    }

    public static State empty() {
        return new State(Map.of(), Set.of(), null, List.of());
    }

    public State withLinks(Map<String, ShortLink> newLinks) {
        return new State(newLinks, users, currentUser, notices);
    }

    public State withSession(UUID userId) {
        return new State(links, users, userId, notices);
    }

    public State withNotices(List<Notice> newNotices) {
        return new State(links, users, currentUser, newNotices);
    }
}

package ru.homework.shortlinks.ui;

import ru.homework.shortlinks.core.LinkException;
import ru.homework.shortlinks.core.LinkService;
import ru.homework.shortlinks.core.Notice;
import ru.homework.shortlinks.core.NoticeType;
import ru.homework.shortlinks.core.ShortLink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Консольное меню; не содержит бизнес-логики и правил доступа. */
public final class ConsoleUi {
    private final LinkService service;
    private final BrowserLauncher browser;
    private final BufferedReader input;
    private final PrintStream output;

    public ConsoleUi(LinkService service, BrowserLauncher browser, BufferedReader input, PrintStream output) {
        this.service = Objects.requireNonNull(service, "service");
        this.browser = Objects.requireNonNull(browser, "browser");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Сервис коротких ссылок (консольная версия). Введите help для списка команд.");
        output.println("UUID пользователя: " + (service.currentUser() == null
                ? "не создан; будет создан при первой команде create" : service.currentUser()));
        while (true) {
            output.print("shortlinks> ");
            output.flush();
            String line = input.readLine();
            if (line == null) {
                output.println("Завершение работы.");
                return;
            }
            String commandLine = line.trim();
            if (commandLine.isEmpty()) {
                continue;
            }
            if (commandLine.equalsIgnoreCase("exit") || commandLine.equalsIgnoreCase("quit")) {
                output.println("До свидания!");
                return;
            }
            try {
                handle(commandLine);
            } catch (IllegalArgumentException | LinkException exception) {
                output.println("Ошибка: " + exception.getMessage());
            } catch (IOException exception) {
                output.println("Ошибка доступа к данным или браузеру: " + exception.getMessage());
            }
        }
    }

    private void handle(String line) throws IOException {
        String[] arguments = line.split("\\s+");
        String command = arguments[0].toLowerCase(java.util.Locale.ROOT);
        switch (command) {
            case "help" -> {
                exactly(arguments, 1);
                printHelp();
            }
            case "create" -> {
                if (arguments.length != 2 && arguments.length != 3) {
                    throw new IllegalArgumentException("Использование: create <URL> [лимит]");
                }
                Integer limit = arguments.length == 3 ? integer(arguments[2]) : null;
                LinkService.CreatedLink created = service.create(arguments[1], limit);
                if (created.newUser()) {
                    output.println("Создан UUID пользователя: " + created.ownerId());
                    output.println("Сохраните этот UUID: он нужен для управления своими ссылками.");
                }
                output.println("Короткая ссылка: " + service.shortAddress(created.link().code()));
                output.println("Доступно переходов: " + created.link().maxClicks());
                output.println("Действует до: " + created.link().expiresAt());
            }
            case "go" -> {
                exactly(arguments, 2);
                openLink(arguments[1]);
            }
            case "list" -> {
                exactly(arguments, 1);
                List<ShortLink> links = service.myLinks();
                if (links.isEmpty()) {
                    output.println("У вас пока нет действующих ссылок.");
                } else {
                    for (ShortLink link : links) {
                        output.println(service.shortAddress(link.code()) + " | "
                                + link.clicks() + "/" + link.maxClicks()
                                + " | действует до " + link.expiresAt());
                    }
                }
            }
            case "info" -> {
                exactly(arguments, 2);
                ShortLink link = service.details(arguments[1]);
                output.println("Короткая ссылка: " + service.shortAddress(link.code()));
                output.println("Исходный URL: " + link.originalUrl());
                output.println("Переходы: " + link.clicks() + "/" + link.maxClicks());
                output.println("Создана: " + link.createdAt());
                output.println("Истекает: " + link.expiresAt());
            }
            case "set-limit" -> {
                exactly(arguments, 3);
                ShortLink link = service.setLimit(arguments[1], integer(arguments[2]));
                output.println("Новый лимит для " + service.shortAddress(link.code()) + ": " + link.maxClicks());
                if (link.clicks() >= link.maxClicks()) {
                    output.println("Уведомление: текущий лимит уже исчерпан; переходы заблокированы.");
                }
            }
            case "set-url" -> {
                exactly(arguments, 3);
                ShortLink link = service.setUrl(arguments[1], arguments[2]);
                output.println("Исходный URL обновлён для " + service.shortAddress(link.code()));
            }
            case "delete" -> {
                exactly(arguments, 2);
                service.delete(arguments[1]);
                output.println("Ссылка удалена.");
            }
            case "whoami" -> {
                exactly(arguments, 1);
                UUID user = service.currentUser();
                output.println(user == null ? "Пользователь ещё не создан." : "Текущий UUID: " + user);
            }
            case "user-new" -> {
                exactly(arguments, 1);
                service.newUserSession();
                output.println("Начата новая сессия. Новый UUID возникнет при следующем create.");
            }
            case "user-use" -> {
                exactly(arguments, 2);
                UUID id;
                try {
                    id = UUID.fromString(arguments[1]);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Неверный формат UUID.", exception);
                }
                service.useExistingUser(id);
                output.println("Выбран пользователь: " + id);
            }
            case "notifications" -> {
                exactly(arguments, 1);
                List<Notice> notifications = service.takeNotices();
                if (notifications.isEmpty()) {
                    output.println("Новых уведомлений нет.");
                } else {
                    for (Notice notice : notifications) {
                        String reason = notice.type() == NoticeType.EXPIRED
                                ? "истёк срок действия, ссылка автоматически удалена"
                                : "исчерпан лимит переходов";
                        output.println("[" + notice.occurredAt() + "] "
                                + service.shortAddress(notice.code()) + ": " + reason
                                + ". Можно создать новую ссылку.");
                    }
                }
            }
            default -> {
                if (arguments.length == 1 && (line.matches("[A-Za-z0-9]{8}")
                        || line.matches("[A-Za-z0-9.-]+/[A-Za-z0-9]{8}"))) {
                    openLink(line);
                } else {
                    output.println("Неизвестная команда. Введите help.");
                }
            }
        }
    }

    private void openLink(String shortCode) throws IOException {
        LinkService.OpenResult result = service.follow(shortCode);
        try {
            browser.open(result.link().originalUrl());
            output.println("Открыт исходный адрес в браузере. Переходов: "
                    + result.link().clicks() + "/" + result.link().maxClicks());
        } catch (IOException exception) {
            output.println("Браузер не открылся; разрешённая попытка перехода уже учтена.");
            throw exception;
        }
        if (result.lastAllowedClick()) {
            output.println("Уведомление: лимит достигнут, дальнейшие переходы заблокированы.");
            output.println("Владелец может изменить лимит или создать новую ссылку.");
        }
    }

    private void printHelp() {
        output.println("Команды:");
        output.println("  create <URL> [лимит]        создать короткую ссылку (при первом create — UUID)");
        output.println("  go <код|короткая_ссылка>    открыть исходный URL в браузере");
        output.println("  <короткая_ссылка>           вставить ссылку напрямую, без go");
        output.println("  list                        список своих действующих ссылок");
        output.println("  info <код>                  параметры своей ссылки");
        output.println("  set-limit <код> <лимит>     изменить максимальное число переходов");
        output.println("  set-url <код> <URL>         изменить исходный URL");
        output.println("  delete <код>                удалить свою ссылку");
        output.println("  whoami                      узнать текущий UUID");
        output.println("  user-new                    новая сессия (следующий create создаст UUID)");
        output.println("  user-use <UUID>             вернуться к существующему пользователю");
        output.println("  notifications               прочитать уведомления владельца");
        output.println("  help                        помощь");
        output.println("  exit                        выход");
        output.println("Пример: create https://www.baeldung.com/java-9-http-client 2");
        output.println("Короткие адреса работают внутри этой программы; публичный HTTP-сервер не запускается.");
    }

    private static int integer(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Лимит должен быть целым числом.", exception);
        }
    }

    private static void exactly(String[] arguments, int expected) {
        if (arguments.length != expected) {
            throw new IllegalArgumentException("Неверное количество аргументов. Введите help для подсказки.");
        }
    }
}

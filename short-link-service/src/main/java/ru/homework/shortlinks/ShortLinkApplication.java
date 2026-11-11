package ru.homework.shortlinks;

import ru.homework.shortlinks.config.AppConfig;
import ru.homework.shortlinks.core.LinkService;
import ru.homework.shortlinks.core.SecureCodeGenerator;
import ru.homework.shortlinks.infra.DesktopBrowser;
import ru.homework.shortlinks.infra.PropertiesLinkStore;
import ru.homework.shortlinks.ui.ConsoleUi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Запуск приложения и фоновой очистки с гарантированным завершением ресурсов. */
public final class ShortLinkApplication {
    private ShortLinkApplication() {
    }

    public static void main(String[] args) {
        if (args.length != 0) {
            System.err.println("Аргументы запуска не нужны. Используйте интерактивные команды внутри программы.");
            System.exit(1);
        }
        try {
            AppConfig config = AppConfig.load();
            LinkService service = new LinkService(
                    new PropertiesLinkStore(config.storagePath()),
                    config,
                    Clock.systemUTC(),
                    new SecureCodeGenerator());

            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "shortlinks-cleanup");
                thread.setDaemon(true);
                return thread;
            });
            try {
                scheduler.scheduleWithFixedDelay(() -> {
                    try {
                        int removed = service.cleanupExpired();
                        if (removed > 0) {
                            System.out.println("\nАвтоочистка: удалено просроченных ссылок: " + removed
                                    + ". Уведомления доступны владельцу через notifications.");
                        }
                    } catch (IOException exception) {
                        System.err.println("Ошибка автоматической очистки: не удалось сохранить состояние.");
                    }
                }, config.cleanupSeconds(), config.cleanupSeconds(), TimeUnit.SECONDS);

                // System.in закрывается при завершении процесса, здесь не закрываем его отдельно.
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(System.in, StandardCharsets.UTF_8));
                new ConsoleUi(service, new DesktopBrowser(), reader, System.out).run();
            } finally {
                scheduler.shutdownNow();
            }
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Не удалось запустить приложение: " + exception.getMessage());
            System.exit(1);
        }
    }
}

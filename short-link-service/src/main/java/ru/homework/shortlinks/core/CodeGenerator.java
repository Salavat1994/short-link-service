package ru.homework.shortlinks.core;

/** Генерация непредсказуемого кода, проверка уникальности выполняется в сервисе. */
@FunctionalInterface
public interface CodeGenerator {
    String nextCode();
}

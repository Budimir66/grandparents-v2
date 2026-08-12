package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;

/**
 * Интерфейс для отправки сообщений в разные платформы (MAX, Telegram и т.д.)
 * Это как "розетка" — у всех платформ одинаковый способ подключения
 */
public interface MessageSender {

    /**
     * Отправить сообщение пользователю
     * @param chatId ID чата (уникальный идентификатор пользователя)
     * @param response Ответ с текстом и кнопками
     */
    void sendMessage(Long chatId, UniversalResponse response);
}
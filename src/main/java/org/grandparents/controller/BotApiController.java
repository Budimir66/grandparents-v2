package org.grandparents.controller;

import org.grandparents.dto.UniversalMessage;
import org.grandparents.dto.UniversalResponse;
import org.grandparents.service.BotService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot")
public class BotApiController {

    private final BotService botService;

    public BotApiController(BotService botService) {
        this.botService = botService;
    }

    @PostMapping("/handle")
    public UniversalResponse handleMessage(@RequestBody UniversalMessage message) {
        System.out.println("📩 [API] Получен запрос от прокси");
        System.out.println("📩 userId: " + message.getUserId());
        System.out.println("📩 text: " + message.getText());
        System.out.println("📩 callbackData: " + message.getCallbackData());

        UniversalResponse response = botService.handleMessage(message);
        System.out.println("📤 Ответ отправлен");
        return response;
    }
}
package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.*;
import org.grandparents.repository.OperatorReactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OperatorService {

    private static final Logger log = LoggerFactory.getLogger(OperatorService.class);

    private final UserService userService;
    private final ElderService elderService;
    private final CareHomeService careHomeService;
    private final BonusSettingService bonusSettingService;
    private final OperatorReactionRepository reactionRepository;
    private final MessageSender messageSender;

    public OperatorService(UserService userService,
                           ElderService elderService,
                           CareHomeService careHomeService,
                           BonusSettingService bonusSettingService,
                           OperatorReactionRepository reactionRepository,
                           MessageSender messageSender) {
        this.userService = userService;
        this.elderService = elderService;
        this.careHomeService = careHomeService;
        this.bonusSettingService = bonusSettingService;
        this.reactionRepository = reactionRepository;
        this.messageSender = messageSender;
    }

    // ============================================================
    // ===== ВЗЯТЬ ЗАЯВКУ В РАБОТУ =====
    // ============================================================

    public UniversalResponse takeElder(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // ===== ЗАЯВКУ НЕЛЬЗЯ ВЗЯТЬ ТОЛЬКО ЕСЛИ ОНА ЗАВЕРШЕНА =====
        if (elder.getStatus() == ElderStatus.COMPLETED) {
            return responseWithMainMenu("✅ Эта заявка уже завершена.");
        }

        User operator = getUserOrNull(userId);
        if (!isOperator(operator)) {
            return responseWithMainMenu("❌ Только операторы могут брать заявки в работу.");
        }

        if (elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете взять в работу свою заявку.");
        }

        // ===== БЕРЁМ ЗАЯВКУ В РАБОТУ (ДАЖЕ ЕСЛИ ОНА УЖЕ В РАБОТЕ) =====
        elder.setAssignedOperatorId(userId);
        elder.setTakenAt(LocalDateTime.now());
        elder.setStatus(ElderStatus.IN_PROGRESS);
        elderService.updateElder(elder);

        // ===== НАЧИСЛЯЕМ БАЛЛЫ =====
        int takeBonus = bonusSettingService.getBonusValue("take_elder");
        if (takeBonus == 0) takeBonus = 1;

        operator.setBonusPoints(operator.getBonusPoints() + takeBonus);
        operator.incrementTotalTaken();
        userService.saveUser(operator);

        log.info("💰 Оператор {} взял заявку #{}, начислено {} баллов",
                operator.getTelegramId(), elderId, takeBonus);

        // ===== ОТВЕТ С КОНТАКТАМИ =====
        String contactInfo = "✅ **Вы взяли заявку #" + elderId + " в работу!**\n\n" +
                "📋 **Полная информация:**\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 **Имя подопечного:** " + elder.getFullName() + "\n" +
                "📍 **Локация:** " + elder.getPreferredLocation() + "\n" +
                "🎂 **Возраст:** " + elder.getAge() + " лет\n" +
                "💊 **Здоровье:** " + elder.getHealthCondition() + "\n" +
                "💰 **Бюджет:** " + elder.getBudget() + " руб.\n" +
                "📝 **Пожелания:** " + elder.getRequirements() + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📱 **Контакты клиента:**\n" +
                "👤 **Имя:** " + elder.getClientFirstName() + "\n" +
                "📞 **Телефон:** " + elder.getClientPhone() + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "💰 **Ваш баланс:** " + operator.getBonusPoints() + " баллов (+" + takeBonus + " за взятие)";

        UniversalResponse response = new UniversalResponse(contactInfo);
        response.addButton("📋 Мои заявки", "my_requests");
        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== ИНТЕРЕСНО =====
    // ============================================================

    public UniversalResponse markInterested(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        User operator = getUserOrNull(userId);
        if (!isOperator(operator)) {
            return responseWithMainMenu("❌ Только операторы могут отмечать заявки.");
        }

        if (elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете отметить свою заявку.");
        }

        if (elder.getAssignedOperatorId() != null) {
            return responseWithMainMenu("❌ Эта заявка уже взята в работу.");
        }

        try {
            OperatorReaction reaction = new OperatorReaction();
            reaction.setElderId(elderId);
            reaction.setOperatorId(userId);
            reaction.setReaction("INTERESTED");
            reaction.setCreatedAt(LocalDateTime.now());
            reactionRepository.save(reaction);

            operator.setTotalInterested(operator.getTotalInterested() + 1);
            userService.saveUser(operator);

            log.info("👍 Оператор {} отметил заявку #{} как интересную", userId, elderId);
        } catch (Exception e) {
            log.warn("⚠️ Реакция уже существует для заявки #{} от оператора {}", elderId, userId);
        }

        UniversalResponse response = new UniversalResponse(
                "👍 Спасибо! Заявка #" + elderId + " добавлена в раздел **Интересные заявки**."
        );
        response.addButton("📋 Мои заявки", "my_requests");
        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== НЕ ПОДХОДИТ =====
    // ============================================================

    public UniversalResponse markNotInterested(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        User operator = getUserOrNull(userId);
        if (!isOperator(operator)) {
            return responseWithMainMenu("❌ Только операторы могут отмечать заявки.");
        }

        if (elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете отметить свою заявку.");
        }

        try {
            OperatorReaction reaction = new OperatorReaction();
            reaction.setElderId(elderId);
            reaction.setOperatorId(userId);
            reaction.setReaction("NOT_INTERESTED");
            reaction.setCreatedAt(LocalDateTime.now());
            reactionRepository.save(reaction);

            operator.setTotalNotInterested(operator.getTotalNotInterested() + 1);
            userService.saveUser(operator);

            log.info("👎 Оператор {} отметил заявку #{} как неподходящую", userId, elderId);
        } catch (Exception e) {
            log.warn("⚠️ Реакция уже существует для заявки #{} от оператора {}", elderId, userId);
        }

        UniversalResponse response = new UniversalResponse("👎 Спасибо за честность! Мы учтём это.");
        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== ЗАПРОС НА ЗАКРЫТИЕ =====
    // ============================================================

    public UniversalResponse requestComplete(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        if (elder.getAssignedOperatorId() == null || !elder.getAssignedOperatorId().equals(userId)) {
            return responseWithMainMenu("❌ Эта заявка не находится у вас в работе.");
        }

        if (elder.getStatus() != ElderStatus.IN_PROGRESS) {
            return responseWithMainMenu("❌ Заявка уже закрыта или не в работе.");
        }

        User operator = getUserOrNull(userId);
        if (!isOperator(operator)) {
            return responseWithMainMenu("❌ Только операторы могут отправлять запросы.");
        }

        // ===== МЕНЯЕМ СТАТУС НА "ОЖИДАНИЕ ПОДТВЕРЖДЕНИЯ" =====
        elder.setStatus(ElderStatus.AWAITING_CONFIRMATION);
        elderService.updateElder(elder);

        Long recipientId;
        String recipientName;
        boolean isAuthorOperator;

        if (elder.getCreatedBy() != null) {
            User author = getUserOrNull(elder.getCreatedBy());
            if (author != null && isOperator(author)) {
                recipientId = author.getTelegramId();
                recipientName = author.getFirstName();
                isAuthorOperator = true;
            } else {
                recipientId = elder.getClientTelegramId();
                recipientName = elder.getClientFirstName() != null ? elder.getClientFirstName() : "Клиент";
                isAuthorOperator = false;
            }
        } else {
            recipientId = elder.getClientTelegramId();
            recipientName = elder.getClientFirstName() != null ? elder.getClientFirstName() : "Клиент";
            isAuthorOperator = false;
        }

        String careHomeName = "пансионат";
        if (operator.getCareHomeId() != null) {
            CareHome careHome = careHomeService.findById(operator.getCareHomeId());
            if (careHome != null) {
                careHomeName = careHome.getName();
            }
        }

        String notificationText = "🏁 **Запрос на закрытие заявки!**\n\n" +
                "Оператор " + operator.getFirstName() + " сообщает, что ваш подопечный **" + elder.getFullName() +
                "** заселился в пансионат **" + careHomeName + "**.\n\n" +
                "Подтвердите закрытие заявки.\n\n" +
                "💰 Оператор получит +5 баллов.\n" +
                (isAuthorOperator ? "📌 Вы получите +3 балла как автор заявки.\n\n" : "") +
                "Подтверждаете?";

        UniversalResponse notifyResponse = new UniversalResponse(notificationText);
        notifyResponse.addButton("✅ Да, подтверждаю", "confirm_complete_elder_" + elderId);
        notifyResponse.addButton("❌ Нет, ещё нет", "reject_complete_elder_" + elderId);

        try {
            User recipientUser = getUserOrNull(recipientId);
            Long recipientChatId = recipientUser != null && recipientUser.getChatId() != null
                    ? recipientUser.getChatId()
                    : recipientId;
            messageSender.sendMessage(recipientChatId, notifyResponse);
            log.info("📨 Уведомление отправлено получателю {} (chatId={})", recipientId, recipientChatId);
        } catch (Exception e) {
            log.error("❌ Ошибка отправки уведомления: {}", e.getMessage(), e);
        }

        UniversalResponse response = new UniversalResponse(
                "📨 Запрос на закрытие заявки #" + elderId + " отправлен " + recipientName + ".\n\n" +
                        "⏳ Статус заявки: **Ожидает подтверждения**\n\n" +
                        "Вы получите уведомление, когда клиент подтвердит закрытие."
        );
        response.addButton("📋 Мои заявки", "my_requests");
        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== ПОДТВЕРЖДЕНИЕ ЗАКРЫТИЯ =====
    // ============================================================

    @Transactional
    public UniversalResponse confirmComplete(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // ===== ПРОВЕРЯЕМ, ЧТО ЗАЯВКА НЕ ЗАКРЫТА =====
        if (elder.getStatus() == ElderStatus.COMPLETED) {
            return responseWithMainMenu("✅ Заявка уже закрыта.");
        }
        if (elder.getStatus() == ElderStatus.EXPIRED || elder.getStatus() == ElderStatus.DELETED) {
            return responseWithMainMenu("❌ Заявка неактивна и не может быть закрыта.");
        }

        // ===== ПРОВЕРЯЕМ ПРАВА =====
        boolean isAuthor = elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId);
        boolean isClient = elder.getClientTelegramId().equals(userId);
        boolean isOperator = elder.getAssignedOperatorId() != null && elder.getAssignedOperatorId().equals(userId);

        if (!isAuthor && !isClient && !isOperator) {
            return responseWithMainMenu("❌ Вы не можете закрыть эту заявку.");
        }

        // ===== НАЧИСЛЯЕМ БОНУСЫ =====
        User operator = null;
        StringBuilder bonusInfo = new StringBuilder();

        if (elder.getAssignedOperatorId() != null) {
            operator = getUserOrNull(elder.getAssignedOperatorId());
            if (operator != null) {
                int completeBonus = bonusSettingService.getBonusValue("complete_elder");
                operator.setBonusPoints(operator.getBonusPoints() + completeBonus);
                operator.incrementTotalCompleted();
                userService.saveUser(operator);
                bonusInfo.append("👤 **Оператор** ").append(operator.getFirstName())
                        .append(" получил **+").append(completeBonus).append(" баллов**!\n");
            }
        }

        if (elder.getCreatedBy() != null &&
                elder.getAssignedOperatorId() != null &&
                !elder.getCreatedBy().equals(elder.getAssignedOperatorId())) {

            User author = getUserOrNull(elder.getCreatedBy());
            if (author != null && isOperator(author)) {
                int authorBonus = bonusSettingService.getBonusValue("author_complete");
                author.setBonusPoints(author.getBonusPoints() + authorBonus);
                userService.saveUser(author);
                bonusInfo.append("👤 **Автор заявки** ").append(author.getFirstName())
                        .append(" получил **+").append(authorBonus).append(" баллов** за заселение!\n");
            }
        }

        // ===== ЗАКРЫВАЕМ ЗАЯВКУ =====
        elder.setStatus(ElderStatus.COMPLETED);
        elder.setCompletedBy(userId);
        elder.setCompletedAt(LocalDateTime.now());
        elderService.updateElder(elder);

        // ===== УВЕДОМЛЯЕМ ОПЕРАТОРА (ЕСЛИ ОН НЕ ТОТ, КТО ЗАКРЫЛ) =====
        if (operator != null && !operator.getTelegramId().equals(userId)) {
            UniversalResponse notifyOperator = new UniversalResponse(
                    "🎉 Заявка #" + elderId + " успешно закрыта!\n\n" +
                            "Вы получили +5 баллов за закрытие заявки!"
            );
            notifyOperator.addButton("📋 Мои заявки", "my_requests");
            notifyOperator.addButton("🏠 Главное меню", "main_menu");
            messageSender.sendMessage(operator.getTelegramId(), notifyOperator);
        }

        // ===== ОТВЕТ ТОМУ, КТО ЗАКРЫЛ =====
        String message = "🏁 **Заявка #" + elderId + " закрыта!**\n\n" +
                "📋 **Информация о заявке:**\n" +
                "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                "👤 **Клиент:** " + elder.getClientFirstName() + "\n\n" +
                "💰 **Начислено баллов:**\n" +
                bonusInfo.toString() + "\n" +
                "✅ Заявка успешно завершена!";

        UniversalResponse response = new UniversalResponse(message);
        response.addButton("📋 Мои заявки", "my_requests");
        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== СВЯЗЬ С КЛИЕНТОМ ЧЕРЕЗ MAX =====
    // ============================================================

    public UniversalResponse contactClient(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        if (elder.getAssignedOperatorId() == null || !elder.getAssignedOperatorId().equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете связаться с клиентом по этой заявке.");
        }

        User operator = getUserOrNull(userId);
        if (operator == null) {
            return responseWithMainMenu("❌ Оператор не найден.");
        }

        // ===== ПОЛУЧАЕМ НАЗВАНИЕ ПАНСИОНАТА =====
        String careHomeName = "Пансионат (не указан)";
        if (operator.getCareHomeId() != null) {
            CareHome careHome = careHomeService.findById(operator.getCareHomeId());
            if (careHome != null) {
                String name = careHome.getName();
                if (name != null && !name.isEmpty()) {
                    careHomeName = name;
                }
            }
        }

        Long clientId = elder.getClientTelegramId();
        User client = getUserOrNull(clientId);

        // ===== ФОРМИРУЕМ СООБЩЕНИЕ ДЛЯ КЛИЕНТА =====
        String operatorName = operator.getFirstName() != null ? operator.getFirstName() : "Оператор";
        String operatorPhone = operator.getPhone() != null ? operator.getPhone() : "не указан";

        // ===== ЛОГИРУЕМ ДЛЯ ОТЛАДКИ =====
        log.info("📞 Оператор {}: careHomeId={}, phone={}", operatorName, operator.getCareHomeId(), operator.getPhone());

        String clientMessage = "📱 **Вам сообщение от пансионата!**\n\n" +
                "🏢 **" + careHomeName + "**\n" +
                "Оператор **" + operatorName + "** хочет связаться с вами\n" +
                "по поводу заявки #" + elderId + ".\n\n" +
                "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                "📍 **Локация:** " + elder.getPreferredLocation() + "\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📞 **Телефон для связи:** " + operatorPhone + "\n" +
                "👤 **Оператор:** " + operatorName + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "📌 Вы можете связаться с оператором по телефону\n" +
                "или написать ему в MAX.\n\n" +
                "📋 Для просмотра заявки нажмите кнопку ниже.";

        UniversalResponse clientResponse = new UniversalResponse(clientMessage);
        clientResponse.addButtonFullRow("👤 Моя заявка", "my_request");
        clientResponse.addButtonFullRow("🏠 Главное меню", "main_menu");

        // ===== ОТПРАВЛЯЕМ КЛИЕНТУ =====
        boolean sentToClient = false;
        String clientPhone = elder.getClientPhone() != null ? elder.getClientPhone() : "не указан";
        String clientName = elder.getClientFirstName() != null ? elder.getClientFirstName() : "Клиент";

        if (client != null && client.getChatId() != null) {
            try {
                messageSender.sendMessage(client.getChatId(), clientResponse);
                sentToClient = true;
                log.info("📨 Сообщение отправлено клиенту {}", clientId);
            } catch (Exception e) {
                log.error("❌ Ошибка отправки клиенту: {}", e.getMessage(), e);
            }
        } else {
            log.warn("⚠️ Клиент {} не зарегистрирован в MAX или нет chatId", clientId);
        }

        // ===== ОТВЕТ ОПЕРАТОРУ =====
        String operatorResponse;
        if (sentToClient) {
            operatorResponse = "✅ **Сообщение отправлено клиенту!**\n\n" +
                    "📞 **Контакты клиента:**\n" +
                    "👤 Имя: " + clientName + "\n" +
                    "📱 Телефон: " + clientPhone + "\n\n" +
                    "💬 Ваше сообщение отправлено клиенту.\n" +
                    "Ожидайте звонка или сообщения от клиента.";
        } else {
            operatorResponse = "⚠️ **Не удалось отправить сообщение клиенту.**\n\n" +
                    "📞 **Контакты клиента:**\n" +
                    "👤 Имя: " + clientName + "\n" +
                    "📱 Телефон: " + clientPhone + "\n\n" +
                    "Позвоните клиенту по телефону для связи.";
        }

        UniversalResponse response = new UniversalResponse(operatorResponse);
        response.addButtonFullRow("📋 Мои заявки", "my_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== ПРЕДЛОЖЕНИЕ ЗАЯВКИ ПАРТНЁРАМ =====
    // ============================================================

    public UniversalResponse offerElder(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithBackAndMainMenu("❌ Заявка не найдена.", "find_requests");
        }

        User user = getUserOrNull(userId);
        if (!isOperator(user) && !isAdmin(user)) {
            return responseWithBackAndMainMenu("❌ Только операторы могут предлагать заявки.", "find_requests");
        }

        if (elder.getCreatedBy() == null || !elder.getCreatedBy().equals(userId)) {
            return responseWithBackAndMainMenu("❌ Вы можете предлагать только свои заявки.", "find_requests");
        }

        if (elder.getStatus() != ElderStatus.NEW) {
            return responseWithBackAndMainMenu(
                    "❌ Заявка уже не доступна для предложения.\nТекущий статус: " + elder.getStatus(),
                    "find_requests"
            );
        }

        elder.setStatus(ElderStatus.OFFERED);
        elderService.updateElder(elder);

        UniversalResponse response = new UniversalResponse(
                "📤 **Заявка #" + elderId + " предложена партнёрам!**\n\n" +
                        "Теперь другие операторы могут видеть эту заявку и принимать её."
        );
        response.addButton("🔍 Поиск заявок", "find_requests");
        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================================

    private User getUserOrNull(Long userId) {
        return userService.findByTelegramId(userId).orElse(null);
    }

    private boolean isOperator(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.OPERATOR;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.ADMIN;
    }

    private UniversalResponse responseWithMainMenu(String text) {
        UniversalResponse response = new UniversalResponse(text);
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    private UniversalResponse responseWithBackAndMainMenu(String text, String backCallback) {
        UniversalResponse response = new UniversalResponse(text);
        if (backCallback != null) {
            response.addButtonFullRow("🔙 Назад", backCallback);
        }
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
}
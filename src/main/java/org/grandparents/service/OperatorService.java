package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.*;
import org.grandparents.repository.OperatorReactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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
        User user = userService.findByTelegramId(userId).orElse(null);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // ===== ПРОВЕРКА: АВТОР НЕ МОЖЕТ ВЗЯТЬ СВОЮ ЗАЯВКУ =====
        if (elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете взять свою заявку.");
        }

        // ===== ПРОВЕРКА: ЗАЯВКА НЕ ЗАВЕРШЕНА =====
        if (elder.getStatus() == ElderStatus.COMPLETED ||
                elder.getStatus() == ElderStatus.DELETED ||
                elder.getStatus() == ElderStatus.EXPIRED) {
            return responseWithMainMenu("❌ Эта заявка уже завершена или удалена.");
        }

        // ===== ДОБАВЛЯЕМ ОПЕРАТОРА В СПИСОК =====
        String currentIds = elder.getAssignedOperatorIds();
        if (currentIds == null || currentIds.isEmpty()) {
            elder.setAssignedOperatorIds(String.valueOf(userId));
        } else {
            List<String> ids = Arrays.asList(currentIds.split(","));
            if (!ids.contains(String.valueOf(userId))) {
                elder.setAssignedOperatorIds(currentIds + "," + userId);
            }
        }

        elderService.updateElder(elder);

        // ===== НАЧИСЛЯЕМ БАЛЛЫ АВТОРУ (ТОЛЬКО ПРИ ПЕРВОМ ВЗЯТИИ) =====
        if (elder.getBonusPointsAwarded() == null || !elder.getBonusPointsAwarded()) {
            User author = userService.findById(elder.getCreatedBy());
            if (author != null) {
                author.addBonusPoints(-1);
                userService.saveUser(author);
                log.info("📊 У автора {} списано 1 балл за заявку #{}", author.getId(), elderId);
                elder.setBonusPointsAwarded(true);
                elderService.updateElder(elder);
            }
        }

        // ===== УВЕДОМЛЯЕМ КЛИЕНТА =====
        if (elder.getCreatedBy() != null) {
            User author = userService.findById(elder.getCreatedBy());
            if (author != null) {
                String operatorName = user.getFirstName() != null ? user.getFirstName() : "Оператор";
                // Используем sendNotification (добавленный в OperatorService)
                sendNotification(author.getTelegramId(),
                        "📢 **Новый отклик на заявку #" + elderId + "!**\n\n" +
                                "👤 **Оператор:** " + operatorName + "\n" +
                                "📌 **Статус:** Рассматривает вашу заявку\n\n" +
                                "Выберите лучшего оператора в карточке заявки."
                );
            }
        }


        // ===== ФОРМИРУЕМ ПОЛНУЮ КАРТОЧКУ ЗАЯВКИ ДЛЯ ОПЕРАТОРА =====
        StringBuilder card = new StringBuilder();
        card.append("✅ **Заявка #").append(elder.getId()).append(" взята в работу!**\n\n");
        card.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        card.append("📍 **Локация:** ").append(elder.getPreferredLocation() != null ? elder.getPreferredLocation() : "не указана").append("\n");
        card.append("💰 **Бюджет:** ").append(elder.getBudget()).append(" руб.\n");
        card.append("🎂 **Возраст:** ").append(elder.getAge()).append(" лет\n");
        card.append("💊 **Здоровье:** ").append(elder.getHealthCondition() != null ? elder.getHealthCondition() : "не указано").append("\n");
        card.append("📝 **Пожелания:** ").append(elder.getRequirements() != null ? elder.getRequirements() : "не указаны").append("\n");
        card.append("📌 **Статус:** В работе\n");
        card.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        card.append("👤 **Подопечный:** ").append(elder.getFullName()).append("\n");
        card.append("👤 **Клиент:** ").append(elder.getClientFirstName() != null ? elder.getClientFirstName() : "не указан").append("\n");
        card.append("📱 **Телефон:** ").append(elder.getClientPhone() != null ? elder.getClientPhone() : "не указан").append("\n");
        card.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        card.append("💡 Контакты клиента теперь доступны.\n");
        card.append("📱 Свяжитесь с автором для уточнения деталей.");

        UniversalResponse response = new UniversalResponse(card.toString());

        // ===== КНОПКИ ДЛЯ ОПЕРАТОРА =====
        response.addButtonFullRow("📨 Отправить запрос на закрытие", "request_complete_elder_" + elderId);
        response.addButtonFullRow("📱 Связаться через MAX", "contact_client_" + elderId);
        response.addButtonFullRow("📋 Мои заявки", "my_requests");
        response.addButtonFullRow("🔍 Поиск заявок", "find_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

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
        List<OperatorReaction> existing = reactionRepository
                .findAllByOperatorIdAndElderId(userId, elderId);

        if (!existing.isEmpty()) {
            // Уже есть — ничего не делаем
            return responseWithMainMenu("⭐ Эта заявка уже у вас в 'Интересных'.");
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

        // Проверяем, что заявка в работе
        if (elder.getStatus() != ElderStatus.IN_PROGRESS) {
            return responseWithMainMenu("❌ Заявка не в работе.");
        }

        // ===== ПРОВЕРЯЕМ, ЧТО ПОЛЬЗОВАТЕЛЬ ВЕДЁТ ЗАЯВКУ =====
        boolean isAssigned = false;
        if (elder.getAssignedOperatorIds() != null && !elder.getAssignedOperatorIds().isEmpty()) {
            String[] ids = elder.getAssignedOperatorIds().split(",");
            for (String id : ids) {
                if (id.trim().equals(String.valueOf(userId))) {
                    isAssigned = true;
                    break;
                }
            }
        }

        // Если не нашли в assigned_operator_ids, проверяем старую модель
        if (!isAssigned && elder.getAssignedOperatorId() != null) {
            isAssigned = elder.getAssignedOperatorId().equals(userId);
        }

        if (!isAssigned) {
            return responseWithMainMenu("❌ Эта заявка не находится у вас в работе.");
        }

        // Проверяем, что автор не равен оператору
        Long authorId = elder.getCreatedBy();
        if (authorId == null) {
            return responseWithMainMenu("❌ У заявки нет автора.");
        }

        if (authorId.equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете отправить запрос на закрытие своей заявки.");
        }

        // ===== ОТПРАВЛЯЕМ ЗАПРОС АВТОРУ =====
        User author = userService.findById(authorId);
        if (author == null) {
            return responseWithMainMenu("❌ Автор не найден.");
        }

        User operator = userService.findById(userId);
        String operatorName = operator != null ? operator.getFirstName() : "Оператор";

        String message = "🏁 **Запрос на закрытие заявки!**\n\n" +
                "Оператор " + operatorName + " сообщает, что ваш подопечный **" + elder.getFullName() + "** заселился в пансионат **" + getCareHomeName(elder.getCareHomeId()) + "**.\n\n" +
                "Подтвердите закрытие заявки.\n\n" +
                "💰 Оператор получит +5 баллов.\n" +
                "📌 Вы получите +3 балла как автор заявки.\n\n" +
                "Подтверждаете?";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("✅ Да, подтверждаю", "confirm_complete_elder_" + elderId);
        response.addButtonFullRow("❌ Нет, ещё нет", "reject_complete_elder_" + elderId);

        // Отправляем автору
        Long chatId = author.getChatId() != null ? author.getChatId() : author.getTelegramId();
        messageSender.sendMessage(chatId, response);

        return responseWithMainMenu("📨 Запрос на закрытие заявки #" + elderId + " отправлен " + author.getFirstName() + ".\n\n" +
                "⏳ Статус заявки: **Ожидает подтверждения**\n\n" +
                "Вы получите уведомление, когда клиент подтвердит закрытие.");
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
        boolean isClient = elder.getClientTelegramId() != null && elder.getClientTelegramId().equals(userId);
        boolean isOperator = elder.getAssignedOperatorId() != null && elder.getAssignedOperatorId().equals(userId);

        if (!isAuthor && !isClient && !isOperator) {
            return responseWithMainMenu("❌ Вы не можете закрыть эту заявку.");
        }

        // ===== ОПРЕДЕЛЯЕМ ОПЕРАТОРА, КОТОРЫЙ ИНИЦИИРОВАЛ ЗАКРЫТИЕ =====
        // Берём из assigned_operator_ids (первый оператор, который взял заявку)
        Long requestingOperatorId = null;
        if (elder.getAssignedOperatorIds() != null && !elder.getAssignedOperatorIds().isEmpty()) {
            String[] ids = elder.getAssignedOperatorIds().split(",");
            if (ids.length > 0) {
                try {
                    requestingOperatorId = Long.parseLong(ids[0].trim());
                } catch (NumberFormatException e) {
                    log.warn("⚠️ Не удалось распарсить ID оператора: {}", ids[0]);
                }
            }
        }

        // Если не нашли в assigned_operator_ids, пробуем через assigned_operator_id (старая модель)
        if (requestingOperatorId == null && elder.getAssignedOperatorId() != null) {
            requestingOperatorId = elder.getAssignedOperatorId();
        }

        // ===== ЗАКРЫВАЕМ ЗАЯВКУ =====
        elder.setStatus(ElderStatus.COMPLETED);
        elder.setCompletedBy(userId);
        elder.setCompletedAt(LocalDateTime.now());
        elderService.updateElder(elder);

        // ===== УВЕДОМЛЯЕМ ОПЕРАТОРА, КОТОРЫЙ ИНИЦИИРОВАЛ ЗАКРЫТИЕ =====
        if (requestingOperatorId != null && !requestingOperatorId.equals(userId)) {
            User operator = userService.findById(requestingOperatorId);
            if (operator != null) {
                String authorName = getUserOrNull(userId) != null ? getUserOrNull(userId).getFirstName() : "Клиент";
                UniversalResponse notification = new UniversalResponse(
                        "🎉 **Заявка #" + elderId + " закрыта!**\n\n" +
                                "✅ Автор **" + authorName + "** подтвердил заселение.\n" +
                                "📋 **Подопечный:** " + elder.getFullName() + "\n" +
                                "🏢 **Пансионат:** " + getCareHomeName(elder.getCareHomeId()) + "\n\n" +
                                "💰 Вы получили **+5 баллов** за успешное закрытие заявки!"
                );
                notification.addButtonFullRow("📋 Мои заявки", "my_requests");
                notification.addButtonFullRow("🏠 Главное меню", "main_menu");

                Long chatId = operator.getChatId() != null ? operator.getChatId() : operator.getTelegramId();
                messageSender.sendMessage(chatId, notification);
                log.info("📨 Уведомление отправлено оператору {}", operator.getTelegramId());
            }
        } else if (requestingOperatorId == null) {
            log.warn("⚠️ Не удалось определить оператора для уведомления по заявке #{}", elderId);
        } else if (requestingOperatorId.equals(userId)) {
            log.info("ℹ️ Оператор {} сам подтвердил закрытие заявки #{}", userId, elderId);
        }

        // ===== ОТВЕТ ТОМУ, КТО ПОДТВЕРДИЛ ЗАКРЫТИЕ =====
        String message = "🏁 **Заявка #" + elderId + " закрыта!**\n\n" +
                "📋 **Информация о заявке:**\n" +
                "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                "👤 **Клиент:** " + elder.getClientFirstName() + "\n\n" +
                "✅ Заявка успешно завершена!";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("📋 Мои заявки", "my_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
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

   // private User getUserOrNull(Long userId) {
   //     return userService.findByTelegramId(userId).orElse(null);}

    private User getUserOrNull(Long userId) {
        return userService.findById(userId);
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
    private String getUserName(Long userId) {
        if (userId == null) return "Неизвестный";
        User user = userService.findById(userId);
        return user != null ? user.getFirstName() : "Неизвестный";
    }
    // ===== МЕТОД ДЛЯ ОТПРАВКИ УВЕДОМЛЕНИЙ =====
    private void sendNotification(Long userId, String text, String... buttons) {
        try {
            User user = userService.findByTelegramId(userId).orElse(null);
            if (user == null) {
                log.warn("⚠️ Пользователь {} не найден для уведомления", userId);
                return;
            }

            UniversalResponse response = new UniversalResponse(text);
            for (int i = 0; i < buttons.length; i += 2) {
                if (i + 1 < buttons.length) {
                    response.addButtonFullRow(buttons[i], buttons[i + 1]);
                }
            }
            response.addButtonFullRow("🏠 Главное меню", "main_menu");

            Long chatId = user.getChatId() != null ? user.getChatId() : userId;
            messageSender.sendMessage(chatId, response);
            log.info("📨 Уведомление отправлено пользователю {}", userId);
        } catch (Exception e) {
            log.error("❌ Ошибка отправки уведомления пользователю {}: {}", userId, e.getMessage(), e);
        }
    }
    private String getCareHomeName(Long careHomeId) {
        if (careHomeId == null) return "не указан";
        CareHome careHome = careHomeService.findById(careHomeId);
        return careHome != null ? careHome.getName() : "не указан";
    }
}
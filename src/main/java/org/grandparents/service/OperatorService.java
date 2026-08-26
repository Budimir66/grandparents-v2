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

        // ===== ===== ===== ===== ===== ===== ===== ===== ===== =====
        // ===== ГЛАВНОЕ: ДОБАВЛЯЕМ ОПЕРАТОРА В assigned_operator_ids =====
        // ===== ===== ===== ===== ===== ===== ===== ===== ===== =====
        String currentIds = elder.getAssignedOperatorIds();
        if (currentIds == null || currentIds.isEmpty()) {
            elder.setAssignedOperatorIds(String.valueOf(userId));
        } else {
            // Проверяем, не добавлен ли уже
            if (!currentIds.contains(String.valueOf(userId))) {
                elder.setAssignedOperatorIds(currentIds + "," + userId);
            }
        }

        // ===== МЕНЯЕМ СТАТУС НА IN_PROGRESS =====
        if (elder.getStatus() == ElderStatus.NEW) {
            elder.setStatus(ElderStatus.IN_PROGRESS);
        }
        elder.setTakenAt(LocalDateTime.now());
        elderService.updateElder(elder);

        // ===== НАЧИСЛЯЕМ БАЛЛЫ АВТОРУ =====
        if (elder.getBonusPointsAwarded() == null || !elder.getBonusPointsAwarded()) {
            User author = getUserOrNull(elder.getCreatedBy());
            if (author != null) {
                author.addBonusPoints(-1);
                userService.saveUser(author);
                elder.setBonusPointsAwarded(true);
                elderService.updateElder(elder);
            }
        }

        // ===== УВЕДОМЛЯЕМ КЛИЕНТА =====
        if (elder.getCreatedBy() != null) {
            User author = getUserOrNull(elder.getCreatedBy());
            if (author != null) {
                String operatorName = user.getFirstName() != null ? user.getFirstName() : "Оператор";
                sendNotification(author.getTelegramId(),
                        "📢 **Новый отклик на заявку #" + elderId + "!**\n\n" +
                                "👤 **Оператор:** " + operatorName + "\n" +
                                "📌 **Статус:** Рассматривает вашу заявку\n\n" +
                                "Выберите лучшего оператора в карточке заявки."
                );
            }
        }

        // ===== ОТВЕТ ОПЕРАТОРУ =====
        User author = userService.findById(elder.getCreatedBy());
        int bonusAfter = user.getBonusPoints();
        int bonusSpent = 1; // списывается 1 балл

        StringBuilder card = new StringBuilder();
        card.append("✅ **Заявка #").append(elderId).append(" взята в работу!**\n\n");
        card.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        card.append("💰 **Списано баллов:** -").append(bonusSpent).append("\n");
        card.append("💰 **Осталось баллов:** ").append(bonusAfter).append("\n");
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
        card.append("━━━━━━━━━━━━━━━━━━━━━━━");

        UniversalResponse response = new UniversalResponse(card.toString());

// ===== КНОПКИ: "Отправить запрос" и "Связаться" в одну строку =====
        response.addButton("📨 Отправить запрос на закрытие", "request_complete_elder_" + elderId);
        response.addButton("📱 Связаться через MAX", "contact_client_" + elderId);
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

        log.info("🔍 [requestComplete] Заявка #{}, статус: {}, assigned_operator_ids: '{}'",
                elderId, elder.getStatus(), elder.getAssignedOperatorIds());

        // ===== 1. ПРОВЕРЯЕМ СТАТУС =====
        if (elder.getStatus() != ElderStatus.IN_PROGRESS) {
            return responseWithMainMenu("❌ Заявка не в работе.");
        }

        // ===== 2. ПРОВЕРЯЕМ, ЧТО ПОЛЬЗОВАТЕЛЬ ВЕДЁТ ЗАЯВКУ =====
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

        if (!isAssigned && elder.getAssignedOperatorId() != null) {
            isAssigned = elder.getAssignedOperatorId().equals(userId);
        }

        if (!isAssigned) {
            return responseWithMainMenu("❌ Эта заявка не находится у вас в работе.");
        }

        // ===== 3. ОПРЕДЕЛЯЕМ, КОМУ ОТПРАВЛЯТЬ ЗАПРОС =====
        Long authorId = elder.getCreatedBy();
        User author = null;
        Long recipientId = null;
        String recipientName = "Клиент";

        if (authorId != null) {
            author = userService.findById(authorId);
            if (author != null) {
                recipientId = author.getTelegramId();
                recipientName = author.getFirstName() != null ? author.getFirstName() : "Автор";
            }
        }

        // Если автор не найден, отправляем уведомление клиенту (по client_telegram_id)
        if (recipientId == null) {
            recipientId = elder.getClientTelegramId();
            User client = getUserOrNull(recipientId);
            if (client != null) {
                recipientName = client.getFirstName() != null ? client.getFirstName() : "Клиент";
            } else {
                // Если и клиент не найден — отправляем администратору
                List<User> admins = userService.findByAccessLevel(AccessLevel.ADMIN);
                if (!admins.isEmpty()) {
                    recipientId = admins.get(0).getTelegramId();
                    recipientName = "Администратор";
                } else {
                    return responseWithMainMenu("❌ Не удалось определить получателя запроса на закрытие.");
                }
            }
        }

        // ===== 4. ФОРМИРУЕМ СООБЩЕНИЕ =====
        User operator = userService.findById(userId);
        String operatorName = operator != null ? operator.getFirstName() : "Оператор";

        String message = "🏁 **Запрос на закрытие заявки!**\n\n" +
                "Оператор " + operatorName + " сообщает, что ваш подопечный **" + elder.getFullName() + "** заселился в пансионат.\n\n" +
                "Подтвердите закрытие заявки.\n\n" +
                "💰 Оператор получит +5 баллов.\n" +
                "📌 Вы получите +3 балла как автор заявки.\n\n" +
                "Подтверждаете?";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("✅ Да, подтверждаю", "confirm_complete_elder_" + elderId);
        response.addButtonFullRow("❌ Нет, ещё нет", "reject_complete_elder_" + elderId);

        // ===== 5. ОТПРАВЛЯЕМ =====
        Long chatId = null;
        User recipient = getUserOrNull(recipientId);
        if (recipient != null && recipient.getChatId() != null) {
            chatId = recipient.getChatId();
        } else {
            chatId = recipientId;
        }

        if (chatId != null) {
            messageSender.sendMessage(chatId, response);
            log.info("📨 Запрос на закрытие отправлен {} (chatId={})", recipientName, chatId);
        } else {
            log.error("❌ Не удалось отправить запрос на закрытие: нет chatId для {}", recipientName);
            return responseWithMainMenu("❌ Не удалось отправить запрос. Попробуйте позже.");
        }

        // ===== 6. ОТВЕТ ОПЕРАТОРУ =====
        return responseWithMainMenu("📨 Запрос на закрытие заявки #" + elderId + " отправлен " + recipientName + ".\n\n" +
                "⏳ Статус заявки: **Ожидает подтверждения**\n\n" +
                "Вы получите уведомление, когда клиент подтвердит закрытие.");
    }
    // ============================================================
    // ===== ПОДТВЕРЖДЕНИЕ ЗАКРЫТИЯ =====
    // ============================================================

    @Transactional
    public UniversalResponse confirmComplete(Long userId, Long elderId) {
        // ============================================================
        // ===== ДИАГНОСТИКА =====
        // ============================================================
        log.info("🔍 [confirmComplete] НАЧАЛО: userId={}, elderId={}", userId, elderId);

        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            log.warn("⚠️ [confirmComplete] Заявка #{} не найдена!", elderId);
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        log.info("🔍 [confirmComplete] Заявка #{}: status={}, assigned_operator_ids='{}', created_by={}",
                elderId, elder.getStatus(), elder.getAssignedOperatorIds(), elder.getCreatedBy());

        // ===== ПРОВЕРЯЕМ, ЧТО ЗАЯВКА НЕ ЗАКРЫТА =====
        if (elder.getStatus() == ElderStatus.COMPLETED) {
            log.info("ℹ️ [confirmComplete] Заявка #{} уже закрыта", elderId);
            return responseWithMainMenu("✅ Заявка уже закрыта.");
        }
        if (elder.getStatus() == ElderStatus.EXPIRED || elder.getStatus() == ElderStatus.DELETED) {
            log.info("ℹ️ [confirmComplete] Заявка #{} неактивна", elderId);
            return responseWithMainMenu("❌ Заявка неактивна и не может быть закрыта.");
        }

        // ===== ПРОВЕРЯЕМ ПРАВА =====
        boolean isAuthor = elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId);
        boolean isClient = elder.getClientTelegramId() != null && elder.getClientTelegramId().equals(userId);
        boolean isOperator = elder.getAssignedOperatorId() != null && elder.getAssignedOperatorId().equals(userId);

        log.info("🔍 [confirmComplete] isAuthor={}, isClient={}, isOperator={}", isAuthor, isClient, isOperator);

        if (!isAuthor && !isClient && !isOperator) {
            log.warn("⚠️ [confirmComplete] Пользователь {} не имеет прав на закрытие заявки #{}", userId, elderId);
            return responseWithMainMenu("❌ Вы не можете закрыть эту заявку.");
        }

        // ============================================================
        // ===== ОПРЕДЕЛЯЕМ ОПЕРАТОРА, КОТОРЫЙ ОТПРАВИЛ ЗАПРОС =====
        // ============================================================
        Long requestingOperatorId = null;
        if (elder.getAssignedOperatorIds() != null && !elder.getAssignedOperatorIds().isEmpty()) {
            String[] ids = elder.getAssignedOperatorIds().split(",");
            log.info("🔍 [confirmComplete] assigned_operator_ids: {}", Arrays.toString(ids));
            for (String id : ids) {
                Long operatorId = Long.parseLong(id.trim());
                // Пропускаем автора
                if (elder.getCreatedBy() != null && elder.getCreatedBy().equals(operatorId)) {
                    log.info("🔍 [confirmComplete] Пропускаем автора: {}", operatorId);
                    continue;
                }
                // Берём первого НЕ автора
                requestingOperatorId = operatorId;
                log.info("🔍 [confirmComplete] Найден оператор, отправивший запрос: {}", requestingOperatorId);
                break;
            }
        }

        // Если не нашли в assigned_operator_ids, пробуем через assigned_operator_id (старая модель)
        if (requestingOperatorId == null && elder.getAssignedOperatorId() != null) {
            if (elder.getCreatedBy() == null || !elder.getCreatedBy().equals(elder.getAssignedOperatorId())) {
                requestingOperatorId = elder.getAssignedOperatorId();
                log.info("🔍 [confirmComplete] Найден оператор в assigned_operator_id: {}", requestingOperatorId);
            }
        }

        log.info("🔍 [confirmComplete] Итоговый requestingOperatorId: {}", requestingOperatorId);

        // ===== ЗАКРЫВАЕМ ЗАЯВКУ =====
        elder.setStatus(ElderStatus.COMPLETED);
        elder.setCompletedBy(userId);
        elder.setCompletedAt(LocalDateTime.now());
        elderService.updateElder(elder);
        log.info("✅ [confirmComplete] Заявка #{} закрыта", elderId);

        // ============================================================
        // ===== УВЕДОМЛЯЕМ ОПЕРАТОРА, КОТОРЫЙ ОТПРАВИЛ ЗАПРОС =====
        // ============================================================
        if (requestingOperatorId != null) {
            log.info("🔍 [confirmComplete] Пытаемся отправить уведомление оператору {}", requestingOperatorId);
            if (!requestingOperatorId.equals(userId)) {
                User operator = getUserOrNull(requestingOperatorId);
              //  User operator = userService.findById(requestingOperatorId);
                if (operator != null) {
                    log.info("🔍 [confirmComplete] Оператор найден: {}, chatId={}", operator.getFirstName(), operator.getChatId());

                    String confirmatorName = "Клиент";
                    if (isAuthor) {
                        User author = userService.findById(userId);
                        confirmatorName = author != null ? author.getFirstName() : "Автор";
                    } else if (isClient) {
                        confirmatorName = "Клиент";
                    }

                    UniversalResponse notification = new UniversalResponse(
                            "🎉 **Заявка #" + elderId + " закрыта!**\n\n" +
                                    "✅ " + confirmatorName + " подтвердил заселение.\n" +
                                    "📋 **Подопечный:** " + elder.getFullName() + "\n" +
                                    "🏢 **Пансионат:** " + getCareHomeName(elder.getCareHomeId()) + "\n\n" +
                                    "📌 Заявка успешно завершена!"
                    );
                    notification.addButtonFullRow("📋 Мои заявки", "my_requests");
                    notification.addButtonFullRow("🏠 Главное меню", "main_menu");

                    Long chatId = operator.getChatId() != null ? operator.getChatId() : operator.getTelegramId();
                    log.info("📨 [confirmComplete] Отправляем уведомление оператору {} в chatId={}", operator.getTelegramId(), chatId);
                    messageSender.sendMessage(chatId, notification);
                    log.info("✅ [confirmComplete] Уведомление отправлено оператору {}", operator.getTelegramId());
                } else {
                    log.error("❌ [confirmComplete] Оператор {} не найден!", requestingOperatorId);
                }
            } else {
                log.info("ℹ️ [confirmComplete] Оператор {} сам подтвердил закрытие заявки #{}", userId, elderId);
            }
        } else {
            log.warn("⚠️ [confirmComplete] Не удалось определить оператора для уведомления по заявке #{}", elderId);
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

    private User getUserOrNull(Long userId) {
       return userService.findByTelegramId(userId).orElse(null);}

   // private User getUserOrNull(Long userId) {
   //     return userService.findById(userId);}

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
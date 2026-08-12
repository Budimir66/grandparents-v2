package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.AccessLevel;
import org.grandparents.model.Elder;
import org.grandparents.model.ElderStatus;
import org.grandparents.model.User;
import org.grandparents.statemachine.DialogState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ElderFormService {

    private static final Logger log = LoggerFactory.getLogger(ElderFormService.class);

    private final UserService userService;
    private final ElderService elderService;
    private final UserStateService stateService;
    private final NotificationService notificationService;
    private final BonusSettingService bonusSettingService;
    private final MessageSender messageSender;

    public ElderFormService(UserService userService,
                            ElderService elderService,
                            UserStateService stateService,
                            NotificationService notificationService,
                            BonusSettingService bonusSettingService,
                            MessageSender messageSender) {
        this.userService = userService;
        this.elderService = elderService;
        this.stateService = stateService;
        this.notificationService = notificationService;
        this.bonusSettingService = bonusSettingService;
        this.messageSender = messageSender;
    }

    /**
     * Начать создание новой заявки
     */
    public UniversalResponse startNewRequest(Long userId) {
        User user = getUserOrNull(userId);
        if (user != null && isGuest(user)) {
            List<Elder> existingElders = elderService.findByClientTelegramId(userId);
            boolean hasActive = existingElders.stream()
                    .anyMatch(e -> e.getStatus() != ElderStatus.COMPLETED &&
                            e.getStatus() != ElderStatus.EXPIRED &&
                            e.getStatus() != ElderStatus.DELETED);
            if (hasActive) {
                UniversalResponse response = new UniversalResponse(
                        "❌ У вас уже есть активная заявка.\n\n" +
                                "Вы можете создать новую только после завершения или удаления текущей.\n" +
                                "👤 Перейдите в раздел **Моя заявка** для управления."
                );
                response.addButton("👤 Моя заявка", "my_request");
                response.addButton("🏠 Главное меню", "main_menu");
                return response;
            }
        }

        stateService.setState(userId, DialogState.AWAITING_ELDER_NAME);
        UniversalResponse response = new UniversalResponse(
                "📝 Начинаем создание заявки!\n\n" +
                        "Введите полное имя подопечного (например: Иванова Анна Петровна):"
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    /**
     * Обработать ввод в анкете
     */
    public UniversalResponse handleFormInput(Long userId, String text, DialogState state) {
        Elder tempElder = stateService.getTempElder(userId);
        if (tempElder == null) {
            tempElder = new Elder();
            stateService.setTempElder(userId, tempElder);
        }

        UniversalResponse response = new UniversalResponse();

        log.info("📥 elderFormService.handleFormInput: userId={}, state={}, text={}", userId, state, text);

        switch (state) {
            case AWAITING_ELDER_NAME -> {
                tempElder.setFullName(text);
                stateService.setState(userId, DialogState.AWAITING_CLIENT_NAME);
                response.setText("👤 Как к вам обращаться? (например: Иван Петрович):");
                response.addButton("❌ Отменить", "cancel_action");
            }
            case AWAITING_CLIENT_NAME -> {
                tempElder.setClientFirstName(text);
                stateService.setState(userId, DialogState.AWAITING_ELDER_AGE);
                response.setText("📝 Введите возраст подопечного (число):");
                response.addButton("❌ Отменить", "cancel_action");
            }
            case AWAITING_ELDER_AGE -> {
                try {
                    int age = Integer.parseInt(text);
                    tempElder.setAge(age);
                    stateService.setState(userId, DialogState.AWAITING_ELDER_HEALTH);
                    response.setText("📝 Введите состояние здоровья (например: диабет, деменция, нужен уход):");
                    response.addButton("❌ Отменить", "cancel_action");
                } catch (NumberFormatException e) {
                    response.setText("❌ Пожалуйста, введите число (например, 75):");
                    response.addButton("❌ Отменить", "cancel_action");
                }
            }
            case AWAITING_ELDER_HEALTH -> {
                tempElder.setHealthCondition(text);
                stateService.setState(userId, DialogState.AWAITING_ELDER_BUDGET);
                response.setText("📝 Введите бюджет на месяц в рублях (например, 50000):");
                response.addButton("❌ Отменить", "cancel_action");
            }
            case AWAITING_ELDER_BUDGET -> {
                try {
                    double budget = Double.parseDouble(text);
                    if (budget <= 0) {
                        response.setText("❌ Бюджет должен быть положительным числом. Попробуйте снова:");
                        response.addButton("❌ Отменить", "cancel_action");
                        break;
                    }
                    tempElder.setBudget(budget);
                    stateService.setState(userId, DialogState.AWAITING_ELDER_LOCATION);
                    response.setText("📝 Введите желаемую локацию (город, район):");
                    response.addButton("❌ Отменить", "cancel_action");
                } catch (NumberFormatException e) {
                    response.setText("❌ Пожалуйста, введите число (например, 50000):");
                    response.addButton("❌ Отменить", "cancel_action");
                }
            }
            case AWAITING_ELDER_LOCATION -> {
                tempElder.setPreferredLocation(text);
                stateService.setState(userId, DialogState.AWAITING_ELDER_PHONE);
                response.setText("📱 Введите номер телефона для связи\n\n" +
                        "Вы можете ввести номер вручную или поделиться из MAX:");
                response.addButton("✏️ Ввести вручную", "enter_phone_manually");
                response.addButton("📱 Отправить номер из MAX", "request_contact_from_max");
                response.addButton("❌ Отменить", "cancel_action");
            }
            case AWAITING_ELDER_PHONE_MANUAL -> {
                String cleanPhone = text.replaceAll("[^0-9+]", "");
                if (!cleanPhone.matches("^[+]?[0-9]{10,15}$")) {
                    response.setText("❌ Неверный формат. Введите номер в формате +7 999 123-45-67:");
                    response.addButton("❌ Отменить", "cancel_action");
                    break;
                }
                tempElder.setClientPhone(cleanPhone);
                stateService.setState(userId, DialogState.AWAITING_ELDER_REQUIREMENTS);
                response.setText("📝 Введите особые пожелания (например: первый этаж, диетическое питание):");
                response.addButton("❌ Отменить", "cancel_action");
            }
            case AWAITING_ELDER_REQUIREMENTS -> {
                tempElder.setRequirements(text);
                tempElder.setClientTelegramId(userId);
                tempElder.setExpiresAt(LocalDateTime.now().plusDays(14));

                // ===== ПЕРЕХОДИМ К СОГЛАСИЮ =====
                stateService.setState(userId, DialogState.AWAITING_CONSENT);

                String consentText = """
            📋 **Согласие на обработку персональных данных**

            Я, заполняя данную заявку, даю своё согласие на обработку моих персональных данных
            и данных моего подопечного в соответствии с Федеральным законом № 152-ФЗ
            «О персональных данных».

            Я соглашаюсь, что мои контактные данные (имя, телефон) и информация о моём подопечном
            (имя, возраст, состояние здоровья, бюджет, локация, пожелания) будут переданы
            операторам пансионатов для поиска подходящего места проживания.

            Я понимаю, что могу отозвать своё согласие в любое время, написав в поддержку.

            Нажимая "✅ Согласен", я принимаю условия.

            ❌ Не согласен — заявка не будет отправлена.""";

                response = new UniversalResponse(consentText);
                response.addButtonFullRow("✅ Согласен", "accept_consent");
                response.addButtonFullRow("❌ Не согласен", "decline_consent");
                return response;
            }
            // ===== РЕДАКТИРОВАНИЕ ЗАЯВКИ =====
            // ===== РЕДАКТИРОВАНИЕ ЗАЯВКИ =====
            // ===== РЕДАКТИРОВАНИЕ ЗАЯВКИ =====
            case EDITING_ELDER_NAME -> {
                // Если пользователь ввёл текст — сохраняем его
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_field")) {
                    tempElder.setFullName(text.trim());
                }
                stateService.setState(userId, DialogState.EDITING_ELDER_AGE);
                response.setText("✏️ Введите новый возраст (текущий: " + tempElder.getAge() + "):");
                response.addButton("⏭️ Оставить как есть", "skip_edit_field");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case EDITING_ELDER_AGE -> {
                // Если пользователь ввёл число — сохраняем его
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_field")) {
                    try {
                        int age = Integer.parseInt(text.trim());
                        if (age > 0) {
                            tempElder.setAge(age);
                        }
                    } catch (NumberFormatException e) {
                        // Если введено не число — оставляем как было
                        log.info("Возраст не изменён, оставлено значение: {}", tempElder.getAge());
                    }
                }
                stateService.setState(userId, DialogState.EDITING_ELDER_HEALTH);
                response.setText("✏️ Введите новое состояние здоровья (текущее: " + tempElder.getHealthCondition() + "):");
                response.addButton("⏭️ Оставить как есть", "skip_edit_field");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case EDITING_ELDER_HEALTH -> {
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_field")) {
                    tempElder.setHealthCondition(text.trim());
                }
                stateService.setState(userId, DialogState.EDITING_ELDER_BUDGET);
                response.setText("✏️ Введите новый бюджет (текущий: " + tempElder.getBudget() + " руб.):");
                response.addButton("⏭️ Оставить как есть", "skip_edit_field");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case EDITING_ELDER_BUDGET -> {
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_field")) {
                    try {
                        double budget = Double.parseDouble(text.trim());
                        if (budget > 0) {
                            tempElder.setBudget(budget);
                        }
                    } catch (NumberFormatException e) {
                        log.info("Бюджет не изменён, оставлено значение: {}", tempElder.getBudget());
                    }
                }
                stateService.setState(userId, DialogState.EDITING_ELDER_LOCATION);
                response.setText("✏️ Введите новую локацию (текущая: " + tempElder.getPreferredLocation() + "):");
                response.addButton("⏭️ Оставить как есть", "skip_edit_field");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case EDITING_ELDER_LOCATION -> {
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_field")) {
                    tempElder.setPreferredLocation(text.trim());
                }
                stateService.setState(userId, DialogState.EDITING_ELDER_REQUIREMENTS);
                response.setText("✏️ Введите новые пожелания (текущие: " + tempElder.getRequirements() + "):");
                response.addButton("⏭️ Оставить как есть", "skip_edit_field");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case EDITING_ELDER_REQUIREMENTS -> {
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_field")) {
                    tempElder.setRequirements(text.trim());
                }
                tempElder.setUpdatedAt(LocalDateTime.now());
                elderService.updateElder(tempElder);
                stateService.clearState(userId);

                response.setText("✅ **Заявка #" + tempElder.getId() + " отредактирована!**\n\n" +
                        "📋 **Обновлённые данные:**\n" +
                        "👤 **Имя:** " + tempElder.getFullName() + "\n" +
                        "🎂 **Возраст:** " + tempElder.getAge() + " лет\n" +
                        "💊 **Здоровье:** " + tempElder.getHealthCondition() + "\n" +
                        "💰 **Бюджет:** " + tempElder.getBudget() + " руб.\n" +
                        "📍 **Локация:** " + tempElder.getPreferredLocation() + "\n" +
                        "📝 **Пожелания:** " + tempElder.getRequirements());
                response.addButton("👤 Моя заявка", "my_request");
                response.addButton("🏠 Главное меню", "main_menu");
            }
            default -> {
                stateService.clearState(userId);
                response.setText("❌ Что-то пошло не так. Начните заново через /start");
                response.addMainMenuButton();
            }
        }

        return response;
    }

    /**
     * Редактирование заявки
     */
    /**
     * Редактирование заявки
     */
    public UniversalResponse handleEditElder(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        boolean isAuthor = elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId);
        boolean isClient = elder.getClientTelegramId().equals(userId);

        if (!isAuthor && !isClient) {
            return responseWithMainMenu("❌ Вы не можете редактировать эту заявку.");
        }

        if (elder.getAssignedOperatorId() != null) {
            return responseWithMainMenu("❌ Заявка уже взята в работу и не может быть отредактирована.");
        }

        if (elder.getStatus() == ElderStatus.COMPLETED ||
                elder.getStatus() == ElderStatus.EXPIRED ||
                elder.getStatus() == ElderStatus.DELETED) {
            return responseWithMainMenu("❌ Заявка уже неактивна и не может быть отредактирована.");
        }

        // Сохраняем заявку в состояние
        stateService.setTempElder(userId, elder);
        stateService.setState(userId, DialogState.EDITING_ELDER_NAME);

        UniversalResponse response = new UniversalResponse(
                "✏️ **Редактирование заявки #" + elderId + "**\n\n" +
                        "Текущее имя: " + elder.getFullName() + "\n\n" +
                        "Введите новое имя подопечного:"
        );

        // ===== ДОБАВЛЯЕМ ДВЕ КНОПКИ =====
        response.addButton("⏭️ Оставить как есть", "skip_edit_field");
        response.addButton("❌ Отменить", "cancel_action");

        return response;
    }

    /**
     * Удаление заявки
     */
    public UniversalResponse handleDeleteElder(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithBackAndMainMenu("❌ Заявка не найдена.", "my_requests");
        }

        if (!elder.getClientTelegramId().equals(userId)) {
            return responseWithBackAndMainMenu("❌ Это не ваша заявка.", "my_requests");
        }

        if (elder.getAssignedOperatorId() != null) {
            return responseWithMainMenu("❌ Заявка уже взята в работу и не может быть удалена.");
        }

        stateService.setTempElder(userId, elder);
        stateService.setState(userId, DialogState.CONFIRM_DELETE_ELDER);

        UniversalResponse response = new UniversalResponse(
                "⚠️ **Вы уверены, что хотите удалить заявку #" + elderId + "?**\n\n" +
                        "👤 " + elder.getFullName() + "\n" +
                        "💰 " + elder.getBudget() + " руб.\n\n" +
                        "Это действие нельзя отменить."
        );
        response.addButton("✅ Да, удалить", "confirm_delete_yes");
        response.addButton("❌ Нет, отменить", "confirm_delete_no");
        return response;
    }

    /**
     * Подтверждение удаления заявки
     */
    public UniversalResponse confirmDeleteElder(Long userId, boolean confirm) {
        if (confirm) {
            Elder elder = stateService.getTempElder(userId);
            if (elder != null) {
                elderService.deleteElder(elder.getId());
                stateService.clearState(userId);

                UniversalResponse response = new UniversalResponse(
                        "✅ Заявка #" + elder.getId() + " успешно удалена."
                );
                response.addButton("📋 Мои заявки", "my_requests");
                response.addButton("🏠 Главное меню", "main_menu");
                return response;
            }
        }

        stateService.clearState(userId);
        return responseWithBackAndMainMenu("❌ Удаление отменено.", "my_requests");
    }

    /**
     * Продление заявки
     */
    public UniversalResponse handleExtendElder(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        boolean isAuthor = elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId);
        boolean isClient = elder.getClientTelegramId().equals(userId);

        if (!isAuthor && !isClient) {
            return responseWithMainMenu("❌ Вы не можете продлить эту заявку.");
        }

        if (elder.getStatus() == ElderStatus.EXPIRED || elder.getStatus() == ElderStatus.COMPLETED) {
            return responseWithMainMenu("❌ Заявка уже неактивна.");
        }

        stateService.setTempElder(userId, elder);
        stateService.setState(userId, DialogState.AWAITING_EXTEND_BUDGET);

        UniversalResponse response = new UniversalResponse(
                "✏️ **Продление заявки #" + elderId + "**\n\n" +
                        "Текущий бюджет: " + elder.getBudget() + " руб.\n\n" +
                        "Введите **новый (увеличенный) бюджет** в рублях:\n" +
                        "Например: 70000"
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    /**
     * Сохранение продления заявки
     */
    public UniversalResponse saveExtendElder(Long userId, String budgetText) {
        Elder elder = stateService.getTempElder(userId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        try {
            double newBudget = Double.parseDouble(budgetText);
            if (newBudget <= elder.getBudget()) {
                UniversalResponse response = new UniversalResponse(
                        "❌ Новый бюджет должен быть **больше** текущего (" + elder.getBudget() + " руб.).\n\n" +
                                "Введите увеличенный бюджет:"
                );
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            elder.setBudget(newBudget);
            elder.setExpiresAt(LocalDateTime.now().plusDays(14));
            elder.setEditCount(elder.getEditCount() + 1);
            elder.setStatus(ElderStatus.OFFERED);
            elderService.updateElder(elder);
            stateService.clearState(userId);

            notificationService.notifyOperators(elder);

            UniversalResponse response = new UniversalResponse(
                    "✅ **Заявка #" + elder.getId() + " продлена!**\n\n" +
                            "💰 **Новый бюджет:** " + newBudget + " руб.\n" +
                            "📅 **Новый срок:** +14 дней\n" +
                            "✏️ **Редактирований:** " + elder.getEditCount() + "\n\n" +
                            "📢 Уведомления отправлены операторам."
            );
            response.addMainMenuButton();
            return response;

        } catch (NumberFormatException e) {
            UniversalResponse response = new UniversalResponse(
                    "❌ Пожалуйста, введите число (например, 70000):"
            );
            response.addButton("❌ Отменить", "cancel_action");
            return response;
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private User getUserOrNull(Long userId) {
        return userService.findByTelegramId(userId).orElse(null);
    }

    private boolean isGuest(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.GUEST;
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

    private void notifyAdminsAboutNewElder(Elder elder) {
        List<User> admins = userService.findByAccessLevel(AccessLevel.ADMIN);
        if (admins.isEmpty()) {
            log.warn("⚠️ Нет администраторов для уведомления");
            return;
        }

        User client = getUserOrNull(elder.getClientTelegramId());
        String clientName = client != null ? client.getFirstName() : "Неизвестный";

        String message = "📢 **Новая заявка на модерацию!**\n\n" +
                "📋 **Заявка #" + elder.getId() + "**\n" +
                "👤 **Клиент:** " + clientName + "\n" +
                "📱 **Телефон:** " + elder.getClientPhone() + "\n" +
                "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                "🎂 **Возраст:** " + elder.getAge() + " лет\n" +
                "💰 **Бюджет:** " + elder.getBudget() + " руб.\n" +
                "📍 **Локация:** " + elder.getPreferredLocation() + "\n" +
                "⏳ **Статус:** На модерации\n\n" +
                "Выберите действие:";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("✅ Одобрить", "approve_elder_" + elder.getId());
        response.addButtonFullRow("❌ Отклонить", "reject_elder_" + elder.getId());
        response.addButtonFullRow("📋 Модерация заявок", "admin_elder_moderation");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");

        for (User admin : admins) {
            try {
                Long chatId = admin.getChatId() != null ? admin.getChatId() : admin.getTelegramId();
                messageSender.sendMessage(chatId, response);
                log.info("📨 Уведомление отправлено администратору {}", admin.getTelegramId());
            } catch (Exception e) {
                log.error("❌ Ошибка отправки администратору: {}", e.getMessage(), e);
            }
        }
    }
}
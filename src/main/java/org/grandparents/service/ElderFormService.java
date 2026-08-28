package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.AccessLevel;
import org.grandparents.model.Elder;
import org.grandparents.model.ElderStatus;
import org.grandparents.model.User;
import org.grandparents.repository.OperatorReactionRepository;
import org.grandparents.repository.RatingRepository;
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
    private final OperatorReactionRepository reactionRepository;
    private final RatingRepository ratingRepository;
    // private static final Logger log = LoggerFactory.getLogger(ElderFormService.class);


    public ElderFormService(UserService userService,
                            ElderService elderService,
                            UserStateService stateService,
                            NotificationService notificationService,
                            BonusSettingService bonusSettingService,
                            MessageSender messageSender,
                            OperatorReactionRepository reactionRepository,
                            RatingRepository ratingRepository) {
        this.userService = userService;
        this.elderService = elderService;
        this.stateService = stateService;
        this.notificationService = notificationService;
        this.bonusSettingService = bonusSettingService;
        this.messageSender = messageSender;
        this.ratingRepository = ratingRepository;
        this.reactionRepository = reactionRepository;
    }

    /**
     * Начать создание новой заявки
     */
    public UniversalResponse startNewRequest(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        // ===== ПРОВЕРКА НА АКТИВНУЮ ЗАЯВКУ (для GUEST) =====
        if (isGuest(user)) {
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

        // ===== СОЗДАЁМ ВРЕМЕННУЮ ЗАЯВКУ =====
        Elder tempElder = new Elder();
        tempElder.setClientTelegramId(userId);
        tempElder.setConsentGiven(false);
        stateService.setTempElder(userId, tempElder);

        // ===== ЕСЛИ КЛИЕНТ (GUEST) — ПОКАЗЫВАЕМ ОФЕРТУ =====
        if (isGuest(user)) {
            stateService.setState(userId, DialogState.AWAITING_CONSENT_BEFORE_FORM);

            String consentText = """
                📋 **СОГЛАСИЕ НА ОБРАБОТКУ ПЕРСОНАЛЬНЫХ ДАННЫХ**
                и получение информационных сообщений

                Для создания заявки необходимо ваше согласие на обработку персональных данных
                и получение сообщений от операторов пансионатов.

                1. Я даю своё согласие на обработку моих персональных данных
                и данных моего подопечного в соответствии с Федеральным законом № 152-ФЗ
                «О персональных данных».

                2. Я соглашаюсь, что мои контактные данные (имя, телефон, адрес электронной почты)
                и информация о моём подопечном (имя, возраст, состояние здоровья, бюджет, локация, пожелания)
                будут переданы операторам пансионатов для поиска подходящего места проживания.

                3. Я даю своё согласие на получение сообщений от операторов пансионатов.

                4. Я понимаю, что могу в любое время отозвать своё согласие,
                написав в поддержку бота или отправив запрос на отписку.

                5. Я подтверждаю, что ознакомлен(а) с условиями и принимаю их.

                Нажимая "✅ Согласен", я принимаю все условия.

                ❌ Не согласен — заявка не будет создана.""";

            UniversalResponse response = new UniversalResponse(consentText);
            response.addButtonFullRow("✅ Согласен", "accept_consent_before_form");
            response.addButtonFullRow("❌ Не согласен", "decline_consent_before_form");
            return response;
        }

        // Для оператора
        if (user.getAccessLevel() == AccessLevel.OPERATOR || user.getAccessLevel() == AccessLevel.MANAGER) {
            tempElder.setCreatedBy(userId);
            tempElder.setCareHomeId(user.getCareHomeId());
            tempElder.setStatus(ElderStatus.NEW);  // ← НЕ OFFERED и НЕ IN_PROGRESS!
        }

        // ===== ДЛЯ ОПЕРАТОРА, МЕНЕДЖЕРА, АДМИНИСТРАТОРА — СРАЗУ АНКЕТА =====
        // Согласие считается автоматически принятым
        tempElder.setConsentGiven(true);
        tempElder.setConsentGivenAt(LocalDateTime.now());
        stateService.setTempElder(userId, tempElder);
        stateService.setState(userId, DialogState.AWAITING_ELDER_NAME);

        UniversalResponse response = new UniversalResponse(
                "📝 **Создание заявки**\n\n" +
                        "✅ Вводите данные в окне чата бота.\n\n" +
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
                tempElder.setFullName(truncateText(text, 100));
                stateService.setState(userId, DialogState.AWAITING_CLIENT_NAME);

                User user = getUserOrNull(userId);
                String clientNamePrompt;
                if (user != null && isGuest(user)) {
                    clientNamePrompt = "👤 Как к вам обращаться? (до 100 символов):\nНапример: Иван Петрович";
                } else {
                    clientNamePrompt = "👤 Имя клиента (до 100 символов):\nНапример: Анна Ивановна";
                }

                response.setText(clientNamePrompt);
                response.addButton("❌ Отменить", "cancel_action");
            }

            case AWAITING_CLIENT_NAME -> {
                // Ограничение: 100 символов
                tempElder.setClientFirstName(truncateText(text, 100));
                stateService.setState(userId, DialogState.AWAITING_ELDER_AGE);
                response.setText("📝 Введите возраст подопечного (число):");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case AWAITING_ELDER_AGE -> {
                try {
                    int age = Integer.parseInt(text);
                    // Ограничение: возраст от 0 до 120
                    if (age < 0 || age > 120) {
                        response.setText("❌ Введите возраст от 0 до 120 лет:");
                        response.addButton("❌ Отменить", "cancel_action");
                        break;
                    }
                    tempElder.setAge(age);
                    stateService.setState(userId, DialogState.AWAITING_ELDER_HEALTH);
                    response.setText("📝 Введите состояние здоровья (до 255 символов):\n" +
                            "Например: диабет, деменция, нужен постоянный уход");
                    response.addButton("❌ Отменить", "cancel_action");
                } catch (NumberFormatException e) {
                    response.setText("❌ Пожалуйста, введите число (например, 75):");
                    response.addButton("❌ Отменить", "cancel_action");
                }
            }

            case AWAITING_ELDER_HEALTH -> {
                // Ограничение: 255 символов
                tempElder.setHealthCondition(truncateText(text, 255));
                stateService.setState(userId, DialogState.AWAITING_ELDER_BUDGET);
                response.setText("📝 Введите бюджет на месяц в рублях (например, 50000):");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case AWAITING_ELDER_BUDGET -> {
                try {
                    double budget = Double.parseDouble(text);
                    // Ограничение: бюджет от 0 до 10 000 000
                    if (budget <= 0 || budget > 10_000_000) {
                        response.setText("❌ Введите сумму от 1000 до 10 000 000 руб.:");
                        response.addButton("❌ Отменить", "cancel_action");
                        break;
                    }
                    tempElder.setBudget(budget);
                    stateService.setState(userId, DialogState.AWAITING_ELDER_LOCATION);
                    response.setText("📍 Введите желаемую локацию (до 100 символов):\n" +
                            "Например: Москва, Северное Измайлово");
                    response.addButton("❌ Отменить", "cancel_action");
                } catch (NumberFormatException e) {
                    response.setText("❌ Пожалуйста, введите число (например, 50000):");
                    response.addButton("❌ Отменить", "cancel_action");
                }
            }

            case AWAITING_ELDER_LOCATION -> {
                tempElder.setPreferredLocation(truncateText(text, 100));
                stateService.setState(userId, DialogState.AWAITING_ELDER_PHONE);

                User user = userService.findByTelegramId(userId).orElse(null);

                response = new UniversalResponse();

                // Проверяем: ОПЕРАТОР, МЕНЕДЖЕР или АДМИН
                boolean isOperator = user != null &&
                        (user.getAccessLevel() == AccessLevel.OPERATOR ||
                                user.getAccessLevel() == AccessLevel.MANAGER ||
                                user.getAccessLevel() == AccessLevel.ADMIN);

                if (isOperator) {
                    // Для операторов, менеджеров и админов - ТОЛЬКО ручной ввод
                    response.setText("📱 Введите номер телефона КЛИЕНТА для связи\n\n" +
                            "Укажите актуальный номер, по которому можно связаться с семьёй:");
                    response.addButton("✏️ Ввести номер", "enter_phone_manually");
                    response.addButton("❌ Отменить", "cancel_action");
                } else {
                    // Для клиентов (GUEST) - ручной ввод ИЛИ из MAX
                    response.setText("📱 Введите номер телефона для связи\n\n" +
                            "Вы можете ввести номер вручную или поделиться из MAX:");
                    response.addButton("✏️ Ввести вручную", "enter_phone_manually");
                    response.addButton("📱 Отправить номер из MAX", "request_contact_from_max");
                    response.addButton("❌ Отменить", "cancel_action");
                }

                return response;
            }

            case AWAITING_ELDER_PHONE_MANUAL -> {
                String cleanPhone = text.replaceAll("[^0-9+]", "");
                // Ограничение: длина телефона 10-15 символов
                if (!cleanPhone.matches("^[+]?[0-9]{10,15}$")) {
                    response.setText("❌ Неверный формат. Введите номер в формате +7 999 123-45-67:");
                    response.addButton("❌ Отменить", "cancel_action");
                    break;
                }
                tempElder.setClientPhone(cleanPhone);
                stateService.setState(userId, DialogState.AWAITING_ELDER_REQUIREMENTS);
                response.setText("📝 Введите особые пожелания (до 255 символов):\n" +
                        "Например: первый этаж, диетическое питание, отдельная палата");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case AWAITING_ELDER_REQUIREMENTS -> {
                tempElder.setRequirements(truncateText(text, 255));
                tempElder.setClientTelegramId(userId);
                tempElder.setExpiresAt(LocalDateTime.now().plusDays(14));

                User user = getUserOrNull(userId);

                // ===== ОПРЕДЕЛЯЕМ СТАТУС =====
                if (user != null && isGuest(user)) {
                    tempElder.setStatus(ElderStatus.PENDING);
                } else if (user != null && isOperator(user)) {
                    tempElder.setCreatedBy(userId);
                    tempElder.setCareHomeId(user.getCareHomeId());
                    tempElder.setAssignedOperatorId(null);             // ← НЕ НАЗНАЧАЕМ!
                    tempElder.setStatus(ElderStatus.NEW);              // ← НОВАЯ!
                    tempElder.setTakenAt(null);
                } else if (user != null && isManager(user)) {
                    tempElder.setCreatedBy(userId);
                    tempElder.setCareHomeId(user.getCareHomeId());
                    tempElder.setAssignedOperatorId(null);
                    tempElder.setStatus(ElderStatus.NEW);
                    tempElder.setTakenAt(null);
                } else if (user != null && isAdmin(user)) {
                    tempElder.setCreatedBy(userId);
                    tempElder.setStatus(ElderStatus.NEW);
                    tempElder.setAssignedOperatorId(null);
                } else {
                    tempElder.setStatus(ElderStatus.NEW);
                    tempElder.setAssignedOperatorId(null);
                }

                elderService.createElder(tempElder);
                stateService.clearState(userId);

                // ============================================================
                // ===== РАССЫЛКА ОПЕРАТОРАМ (ЕСЛИ ЗАЯВКА НОВАЯ) =====
                // ============================================================
                if (tempElder.getStatus() == ElderStatus.NEW ||
                        tempElder.getStatus() == ElderStatus.OFFERED) {
                    notificationService.notifyOperators(tempElder);
                    log.info("📢 Рассылка операторам отправлена для заявки #{}", tempElder.getId());
                }

                // ===== ОТВЕТ =====
                if (user != null && isGuest(user)) {
                    notifyAdminsAboutNewElder(tempElder);
                    response.setText("✅ **Заявка отправлена на модерацию!**\n\n" +
                            "📋 **Номер заявки:** #" + tempElder.getId() + "\n" +
                            "👤 **Подопечный:** " + tempElder.getFullName() + "\n" +
                            "⏳ **Статус:** На модерации");
                    response.addButton("👤 Моя заявка", "my_request");
                    response.addButton("🏠 Главное меню", "main_menu");
                } else {
                    response.setText("✅ **Заявка создана!**\n\n" +
                            "📋 **Номер заявки:** #" + tempElder.getId() + "\n" +
                            "👤 **Подопечный:** " + tempElder.getFullName() + "\n" +
                            "💰 **Бюджет:** " + tempElder.getBudget() + " руб.\n" +
                            "📍 **Локация:** " + tempElder.getPreferredLocation());
                    response.addButton("📋 Мои заявки", "my_requests");
                    response.addButton("🏠 Главное меню", "main_menu");
                }
            }
            // ===== РЕДАКТИРОВАНИЕ ЗАЯВКИ =====
            // ===== РЕДАКТИРОВАНИЕ ЗАЯВКИ =====
            // ===== РЕДАКТИРОВАНИЕ ЗАЯВКИ =====
            case EDITING_ELDER_NAME -> {
                // Если пользователь ввёл текст — сохраняем его
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_field")) {
                    tempElder.setFullName(truncateText(text.trim(), 100));
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
                    tempElder.setHealthCondition(truncateText(text.trim(), 255));
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
                    tempElder.setPreferredLocation(truncateText(text.trim(), 100));
                }
                stateService.setState(userId, DialogState.EDITING_ELDER_REQUIREMENTS);
                response.setText("✏️ Введите новые пожелания (текущие: " + tempElder.getRequirements() + "):");
                response.addButton("⏭️ Оставить как есть", "skip_edit_field");
                response.addButton("❌ Отменить", "cancel_action");
            }

            case EDITING_ELDER_REQUIREMENTS -> {
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_field")) {
                    tempElder.setRequirements(truncateText(text.trim(), 255));
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

    private boolean isOperator(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.OPERATOR;
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
    /**
     * Обрабатывает удаление заявки автором
     */
    /**
     * Обрабатывает удаление заявки автором
     */
    public UniversalResponse handleDeleteElder(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // Проверяем, что пользователь — автор
        if (elder.getCreatedBy() == null || !elder.getCreatedBy().equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете удалить эту заявку.");
        }

        // Проверяем, что заявка не завершена и не удалена
        if (elder.getStatus() == ElderStatus.COMPLETED ||
                elder.getStatus() == ElderStatus.DELETED ||
                elder.getStatus() == ElderStatus.EXPIRED) {
            return responseWithMainMenu("❌ Заявка уже завершена или удалена.");
        }

        // ===== ЕСЛИ ЗАЯВКА В РАБОТЕ — СПИСЫВАЕМ БАЛЛЫ =====
        if (elder.getAssignedOperatorId() != null) {
            User author = userService.findById(userId);
            if (author != null) {
                // Списываем 3 балла
                author.addBonusPoints(-3);
                userService.saveUser(author);

                // Уведомляем оператора, который вёл заявку
                User operator = userService.findById(elder.getAssignedOperatorId());
                if (operator != null) {
                    try {
                        UniversalResponse notification = new UniversalResponse(
                                "❌ **Заявка #" + elderId + " удалена автором.**\n\n" +
                                        "📋 Заявка была у вас в работе.\n" +
                                        "💬 Автор удалил заявку. Баллы за неё списаны."
                        );
                        notification.addButtonFullRow("📋 Мои заявки", "my_requests");
                        notification.addButtonFullRow("🏠 Главное меню", "main_menu");

                        Long chatId = operator.getChatId() != null ? operator.getChatId() : operator.getTelegramId();
                        messageSender.sendMessage(chatId, notification);
                    } catch (Exception e) {
                        log.error("❌ Ошибка отправки уведомления оператору: {}", e.getMessage());
                    }
                }
            }
        }

        // ===== УДАЛЯЕМ ЗАЯВКУ =====
        elderService.deleteElder(elder.getId());

      //  elder.setStatus(ElderStatus.DELETED);
      //  elder.setUpdatedAt(LocalDateTime.now());
      //  elderService.updateElder(elder);

        // Очищаем состояние
        stateService.clearState(userId);

        UniversalResponse response = new UniversalResponse(
                "✅ **Заявка #" + elderId + " удалена.**\n\n" +
                        (elder.getAssignedOperatorId() != null ?
                                "⚠️ Заявка была в работе. С вашего счёта списано 3 балла." :
                                "Заявка успешно удалена.")
        );
        response.addButtonFullRow("📋 Мои заявки", "my_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    /**
     * Подтверждение удаления заявки
     */
    public UniversalResponse confirmDeleteElder(Long userId, boolean confirm) {
        if (!confirm) {
            return responseWithMainMenu("❌ Удаление отменено.");
        }

        Elder elder = stateService.getTempElder(userId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // Проверяем, что пользователь — автор
        if (elder.getCreatedBy() == null || !elder.getCreatedBy().equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете удалить эту заявку.");
        }

        // Если заявка в работе — списываем баллы
        if (elder.getAssignedOperatorId() != null) {
            User author = userService.findById(userId);
            if (author != null) {
                author.addBonusPoints(-3);
                userService.saveUser(author);

                // Уведомляем оператора
                try {
                    User operator = userService.findById(elder.getAssignedOperatorId());
                    if (operator != null) {
                        UniversalResponse notification = new UniversalResponse(
                                "❌ **Заявка #" + elder.getId() + " удалена автором.**\n\n" +
                                        "📋 Заявка была у вас в работе.\n" +
                                        "💬 Автор удалил заявку. Баллы за неё списаны."
                        );
                        notification.addButtonFullRow("📋 Мои заявки", "my_requests");
                        notification.addButtonFullRow("🏠 Главное меню", "main_menu");

                        Long chatId = operator.getChatId() != null ? operator.getChatId() : operator.getTelegramId();
                        messageSender.sendMessage(chatId, notification);
                    }
                } catch (Exception e) {
                    log.error("❌ Ошибка отправки уведомления оператору: {}", e.getMessage());
                }
            }
        }

        // ===== УДАЛЯЕМ СВЯЗАННЫЕ ДАННЫЕ =====
        try {
            reactionRepository.deleteByElderId(elder.getId());
        } catch (Exception e) {
            log.warn("⚠️ Не удалось удалить реакции для заявки {}: {}", elder.getId(), e.getMessage());
        }

        try {
            ratingRepository.deleteByElderId(elder.getId());
        } catch (Exception e) {
            log.warn("⚠️ Не удалось удалить оценки для заявки {}: {}", elder.getId(), e.getMessage());
        }

        // ===== ПОЛНОЕ УДАЛЕНИЕ ЗАЯВКИ =====
        elderService.deleteElder(elder.getId());
        stateService.clearState(userId);

        UniversalResponse response = new UniversalResponse(
                "✅ **Заявка #" + elder.getId() + " удалена.**\n\n" +
                        (elder.getAssignedOperatorId() != null ?
                                "⚠️ Заявка была в работе. С вашего счёта списано 3 балла." :
                                "Заявка успешно удалена.")
        );
        response.addButtonFullRow("📋 Мои заявки", "my_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
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
    /**
     * Обрезает текст до указанной длины
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength);
    }
    private boolean isManager(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.MANAGER;
    }
    private boolean isAdmin(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.ADMIN;
    }
}
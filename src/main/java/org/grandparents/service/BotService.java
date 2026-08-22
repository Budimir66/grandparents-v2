package org.grandparents.service;

import org.grandparents.bot.max.MaxWebhookHandler;
import org.grandparents.dto.UniversalMessage;
import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.*;
import org.grandparents.repository.OperatorReactionRepository;
import org.grandparents.statemachine.DialogState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    private final UserService userService;
    private final ElderService elderService;
    private final CareHomeService careHomeService;
    private final UserStateService stateService;
    private final NotificationService notificationService;
    private final OperatorReactionRepository reactionRepository;
    private final MessageSender messageSender;
    private final YandexMapsService yandexMapsService;
    private final BonusSettingService bonusSettingService;
    private final MaxWebhookHandler maxWebhookHandler;
    private final ElderFormService elderFormService;
    private final CareHomeManagementService careHomeManagementService;
    private final AdminService adminService;
    private final UserSettingsService userSettingsService;
    private final OperatorService operatorService;
    private final StatisticsService statisticsService;
    private final InvitationService invitationService;

    public BotService(UserService userService,
                      ElderService elderService,
                      CareHomeService careHomeService,
                      UserStateService stateService,
                      NotificationService notificationService,
                      OperatorReactionRepository reactionRepository,
                      MessageSender messageSender,
                      YandexMapsService yandexMapsService,
                      BonusSettingService bonusSettingService,
                      @Lazy MaxWebhookHandler maxWebhookHandler,
                      ElderFormService elderFormService,
                      CareHomeManagementService careHomeManagementService,
                      AdminService adminService,
                      UserSettingsService userSettingsService,
                      OperatorService operatorService,
                      StatisticsService statisticsService,
                      InvitationService invitationService) {
        this.userService = userService;
        this.elderService = elderService;
        this.careHomeService = careHomeService;
        this.stateService = stateService;
        this.notificationService = notificationService;
        this.reactionRepository = reactionRepository;
        this.messageSender = messageSender;
        this.yandexMapsService = yandexMapsService;
        this.bonusSettingService = bonusSettingService;
        this.maxWebhookHandler = maxWebhookHandler;
        this.elderFormService = elderFormService;
        this.careHomeManagementService = careHomeManagementService;
        this.adminService = adminService;
        this.userSettingsService = userSettingsService;
        this.operatorService = operatorService;
        this.statisticsService = statisticsService;
        this.invitationService = invitationService;
    }

    // ============================================================
    // ===== ОСНОВНОЙ МЕТОД ОБРАБОТКИ СООБЩЕНИЙ =====
    // ============================================================

    public UniversalResponse handleMessage(UniversalMessage message) {
        String text = message.getText();
        String callbackData = message.getCallbackData();
        Long userId = Long.parseLong(message.getUserId());
        String chatId = message.getChatId();

        // ===== ПРОВЕРКА: НЕ ПЫТАЕТСЯ ЛИ ОПЕРАТОР АКТИВИРОВАТЬ ПРИГЛАШЕНИЕ =====
        // ===== ПРОВЕРКА: НЕ ПЫТАЕТСЯ ЛИ ОПЕРАТОР АКТИВИРОВАТЬ ПРИГЛАШЕНИЕ =====
        if (text != null && !text.isEmpty() && !text.startsWith("/")) {
            CareHome careHome = careHomeService.findByNameExact(text.trim());
            if (careHome != null && invitationService.hasActiveInvitation(careHome.getId())) {
                return handleAcceptInvitation(userId, careHome.getId());
            }
        }

        // ===== СОЗДАЁМ ПОЛЬЗОВАТЕЛЯ, ЕСЛИ ЕГО НЕТ =====
        User user = userService.findByTelegramId(userId).orElse(null);
        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.setTelegramId(userId);
            user.setAccessLevel(AccessLevel.GUEST);
            user.setChatId(Long.parseLong(chatId));
            user.setRegistered(false);
            user.setActive(true);
            userService.saveUser(user);
            isNewUser = true;
            log.info("👤 Создан новый пользователь: {}", userId);
        } else {
            // Обновляем chatId на всякий случай (может измениться)
            user.setChatId(Long.parseLong(chatId));
            userService.saveUser(user);
        }

        // ===== НОВЫЙ ПОЛЬЗОВАТЕЛЬ ИЛИ /start — ПОКАЗЫВАЕМ МЕНЮ =====
        if (isNewUser || "/start".equalsIgnoreCase(text)) {
            return handleStartCommand(userId, chatId);
        }

        // ===== ОБРАБОТКА КНОПОК =====
        if (callbackData != null) {
            return handleCallback(userId, chatId, callbackData);
        }

        // ===== ОБРАБОТКА ДИАЛОГА (АНКЕТА) =====
        DialogState state = stateService.getState(userId);
        if (state != DialogState.START) {
            return handleDialogInput(userId, chatId, text, state);
        }

        // ===== ЕСЛИ НЕТ ДИАЛОГА — ПОКАЗЫВАЕМ МЕНЮ =====
        return handleStartCommand(userId, chatId);
    }

    // ============================================================
    // ===== ГОТОВЫЕ ОТВЕТЫ (хелперы) =====
    // ============================================================

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

    // ============================================================
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================================

    private User getUserOrNull(Long userId) {
        return userService.findByTelegramId(userId).orElse(null);
    }

    private Elder getElderOrNull(Long elderId) {
        return elderService.findById(elderId);
    }

    private boolean isAdmin(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.ADMIN;
    }

    private boolean isOperator(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.OPERATOR;
    }

    private boolean isGuest(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.GUEST;
    }

    private String getCareHomeName(Long careHomeId) {
        if (careHomeId == null) return "не указан";
        CareHome careHome = careHomeService.findById(careHomeId);
        return careHome != null ? careHome.getName() : "не указан";
    }

    private String getStatusIcon(ElderStatus status) {
        if (status == null) return "❓";
        return switch (status) {
            case NEW -> "🟢";
            case OFFERED -> "🟡";
            case IN_PROGRESS -> "🟠";
            case ACCEPTED -> "🔵";
            case COMPLETED -> "✅";
            case DELETED -> "❌";
            case EXPIRED -> "⏰";
            case EDITED -> "✏️";
            case PENDING -> "⏳";
            default -> "❓";
        };
    }

    private String getCareHomeStatusIcon(String status) {
        if (status == null) return "❓";
        return switch (status) {
            case "PENDING" -> "⏳";
            case "APPROVED" -> "✅";
            case "REJECTED" -> "❌";
            case "INACTIVE" -> "🔴";
            default -> "❓";
        };
    }

    private void sendNotification(Long userId, String text, String... buttons) {
        try {
            User user = getUserOrNull(userId);
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

    // ============================================================
    // ===== ОБРАБОТКА КОМАНДЫ /start =====
    // ============================================================

    private UniversalResponse handleStartCommand(Long userId, String chatId) {
        User user = getUserOrNull(userId);
        log.debug("🔍 [ДЕБАГ] userId={}, user={}, accessLevel={}",
                userId,
                user != null ? user.getFirstName() : "null",
                user != null ? user.getAccessLevel() : "null");

        boolean isGuest = isGuest(user);
        boolean isOperator = isOperator(user);
        boolean isAdmin = isAdmin(user);

        log.debug("🔍 [ДЕБАГ] isGuest={}, isOperator={}, isAdmin={}", isGuest, isOperator, isAdmin);

        List<Elder> elders = elderService.findByClientTelegramId(userId);
        boolean hasActiveRequest = elders.stream()
                .anyMatch(e -> e.getStatus() != ElderStatus.COMPLETED &&
                        e.getStatus() != ElderStatus.EXPIRED &&
                        e.getStatus() != ElderStatus.DELETED);

        UniversalResponse response = new UniversalResponse();

        // ===== ГОСТЬ БЕЗ ЗАЯВКИ =====
        if (isGuest && !hasActiveRequest) {
            response.setText("""
                    Добро пожаловать в наш бот!!!

                    Бесплатный поиск и подбор пансионата

                    Как мы работаем:
                    1. Вы создаёте заявку с анкетой о своём подопечном
                    Вы заполняете анкету
                    Только Вы можете удалять заявку и редактировать её
                    Пансионаты связываются с Вами
                    Вы сами выбираете какой пансионат Вам подходит

                    Заполните заявку сейчас!!!
                    И с Вами свяжутся пансионаты
                    в ближайшее время!!!

                    ⬇️ жмите кнопку заявка""");
            response.addButton("📝 Создать заявку", "new_request");
            response.addButton("🏢 Список пансионатов", "list_carehomes");
            response.addButton("❓ Помощь", "help");
            return response;
        }
// ===== ДИРЕКТОР ПАНСИОНАТА (MANAGER) =====
        if (user.getAccessLevel() == AccessLevel.MANAGER) {
            response.setText("🏢 **Панель менеджера**\n\n" +
                    "Вы управляете пансионатом:\n" +
                    "📌 Вы можете регистрировать операторов\n" +
                    "📌 Управлять пансионатом\n" +
                    "📌 Просматривать статистику\n\n" +
                    "Выберите действие:");

            response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
            response.addButtonFullRow("👥 Операторы", "manager_operators");
            response.addButtonFullRow("📋 Заявки", "menu_requests");
            response.addButtonFullRow("📊 Статистика", "manager_stats");
            response.addButtonFullRow("❓ Помощь", "help");  // ← ТОЛЬКО ОДНА КНОПКА
            return response;
        }

        // ===== ГОСТЬ С ЗАЯВКОЙ =====
        if (isGuest && hasActiveRequest) {
            Elder activeElder = elders.stream()
                    .filter(e -> e.getStatus() != ElderStatus.COMPLETED &&
                            e.getStatus() != ElderStatus.EXPIRED &&
                            e.getStatus() != ElderStatus.DELETED)
                    .findFirst().orElse(null);

            String statusText = activeElder != null ? activeElder.getStatus().getDisplayName() : "неизвестен";
            String elderName = activeElder != null ? activeElder.getFullName() : "не указано";

            response.setText("✅ **У вас есть активная заявка!**\n\n" +
                    "📌 **Статус:** " + statusText + "\n" +
                    "👤 **Подопечный:** " + elderName + "\n\n" +
                    "Операторы помнят о вас. Скоро с вами свяжутся.");
            response.addButton("👤 Моя заявка", "my_request");
            response.addButton("🏢 Список пансионатов", "list_carehomes");
            response.addButton("🏠 Главное меню", "main_menu");
            return response;
        }

        // ===== ОПЕРАТОР =====
        if (isOperator) {
            int newRequests = (int) elderService.countActiveElders();
            int bonus = user.getBonusPoints();

            response.setText("📊 **Вы вошли как оператор**\n\n" +
                    "💰 **Ваш баланс:** " + bonus + " баллов\n" +
                    "📨 **Новых заявок:** " + newRequests + "\n\n" +
                    "Хорошей работы! 🚀");
            response.addButton("📋 Заявки", "menu_requests");
            response.addButton("🏢 Мои пансионаты", "my_carehomes");
            response.addButton("📊 Моя статистика", "my_stats");
            response.addButton("👤 Мой профиль", "my_profile");  // ← НОВАЯ КНОПКА
            response.addButton("❓ Помощь", "help");
            return response;
        }
        // ===== АДМИНИСТРАТОР =====
        if (isAdmin) {
            response.setText("⚙️ **Админ-панель**\n\nУправление системой:");
            response.addButton("📋 Заявки", "menu_requests");
            response.addButton("🏢 Пансионаты", "menu_carehomes");
            response.addButton("👥 Управление операторами", "admin_operators");
            response.addButton("🏢 Управление пансионатами", "admin_carehomes_menu");
            response.addButton("📋 Модерация заявок", "admin_elder_moderation");
            response.addButton("📊 Общая статистика", "admin_stats");
            response.addButton("💰 Настройка бонусов", "admin_bonus_settings");
            response.addButton("🏠 Главное меню", "main_menu");
            return response;
        }

        // ===== ЗАЩИТА ОТ NULL =====
        response.setText("🏠 **Главное меню**\n\nВыберите раздел:");
        response.addButton("📋 Заявки", "menu_requests");
        response.addButton("🏢 Пансионаты", "menu_carehomes");
        response.addButton("❓ Помощь", "help");
        return response;
    }

    // ============================================================
    // ===== ОБРАБОТКА КНОПОК (CALLBACK) =====
    // ============================================================

    private UniversalResponse handleCallback(Long userId, String chatId, String callbackData) {
        User user = getUserOrNull(userId);
        boolean isOperator = isOperator(user);
        boolean isAdmin = isAdmin(user);
        boolean isGuest = isGuest(user);

        log.info("📥 [CALLBACK] userId={}, callbackData={}", userId, callbackData);

        if (callbackData.equals("help")) {
            return showHelp(userId);
        }
// ===== ПРИГЛАШЕНИЕ ОПЕРАТОРА =====
        if (callbackData.equals("invite_operator")) {
            return careHomeManagementService.inviteOperator(userId);
        }
        // ===== ВЫБОР ПАНСИОНАТА ДЛЯ ПРИГЛАШЕНИЯ =====
        if (callbackData.startsWith("invite_carehome_")) {
            String param = callbackData.substring("invite_carehome_".length());
            if ("all".equals(param)) {
                return careHomeManagementService.inviteOperator(userId);
            } else {
                Long careHomeId = Long.parseLong(param);
                return careHomeManagementService.inviteOperator(userId);
            }
        }

// ===== ПРИНЯТИЕ ОФЕРТЫ ПЕРЕД АНКЕТОЙ =====
        if (callbackData.equals("accept_consent_before_form")) {
            DialogState currentState = stateService.getState(userId);
            if (currentState != DialogState.AWAITING_CONSENT_BEFORE_FORM) {
                return responseWithMainMenu("❌ Нет активной заявки для подтверждения.");
            }

            Elder tempElder = stateService.getTempElder(userId);
            if (tempElder != null) {
                tempElder.setConsentGiven(true);
                tempElder.setConsentGivenAt(LocalDateTime.now());
                stateService.setTempElder(userId, tempElder);
            }

            stateService.setState(userId, DialogState.AWAITING_ELDER_NAME);
            UniversalResponse response = new UniversalResponse(
                    "📝 Начинаем создание заявки!\n\n" +
                            "✅ Согласие на обработку данных получено.\n\n" +
                            "Введите полное имя подопечного (например: Иванова Анна Петровна):"
            );
            response.addButton("❌ Отменить", "cancel_action");
            return response;
        }

// ===== ОТКАЗ ОТ ОФЕРТЫ =====
        if (callbackData.equals("decline_consent_before_form")) {
            stateService.clearState(userId);
            return responseWithMainMenu("❌ Вы отклонили согласие на обработку данных.\n\n" +
                    "Заявка не будет создана.\n\n" +
                    "Если передумаете, нажмите 'Создать заявку' заново.");
        }
        // ===== ПРИНЯТИЕ СОГЛАСИЯ =====
        if (callbackData.equals("accept_consent")) {
            // Проверяем, что пользователь в процессе создания заявки
            DialogState currentState = stateService.getState(userId);
            if (currentState != DialogState.AWAITING_CONSENT) {
                return responseWithMainMenu("❌ Нет активной заявки для подтверждения.");
            }

            // Получаем временную заявку
            Elder tempElder = stateService.getTempElder(userId);
            if (tempElder == null) {
                return responseWithMainMenu("❌ Ошибка: заявка не найдена.");
            }

            // ===== СОХРАНЯЕМ ЗАЯВКУ =====
            tempElder.setConsentGiven(true);
            tempElder.setConsentGivenAt(LocalDateTime.now());

            user = getUserOrNull(userId);
            if (user != null && user.getAccessLevel() == AccessLevel.GUEST) {
                tempElder.setStatus(ElderStatus.PENDING);
            } else if (user != null && user.getAccessLevel() == AccessLevel.OPERATOR) {
                tempElder.setCreatedBy(userId);
                tempElder.setCareHomeId(user.getCareHomeId());
                tempElder.setStatus(ElderStatus.OFFERED);
            } else {
                tempElder.setStatus(ElderStatus.NEW);
            }

            elderService.createElder(tempElder);
            stateService.clearState(userId);

            // ===== УВЕДОМЛЯЕМ АДМИНИСТРАТОРА =====
            if (user != null && user.getAccessLevel() == AccessLevel.GUEST) {
                notifyAdminsAboutNewElder(tempElder);
                UniversalResponse response = new UniversalResponse(
                        "✅ **Заявка отправлена на модерацию!**\n\n" +
                                "📋 **Номер заявки:** #" + tempElder.getId() + "\n" +
                                "👤 **Подопечный:** " + tempElder.getFullName() + "\n" +
                                "⏳ **Статус:** На модерации\n\n" +
                                "📋 **Согласие на обработку данных принято** ✅\n\n" +
                                "Администратор рассмотрит заявку в ближайшее время.\n" +
                                "Вы получите уведомление о решении."
                );
                response.addButtonFullRow("👤 Моя заявка", "my_request");
                response.addButtonFullRow("🏠 Главное меню", "main_menu");
                return response;
            }

            // Для оператора
            UniversalResponse response = new UniversalResponse(
                    "✅ **Заявка создана!**\n\n" +
                            "📋 **Номер заявки:** #" + tempElder.getId() + "\n" +
                            "👤 **Подопечный:** " + tempElder.getFullName() + "\n" +
                            "💰 **Бюджет:** " + tempElder.getBudget() + " руб.\n" +
                            "📍 **Локация:** " + tempElder.getPreferredLocation() + "\n" +
                            "📋 **Согласие на обработку данных принято** ✅"
            );
            response.addButtonFullRow("📋 Мои заявки", "my_requests");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

// ===== ОТКАЗ ОТ СОГЛАСИЯ =====
        if (callbackData.equals("decline_consent")) {
            stateService.clearState(userId);
            return responseWithMainMenu("❌ Вы отклонили согласие на обработку данных.\n\n" +
                    "Заявка не будет создана.\n\n" +
                    "Если передумаете, начните создание заявки заново.");
        }

        // ===== АДМИН: ПРОСМОТР ОПЕРАТОРА =====
        if (callbackData.startsWith("admin_view_operator_")) {
            String operatorIdStr = callbackData.substring("admin_view_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return adminService.showOperatorCardForAdmin(userId, operatorId);
        }

// ===== АДМИН: ДОБАВИТЬ БАЛЛ =====
        if (callbackData.startsWith("admin_add_bonus_")) {
            String operatorIdStr = callbackData.substring("admin_add_bonus_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return adminService.addBonusToOperator(userId, operatorId);
        }

// ===== АДМИН: УБРАТЬ БАЛЛ =====
        if (callbackData.startsWith("admin_remove_bonus_")) {
            String operatorIdStr = callbackData.substring("admin_remove_bonus_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return adminService.removeBonusFromOperator(userId, operatorId);
        }

// ===== АДМИН: БЛОКИРОВКА ОПЕРАТОРА =====
        if (callbackData.startsWith("admin_block_operator_")) {
            String operatorIdStr = callbackData.substring("admin_block_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return adminService.blockOperator(userId, operatorId);
        }

// ===== АДМИН: РАЗБЛОКИРОВКА ОПЕРАТОРА =====
        if (callbackData.startsWith("admin_unblock_operator_")) {
            String operatorIdStr = callbackData.substring("admin_unblock_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return adminService.unblockOperator(userId, operatorId);
        }

// ===== АДМИН: ПАГИНАЦИЯ =====
        if (callbackData.equals("admin_operators_prev")) {
            int currentPage = stateService.getCurrentPage(userId);
            stateService.setCurrentPage(userId, Math.max(0, currentPage - 1));
            return adminService.showOperatorsList(userId);
        }
        if (callbackData.equals("admin_operators_next")) {
            int currentPage = stateService.getCurrentPage(userId);
            stateService.setCurrentPage(userId, currentPage + 1);
            return adminService.showOperatorsList(userId);
        }

        // ===== ПРОПУСТИТЬ ШАГ РЕДАКТИРОВАНИЯ ПРОФИЛЯ =====
        if (callbackData.equals("skip_edit_profile")) {
            DialogState currentState = stateService.getState(userId);
            if (currentState == null) {
                return responseWithMainMenu("❌ Нет активного редактирования.");
            }
            return handleDialogInput(userId, null, "skip_edit_profile", currentState);
        }

        // ===== МОЙ ПРОФИЛЬ =====
        if (callbackData.equals("my_profile")) {
            return showOperatorProfile(userId);
        }

// ===== РЕДАКТИРОВАНИЕ ПРОФИЛЯ =====
        if (callbackData.equals("edit_profile")) {
            stateService.setState(userId, DialogState.EDITING_PROFILE_NAME);
            UniversalResponse response = new UniversalResponse(
                    "✏️ **Редактирование профиля**\n\n" +
                            "👤 Текущее имя: " + user.getFirstName() + "\n\n" +
                            "Введите **новое имя** (или нажмите 'Оставить без изменений'):"
            );
            response.addButton("⏭️ Оставить как есть", "skip_edit_profile");
            response.addButton("❌ Отменить", "cancel_action");
            return response;
        }

        // ===== ОТКАЗ ОТ ЗАКРЫТИЯ ЗАЯВКИ =====
        if (callbackData.startsWith("reject_complete_elder_")) {
            String elderIdStr = callbackData.substring("reject_complete_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            Elder elder = elderService.findById(elderId);
            if (elder != null) {
                elder.setStatus(ElderStatus.IN_PROGRESS);
                elderService.updateElder(elder);

                // Уведомляем оператора
                if (elder.getAssignedOperatorId() != null) {
                    User operator = getUserOrNull(elder.getAssignedOperatorId());
                    if (operator != null) {
                        sendNotification(operator.getTelegramId(),
                                "❌ **Клиент отклонил запрос на закрытие заявки #" + elderId + "**\n\n" +
                                        "Заявка возвращена в статус **В работе**.\n\n" +
                                        "Свяжитесь с клиентом для уточнения деталей.",
                                "📋 Мои заявки", "my_requests"
                        );
                    }
                }
            }
            return responseWithMainMenu("❌ Запрос на закрытие отклонён.\n\nЗаявка возвращена в работу.");
        }

        // ===== ПЕРЕКЛЮЧЕНИЕ ФИЛЬТРА ПО ДАТЕ (КАК ТУМБЛЕР) =====
        // ===== ПЕРЕКЛЮЧЕНИЕ ФИЛЬТРА ПО ДАТЕ =====
        if (callbackData.equals("filter_today_toggle")) {
            boolean current = stateService.isFilterTodayOnly(userId);
            boolean newValue = !current;
            stateService.setFilterTodayOnly(userId, newValue);
            log.info("📅 Фильтр 'Только за сегодня' переключен: {} -> {}", current, newValue);
            return handleFindRequests(userId);
        }

        // ===== ВКЛЮЧИТЬ ФИЛЬТР "ТОЛЬКО ЗА СЕГОДНЯ" =====
        if (callbackData.equals("filter_today_on")) {
            stateService.setFilterTodayOnly(userId, true);
            log.info("📅 Фильтр 'Только за сегодня' ВКЛЮЧЕН");
            return handleFindRequests(userId);
        }

// ===== ВЫКЛЮЧИТЬ ФИЛЬТР "ТОЛЬКО ЗА СЕГОДНЯ" =====
        if (callbackData.equals("filter_today_off")) {
            stateService.setFilterTodayOnly(userId, false);
            log.info("📅 Фильтр 'Только за сегодня' ВЫКЛЮЧЕН");
            return handleFindRequests(userId);
        }

        // ===== СБРОС ФИЛЬТРА ГОРОДА =====
        if (callbackData.equals("settings_city_clear")) {
            user = getUserOrNull(userId);
            if (user != null) {
                user.setPreferredCity(null);
                userService.saveUser(user);
            }
            // ===== ВОЗВРАЩАЕМ В ГЛАВНОЕ МЕНЮ НАСТРОЕК =====
            return userSettingsService.showSettingsMenu(userId);
        }

// ===== СБРОС ФИЛЬТРА РЕГИОНА =====
        if (callbackData.equals("settings_region_clear")) {
            user = getUserOrNull(userId);
            if (user != null) {
                user.setPreferredRegion(null);
                userService.saveUser(user);
            }
            // ===== ВОЗВРАЩАЕМ В ГЛАВНОЕ МЕНЮ НАСТРОЕК =====
            return userSettingsService.showSettingsMenu(userId);
        }

// ===== СБРОС ФИЛЬТРА БЮДЖЕТА =====
        if (callbackData.equals("settings_budget_clear")) {
            user = getUserOrNull(userId);
            if (user != null) {
                user.setBudgetMin(null);
                user.setBudgetMax(null);
                userService.saveUser(user);
            }
            // ===== ВОЗВРАЩАЕМ В ГЛАВНОЕ МЕНЮ НАСТРОЕК =====
            return userSettingsService.showSettingsMenu(userId);
        }

        // ===== НАСТРОЙКА РЕГИОНА =====
        if (callbackData.equals("settings_region")) {
            return userSettingsService.handleSettingsRegion(userId);
        }

        // ===== ОЧИСТКА ФИЛЬТРОВ =====
        if (callbackData.equals("clear_filters")) {
            user = getUserOrNull(userId);
            if (user != null) {
                user.setPreferredCity(null);
                user.setPreferredRegion(null);
                user.setBudgetMin(null);
                user.setBudgetMax(null);
                userService.saveUser(user);
            }
            return handleFindRequests(userId);
        }

        // ===== МОИ ЗАЯВКИ (ДЛЯ ОПЕРАТОРА) =====
        if (callbackData.equals("my_requests")) {
            return handleMyRequests(userId);
        }

        // ===== ПРОПУСТИТЬ ШАГ РЕДАКТИРОВАНИЯ ОПЕРАТОРА =====
        if (callbackData.equals("skip_edit_operator")) {
            Long operatorId = stateService.getEditingOperatorId(userId);
            if (operatorId == null) {
                return responseWithMainMenu("❌ Нет активного редактирования.");
            }

            DialogState currentState = stateService.getState(userId);
            if (currentState == null) {
                return responseWithMainMenu("❌ Нет активного редактирования.");
            }

            // Передаём управление с пустым текстом (означает "оставить как было")
            return elderFormService.handleFormInput(userId, "skip_edit_operator", currentState);
        }

// ===== РЕДАКТИРОВАНИЕ ОПЕРАТОРА =====
        if (callbackData.startsWith("edit_operator_")) {
            String operatorIdStr = callbackData.substring("edit_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return careHomeManagementService.startEditOperator(userId, operatorId);
        }

// ===== ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ ОПЕРАТОРА =====
        if (callbackData.startsWith("confirm_delete_operator_")) {
            String operatorIdStr = callbackData.substring("confirm_delete_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return careHomeManagementService.confirmDeleteOperator(userId, operatorId);
        }

// ===== УДАЛЕНИЕ ОПЕРАТОРА (ПОСЛЕ ПОДТВЕРЖДЕНИЯ) =====
        if (callbackData.startsWith("delete_operator_confirm_")) {
            String operatorIdStr = callbackData.substring("delete_operator_confirm_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return careHomeManagementService.deleteOperator(userId, operatorId);
        }

        // ===== СПИСОК ОПЕРАТОРОВ (ДЛЯ MANAGER) =====
        if (callbackData.equals("manager_operators")) {
            return careHomeManagementService.showOperatorsListForManager(userId);
        }

        // ===== ПРОСМОТР КАРТОЧКИ ОПЕРАТОРА =====
        if (callbackData.startsWith("view_operator_")) {
            String operatorIdStr = callbackData.substring("view_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return careHomeManagementService.showOperatorCard(userId, operatorId);
        }

        // ===== ВЫБОР ПАНСИОНАТА ДЛЯ РЕГИСТРАЦИИ ОПЕРАТОРА =====
        if (callbackData.startsWith("select_carehome_for_operator_")) {
            String param = callbackData.substring("select_carehome_for_operator_".length());

            if ("all".equals(param)) {
                // Вся сеть
                stateService.setEditingCareHomeId(userId, -1L);
            } else {
                Long careHomeId = Long.parseLong(param);
                stateService.setEditingCareHomeId(userId, careHomeId);
            }

            stateService.setState(userId, DialogState.AWAITING_OPERATOR_PHONE);

            UniversalResponse response = new UniversalResponse(
                    "📱 **Регистрация оператора**\n\n" +
                            "Введите номер телефона оператора в формате:\n" +
                            "+7 999 123-45-67"
            );
            response.addButtonFullRow("❌ Отменить", "cancel_action");
            return response;
        }

// ===== СПИСОК ОПЕРАТОРОВ ПАНСИОНАТА (ИЗ КАРТОЧКИ) =====
        if (callbackData.startsWith("carehome_operators_")) {
            String careHomeIdStr = callbackData.substring("carehome_operators_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return careHomeManagementService.showOperatorsForCarehome(userId, careHomeId);
        }
        // ===== УДАЛЕНИЕ ПАНСИОНАТА (ДЛЯ MANAGER/ADMIN) =====
        // ===== УДАЛЕНИЕ ПАНСИОНАТА (ЗАПРОС ПОДТВЕРЖДЕНИЯ) =====
        if (callbackData.startsWith("manager_carehome_delete_")) {
            String careHomeIdStr = callbackData.substring("manager_carehome_delete_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return careHomeManagementService.confirmDeleteCarehome(userId, careHomeId);
        }

        // ===== ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ ПАНСИОНАТА =====
        if (callbackData.startsWith("manager_carehome_delete_confirm_")) {
            String careHomeIdStr = callbackData.substring("manager_carehome_delete_confirm_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return careHomeManagementService.deleteCarehomeForManager(userId, careHomeId);
        }

        // ===== ПРОСМОТР КАРТОЧКИ ПАНСИОНАТА (ДЛЯ MANAGER/ADMIN) =====
        if (callbackData.startsWith("view_my_carehome_")) {
            String careHomeIdStr = callbackData.substring("view_my_carehome_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return careHomeManagementService.showCarehomeCardForManager(userId, careHomeId);
        }

// ===== СТАТИСТИКА ПО КОНКРЕТНОМУ ПАНСИОНАТУ (ИЗ КАРТОЧКИ) =====
        if (callbackData.startsWith("manager_stats_for_carehome_")) {
            String careHomeIdStr = callbackData.substring("manager_stats_for_carehome_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return statisticsService.showManagerStatsForCarehome(userId, careHomeId);
        }

        // ===== СТАТИСТИКА ДЛЯ MANAGER (СУММАРНАЯ ПО ВСЕМ ПАНСИОНАТАМ) =====
        if (callbackData.equals("manager_stats")) {
            return statisticsService.showManagerStats(userId);
        }

        // ===== АДМИН-ПАНЕЛЬ =====
        if (callbackData.equals("admin_menu")) {
            return adminService.showAdminMenu(userId);
        }

        // ===== РЕГИСТРАЦИЯ ОПЕРАТОРА =====
        if (callbackData.equals("register_operator")) {
            user = getUserOrNull(userId);
            if (user == null) {
                return responseWithMainMenu("❌ Пользователь не найден.");
            }

            // Проверяем, что пользователь MANAGER или ADMIN
            if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
                return responseWithMainMenu("❌ Только директор может регистрировать операторов.");
            }

            // Получаем все пансионаты MANAGER
            List<CareHome> careHomes = careHomeService.findByProposedBy(userId);
            if (user.getCareHomeId() != null) {
                CareHome careHome = careHomeService.findById(user.getCareHomeId());
                if (careHome != null && !careHomes.contains(careHome)) {
                    careHomes.add(careHome);
                }
            }

            if (careHomes.isEmpty()) {
                return responseWithMainMenu("❌ У вас нет пансионатов для регистрации оператора.");
            }

            // Если только один пансионат — сразу спрашиваем номер телефона
            if (careHomes.size() == 1) {
                stateService.setEditingCareHomeId(userId, careHomes.get(0).getId());
                stateService.setState(userId, DialogState.AWAITING_OPERATOR_PHONE);

                UniversalResponse response = new UniversalResponse(
                        "📱 **Регистрация оператора**\n\n" +
                                "Пансионат: **" + careHomes.get(0).getName() + "**\n\n" +
                                "Введите номер телефона оператора в формате:\n" +
                                "+7 999 123-45-67"
                );
                response.addButtonFullRow("❌ Отменить", "cancel_action");
                return response;
            }

            // Если несколько пансионатов — показываем список для выбора
            UniversalResponse response = new UniversalResponse(
                    "🏢 **Выберите пансионат для регистрации оператора:**\n\n" +
                            "📌 Оператор будет привязан к выбранному пансионату."
            );

            for (CareHome careHome : careHomes) {
                response.addButtonFullRow(careHome.getName(), "select_carehome_for_operator_" + careHome.getId());
            }

            // ===== КНОПКА "ВСЯ СЕТЬ" =====
            response.addButtonFullRow("🌐 Вся сеть", "select_carehome_for_operator_all");
            response.addButtonFullRow("❌ Отменить", "cancel_action");
            return response;
        }
        // ===== ПРИНЯТИЕ ОФЕРТЫ =====
        if (callbackData.equals("accept_offer")) {
            // Проверяем, что пользователь в процессе предложения пансионата
            DialogState currentState = stateService.getState(userId);
            if (currentState != DialogState.AWAITING_OFFER_ACCEPT) {
                return responseWithMainMenu("❌ Нет активного предложения пансионата.");
            }

            // Вызываем метод сохранения (с тем же кодом, который был в кейсе)
            return saveProposedCarehomeAfterOffer(userId);
        }

// ===== ОТКАЗ ОТ ОФЕРТЫ =====
        if (callbackData.equals("decline_offer")) {
            stateService.clearState(userId);
            return responseWithMainMenu("❌ Вы отклонили условия оферты.\n\n" +
                    "Предложение пансионата отменено.");
        }

// ===== ОТКАЗ ОТ ОФЕРТЫ =====
        if (callbackData.equals("decline_offer")) {
            stateService.clearState(userId);
            return responseWithMainMenu("❌ Вы отклонили условия оферты.\n\n" +
                    "Предложение пансионата отменено.");
        }

        // ===== ПРЕДЛОЖИТЬ ПАНСИОНАТ (ДЛЯ КЛИЕНТОВ) =====
        if (callbackData.equals("propose_carehome_client")) {
            // Запускаем процесс предложения пансионата (без проверки на активную заявку)
            return careHomeManagementService.startProposeCarehomeClient(userId);
        }

        // ===== ПАГИНАЦИЯ: ПРЕДЫДУЩАЯ СТРАНИЦА =====
        if (callbackData.equals("find_requests_prev")) {
            int currentPage = stateService.getCurrentPage(userId);
            stateService.setCurrentPage(userId, Math.max(0, currentPage - 1));
            return handleFindRequests(userId);
        }

// ===== ПАГИНАЦИЯ: СЛЕДУЮЩАЯ СТРАНИЦА =====
        if (callbackData.equals("find_requests_next")) {
            int currentPage = stateService.getCurrentPage(userId);
            stateService.setCurrentPage(userId, currentPage + 1);
            return handleFindRequests(userId);
        }

        // ===== ПРОПУСТИТЬ ШАГ РЕДАКТИРОВАНИЯ =====
        if (callbackData.equals("skip_edit_field")) {
            // Получаем текущее состояние пользователя
            DialogState currentState = stateService.getState(userId);
            if (currentState == null) {
                return responseWithMainMenu("❌ Нет активного редактирования.");
            }

            // Передаём управление в ElderFormService с пустым текстом
            // (пустой текст означает "оставить как было")
            return elderFormService.handleFormInput(userId, "", currentState);
        }

        // ===== ОТКЛОНЕНИЕ ЗАЯВКИ (АДМИН) =====
        if (callbackData.startsWith("reject_elder_")) {
            return handleRejectElder(userId, callbackData);
        }

        // ===== ПРОДЛИТЬ ЗАЯВКУ =====
        if (callbackData.startsWith("extend_elder_")) {
            String elderIdStr = callbackData.substring("extend_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return elderFormService.handleExtendElder(userId, elderId);
        }

        // ===== ПРЕДЛОЖЕНИЕ ЗАЯВКИ ПАРТНЁРАМ =====
        if (callbackData.startsWith("offer_elder_")) {
            String elderIdStr = callbackData.substring("offer_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return operatorService.offerElder(userId, elderId);
        }

        // ===== ПРОСМОТР КАРТОЧКИ ЗАЯВКИ =====
        if (callbackData.startsWith("view_elder_")) {
            return handleViewElder(userId, callbackData);
        }

        // ===== ПРОСМОТР КАРТОЧКИ ПАНСИОНАТА =====
        if (callbackData.startsWith("view_carehome_")) {
            String careHomeIdStr = callbackData.substring("view_carehome_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return careHomeManagementService.viewCarehome(userId, careHomeId);
        }

        // ===== ВЗЯТЬ В РАБОТУ =====
        if (callbackData.startsWith("take_elder_")) {
            String elderIdStr = callbackData.substring("take_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return operatorService.takeElder(userId, elderId);
        }

        // ===== ИНТЕРЕСНО =====
        if (callbackData.startsWith("interested_elder_")) {
            String elderIdStr = callbackData.substring("interested_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return operatorService.markInterested(userId, elderId);
        }

        // ===== НЕ ПОДХОДИТ =====
        // ✅ НЕ ПОДХОДИТ
        if (callbackData.startsWith("not_interested_elder_")) {
            String elderIdStr = callbackData.substring("not_interested_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return operatorService.markNotInterested(userId, elderId);
        }

        // ✅ ЗАПРОС НА ЗАКРЫТИЕ
        if (callbackData.startsWith("request_complete_elder_")) {
            String elderIdStr = callbackData.substring("request_complete_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return operatorService.requestComplete(userId, elderId);
        }

        // ===== ОТПРАВИТЬ ЗАПРОС НА ЗАКРЫТИЕ =====
        if (callbackData.startsWith("not_interested_elder_")) {
            String elderIdStr = callbackData.substring("not_interested_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return operatorService.markNotInterested(userId, elderId);
        }

        // ===== ПОДТВЕРЖДЕНИЕ ЗАКРЫТИЯ =====
        if (callbackData.startsWith("confirm_complete_elder_")) {
            String elderIdStr = callbackData.substring("confirm_complete_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return operatorService.confirmComplete(userId, elderId);
        }

        // ===== УДАЛЕНИЕ ЗАЯВКИ =====
        if (callbackData.startsWith("delete_elder_")) {
            String elderIdStr = callbackData.substring("delete_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return elderFormService.handleDeleteElder(userId, elderId);
        }

        // ===== ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ =====
        if (callbackData.startsWith("confirm_delete_")) {
            String action = callbackData.substring("confirm_delete_".length());
            boolean confirm = "yes".equals(action);
            return elderFormService.confirmDeleteElder(userId, confirm);
        }

        // ===== РЕДАКТИРОВАНИЕ ЗАЯВКИ =====
        if (callbackData.startsWith("edit_elder_")) {
            String elderIdStr = callbackData.substring("edit_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return elderFormService.handleEditElder(userId, elderId);
        }

        // ===== СВЯЗАТЬСЯ ЧЕРЕЗ MAX =====
        if (callbackData.startsWith("contact_client_")) {
            String elderIdStr = callbackData.substring("contact_client_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return operatorService.contactClient(userId, elderId);
        }

        // ===== АДМИН: УПРАВЛЕНИЕ ОПЕРАТОРАМИ =====
        if (callbackData.equals("admin_operators")) {
            return adminService.showOperatorsList(userId);
        }
        if (callbackData.equals("admin_block_operator")) {
            stateService.setState(userId, DialogState.AWAITING_ADMIN_OPERATOR_ID);
            return responseWithBackAndMainMenu(
                    "🔒 Введите **ID оператора**, которого нужно заблокировать:\n\nID можно найти в списке операторов.",
                    "admin_operators"
            );
        }
        if (callbackData.equals("admin_unblock_operator")) {
            stateService.setState(userId, DialogState.AWAITING_ADMIN_OPERATOR_ID_UNBLOCK);
            return responseWithBackAndMainMenu(
                    "🔓 Введите **ID оператора**, которого нужно разблокировать:",
                    "admin_operators"
            );
        }

        // ===== АДМИН: УПРАВЛЕНИЕ ПАНСИОНАТАМИ =====
        if (callbackData.equals("admin_carehomes_menu")) {
            return adminService.showAdminCarehomesMenu(userId);
        }
        if (callbackData.equals("admin_carehomes_list")) {
            return adminService.showAdminCarehomesList(userId);
        }
        if (callbackData.equals("admin_carehomes_add")) {
            stateService.setState(userId, DialogState.ADMIN_ADD_CAREHOME_NAME);
            UniversalResponse response = new UniversalResponse(
                    "🏢 **Добавление пансионата**\n\nВведите **название** пансионата:"
            );
            response.addButton("❌ Отменить", "cancel_action");
            response.addButtonFullRow("🔙 Назад", "admin_carehomes_menu");
            return response;
        }

        // ===== АДМИН: ОДОБРИТЬ/ОТКЛОНИТЬ ПАНСИОНАТ =====
        if (callbackData.startsWith("admin_approve_carehome_")) {
            String careHomeIdStr = callbackData.substring("admin_approve_carehome_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return adminService.handleApproveCarehome(userId, careHomeId);
        }
        if (callbackData.startsWith("admin_reject_carehome_")) {
            String careHomeIdStr = callbackData.substring("admin_reject_carehome_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return adminService.handleRejectCarehome(userId, careHomeId);
        }

        if (callbackData.equals("my_carehomes")) {
            return careHomeManagementService.showMyCarehomes(userId);  // ✅ ПРАВИЛЬНО
        }

// ===== АДМИН: ПРОСМОТР/РЕДАКТИРОВАНИЕ ПАНСИОНАТА =====
        if (callbackData.startsWith("admin_carehome_view_")) {
            String careHomeIdStr = callbackData.substring("admin_carehome_view_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            CareHome careHome = careHomeService.findById(careHomeId);
            if (careHome == null) {
                return responseWithBackAndMainMenu("❌ Пансионат не найден.", "admin_carehomes_menu");
            }
            return adminService.showAdminCarehomeCard(userId, careHome);
        }
        if (callbackData.startsWith("admin_carehome_edit_")) {
            return handleAdminCarehomeEdit(userId, callbackData);
        }
        if (callbackData.startsWith("admin_carehome_delete_")) {
            return handleAdminCarehomeDelete(userId, callbackData);
        }

        // ===== АДМИН: СТАТИСТИКА И БОНУСЫ =====
        if (callbackData.equals("admin_stats")) {
            return adminService.showAdminStats(userId);
        }
        if (callbackData.equals("admin_bonus_settings")) {
            return adminService.showBonusSettings(userId);
        }
        if (callbackData.startsWith("admin_bonus_edit_")) {
            return handleBonusEdit(userId, callbackData);
        }

        // ===== АДМИН: МОДЕРАЦИЯ ЗАЯВОК =====
        if (callbackData.equals("admin_elder_moderation")) {
            return adminService.showElderModerationList(userId);
        }
        if (callbackData.startsWith("admin_elder_view_")) {
            String elderIdStr = callbackData.substring("admin_elder_view_".length());
            Long elderId = Long.parseLong(elderIdStr);
            Elder elder = elderService.findById(elderId);
            if (elder == null) {
                return responseWithBackAndMainMenu("❌ Заявка не найдена.", "admin_elder_moderation");
            }
            return adminService.showAdminElderCard(userId, elder);
        }
        if (callbackData.startsWith("approve_elder_")) {
            return handleApproveElder(userId, callbackData);
        }

        // ===== РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ДЛЯ MANAGER) =====
        if (callbackData.startsWith("manager_carehome_edit_")) {
            String careHomeIdStr = callbackData.substring("manager_carehome_edit_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return careHomeManagementService.startEditCarehome(userId, careHomeId);
        }

// ===== АДМИН: ОДОБРИТЬ ИЗМЕНЕНИЯ ПАНСИОНАТА =====
        if (callbackData.startsWith("approve_carehome_edit_")) {
            String careHomeIdStr = callbackData.substring("approve_carehome_edit_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return adminService.approveCarehomeEdit(userId, careHomeId);
        }

// ===== АДМИН: ОТКЛОНИТЬ ИЗМЕНЕНИЯ ПАНСИОНАТА =====
        if (callbackData.startsWith("reject_carehome_edit_")) {
            String careHomeIdStr = callbackData.substring("reject_carehome_edit_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return adminService.rejectCarehomeEdit(userId, careHomeId);
        }

        // ===== ПРОПУСТИТЬ ШАГ РЕДАКТИРОВАНИЯ =====
        if (callbackData.equals("skip_edit_carehome")) {
            return handleSkipEditCarehome(userId);
        }

        // ===== ОТМЕНА ДЕЙСТВИЯ =====
        if (callbackData.equals("cancel_action")) {
            stateService.clearState(userId);
            return responseWithMainMenu("❌ Действие отменено.");
        }

        // ===== ГЛАВНОЕ МЕНЮ =====
        if (callbackData.equals("main_menu")) {
            return handleStartCommand(userId, chatId);
        }

        // ===== ОСТАЛЬНЫЕ МЕНЮ =====
        return switch (callbackData) {
            case "find_requests_back" -> handleFindRequestsBack(userId);
            case "find_requests_more" -> handleFindRequestsMore(userId);
            case "menu_requests" -> handleMenuRequests(userId);
            case "menu_carehomes" -> handleMenuCarehomes(userId);
            case "new_request" -> elderFormService.startNewRequest(userId);
            case "find_requests" -> handleFindRequests(userId);
            case "my_stats" -> statisticsService.showMyStatistics(userId);
            case "my_request" -> showMyRequest(userId);
            case "my_carehomes" -> showMyCarehomes(userId);
            case "list_carehomes" -> careHomeManagementService.listCarehomes(userId);
            case "map_carehomes" -> careHomeManagementService.mapCarehomes(userId);
            case "propose_carehome" -> careHomeManagementService.startProposeCarehome(userId);
            case "settings_menu" -> userSettingsService.showSettingsMenu(userId);
            case "settings_city" -> userSettingsService.handleSettingsCity(userId);
            case "settings_region" -> userSettingsService.handleSettingsRegion(userId);
            case "settings_budget" -> userSettingsService.handleSettingsBudget(userId);
            case "settings_notifications" -> userSettingsService.showNotificationsSettings(userId);
            case "settings_notifications_toggle" -> userSettingsService.toggleNotifications(userId);
            case "settings_time" -> userSettingsService.handleSettingsTime(userId);
            case "help" -> responseWithMainMenu("""
            ❓ **Помощь**
            
            📋 **Заявки** — создание, просмотр, поиск заявок
            🏢 **Пансионаты** — регистрация и список пансионатов
            
            Для возврата в главное меню нажмите кнопку ниже.""");
            case "register_carehome" -> handleRegisterCarehome(userId);
            case "register_operator" -> handleRegisterOperator(userId);
            case "request_contact_from_max" -> handleRequestContactFromMax(userId);
            case "enter_phone_manually" -> handleEnterPhoneManually(userId);
            case "my_requests_created" -> showMyRequestsByType(userId, "created");
            case "my_requests_in_progress" -> showMyRequestsByType(userId, "in_progress");
            case "my_requests_interested" -> showMyRequestsByType(userId, "interested");
            case "my_requests_completed" -> showMyRequestsByType(userId, "completed");
            default -> responseWithMainMenu("❌ Неизвестная команда");
        };
    }

    // ============================================================
    // ===== ОБРАБОТКА ДИАЛОГА (ВВОД ТЕКСТА) =====
    // ============================================================

    private UniversalResponse handleDialogInput(Long userId, String chatId, String text, DialogState state) {
        Elder tempElder = stateService.getTempElder(userId);
        if (tempElder == null) {
            tempElder = new Elder();
            stateService.setTempElder(userId, tempElder);
        }

        String cleanPhone;
        User user;
        CareHome careHome;
        Double price;
        Long editId;
        CareHome editCareHome;
        UniversalResponse response = new UniversalResponse();
        log.info("📥 handleDialogInput: userId={}, state={}, text={}", userId, state, text);
        switch (state) {

            case AWAITING_OPERATOR_PROFILE_NAME,
                 AWAITING_OPERATOR_PROFILE_PHONE,
                 AWAITING_OPERATOR_PROFILE_WHATSAPP,
                 AWAITING_OPERATOR_PROFILE_EMAIL -> {
                return handleOperatorProfile(userId, text, state);
            }

            case EDITING_PROFILE_NAME -> {
                user = getUserOrNull(userId);
                if (user == null) {
                    return responseWithMainMenu("❌ Пользователь не найден.");
                }

                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_profile")) {
                    user.setFirstName(text.trim());
                    userService.saveUser(user);
                }

                stateService.setState(userId, DialogState.EDITING_PROFILE_PHONE);
                response = new UniversalResponse(
                        "✏️ **Редактирование профиля**\n\n" +
                                "📱 Текущий телефон: " + (user.getPhone() != null ? user.getPhone() : "не указан") + "\n\n" +
                                "Введите **новый номер телефона** (или нажмите 'Оставить как есть'):"
                );
                response.addButton("⏭️ Оставить как есть", "skip_edit_profile");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case EDITING_PROFILE_PHONE -> {
                user = getUserOrNull(userId);
                if (user == null) {
                    return responseWithMainMenu("❌ Пользователь не найден.");
                }

                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_profile")) {
                    String phone = text.trim().replaceAll("[^0-9+]", "");
                    if (phone.matches("^[+]?[0-9]{10,15}$")) {
                        user.setPhone(phone);
                        userService.saveUser(user);
                    } else {
                        response = new UniversalResponse(
                                "❌ Неверный формат телефона.\n\n" +
                                        "Введите номер в формате +7 999 123-45-67\n" +
                                        "или нажмите 'Оставить как есть':"
                        );
                        response.addButton("⏭️ Оставить как есть", "skip_edit_profile");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                }

                stateService.setState(userId, DialogState.EDITING_PROFILE_WHATSAPP);
                response = new UniversalResponse(
                        "✏️ **Редактирование профиля**\n\n" +
                                "📱 Текущий WhatsApp: " + (user.getWhatsapp() != null ? user.getWhatsapp() : "не указан") + "\n\n" +
                                "Введите **номер WhatsApp** (или нажмите 'Оставить как есть'):"
                );
                response.addButton("⏭️ Оставить как есть", "skip_edit_profile");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case EDITING_PROFILE_WHATSAPP -> {
                user = getUserOrNull(userId);
                if (user == null) {
                    return responseWithMainMenu("❌ Пользователь не найден.");
                }

                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_profile")) {
                    user.setWhatsapp(text.trim());
                    userService.saveUser(user);
                }

                stateService.setState(userId, DialogState.EDITING_PROFILE_TELEGRAM);
                response = new UniversalResponse(
                        "✏️ **Редактирование профиля**\n\n" +
                                "✈️ Текущий Telegram: " + (user.getTelegramUsername() != null ? "@" + user.getTelegramUsername() : "не указан") + "\n\n" +
                                "Введите **username Telegram** (без @, или нажмите 'Оставить как есть'):"
                );
                response.addButton("⏭️ Оставить как есть", "skip_edit_profile");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case EDITING_PROFILE_TELEGRAM -> {
                user = getUserOrNull(userId);
                if (user == null) {
                    return responseWithMainMenu("❌ Пользователь не найден.");
                }

                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_profile")) {
                    String username = text.trim();
                    if (username.startsWith("@")) {
                        username = username.substring(1);
                    }
                    user.setTelegramUsername(username);
                    userService.saveUser(user);
                }

                stateService.setState(userId, DialogState.EDITING_PROFILE_EMAIL);
                response = new UniversalResponse(
                        "✏️ **Редактирование профиля**\n\n" +
                                "📧 Текущий Email: " + (user.getEmail() != null ? user.getEmail() : "не указан") + "\n\n" +
                                "Введите **Email** (или нажмите 'Оставить как есть'):"
                );
                response.addButton("⏭️ Оставить как есть", "skip_edit_profile");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case EDITING_PROFILE_EMAIL -> {
                user = getUserOrNull(userId);
                if (user == null) {
                    return responseWithMainMenu("❌ Пользователь не найден.");
                }

                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_profile")) {
                    String email = text.trim();
                    if (email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                        user.setEmail(email);
                        userService.saveUser(user);
                    } else {
                        response = new UniversalResponse(
                                "❌ Неверный формат Email.\n\n" +
                                        "Введите корректный Email (например: name@domain.com)\n" +
                                        "или нажмите 'Оставить как есть':"
                        );
                        response.addButton("⏭️ Оставить как есть", "skip_edit_profile");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                }

                stateService.clearState(userId);

                response = new UniversalResponse(
                        "✅ **Профиль обновлён!**\n\n" +
                                "👤 Имя: " + user.getFirstName() + "\n" +
                                "📱 Телефон: " + (user.getPhone() != null ? user.getPhone() : "не указан") + "\n" +
                                "📱 WhatsApp: " + (user.getWhatsapp() != null ? user.getWhatsapp() : "не указан") + "\n" +
                                "✈️ Telegram: " + (user.getTelegramUsername() != null ? "@" + user.getTelegramUsername() : "не указан") + "\n" +
                                "📧 Email: " + (user.getEmail() != null ? user.getEmail() : "не указан")
                );
                response.addButtonFullRow("👤 Мой профиль", "my_profile");
                response.addButtonFullRow("🏠 Главное меню", "main_menu");
                return response;
            }

            case EDITING_OPERATOR_NAME -> {
                Long operatorId = stateService.getEditingOperatorId(userId);
                if (operatorId == null) {
                    return responseWithMainMenu("❌ Ошибка: оператор не найден.");
                }

                User operator = userService.findById(operatorId);
                if (operator == null) {
                    stateService.clearState(userId);
                    return responseWithMainMenu("❌ Оператор не найден.");
                }

                // Если пользователь ввёл текст — сохраняем имя
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_operator")) {
                    operator.setFirstName(text.trim());
                    userService.saveUser(operator);
                    log.info("✏️ Имя оператора {} изменено на {}", operatorId, text.trim());
                }

                // Переход к следующему шагу — редактирование телефона
                stateService.setState(userId, DialogState.EDITING_OPERATOR_PHONE);

                response = new UniversalResponse(
                        "✏️ **Редактирование оператора**\n\n" +
                                "📱 Текущий телефон: " + (operator.getPhone() != null ? operator.getPhone() : "не указан") + "\n\n" +
                                "Введите **новый номер телефона** (или нажмите 'Оставить без изменений'):"
                );
                response.addButton("⏭️ Оставить как есть", "skip_edit_operator");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case EDITING_OPERATOR_PHONE -> {
                Long operatorId = stateService.getEditingOperatorId(userId);
                if (operatorId == null) {
                    return responseWithMainMenu("❌ Ошибка: оператор не найден.");
                }

                User operator = userService.findById(operatorId);
                if (operator == null) {
                    stateService.clearState(userId);
                    return responseWithMainMenu("❌ Оператор не найден.");
                }

                // Если пользователь ввёл текст — сохраняем телефон
                if (text != null && !text.trim().isEmpty() && !text.equals("skip_edit_operator")) {
                    String phone = text.trim().replaceAll("[^0-9+]", "");
                    if (phone.matches("^[+]?[0-9]{10,15}$")) {
                        operator.setPhone(phone);
                        userService.saveUser(operator);
                        log.info("✏️ Телефон оператора {} изменён на {}", operatorId, phone);
                    } else {
                        response = new UniversalResponse(
                                "❌ Неверный формат телефона.\n\n" +
                                        "Введите номер в формате +7 999 123-45-67\n" +
                                        "или нажмите 'Оставить как есть':"
                        );
                        response.addButton("⏭️ Оставить как есть", "skip_edit_operator");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                }

                // Завершаем редактирование
                stateService.clearState(userId);
                stateService.clearEditingOperatorId(userId);

             response = new UniversalResponse(
                        "✅ **Данные оператора обновлены!**\n\n" +
                                "👤 Имя: " + operator.getFirstName() + "\n" +
                                "📱 Телефон: " + (operator.getPhone() != null ? operator.getPhone() : "не указан")
                );
                response.addButtonFullRow("👥 Список операторов", "manager_operators");
                response.addButtonFullRow("🔙 Назад к оператору", "view_operator_" + operatorId);
                response.addButtonFullRow("🏠 Главное меню", "main_menu");
                return response;
            }
            // ===== СОЗДАНИЕ ЗАЯВКИ (все шаги анкеты) ==================================================================================
            case AWAITING_ELDER_NAME, AWAITING_CLIENT_NAME, AWAITING_ELDER_AGE,
                 AWAITING_ELDER_HEALTH, AWAITING_ELDER_BUDGET, AWAITING_ELDER_LOCATION,
                 AWAITING_ELDER_PHONE_MANUAL, AWAITING_ELDER_REQUIREMENTS -> {
                return elderFormService.handleFormInput(userId, text, state);
            }

            // ===== РЕДАКТИРОВАНИЕ ЗАЯВКИ (все шаги) =====
            case EDITING_ELDER_NAME, EDITING_ELDER_AGE, EDITING_ELDER_HEALTH,
                 EDITING_ELDER_BUDGET, EDITING_ELDER_LOCATION, EDITING_ELDER_REQUIREMENTS -> {
                return elderFormService.handleFormInput(userId, text, state);
            }

            // ===== ПРОДЛЕНИЕ ЗАЯВКИ =====
            case AWAITING_EXTEND_BUDGET -> {
                return elderFormService.saveExtendElder(userId, text);
            }

            // ===== ОТКЛОНЕНИЕ ЗАЯВКИ (АДМИН) =====
            case AWAITING_REJECT_COMMENT_FOR_ELDER -> {
                Elder elder = stateService.getTempElder(userId);
                if (elder == null) {
                    return responseWithMainMenu("❌ Заявка не найдена.");
                }

                elder.setStatus(ElderStatus.DELETED);
                elder.setAcceptedBy(userId);
                elder.setAcceptedAt(LocalDateTime.now());
                elderService.updateElder(elder);
                stateService.clearState(userId);

                User client = userService.findByTelegramId(elder.getClientTelegramId()).orElse(null);
                if (client != null) {
                    sendNotification(client.getTelegramId(),
                            "❌ **Ваша заявка #" + elder.getId() + " отклонена.**\n\n" +
                                    "💬 **Причина:** " + text + "\n\n" +
                                    "Вы можете создать новую заявку.",
                            "📝 Создать заявку", "new_request"
                    );
                }

                response = new UniversalResponse(
                        "❌ Заявка #" + elder.getId() + " отклонена.\n\n💬 Комментарий отправлен клиенту."
                );
                response.addButtonFullRow("📋 Модерация заявок", "admin_elder_moderation");
                response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
                response.addButtonFullRow("🏠 Главное меню", "main_menu");
                return response;
            }
            case AWAITING_SETTINGS_CITY, AWAITING_SETTINGS_REGION,
                 AWAITING_SETTINGS_BUDGET_MIN, AWAITING_SETTINGS_BUDGET_MAX,
                 AWAITING_SETTINGS_TIME_FROM, AWAITING_SETTINGS_TIME_TO -> {
                return userSettingsService.handleSettingsInput(userId, text, state);
            }
            // ===== БОНУСЫ: НОВОЕ ЗНАЧЕНИЕ =====
            case AWAITING_BONUS_NEW_VALUE -> {
                try {
                    int newValue = Integer.parseInt(text.trim());
                    String actionKey = stateService.getEditingBonusKey(userId);
                    if (actionKey == null) {
                        return responseWithMainMenu("❌ Не найдена настройка для редактирования.");
                    }

                    bonusSettingService.updateBonusValue(actionKey, newValue, userId);
                    stateService.clearState(userId);
                    stateService.clearEditingBonusKey(userId);

                    BonusSetting setting = bonusSettingService.findByActionKey(actionKey);
                    String sign = newValue > 0 ? "+" : "";

                    response = new UniversalResponse(
                            "✅ **Бонус обновлён!**\n\n" +
                                    "📌 **Действие:** " + setting.getActionName() + "\n" +
                                    "💰 **Новое значение:** " + sign + newValue + " балл" +
                                    (Math.abs(newValue) != 1 ? "а" : "") + "\n\n" +
                                    "Изменения вступят в силу сразу."
                    );
                    response.addButtonFullRow("💰 Настройка бонусов", "admin_bonus_settings");
                    response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
                    response.addButtonFullRow("🏠 Главное меню", "main_menu");
                    return response;
                } catch (NumberFormatException e) {
                    response = new UniversalResponse(
                            "❌ Введите **целое число**.\n\nНапример: 2, -1, 5"
                    );
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
            }

            // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА (ШАГ 1: НАЗВАНИЕ) =====
            case AWAITING_CAREHOME_NAME_PROPOSAL -> {
                stateService.setTempCareHomeName(userId, truncateText(text, 100));
             //   stateService.setTempCareHomeName(userId, text);
                stateService.setState(userId, DialogState.AWAITING_CAREHOME_ADDRESS_PROPOSAL);
                response = new UniversalResponse(
                        "📍 Введите **адрес** пансионата (до 255 символов):\n" +
                                "Например: Московская область, г. Королёв, ул. Центральная 15"
                );
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА (ШАГ 2: АДРЕС) =====
            case AWAITING_CAREHOME_ADDRESS_PROPOSAL -> {
                stateService.setTempCareHomeAddress(userId, truncateText(text, 255));
              //  stateService.setTempCareHomeAddress(userId, text);
                stateService.setState(userId, DialogState.AWAITING_CAREHOME_PHONE_PROPOSAL);
                response = new UniversalResponse(
                        "📞 Введите **телефон** пансионата (в формате +7 999 123-45-67):"
                );
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА (ШАГ 3: ТЕЛЕФОН) =====
            case AWAITING_CAREHOME_PHONE_PROPOSAL -> {
                cleanPhone = text.replaceAll("[^0-9+]", "");
                if (!cleanPhone.matches("^[+]?[0-9]{10,15}$")) {
                    response = new UniversalResponse(
                            "❌ Неверный формат. Введите номер в формате +7 999 123-45-67:"
                    );
                    response.addButtonFullRow("❌ Отменить", "cancel_action");
                    return response;
                }
                stateService.setTempCareHomePhone(userId, cleanPhone);
                stateService.setState(userId, DialogState.AWAITING_CAREHOME_PRICE_PROPOSAL);
                response = new UniversalResponse(
                        "💰 Введите **минимальную цену** за месяц (в рублях):\n\nНапример: 50000"
                );
                response.addButtonFullRow("❌ Отменить", "cancel_action");
                return response;
            }

            // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА (ШАГ 4: ЦЕНА) =====
            case AWAITING_CAREHOME_PRICE_PROPOSAL -> {
                try {
                    double priceValue = Double.parseDouble(text);
                    if (priceValue <= 0) {
                        response = new UniversalResponse(
                                "❌ Цена должна быть положительным числом. Попробуйте снова:"
                        );
                        response.addButtonFullRow("❌ Отменить", "cancel_action");
                        return response;
                    }
                    stateService.setTempCareHomePrice(userId, priceValue);
                    stateService.setState(userId, DialogState.AWAITING_CAREHOME_WEBSITE_PROPOSAL);
                    response = new UniversalResponse(
                            "🌐 Введите **адрес сайта** пансионата (до 100 символов):\n" +
                                    "Например: https://пансальянс.рф\n\n" +
                                    "Если сайта нет, напишите '-'"
                    );
                    response.addButtonFullRow("❌ Отменить", "cancel_action");
                    return response;
                } catch (NumberFormatException e) {
                    response = new UniversalResponse("❌ Введите число. Например: 50000");
                    response.addButtonFullRow("❌ Отменить", "cancel_action");
                    return response;
                }
            }

            // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА (ШАГ 5: САЙТ) =====
            case AWAITING_CAREHOME_WEBSITE_PROPOSAL -> {
                stateService.setTempCareHomeWebsite(userId, text);
                stateService.setState(userId, DialogState.AWAITING_CAREHOME_DESCRIPTION_PROPOSAL);
                response = new UniversalResponse(
                        "📝 Введите **описание** пансионата (до 500 символов):\n" +
                                "Например: Современный пансионат с круглосуточным уходом и медицинским персоналом"
                );
                response.addButtonFullRow("❌ Отменить", "cancel_action");
                return response;
            }

            // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА (ШАГ 6: ОПИСАНИЕ) =====
            case AWAITING_CAREHOME_DESCRIPTION_PROPOSAL -> {
                stateService.setTempCareHomeDescription(userId, truncateText(text, 255));
              //  stateService.setTempCareHomeDescription(userId, text);
                stateService.setState(userId, DialogState.AWAITING_CAREHOME_SPECIALIZATION_PROPOSAL);
                response = new UniversalResponse(
                        "🏥 Введите **специализацию** пансионата (до 100 символов):\n" +
                                "Например: деменция, диабет, общий уход, паллиатив"
                );
                response.addButtonFullRow("❌ Отменить", "cancel_action");
                return response;
            }

            // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА (ШАГ 7: СОХРАНЕНИЕ) =====
            // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА (ШАГ 7: ОФЕРТА) =====
            case AWAITING_CAREHOME_SPECIALIZATION_PROPOSAL -> {
              //  stateService.setTempCareHomeSpecialization(userId, text);
                stateService.setTempCareHomeSpecialization(userId, truncateText(text, 100));

                // ===== ПЕРЕХОДИМ К ОФЕРТЕ =====
                stateService.setState(userId, DialogState.AWAITING_OFFER_ACCEPT);

                String offerText = """
            📋 **Публичная оферта**
            
            Я, предлагая пансионат для размещения в сервисе "ПансАльянс", 
            соглашаюсь с условиями:
            
            1. Я являюсь уполномоченным представителем пансионата
            2. Вся предоставленная информация достоверна
            3. Я согласен на обработку данных
            4. Я ознакомлен с правилами сервиса
            
            Подтверждая, вы принимаете условия оферты.""";

                response = new UniversalResponse(offerText);
                response.addButtonFullRow("✅ Принимаю условия", "accept_offer");
                response.addButtonFullRow("❌ Не принимаю", "decline_offer");
                return response;
            }

            // ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 1: НАЗВАНИЕ) =====
            // ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 1: НАЗВАНИЕ) =====
            case ADMIN_EDIT_CAREHOME_NAME -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                if (!text.equals("-")) {
                    // Ограничение: 100 символов
                    editCareHome.setName(truncateText(text, 100));
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_ADDRESS);
                response = new UniversalResponse(
                        "📍 **Текущий адрес:** " + editCareHome.getAddress() + "\n\n" +
                                "Введите **новый адрес** (или нажмите 'Оставить без изменений'):"
                );
                response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 2: АДРЕС) =====
            case ADMIN_EDIT_CAREHOME_ADDRESS -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                if (!text.equals("-")) {
                    // Ограничение: 255 символов
                    editCareHome.setAddress(truncateText(text, 255));
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_PHONE);
                response = new UniversalResponse(
                        "📞 **Текущий телефон:** " + editCareHome.getPhone() + "\n\n" +
                                "Введите **новый телефон** (или нажмите 'Оставить без изменений'):"
                );
                response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 3: ТЕЛЕФОН) =====
            case ADMIN_EDIT_CAREHOME_PHONE -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                if (!text.equals("-")) {
                    cleanPhone = text.replaceAll("[^0-9+]", "");
                    // Ограничение: длина телефона 10-15 символов
                    if (!cleanPhone.matches("^[+]?[0-9]{10,15}$")) {
                        response = new UniversalResponse(
                                "❌ Неверный формат. Введите номер в формате +7 999 123-45-67:"
                        );
                        response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                    editCareHome.setPhone(cleanPhone);
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_WEBSITE);
                response = new UniversalResponse(
                        "🌐 **Текущий сайт:** " +
                                (editCareHome.getWebsite() != null ? editCareHome.getWebsite() : "не указан") + "\n\n" +
                                "Введите **новый сайт** (или нажмите 'Оставить без изменений'):"
                );
                response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 4: САЙТ) =====
            case ADMIN_EDIT_CAREHOME_WEBSITE -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                if (!text.equals("-")) {
                    // Ограничение: 100 символов
                    editCareHome.setWebsite(truncateText(text, 100));
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_PRICE);
                response = new UniversalResponse(
                        "💰 **Текущая цена:** " + editCareHome.getPriceFrom() + " руб.\n\n" +
                                "Введите **новую цену** (или нажмите 'Оставить без изменений'):"
                );
                response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 5: ЦЕНА) =====
            case ADMIN_EDIT_CAREHOME_PRICE -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                try {
                    double priceValue = Double.parseDouble(text);
                    // Ограничение: цена от 0 до 10 000 000
                    if (priceValue <= 0 || priceValue > 10_000_000) {
                        response = new UniversalResponse(
                                "❌ Введите сумму от 1 до 10 000 000 руб.:"
                        );
                        response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                    editCareHome.setPriceFrom(priceValue);
                    stateService.setEditingCareHome(userId, editCareHome);
                } catch (NumberFormatException e) {
                    response = new UniversalResponse("❌ Введите число (например, 50000):");
                    response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }

                stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_DESCRIPTION);
                response = new UniversalResponse(
                        "📝 **Текущее описание:** " + editCareHome.getDescription() + "\n\n" +
                                "Введите **новое описание** (или нажмите 'Оставить без изменений'):"
                );
                response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 6: ОПИСАНИЕ) =====
            case ADMIN_EDIT_CAREHOME_DESCRIPTION -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                if (!text.equals("-")) {
                    // Ограничение: 500 символов
                    editCareHome.setDescription(truncateText(text, 500));
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_SPECIALIZATION);
                response = new UniversalResponse(
                        "🏥 **Текущая специализация:** " + editCareHome.getSpecialization() + "\n\n" +
                                "Введите **новую специализацию** (или нажмите 'Оставить без изменений'):"
                );
                response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 7: СПЕЦИАЛИЗАЦИЯ) =====
            case ADMIN_EDIT_CAREHOME_SPECIALIZATION -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                if (!text.equals("-")) {
                    // Ограничение: 100 символов
                    editCareHome.setSpecialization(truncateText(text, 100));
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_LATITUDE);
                String currentLat = editCareHome.getLatitude() != null
                        ? String.valueOf(editCareHome.getLatitude())
                        : "не задана";
                response = new UniversalResponse(
                        "🗺️ **Текущая широта:** " + currentLat + "\n\n" +
                                "Введите **широту** (например: 55.7558):\n" +
                                "(или нажмите 'Оставить без изменений')"
                );
                response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 8: ШИРОТА) =====
            case ADMIN_EDIT_CAREHOME_LATITUDE -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                try {
                    double lat = Double.parseDouble(text);
                    // Ограничение: широта от -90 до 90
                    if (lat < -90 || lat > 90) {
                        response = new UniversalResponse(
                                "❌ Широта должна быть в диапазоне от -90 до 90:"
                        );
                        response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                    editCareHome.setLatitude(lat);
                    stateService.setEditingCareHome(userId, editCareHome);

                    stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_LONGITUDE);
                    String currentLon = editCareHome.getLongitude() != null
                            ? String.valueOf(editCareHome.getLongitude())
                            : "не задана";
                    response = new UniversalResponse(
                            "🗺️ **Текущая долгота:** " + currentLon + "\n\n" +
                                    "Введите **долготу** (например: 37.6173):\n" +
                                    "(или нажмите 'Оставить без изменений')"
                    );
                    response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                } catch (NumberFormatException e) {
                    response = new UniversalResponse(
                            "❌ Введите число (широту). Например: 55.7558"
                    );
                    response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
            }

// ===== АДМИН: РЕДАКТИРОВАНИЕ ПАНСИОНАТА (ШАГ 9: ДОЛГОТА + СОХРАНЕНИЕ) =====
            case ADMIN_EDIT_CAREHOME_LONGITUDE -> {
                editCareHome = stateService.getEditingCareHome(userId);
                if (editCareHome == null) {
                    editId = stateService.getEditingCareHomeId(userId);
                    editCareHome = careHomeService.findById(editId);
                    if (editCareHome == null) {
                        return responseWithMainMenu("❌ Пансионат не найден.");
                    }
                    stateService.setEditingCareHome(userId, editCareHome);
                }

                try {
                    double lon = Double.parseDouble(text);
                    // Ограничение: долгота от -180 до 180
                    if (lon < -180 || lon > 180) {
                        response = new UniversalResponse(
                                "❌ Долгота должна быть в диапазоне от -180 до 180:"
                        );
                        response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                    editCareHome.setLongitude(lon);
                    stateService.setEditingCareHome(userId, editCareHome);

                    careHomeService.save(editCareHome);
                    stateService.clearState(userId);
                    stateService.clearEditingCareHome(userId);

                    response = new UniversalResponse(
                            "✅ **Пансионат успешно обновлён!**\n\n" +
                                    "📋 **Обновлённые данные:**\n" +
                                    "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                                    "🏢 **Название:** " + editCareHome.getName() + "\n" +
                                    "📍 **Адрес:** " + editCareHome.getAddress() + "\n" +
                                    "📞 **Телефон:** " + editCareHome.getPhone() + "\n" +
                                    "💰 **Цена:** " + editCareHome.getPriceFrom() + " руб.\n" +
                                    "📝 **Описание:** " + editCareHome.getDescription() + "\n" +
                                    "🏥 **Специализация:** " + editCareHome.getSpecialization() + "\n" +
                                    "🗺️ **Координаты:** " +
                                    (editCareHome.getLatitude() != null ? editCareHome.getLatitude() : "не задана") + ", " +
                                    (editCareHome.getLongitude() != null ? editCareHome.getLongitude() : "не задана") + "\n" +
                                    "━━━━━━━━━━━━━━━━━━━━━━━"
                    );
                    response.addButtonFullRow("📋 Назад к пансионату", "admin_carehome_view_" + editCareHome.getId());
                    response.addButtonFullRow("🏢 Управление пансионатами", "admin_carehomes_menu");
                    response.addButtonFullRow("🏠 Главное меню", "main_menu");
                    return response;
                } catch (NumberFormatException e) {
                    response = new UniversalResponse(
                            "❌ Введите число (долготу). Например: 37.6173"
                    );
                    response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
            }

            // ===== АДМИН: ДОБАВЛЕНИЕ ПАНСИОНАТА (ШАГ 1: НАЗВАНИЕ) =====
            case ADMIN_ADD_CAREHOME_NAME -> {
                stateService.setTempCareHomeName(userId, truncateText(text, 100));
                stateService.setState(userId, DialogState.ADMIN_ADD_CAREHOME_ADDRESS);
                response = new UniversalResponse(
                        "📍 Введите **адрес** пансионата (до 255 символов):"
                );
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: ДОБАВЛЕНИЕ ПАНСИОНАТА (ШАГ 2: АДРЕС) =====
            case ADMIN_ADD_CAREHOME_ADDRESS -> {
                stateService.setTempCareHomeAddress(userId, truncateText(text, 255));
                stateService.setState(userId, DialogState.ADMIN_ADD_CAREHOME_PHONE);
                response = new UniversalResponse("📞 Введите **телефон** пансионата:");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: ДОБАВЛЕНИЕ ПАНСИОНАТА (ШАГ 3: ТЕЛЕФОН) =====
            case ADMIN_ADD_CAREHOME_PHONE -> {
                cleanPhone = text.replaceAll("[^0-9+]", "");
                if (!cleanPhone.matches("^[+]?[0-9]{10,15}$")) {
                    response = new UniversalResponse(
                            "❌ Неверный формат. Введите номер в формате +7 999 123-45-67:"
                    );
                    response.addButtonFullRow("❌ Отменить", "cancel_action");
                    return response;
                }
                stateService.setTempCareHomePhone(userId, cleanPhone);
                stateService.setState(userId, DialogState.ADMIN_ADD_CAREHOME_PRICE);
                response = new UniversalResponse("💰 Введите **минимальную цену** за месяц (в рублях):");
                response.addButtonFullRow("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: ДОБАВЛЕНИЕ ПАНСИОНАТА (ШАГ 4: ЦЕНА) =====
            case ADMIN_ADD_CAREHOME_PRICE -> {
                try {
                    double priceValue = Double.parseDouble(text);
                    // Ограничение: цена от 1000 до 10 000 000
                    if (priceValue < 1000 || priceValue > 10_000_000) {
                        response = new UniversalResponse(
                                "❌ Введите сумму от 1000 до 10 000 000 руб.:"
                        );
                        response.addButtonFullRow("❌ Отменить", "cancel_action");
                        return response;
                    }
                    stateService.setTempCareHomePrice(userId, priceValue);
                    stateService.setState(userId, DialogState.ADMIN_ADD_CAREHOME_WEBSITE);
                    response = new UniversalResponse(
                            "🌐 Введите **адрес сайта** пансионата:\n\n" +
                                    "Например: https://голубые-ели.рф\n\n" +
                                    "Если сайта нет, напишите '-'"
                    );
                    response.addButtonFullRow("❌ Отменить", "cancel_action");
                    return response;
                } catch (NumberFormatException e) {
                    response = new UniversalResponse("❌ Введите число (например, 50000):");
                    response.addButtonFullRow("❌ Отменить", "cancel_action");
                    return response;
                }
            }

// ===== АДМИН: ДОБАВЛЕНИЕ ПАНСИОНАТА (ШАГ 5: САЙТ) =====
            case ADMIN_ADD_CAREHOME_WEBSITE -> {
                stateService.setTempCareHomeWebsite(userId, truncateText(text, 100));
                stateService.setState(userId, DialogState.ADMIN_ADD_CAREHOME_DESCRIPTION);
                response = new UniversalResponse(
                        "📝 Введите **описание** пансионата (до 255 символов):"
                );
                response.addButtonFullRow("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: ДОБАВЛЕНИЕ ПАНСИОНАТА (ШАГ 6: ОПИСАНИЕ) =====
            case ADMIN_ADD_CAREHOME_DESCRIPTION -> {
                stateService.setTempCareHomeDescription(userId, truncateText(text, 500));
                stateService.setState(userId, DialogState.ADMIN_ADD_CAREHOME_SPECIALIZATION);
                response = new UniversalResponse("🏥 Введите **специализацию** пансионата:");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

// ===== АДМИН: ДОБАВЛЕНИЕ ПАНСИОНАТА (ШАГ 7: СПЕЦИАЛИЗАЦИЯ + СОХРАНЕНИЕ) =====
            case ADMIN_ADD_CAREHOME_SPECIALIZATION -> {
                stateService.setTempCareHomeSpecialization(userId, truncateText(text, 100));

                String name = stateService.getTempCareHomeName(userId);
                String address = stateService.getTempCareHomeAddress(userId);
                String phone = stateService.getTempCareHomePhone(userId);
                Double priceValue = stateService.getTempCareHomePrice(userId);
                String description = stateService.getTempCareHomeDescription(userId);
                String specialization = stateService.getTempCareHomeSpecialization(userId);
                String website = stateService.getTempCareHomeWebsite(userId);

                // ===== СОЗДАЁМ ПАНСИОНАТ =====
               careHome = new CareHome();
                careHome.setName(truncateText(name, 100));
                careHome.setAddress(truncateText(address, 255));
                careHome.setPhone(phone);
                careHome.setPriceFrom(priceValue);
                careHome.setDescription(truncateText(description, 500));
                careHome.setSpecialization(truncateText(specialization, 100));
                careHome.setWebsite(truncateText(website, 100));


                careHome.setStatus("APPROVED");
                careHome.setActive(true);
                careHome.setSubscribed(true);
                careHome.setSubscriptionStart(LocalDateTime.now());
                careHome.setSubscriptionEnd(LocalDateTime.now().plusDays(30));
                careHomeService.save(careHome);

                stateService.clearState(userId);

                response = new UniversalResponse(
                        "✅ **Пансионат добавлен!**\n\n" +
                                "🏢 **Название:** " + name + "\n" +
                                "📍 **Адрес:** " + address + "\n" +
                                "📞 **Телефон:** " + phone + "\n" +
                                "💰 **Цена от:** " + priceValue + " руб.\n" +
                                "📝 **Описание:** " + description + "\n" +
                                "🏥 **Специализация:** " + specialization + "\n" +
                                "📅 **Подписка до:** " + careHome.getSubscriptionEnd()
                );
                response.addButtonFullRow("🏢 Управление пансионатами", "admin_carehomes_menu");
                response.addButtonFullRow("🏠 Главное меню", "main_menu");
                return response;
            }

            // ===== ОТКАЗ ОТ ПАНСИОНАТА (АДМИН) =====
            case AWAITING_REJECT_COMMENT -> {
                Long careHomeId = stateService.getEditingCareHomeId(userId);
                careHome = careHomeService.findById(careHomeId);

                if (careHome == null) {
                    return responseWithMainMenu("❌ Пансионат не найден.");
                }

                careHome.setStatus("REJECTED");
                careHome.setActive(false);
                careHome.setModeratedBy(userId);
                careHome.setModeratedAt(LocalDateTime.now());
                careHome.setModeratorComment(text);
                careHomeService.save(careHome);

                stateService.clearState(userId);

                if (careHome.getProposedBy() != null) {
                    User operator = userService.findByTelegramId(careHome.getProposedBy()).orElse(null);
                    if (operator != null) {
                        sendNotification(operator.getTelegramId(),
                                "❌ **Ваш пансионат отклонён**\n\n" +
                                        "🏢 **Название:** " + careHome.getName() + "\n" +
                                        "📍 **Адрес:** " + careHome.getAddress() + "\n\n" +
                                        "💬 **Причина:** " + text + "\n\n" +
                                        "Вы можете исправить ошибки и предложить пансионат снова.",
                                "📝 Предложить пансионат", "propose_carehome"
                        );
                    }
                }

                response = new UniversalResponse(
                        "❌ Пансионат **" + careHome.getName() + "** отклонён.\n\n" +
                                "💬 Комментарий отправлен оператору."
                );
                response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
                response.addButtonFullRow("🏠 Главное меню", "main_menu");
                return response;
            }

            // ===== РЕГИСТРАЦИЯ ОПЕРАТОРА (ШАГ 1: ПАНСИОНАТ) =====
            case AWAITING_OPERATOR_CAREHOME_NAME -> {
                String careHomeName = text.trim();
                careHome = careHomeService.findByNameExact(careHomeName);

                if (careHome == null) {
                    response = new UniversalResponse(
                            "❌ Пансионат с названием \"" + careHomeName + "\" не найден.\n\n" +
                                    "Проверьте название или зарегистрируйте пансионат через меню 'Пансионаты'."
                    );
                    response.addButton("🏠 Главное меню", "main_menu");
                    stateService.clearState(userId);
                    return response;
                }

                stateService.setEditingCareHomeId(userId, careHome.getId());
                stateService.setState(userId, DialogState.AWAITING_OPERATOR_NAME);

                response = new UniversalResponse("👤 Введите ваше имя (как вас называть):");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            // ===== РЕГИСТРАЦИЯ ОПЕРАТОРА (ШАГ 2: ИМЯ) =====
            case AWAITING_OPERATOR_NAME -> {
                stateService.setTempCareHomeName(userId, text.trim());
                stateService.setState(userId, DialogState.AWAITING_OPERATOR_PHONE);

                response = new UniversalResponse(
                        "📱 Введите ваш номер телефона для связи:\n\n" +
                                "Введите номер в формате +7 999 123-45-67\n" +
                                "или просто 89991234567"
                );
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            // ===== РЕГИСТРАЦИЯ ОПЕРАТОРА (ШАГ 3: ТЕЛЕФОН + СОХРАНЕНИЕ) =====
            case AWAITING_OPERATOR_PHONE -> {
                cleanPhone = text.replaceAll("[^0-9+]", "");
                if (!cleanPhone.matches("^[+]?[0-9]{10,15}$")) {
                    response = new UniversalResponse(
                            "❌ Неверный формат. Введите номер в формате +7 999 123-45-67:"
                    );
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }

                Long careHomeId = stateService.getEditingCareHomeId(userId);
                String operatorName = stateService.getTempCareHomeName(userId);

                // ===== СОЗДАЁМ НОВОГО ПОЛЬЗОВАТЕЛЯ С РОЛЬЮ OPERATOR =====
                User operator = new User();
                operator.setTelegramId(userId);  // В реальности здесь должен быть ID нового оператора
                operator.setFirstName(operatorName);
                operator.setPhone(cleanPhone);
                operator.setAccessLevel(AccessLevel.OPERATOR);
                operator.setCareHomeId(careHomeId);

                // ===== НАЧИСЛЯЕМ СТАРТОВЫЙ БОНУС ТОЛЬКО ОПЕРАТОРУ =====
                int startBonus = bonusSettingService.getBonusValue("start_bonus");
                operator.setBonusPoints(startBonus);
                operator.setRegistered(true);
                operator.setActive(true);
                userService.saveUser(operator);

                stateService.clearState(userId);

               careHome = careHomeService.findById(careHomeId);

                response = new UniversalResponse(
                        "✅ **Оператор зарегистрирован!**\n\n" +
                                "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                                "👤 **Имя оператора:** " + operatorName + "\n" +
                                "📱 **Телефон:** " + cleanPhone + "\n\n" +
                                "🎁 Оператору начислен стартовый бонус: **" + startBonus + " баллов**\n\n" +
                                "📌 Теперь оператор может:\n" +
                                "• Искать и брать заявки в работу\n" +
                                "• Управлять заявками\n" +
                                "• Получать бонусы за работу\n\n" +
                                "🚀 Удачи!"
                );
                response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
                response.addButtonFullRow("🏠 Главное меню", "main_menu");
                return response;
            }

            // ===== АДМИН: БЛОКИРОВКА ОПЕРАТОРА =====
            case AWAITING_ADMIN_OPERATOR_ID -> {
                try {
                    Long operatorId = Long.parseLong(text);
                    User operator = userService.findByTelegramId(operatorId).orElse(null);

                    if (operator == null) {
                        response = new UniversalResponse(
                                "❌ Оператор с ID " + operatorId + " не найден."
                        );
                        response.addButton("⚙️ Админ-панель", "admin_menu");
                        response.addButton("🏠 Главное меню", "main_menu");
                        return response;
                    }

                    if (operator.getAccessLevel() == AccessLevel.ADMIN) {
                        response = new UniversalResponse(
                                "❌ Нельзя заблокировать администратора."
                        );
                        response.addButton("⚙️ Админ-панель", "admin_menu");
                        response.addButton("🏠 Главное меню", "main_menu");
                        return response;
                    }

                    operator.setIsActive(false);
                    userService.saveUser(operator);
                    stateService.clearState(userId);

                    response = new UniversalResponse(
                            "✅ Оператор " + operator.getFirstName() + " заблокирован."
                    );
                    response.addButton("⚙️ Админ-панель", "admin_menu");
                    response.addButton("🏠 Главное меню", "main_menu");
                    return response;
                } catch (NumberFormatException e) {
                    response = new UniversalResponse(
                            "❌ Введите корректный ID (число):"
                    );
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
            }

            // ===== АДМИН: РАЗБЛОКИРОВКА ОПЕРАТОРА =====
            case AWAITING_ADMIN_OPERATOR_ID_UNBLOCK -> {
                try {
                    Long operatorId = Long.parseLong(text);
                    User operator = userService.findByTelegramId(operatorId).orElse(null);

                    if (operator == null) {
                        response = new UniversalResponse(
                                "❌ Оператор с ID " + operatorId + " не найден."
                        );
                        response.addButton("⚙️ Админ-панель", "admin_menu");
                        response.addButton("🏠 Главное меню", "main_menu");
                        return response;
                    }

                    operator.setIsActive(true);
                    operator.setIsBlocked(false);
                    operator.setBlockedAt(null);
                    operator.setBlockedReason(null);
                    userService.saveUser(operator);
                    stateService.clearState(userId);

                    response = new UniversalResponse(
                            "✅ Оператор " + operator.getFirstName() + " разблокирован."
                    );
                    response.addButton("⚙️ Админ-панель", "admin_menu");
                    response.addButton("🏠 Главное меню", "main_menu");
                    return response;
                } catch (NumberFormatException e) {
                    response = new UniversalResponse(
                            "❌ Введите корректный ID (число):"
                    );
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
            }
            case AWAITING_ELDER_PHONE -> {
                // Пользователь ввёл номер напрямую (без нажатия кнопки)
                return elderFormService.handleFormInput(userId, text, DialogState.AWAITING_ELDER_PHONE_MANUAL);
            }
            default -> {
                stateService.clearState(userId);
                response = new UniversalResponse(
                        "❌ Что-то пошло не так. Начните заново через /start"
                );
                response.addMainMenuButton();
                return response;
            }
        }
    }

    // ============================================================
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ CALLBACK =====
    // ============================================================

    private UniversalResponse handleRejectElder(Long userId, String callbackData) {
        String elderIdStr = callbackData.substring("reject_elder_".length());
        Long elderId = Long.parseLong(elderIdStr);
        Elder elder = getElderOrNull(elderId);

        if (elder == null) {
            return responseWithBackAndMainMenu("❌ Заявка не найдена.", "admin_elder_moderation");
        }

        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        if (elder.getStatus() != ElderStatus.PENDING) {
            return responseWithBackAndMainMenu(
                    "❌ Заявка уже обработана или не на модерации.",
                    "admin_elder_moderation"
            );
        }

        // Сохраняем в состояние для ввода комментария
        stateService.setTempElder(userId, elder);
        stateService.setState(userId, DialogState.AWAITING_REJECT_COMMENT_FOR_ELDER);

        UniversalResponse response = new UniversalResponse(
                "❌ **Отклонение заявки #" + elderId + "**\n\n" +
                        "Заявка: " + elder.getFullName() + "\n\n" +
                        "Введите **причину отказа** (комментарий для клиента):"
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    private UniversalResponse handleViewElder(Long userId, String callbackData) {
        String elderIdStr = callbackData.substring("view_elder_".length());
        Long elderId = Long.parseLong(elderIdStr);
        Elder elder = getElderOrNull(elderId);

        if (elder == null) {
            return responseWithBackAndMainMenu("❌ Заявка не найдена.", "find_requests");
        }

        User user = getUserOrNull(userId);
        boolean isOperator = isOperator(user);
        boolean isAdmin = isAdmin(user);
        boolean isAuthor = elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId);
        boolean isAssigned = elder.getAssignedOperatorId() != null && elder.getAssignedOperatorId().equals(userId);

        // ===== ФОРМИРУЕМ КАРТОЧКУ =====
        String card = "📋 **Заявка #" + elder.getId() + "**\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📍 **Локация:** " + elder.getPreferredLocation() + "\n" +
                "💰 **Бюджет:** " + elder.getBudget() + " руб.\n" +
                "🎂 **Возраст:** " + elder.getAge() + " лет\n" +
                "💊 **Здоровье:** " + elder.getHealthCondition() + "\n" +
                "📝 **Пожелания:** " + elder.getRequirements() + "\n" +
                "📌 **Статус:** " + elder.getStatus() + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n";

        // ===== ПОКАЗЫВАЕМ ИМЯ И КОНТАКТЫ ТОЛЬКО ЕСЛИ ЗАЯВКА В РАБОТЕ =====
        boolean canSeeContacts = isAssigned || isAuthor || isAdmin;

        if (canSeeContacts) {
            card += "👤 **Имя:** " + elder.getFullName() + "\n" +
                    "👤 **Клиент:** " + elder.getClientFirstName() + "\n" +
                    "📱 **Телефон:** " + elder.getClientPhone() + "\n";
        } else {
            card += "🔒 **Имя и контакты скрыты**\n" +
                    "📌 Для получения контактов возьмите заявку в работу.\n";
        }

        card += "━━━━━━━━━━━━━━━━━━━━━━━";

        UniversalResponse response = new UniversalResponse(card);

        // ===== КНОПКИ (НА ВСЮ СТРОКУ) =====
        if (isOperator && !isAuthor) {
            if (elder.getAssignedOperatorId() == null) {
                if (elder.getStatus() == ElderStatus.NEW || elder.getStatus() == ElderStatus.OFFERED) {
                    response.addButtonFullRow("✅ Взять в работу", "take_elder_" + elderId);
                    response.addButtonFullRow("👍 Интересно", "interested_elder_" + elderId);
                    response.addButtonFullRow("👎 Не подходит", "not_interested_elder_" + elderId);
                }
            }

            if (isAssigned && elder.getStatus() == ElderStatus.IN_PROGRESS) {
                response.addButtonFullRow("📨 Отправить запрос на закрытие", "request_complete_elder_" + elderId);
                response.addButtonFullRow("📱 Связаться через MAX", "contact_client_" + elderId);
            }
        }

        if (isAuthor) {
            if (elder.getAssignedOperatorId() == null) {
                if (elder.getStatus() == ElderStatus.NEW || elder.getStatus() == ElderStatus.OFFERED) {
                    response.addButtonFullRow("✏️ Редактировать", "edit_elder_" + elderId);
                    response.addButtonFullRow("🗑️ Удалить", "delete_elder_" + elderId);
                }
            } else {
                response.addButtonFullRow("📋 Мои заявки", "my_requests");
            }
        }

        // ===== ОБЩИЕ КНОПКИ (можно оставить как есть или тоже на всю строку) =====
        response.addButtonFullRow("🔍 Поиск заявок", "find_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }

    // ============================================================
    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С ПАНСИОНАТАМИ (АДМИН) =====
    // ============================================================

  /**
     * Одобрение пансионата администратором
     */
    public UniversalResponse handleApproveCarehome(Long userId, Long careHomeId) {
        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "admin_menu");
        }

        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        // ===== ОДОБРЯЕМ ПАНСИОНАТ =====
        careHome.setStatus("APPROVED");
        careHome.setActive(true);
        careHome.setModeratedBy(userId);
        careHome.setModeratedAt(LocalDateTime.now());
        careHome.setSubscribed(true);
        careHome.setSubscriptionStart(LocalDateTime.now());
        careHome.setSubscriptionEnd(LocalDateTime.now().plusDays(30));
        careHomeService.save(careHome);

        // ===== НАХОДИМ АВТОРА ПАНСИОНАТА =====
        Long authorId = careHome.getProposedBy();
        if (authorId != null) {
            User author = getUserOrNull(authorId);
            if (author != null) {
                // ===== МЕНЯЕМ СТАТУС НА MANAGER (ДИРЕКТОР) =====
                // НЕ НАЧИСЛЯЕМ БОНУСЫ!
                author.setAccessLevel(AccessLevel.MANAGER);
                author.setCareHomeId(careHome.getId());
                userService.saveUser(author);

                log.info("👤 Пользователь {} может управлять пансионатом {}",
                        author.getTelegramId(), careHome.getName());

                // ===== УВЕДОМЛЯЕМ АВТОРА =====
                sendNotification(author.getTelegramId(),
                        "🎉 **Поздравляем! Ваш пансионат одобрен!**\n\n" +
                                "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                                "📍 **Адрес:** " + careHome.getAddress() + "\n\n" +
                                "✅ Теперь вы можете управлять пансионатом!\n" +
                                "📌 Вам доступна панель управления:\n" +
                                "• Регистрация операторов\n" +
                                "• Управление пансионатом\n" +
                                "• Просмотр статистики\n\n" +
                                "🔑 Чтобы зарегистрировать оператора,\n" +
                                "перейдите в раздел 'Мои пансионаты'.\n\n" +
                                "🚀 Удачи!",
                        "🏢 Мои пансионаты", "my_carehomes"
                );
            }
        }

        // ===== ОТВЕТ АДМИНИСТРАТОРУ =====
        UniversalResponse response = new UniversalResponse(
                "✅ Пансионат **" + careHome.getName() + "** одобрен!\n\n" +
                        "📅 Бесплатный период: 30 дней\n" +
                        "📆 До: " + careHome.getSubscriptionEnd() + "\n\n" +
                        "👤 Автор стал директором пансионата."
        );
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    private UniversalResponse handleRejectCarehome(Long userId, String callbackData) {
        String careHomeIdStr = callbackData.substring("reject_carehome_".length());
        Long careHomeId = Long.parseLong(careHomeIdStr);
        CareHome careHome = careHomeService.findById(careHomeId);

        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "admin_menu");
        }

        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        stateService.setEditingCareHomeId(userId, careHomeId);
        stateService.setState(userId, DialogState.AWAITING_REJECT_COMMENT);

        UniversalResponse response = new UniversalResponse(
                "❌ **Отклонение пансионата**\n\n" +
                        "Пансионат: **" + careHome.getName() + "**\n\n" +
                        "Введите причину отказа (комментарий для оператора):"
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    private UniversalResponse handleAdminCarehomeEdit(Long userId, String callbackData) {
        String careHomeIdStr = callbackData.substring("admin_carehome_edit_".length());
        Long careHomeId = Long.parseLong(careHomeIdStr);
        CareHome careHome = careHomeService.findById(careHomeId);

        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "admin_carehomes_menu");
        }

        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        stateService.setEditingCareHomeId(userId, careHomeId);
        stateService.setEditingCareHome(userId, careHome);
        stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_NAME);

        UniversalResponse response = new UniversalResponse(
                "✏️ **Редактирование пансионата**\n\n" +
                        "🏢 **Текущее название:** " + careHome.getName() + "\n\n" +
                        "Введите **новое название**:"
        );
        response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    private UniversalResponse handleAdminCarehomeDelete(Long userId, String callbackData) {
        String careHomeIdStr = callbackData.substring("admin_carehome_delete_".length());
        Long careHomeId = Long.parseLong(careHomeIdStr);
        CareHome careHome = careHomeService.findById(careHomeId);

        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "admin_carehomes_menu");
        }

        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        stateService.setEditingCareHomeId(userId, careHomeId);
        stateService.setState(userId, DialogState.ADMIN_CONFIRM_DELETE_CAREHOME);

        UniversalResponse response = new UniversalResponse(
                "⚠️ **Вы уверены, что хотите удалить пансионат?**\n\n" +
                        "🏢 **Название:** " + careHome.getName() + "\n" +
                        "📍 **Адрес:** " + careHome.getAddress() + "\n\n" +
                        "Это действие нельзя отменить!"
        );
        response.addButtonFullRow("✅ Да, удалить", "admin_carehome_delete_confirm_" + careHomeId);
        response.addButtonFullRow("❌ Отменить", "admin_carehome_view_" + careHomeId);
        return response;
    }

    private UniversalResponse handleSkipEditCarehome(Long userId) {
        DialogState currentState = stateService.getState(userId);
        if (currentState == null) {
            return responseWithBackAndMainMenu("❌ Нет активного редактирования.", "admin_carehomes_menu");
        }

        CareHome editCareHome = stateService.getEditingCareHome(userId);
        if (editCareHome == null) {
            Long editId = stateService.getEditingCareHomeId(userId);
            editCareHome = careHomeService.findById(editId);
            if (editCareHome == null) {
                return responseWithMainMenu("❌ Пансионат не найден.");
            }
            stateService.setEditingCareHome(userId, editCareHome);
        }

        stateService.setEditingCareHome(userId, editCareHome);

        DialogState nextState;
        String nextMessage;

        switch (currentState) {
            case ADMIN_EDIT_CAREHOME_NAME -> {
                nextState = DialogState.ADMIN_EDIT_CAREHOME_ADDRESS;
                nextMessage = "📍 **Текущий адрес:** " + editCareHome.getAddress() + "\n\n" +
                        "Введите **новый адрес** (или нажмите 'Оставить без изменений'):";
            }
            case ADMIN_EDIT_CAREHOME_ADDRESS -> {
                nextState = DialogState.ADMIN_EDIT_CAREHOME_PHONE;
                nextMessage = "📞 **Текущий телефон:** " + editCareHome.getPhone() + "\n\n" +
                        "Введите **новый телефон** (или нажмите 'Оставить без изменений'):";
            }
            case ADMIN_EDIT_CAREHOME_PHONE -> {
                nextState = DialogState.ADMIN_EDIT_CAREHOME_WEBSITE;
                nextMessage = "🌐 **Текущий сайт:** " +
                        (editCareHome.getWebsite() != null ? editCareHome.getWebsite() : "не указан") + "\n\n" +
                        "Введите **новый сайт** (или нажмите 'Оставить без изменений'):";
            }
            case ADMIN_EDIT_CAREHOME_WEBSITE -> {
                nextState = DialogState.ADMIN_EDIT_CAREHOME_PRICE;
                nextMessage = "💰 **Текущая цена:** " + editCareHome.getPriceFrom() + " руб.\n\n" +
                        "Введите **новую цену** (или нажмите 'Оставить без изменений'):";
            }
            case ADMIN_EDIT_CAREHOME_PRICE -> {
                nextState = DialogState.ADMIN_EDIT_CAREHOME_DESCRIPTION;
                nextMessage = "📝 **Текущее описание:** " + editCareHome.getDescription() + "\n\n" +
                        "Введите **новое описание** (или нажмите 'Оставить без изменений'):";
            }
            case ADMIN_EDIT_CAREHOME_DESCRIPTION -> {
                nextState = DialogState.ADMIN_EDIT_CAREHOME_SPECIALIZATION;
                nextMessage = "🏥 **Текущая специализация:** " + editCareHome.getSpecialization() + "\n\n" +
                        "Введите **новую специализацию** (или нажмите 'Оставить без изменений'):";
            }
            case ADMIN_EDIT_CAREHOME_SPECIALIZATION -> {
                nextState = DialogState.ADMIN_EDIT_CAREHOME_LATITUDE;
                String currentLat = editCareHome.getLatitude() != null
                        ? String.valueOf(editCareHome.getLatitude())
                        : "не задана";
                nextMessage = "🗺️ **Текущая широта:** " + currentLat + "\n\n" +
                        "Введите **широту** (или нажмите 'Оставить без изменений'):\n" +
                        "Например: 55.7558";
            }
            case ADMIN_EDIT_CAREHOME_LATITUDE -> {
                nextState = DialogState.ADMIN_EDIT_CAREHOME_LONGITUDE;
                String currentLon = editCareHome.getLongitude() != null
                        ? String.valueOf(editCareHome.getLongitude())
                        : "не задана";
                nextMessage = "🗺️ **Текущая долгота:** " + currentLon + "\n\n" +
                        "Введите **долготу** (или нажмите 'Оставить без изменений'):\n" +
                        "Например: 37.6173";
            }
            case ADMIN_EDIT_CAREHOME_LONGITUDE -> {
                careHomeService.save(editCareHome);
                stateService.clearState(userId);
                stateService.clearEditingCareHome(userId);

                String confirmMessage = "✅ **Пансионат успешно обновлён!**\n\n" +
                        "📋 **Обновлённые данные:**\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "🏢 **Название:** " + editCareHome.getName() + "\n" +
                        "📍 **Адрес:** " + editCareHome.getAddress() + "\n" +
                        "📞 **Телефон:** " + editCareHome.getPhone() + "\n" +
                        "💰 **Цена:** " + editCareHome.getPriceFrom() + " руб.\n" +
                        "📝 **Описание:** " + editCareHome.getDescription() + "\n" +
                        "🏥 **Специализация:** " + editCareHome.getSpecialization() + "\n" +
                        "🗺️ **Координаты:** " +
                        (editCareHome.getLatitude() != null ? editCareHome.getLatitude() : "не задана") + ", " +
                        (editCareHome.getLongitude() != null ? editCareHome.getLongitude() : "не задана") + "\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━";

                UniversalResponse confirmResponse = new UniversalResponse(confirmMessage);
                confirmResponse.addButtonFullRow("📋 Назад к пансионату", "admin_carehome_view_" + editCareHome.getId());
                confirmResponse.addButtonFullRow("🏢 Управление пансионатами", "admin_carehomes_menu");
                confirmResponse.addButtonFullRow("🏠 Главное меню", "main_menu");
                return confirmResponse;
            }
            default -> {
                return responseWithBackAndMainMenu("❌ Неизвестный шаг.", "admin_carehomes_menu");
            }
        }

        stateService.setState(userId, nextState);
        UniversalResponse response = new UniversalResponse(
                "⏭️ Шаг пропущен.\n\n" + nextMessage
        );
        response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    // ============================================================
    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С БОНУСАМИ =====
    // ============================================================

    private UniversalResponse handleBonusEdit(Long userId, String callbackData) {
        String actionKey = callbackData.substring("admin_bonus_edit_".length());

        BonusSetting setting = bonusSettingService.findByActionKey(actionKey);
        if (setting == null) {
            return responseWithBackAndMainMenu("❌ Настройка не найдена.", "admin_bonus_settings");
        }

        stateService.setEditingBonusKey(userId, actionKey);
        stateService.setState(userId, DialogState.AWAITING_BONUS_NEW_VALUE);

        String sign = setting.getValue() > 0 ? "+" : "";
        UniversalResponse response = new UniversalResponse(
                "✏️ **Изменение бонуса**\n\n" +
                        "📌 **Действие:** " + setting.getActionName() + "\n" +
                        "💰 **Текущее значение:** " + sign + setting.getValue() + " балл" +
                        (Math.abs(setting.getValue()) != 1 ? "а" : "") + "\n\n" +
                        "Введите **новое значение** (целое число):\n" +
                        "Например: " + (setting.getValue() + 2)
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    // ============================================================
    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С ЗАЯВКАМИ (АДМИН) =====
    // ============================================================

    private UniversalResponse handleApproveElder(Long userId, String callbackData) {
        String elderIdStr = callbackData.substring("approve_elder_".length());
        Long elderId = Long.parseLong(elderIdStr);
        Elder elder = getElderOrNull(elderId);

        if (elder == null) {
            return responseWithBackAndMainMenu("❌ Заявка не найдена.", "admin_elder_moderation");
        }

        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        if (elder.getStatus() != ElderStatus.PENDING) {
            return responseWithBackAndMainMenu(
                    "❌ Заявка уже обработана или не на модерации.\nТекущий статус: " + elder.getStatus(),
                    "admin_elder_moderation"
            );
        }

        elder.setStatus(ElderStatus.NEW);
        elder.setAcceptedBy(userId);
        elder.setAcceptedAt(LocalDateTime.now());
        elderService.updateElder(elder);

        User client = getUserOrNull(elder.getClientTelegramId());
        if (client != null) {
            sendNotification(client.getTelegramId(),
                    "✅ **Ваша заявка #" + elder.getId() + " одобрена!**\n\n" +
                            "🎉 Теперь операторы увидят вашу заявку.\n\n" +
                            "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                            "📌 **Статус:** Ожидает оператора",
                    "👤 Моя заявка", "my_request"
            );
        }

        UniversalResponse response = new UniversalResponse(
                "✅ Заявка #" + elderId + " одобрена!\n\n" +
                        "Клиент получит уведомление."
        );
        response.addButtonFullRow("📋 Модерация заявок", "admin_elder_moderation");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    // ============================================================
    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С МЕНЮ =====
    // ============================================================

    private UniversalResponse handleMenuRequests(Long userId) {
        User user = getUserOrNull(userId);
        boolean isOperator = isOperator(user);
        boolean isAdmin = isAdmin(user);
        boolean isGuest = isGuest(user);

        UniversalResponse response = new UniversalResponse(
                "📋 **Управление заявками**\n\nВыберите действие:"
        );

        if (isOperator || isAdmin) {
            response.addButton("📝 Создать заявку", "new_request");
            response.addButton("📋 Мои заявки", "my_requests");
            response.addButton("🔍 Поиск заявок", "find_requests");
            response.addButton("⚙️ Настройки поиска", "settings_menu");  // ← НОВАЯ КНОПКА
        }

        if (isGuest) {
            response.addButton("📝 Создать заявку", "new_request");
            response.addButton("👤 Моя заявка", "my_request");
        }

        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    private UniversalResponse handleMenuCarehomes(Long userId) {
        User user = getUserOrNull(userId);
        boolean isOperator = isOperator(user);
        boolean isManager = user != null && user.getAccessLevel() == AccessLevel.MANAGER;  // ← ДОБАВИТЬ
        boolean isAdmin = isAdmin(user);

        UniversalResponse response = new UniversalResponse(
                "🏢 **Управление пансионатами**\n\nВыберите действие:"
        );

        // ===== ДЛЯ ОПЕРАТОРОВ, ДИРЕКТОРОВ И АДМИНИСТРАТОРОВ =====
        if (isOperator || isManager || isAdmin) {
            response.addButton("📝 Предложить пансионат", "propose_carehome");
            response.addButton("📋 Мои пансионаты", "my_carehomes");
            response.addButton("⚙️ Настройки", "settings_menu");

            // ===== КНОПКА ПОМОЩИ ТОЛЬКО ДЛЯ ДИРЕКТОРОВ =====
            if (isManager) {
                response.addButton("❓ Помощь", "help");
            }
        }

        // ===== ОБЩИЕ КНОПКИ ДЛЯ ВСЕХ =====
        response.addButton("📋 Список пансионатов", "list_carehomes");
        response.addButton("🗺️ Карта пансионатов", "map_carehomes");

        // ===== АДМИНИСТРАТОР =====
        if (isAdmin) {
            response.addButton("⚙️ Управление пансионатами (админ)", "admin_carehomes_menu");
        }

        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    private UniversalResponse handleMyRequests(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        // ===== ДЛЯ ОПЕРАТОРОВ =====
        if (isOperator(user) || isAdmin(user)) {
            UniversalResponse response = new UniversalResponse(
                    "📋 **Мои заявки**\n\nВыберите раздел:"
            );
            response.addButtonFullRow("📤 Переданные заявки", "my_requests_created");
            response.addButtonFullRow("📋 В работе", "my_requests_in_progress");
            response.addButtonFullRow("⭐ Интересные", "my_requests_interested");
            response.addButtonFullRow("✅ Завершённые", "my_requests_completed");
            response.addButtonFullRow("📋 Заявки", "menu_requests");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        // ===== ДЛЯ КЛИЕНТОВ (GUEST) =====
        if (isGuest(user)) {
            return showMyRequest(userId);
        }

        return responseWithMainMenu("❌ Доступ запрещён.");
    }

    private UniversalResponse handleFindRequests(Long userId) {
        User user = getUserOrNull(userId);
        if (!isOperator(user) && !isAdmin(user)) {
            return responseWithBackAndMainMenu("❌ Доступно только для операторов.", "menu_requests");
        }

        // ===== НАСТРОЙКИ ПОЛЬЗОВАТЕЛЯ =====
        String city = user.getPreferredCity();
        String region = user.getPreferredRegion();
        Double budgetMin = user.getBudgetMin();
        Double budgetMax = user.getBudgetMax();

        boolean filterTodayOnly = stateService.isFilterTodayOnly(userId);

        boolean hasCityFilter = city != null && !city.trim().isEmpty();
        boolean hasRegionFilter = region != null && !region.trim().isEmpty();
        boolean hasFilters = hasCityFilter || hasRegionFilter ||
                budgetMin != null || budgetMax != null ||
                filterTodayOnly;

        // ===== ФИЛЬТРАЦИЯ =====
        List<Elder> allElders = elderService.findActiveElders();

        List<Elder> filteredElders = allElders.stream()
                .filter(elder -> {
                    // Фильтр по дате
                    if (filterTodayOnly) {
                        LocalDateTime today = LocalDateTime.now();
                        LocalDateTime startOfDay = today.withHour(0).withMinute(0).withSecond(0).withNano(0);
                        if (elder.getCreatedAt() == null || elder.getCreatedAt().isBefore(startOfDay)) {
                            return false;
                        }
                    }

                    // Фильтр по городу/региону (ИЛИ)
                    if (hasCityFilter || hasRegionFilter) {
                        String elderCity = elder.getCity() != null ? elder.getCity().toLowerCase() : "";
                        String elderLocation = elder.getPreferredLocation() != null ? elder.getPreferredLocation().toLowerCase() : "";
                        String elderRegion = elder.getRegion() != null ? elder.getRegion().toLowerCase() : "";

                        boolean locationMatches = false;
                        if (hasCityFilter) {
                            String searchCity = city.toLowerCase().trim();
                            if (elderCity.contains(searchCity) || elderLocation.contains(searchCity)) {
                                locationMatches = true;
                            }
                        }
                        if (!locationMatches && hasRegionFilter) {
                            String searchRegion = region.toLowerCase().trim();
                            if (elderRegion.contains(searchRegion) || elderLocation.contains(searchRegion)) {
                                locationMatches = true;
                            }
                        }
                        if (!locationMatches) {
                            return false;
                        }
                    }

                    // Фильтр по бюджету
                    if (budgetMin != null && elder.getBudget() < budgetMin) return false;
                    if (budgetMax != null && elder.getBudget() > budgetMax) return false;

                    return true;
                })
                .collect(Collectors.toList());

        // ===== ОТВЕТ =====
        UniversalResponse response;

        if (filteredElders.isEmpty()) {
            response = new UniversalResponse("🔍 Активных заявок, соответствующих вашим фильтрам, пока нет.");
        } else {
            // Пагинация
            int page = stateService.getCurrentPage(userId);
            int pageSize = 10;
            int totalPages = (int) Math.ceil((double) filteredElders.size() / pageSize);
            if (page >= totalPages) {
                page = 0;
                stateService.setCurrentPage(userId, 0);
            }

            int fromIndex = page * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, filteredElders.size());
            List<Elder> elders = filteredElders.subList(fromIndex, toIndex);

            // Сообщение с индикатором фильтров
            StringBuilder message = new StringBuilder();
            message.append("🔍 **Доступные заявки (страница ").append(page + 1).append(" из ").append(totalPages).append("):**\n\n");

            if (hasCityFilter) {
                message.append("📍 Город: ").append(city).append("\n");
            }
            if (hasRegionFilter) {
                message.append("📍 Регион: ").append(region).append("\n");
            }
            if (budgetMin != null || budgetMax != null) {
                String budgetText = "";
                if (budgetMin != null && budgetMax != null) {
                    budgetText = budgetMin + " - " + budgetMax + " руб.";
                } else if (budgetMin != null) {
                    budgetText = "от " + budgetMin + " руб.";
                } else if (budgetMax != null) {
                    budgetText = "до " + budgetMax + " руб.";
                }
                message.append("💰 Бюджет: ").append(budgetText).append("\n");
            }
            if (filterTodayOnly) {
                message.append("📅 За сегодня\n");
            }

            if (hasFilters) {
                if (hasCityFilter || hasRegionFilter) {
                    message.append("📌 Ищутся заявки с **ИЛИ** город, **ИЛИ** регион\n");
                }
                message.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
            }

            message.append("📌 Нажмите на заявку, чтобы просмотреть детали.\n");
            message.append("📌 Имена и контакты скрыты до взятия в работу.\n");
            message.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

            response = new UniversalResponse(message.toString());

            for (Elder elder : elders) {
                String location = elder.getPreferredLocation() != null ? elder.getPreferredLocation() : "не указана";
                String buttonText = location + " | " + elder.getBudget() + " руб. | " + elder.getAge() + " лет";
                response.addButtonFullRow(buttonText, "view_elder_" + elder.getId());
            }

            // Кнопки пагинации
            if (page > 0) {
                response.addButtonFullRow("⬅️ Предыдущая", "find_requests_prev");
            }
            if (page < totalPages - 1) {
                response.addButtonFullRow("➡️ Следующая", "find_requests_next");
            }
        }

        // ===== КНОПКИ ФИЛЬТРОВ (ВСЕГДА ВНИЗУ) =====
        if (filterTodayOnly) {
            response.addButtonFullRow("📅 Показать все", "filter_today_off");
        } else {
            response.addButtonFullRow("📅 Только за сегодня", "filter_today_on");
        }

        if (hasFilters) {
            response.addButtonFullRow("🧹 Очистить фильтры", "clear_filters");
        }

        response.addButtonFullRow("📋 Заявки", "menu_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    private UniversalResponse handleRegisterCarehome(Long userId) {
        stateService.setState(userId, DialogState.AWAITING_CAREHOME_NAME);
        return responseWithBackAndMainMenu(
                "🏢 Введите название нового пансионата:",
                "menu_carehomes"
        );
    }

    /**
     * Регистрация оператора (только для MANAGER)
     */
    private UniversalResponse handleRegisterOperator(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        // ===== ТОЛЬКО MANAGER (ДИРЕКТОР) МОЖЕТ РЕГИСТРИРОВАТЬ ОПЕРАТОРА =====
        if (user.getAccessLevel() != AccessLevel.MANAGER) {
            return responseWithMainMenu("❌ Только директор пансионата может регистрировать операторов.");
        }

        // Проверяем, есть ли у директора привязанный пансионат
        if (user.getCareHomeId() == null) {
            return responseWithMainMenu("❌ У вас нет привязанного пансионата.");
        }

        stateService.setState(userId, DialogState.AWAITING_OPERATOR_NAME);
        stateService.setEditingCareHomeId(userId, user.getCareHomeId());

        UniversalResponse response = new UniversalResponse(
                "🔑 **Регистрация оператора**\n\n" +
                        "👤 Введите **имя** оператора (как его называть):"
        );
        response.addButtonFullRow("❌ Отменить", "cancel_action");
        response.addButtonFullRow("🔙 Назад", "my_carehomes");
        return response;
    }

    private UniversalResponse handleRequestContactFromMax(Long userId) {
        stateService.setTempPurpose(userId, "elder_phone");
        stateService.setState(userId, DialogState.AWAITING_CONTACT_FROM_MAX);

        UniversalResponse response = new UniversalResponse(
                "📱 Нажмите кнопку ниже, чтобы отправить ваш номер телефона:"
        );
        response.addContactRequestButton("📱 Отправить номер");
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    private UniversalResponse handleEnterPhoneManually(Long userId) {
        stateService.setState(userId, DialogState.AWAITING_ELDER_PHONE_MANUAL);
        UniversalResponse response = new UniversalResponse(
                "📱 Введите номер телефона в формате:\n\n" +
                        "+7 999 123-45-67\n" +
                        "или просто 89991234567\n\n" +
                        "Для отмены нажмите кнопку ниже."
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

    // ============================================================
    // ===== МЕТОДЫ ДЛЯ ОТОБРАЖЕНИЯ (VIEW) =====
    // ============================================================


    private UniversalResponse showMyRequest(Long userId) {
        List<Elder> allElders = elderService.findByClientTelegramId(userId);

        List<Elder> activeElders = allElders.stream()
                .filter(e -> e.getStatus() != ElderStatus.DELETED)
                .filter(e -> e.getStatus() != ElderStatus.EXPIRED)
                .filter(e -> e.getStatus() != ElderStatus.COMPLETED)
                .collect(Collectors.toList());

        if (activeElders.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "👤 **У вас нет активной заявки.**\n\n" +
                            "Нажмите кнопку ниже, чтобы создать заявку."
            );
            response.addButton("📝 Создать заявку", "new_request");
            response.addButton("🏠 Главное меню", "main_menu");
            return response;
        }

        Elder elder = activeElders.get(0);

        String statusDisplay = elder.getStatus().getDisplayName();
        if (elder.getStatus() == ElderStatus.PENDING) {
            statusDisplay = "⏳ На модерации";
        }

        String card = "👤 **Моя заявка #" + elder.getId() + "**\n\n" +
                "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                "🎂 **Возраст:** " + elder.getAge() + " лет\n" +
                "💊 **Здоровье:** " + elder.getHealthCondition() + "\n" +
                "💰 **Бюджет:** " + elder.getBudget() + " руб.\n" +
                "📍 **Локация:** " + elder.getPreferredLocation() + "\n" +
                "📝 **Пожелания:** " + elder.getRequirements() + "\n" +
                "📌 **Статус:** " + statusDisplay;

        UniversalResponse response = new UniversalResponse(card);

        if (elder.getAssignedOperatorId() == null &&
                elder.getStatus() != ElderStatus.COMPLETED &&
                elder.getStatus() != ElderStatus.EXPIRED &&
                elder.getStatus() != ElderStatus.DELETED &&
                elder.getStatus() != ElderStatus.PENDING) {
            response.addButton("✏️ Редактировать", "edit_elder_" + elder.getId());
            response.addButton("🗑️ Удалить", "delete_elder_" + elder.getId());
        }

        if (elder.getStatus() == ElderStatus.COMPLETED) {
            response.addButton("✅ Заявка завершена", "main_menu");
        }

        if (elder.getStatus() == ElderStatus.EXPIRED) {
            response.addButton("⏰ Заявка истекла", "main_menu");
        }

        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    public UniversalResponse showMyCarehomes(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        List<CareHome> careHomes = careHomeService.findByProposedBy(userId);

        if (user.getCareHomeId() != null) {
            CareHome careHome = careHomeService.findById(user.getCareHomeId());
            if (careHome != null && !careHomes.contains(careHome)) {
                careHomes.add(careHome);
            }
        }

        if (careHomes.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "🏢 **Мои пансионаты**\n\n" +
                            "У вас пока нет пансионатов.\n\n" +
                            "📝 Предложите пансионат для регистрации в системе."
            );
            response.addButtonFullRow("📝 Предложить пансионат", "propose_carehome");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        UniversalResponse response = new UniversalResponse("🏢 **Мои пансионаты**\n\nВыберите пансионат:");

        // ===== КНОПКИ ДЛЯ КАЖДОГО ПАНСИОНАТА =====
        for (CareHome careHome : careHomes) {
            String statusIcon = getCareHomeStatusIcon(careHome.getStatus());
            String buttonText = statusIcon + " " + careHome.getName();
            response.addButtonFullRow(buttonText, "view_my_carehome_" + careHome.getId());
        }

        response.addButtonFullRow("📝 Предложить пансионат", "propose_carehome");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    private UniversalResponse showMyRequestsByType(Long userId, String type) {
        List<Elder> elders = new ArrayList<>();
        String title = "";

        switch (type) {
            case "created" -> {
                elders = elderService.findByCreatedBy(userId);
                title = "📤 **Переданные заявки**\n\nЗаявки, которые вы создали:";
            }
            case "in_progress" -> {
                List<Elder> allInProgress = elderService.findByAssignedOperatorId(userId);
                elders = allInProgress.stream()
                        .filter(e -> e.getCreatedBy() == null || !e.getCreatedBy().equals(userId))
                        .collect(Collectors.toList());
                title = "📋 **Заявки в работе**\n\nЗаявки, которые вы взяли:";
            }
            case "interested" -> {
                List<OperatorReaction> reactions = reactionRepository
                        .findByOperatorIdAndReaction(userId, "INTERESTED");
                for (OperatorReaction reaction : reactions) {
                    Elder elder = elderService.findById(reaction.getElderId());
                    if (elder != null && elder.getAssignedOperatorId() == null) {
                        if (elder.getCreatedBy() == null || !elder.getCreatedBy().equals(userId)) {
                            elders.add(elder);
                        }
                    }
                }
                title = "⭐ **Интересные заявки**\n\nЗаявки, которые вас заинтересовали:";
            }
            case "completed" -> {
                elders = elderService.findByCompletedBy(userId);
                title = "✅ **Завершённые заявки**\n\nЗаявки, которые вы закрыли:";
            }
            default -> {
                return responseWithBackAndMainMenu("❌ Неизвестный раздел.", "my_requests");
            }
        }

        if (elders.isEmpty()) {
            UniversalResponse response = new UniversalResponse(title + "\n\n📭 Здесь пока пусто.");
            response.addButtonFullRow("📋 Мои заявки", "my_requests");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        UniversalResponse response = new UniversalResponse(title);

        int maxButtons = Math.min(elders.size(), 10);
        for (int i = 0; i < maxButtons; i++) {
            Elder elder = elders.get(i);
            // ===== КОМПАКТНАЯ КНОПКА БЕЗ ЗНАЧКОВ =====
            String buttonText = elder.getFullName() + " | " + elder.getBudget() + " руб. | " + elder.getAge() + " лет";
            response.addButtonFullRow(buttonText, "view_elder_" + elder.getId());
        }

        if (elders.size() > 10) {
            response.addButtonFullRow("📋 Показать ещё...", "find_requests_more");
        }

        response.addButtonFullRow("📋 Мои заявки", "my_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    // ============================================================
    // ===== УВЕДОМЛЕНИЯ =====
    // ============================================================

    private void notifyAdminsAboutNewCarehome(CareHome careHome) {
        List<User> admins = userService.findByAccessLevel(AccessLevel.ADMIN);

        if (admins.isEmpty()) {
            log.warn("⚠️ Нет администраторов для уведомления");
            return;
        }

        User operator = getUserOrNull(careHome.getProposedBy());
        String operatorName = operator != null ? operator.getFirstName() : "Неизвестный";

        String message = "📢 **Новый пансионат на модерацию!**\n\n" +
                "🏢 **Название:** " + careHome.getName() + "\n" +
                "📍 **Адрес:** " + careHome.getAddress() + "\n" +
                "📞 **Телефон:** " + careHome.getPhone() + "\n" +
                "💰 **Цена от:** " + careHome.getPriceFrom() + " руб.\n" +
                "📝 **Описание:** " + careHome.getDescription() + "\n" +
                "🏥 **Специализация:** " + careHome.getSpecialization() + "\n" +
                "👤 **Предложил:** " + operatorName + "\n\n" +
                "Выберите действие:";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("✅ Одобрить", "approve_carehome_" + careHome.getId());
        response.addButtonFullRow("❌ Отклонить", "reject_carehome_" + careHome.getId());
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");

        for (User admin : admins) {
            try {
                Long chatId = admin.getChatId() != null ? admin.getChatId() : admin.getTelegramId();
                messageSender.sendMessage(chatId, response);
                log.info("📨 Уведомление отправлено администратору {}", admin.getTelegramId());
            } catch (Exception e) {
                log.error("❌ Ошибка отправки администратору {}: {}", admin.getTelegramId(), e.getMessage(), e);
            }
        }
    }
    private UniversalResponse showSearchResults(Long userId, int offset) {
        int pageSize = 10;
        List<Elder> allElders = stateService.getSearchResults(userId);

        if (allElders == null || allElders.isEmpty()) {
            UniversalResponse response = new UniversalResponse("🔍 Активных заявок пока нет.");
            response.addButtonFullRow("📋 Заявки", "menu_requests");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        // Получаем порцию заявок
        int fromIndex = offset;
        int toIndex = Math.min(offset + pageSize, allElders.size());
        List<Elder> pageElders = allElders.subList(fromIndex, toIndex);

        String title = "🔍 **Доступные заявки:**\n\n" +
                "Показаны " + (fromIndex + 1) + "-" + toIndex +
                " из " + allElders.size() + "\n\n";

        UniversalResponse response = new UniversalResponse(title);

        for (Elder elder : pageElders) {
            String buttonText = "👤 " + elder.getFullName() +
                    " (" + elder.getAge() + " лет, " + elder.getBudget() + " руб.)";
            response.addButtonFullRow(buttonText, "view_elder_" + elder.getId());
        }

        // Кнопки пагинации
        if (toIndex < allElders.size()) {
            response.addButtonFullRow("➡️ Показать ещё", "find_requests_more");
        }

        if (offset > 0) {
            response.addButtonFullRow("⬅️ Назад", "find_requests_back");
        }

        response.addButtonFullRow("📋 Заявки", "menu_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        stateService.setSearchOffset(userId, offset);
        return response;
    }
    private UniversalResponse handleFindRequestsMore(Long userId) {
        Integer currentOffset = stateService.getSearchOffset(userId);
        int nextOffset = (currentOffset != null ? currentOffset : 0) + 10;
        return showSearchResults(userId, nextOffset);
    }
    private UniversalResponse handleFindRequestsBack(Long userId) {
        Integer currentOffset = stateService.getSearchOffset(userId);
        int prevOffset = Math.max(0, (currentOffset != null ? currentOffset : 0) - 10);
        return showSearchResults(userId, prevOffset);
    }
    /**
     * Сохраняет предложенный пансионат после принятия оферты
     */
    private UniversalResponse saveProposedCarehomeAfterOffer(Long userId) {
        // ===== ВЕСЬ КОД ИЗ СТАРОГО КЕЙСА =====
        String name = stateService.getTempCareHomeName(userId);
        String address = stateService.getTempCareHomeAddress(userId);
        String phone = stateService.getTempCareHomePhone(userId);
        Double priceValue = stateService.getTempCareHomePrice(userId);
        String description = stateService.getTempCareHomeDescription(userId);
        String specialization = stateService.getTempCareHomeSpecialization(userId);
        String website = stateService.getTempCareHomeWebsite(userId);

        CareHome careHome = new CareHome();
        careHome.setName(name);
        careHome.setAddress(address);
        careHome.setPhone(phone);
        careHome.setPriceFrom(priceValue);
        careHome.setDescription(description);
        careHome.setSpecialization(specialization);
        if (website != null && !website.equals("-")) {
            careHome.setWebsite(website);
        }
        careHome.setStatus("PENDING");
        careHome.setProposedBy(userId);
        careHome.setProposedAt(LocalDateTime.now());
        careHome.setActive(false);
        careHome.setSubscribed(false);
        careHomeService.save(careHome);

        stateService.clearState(userId);

        // Геокодируем адрес
        try {
            double[] coords = yandexMapsService.geocodeAddress(address);
            if (coords != null) {
                careHome.setLatitude(coords[0]);
                careHome.setLongitude(coords[1]);
                careHomeService.save(careHome);
                log.info("✅ Координаты сохранены для {}", name);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка геокодирования: {}", e.getMessage());
        }

        // Уведомляем администраторов
        notifyAdminsAboutNewCarehome(careHome);

        // ===== ОТВЕТ ПОЛЬЗОВАТЕЛЮ =====
        UniversalResponse response = new UniversalResponse(
                "✅ **Пансионат отправлен на модерацию!**\n\n" +
                        "🏢 **Название:** " + name + "\n" +
                        "📍 **Адрес:** " + address + "\n" +
                        "📞 **Телефон:** " + phone + "\n" +
                        "💰 **Цена от:** " + priceValue + " руб.\n" +
                        "📝 **Описание:** " + description + "\n" +
                        "🏥 **Специализация:** " + specialization + "\n" +
                        "🌐 **Сайт:** " + (website != null && !website.equals("-") ? website : "не указан") + "\n" +
                        "📋 **Оферта принята:** ✅\n\n" +
                        "⏳ Статус: На модерации\n\n" +
                        "Администратор рассмотрит заявку в ближайшее время."
        );
        response.addButtonFullRow("🏢 Список пансионатов", "list_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Показывает профиль оператора
     */
    private UniversalResponse showOperatorProfile(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (!isOperator(user)) {
            return responseWithMainMenu("❌ Доступно только для операторов.");
        }

        // ===== ФОРМИРУЕМ КАРТОЧКУ ПРОФИЛЯ =====
        StringBuilder sb = new StringBuilder();
        sb.append("👤 **Мой профиль**\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("👤 **Имя:** ").append(user.getFirstName() != null ? user.getFirstName() : "не указано").append("\n");
        sb.append("📱 **Телефон:** ").append(user.getPhone() != null ? user.getPhone() : "не указан").append("\n");
        sb.append("📱 **WhatsApp:** ").append(user.getWhatsapp() != null ? user.getWhatsapp() : "не указан").append("\n");
        sb.append("✈️ **Telegram:** ").append(user.getTelegramUsername() != null ? "@" + user.getTelegramUsername() : "не указан").append("\n");
        sb.append("📧 **Email:** ").append(user.getEmail() != null ? user.getEmail() : "не указан").append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🏢 **Пансионат:** ").append(getCareHomeName(user.getCareHomeId())).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━");

        UniversalResponse response = new UniversalResponse(sb.toString());

        // ===== КНОПКИ РЕДАКТИРОВАНИЯ =====
        response.addButtonFullRow("✏️ Редактировать профиль", "edit_profile");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }
    /**
     * Показывает справочную информацию в зависимости от роли пользователя.
     */
    private UniversalResponse showHelp(Long userId) {
        User user = getUserOrNull(userId);
        String helpText;

        if (user == null) {
            helpText = "❓ **Помощь**\n\nПожалуйста, авторизуйтесь, чтобы получить полную справку.";
        } else {
            switch (user.getAccessLevel()) {
                case ADMIN:
                    helpText = "❓ **Помощь для администратора**\n\n" +
                            "**Ваши основные действия:**\n" +
                            "*   **📋 Модерация заявок:** Проверка и одобрение/отклонение новых заявок.\n" +
                            "*   **🏢 Управление пансионатами:** Просмотр, одобрение, редактирование и блокировка пансионатов.\n" +
                            "*   **👥 Управление операторами:** Просмотр, блокировка и управление баллами операторов.\n" +
                            "*   **📊 Общая статистика:** Общая аналитика по системе.\n" +
                            "*   **💰 Настройка бонусов:** Изменение количества баллов за действия.\n\n" +
                            "**Важно:** В карточке оператора вы можете добавлять или убирать баллы, а также блокировать или удалять его.";
                    break;
                case MANAGER:
                    helpText = "❓ **Помощь для директора пансионата**\n\n" +
                            "Вы управляете своим пансионатом в системе.\n\n" +
                            "**Ваши основные действия:**\n" +
                            "*   **🏢 Мои пансионаты:** Просмотр и управление вашими пансионатами.\n" +
                            "*   **👥 Операторы:** Управление командой операторов.\n" +
                            "*   **📋 Заявки:** Полный доступ к управлению заявками.\n" +
                            "*   **📊 Статистика:** Аналитика по вашим пансионатам.\n\n" +
                            "**Управление командой:**\n" +
                            "Чтобы зарегистрировать нового оператора, перейдите в раздел **\"👥 Операторы\"** и нажмите **\"📝 Регистрация оператора\"**.";
                    break;
                case OPERATOR:
                    helpText = "❓ **Помощь для операторов**\n\n" +
                            "Это ваш инструмент для поиска клиентов.\n\n" +
                            "**Ваши основные действия:**\n" +
                            "*   **📋 Заявки:** Управление всеми заявками.\n" +
                            "*   **🔍 Поиск заявок:** Находить новые заявки с фильтрацией.\n" +
                            "*   **📋 Мои заявки:** Отслеживать ваши заявки.\n" +
                            "*   **🏢 Мои пансионаты:** Информация о вашем пансионате.\n" +
                            "*   **📊 Моя статистика:** Следить за эффективностью.\n\n" +
                            "**Процесс работы:**\n" +
                            "1. Найдите заявку и нажмите **\"✅ Взять в работу\"**.\n" +
                            "2. Используйте **\"📱 Связаться через MAX\"** для связи с клиентом.\n" +
                            "3. После заселения нажмите **\"📨 Отправить запрос на закрытие\"**.\n\n" +
                            "**Важно:** Контакты клиентов скрыты до взятия заявки.";
                    break;
                default: // GUEST или CLIENT
                    helpText = "❓ **Помощь для клиентов**\n\n" +
                            "Этот бот помогает найти пансионат для ваших близких.\n\n" +
                            "**Как мы работаем:**\n" +
                            "1.  **Создайте заявку:** Нажмите \"📝 Создать заявку\".\n" +
                            "2.  **Дождитесь модерации:** Администратор проверит её.\n" +
                            "3.  **Получите предложения:** Операторы свяжутся с вами.\n" +
                            "4.  **Выберите пансионат:** Сравните и примите решение.\n\n" +
                            "**Ваши основные действия:**\n" +
                            "*   **📝 Создать заявку:** Начать поиск.\n" +
                            "*   **👤 Моя заявка:** Посмотреть статус.\n" +
                            "*   **🏢 Список пансионатов:** Просмотреть все пансионаты.";
                    break;
            }
        }

        // Используем готовый метод для добавления кнопки "Главное меню"
        return responseWithMainMenu(helpText);
    }
    /**
     * Уведомляет администраторов о новой заявке
     */
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
                "📋 **Согласие на обработку данных:** ✅\n" +
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
    private UniversalResponse handleAcceptInvitation(Long userId, Long careHomeId) {
        User user = userService.findByTelegramId(userId).orElse(null);
        if (user != null && user.getAccessLevel() == AccessLevel.OPERATOR) {
            return responseWithMainMenu("✅ Вы уже являетесь оператором.");
        }

        if (user == null) {
            user = new User();
            user.setTelegramId(userId);
        }

        user.setAccessLevel(AccessLevel.OPERATOR);
        user.setCareHomeId(careHomeId);
        user.setRegistered(true);
        user.setRegisteredAt(LocalDateTime.now());
        if (user.getBonusPoints() == 0) {
            user.setBonusPoints(10);
        }
        userService.saveUser(user);

        // ❌ НЕ ПОМЕЧАЕМ ПРИГЛАШЕНИЕ КАК ИСПОЛЬЗОВАННОЕ!
        // Потому что приглашение не привязано к конкретному оператору

        stateService.setState(userId, DialogState.AWAITING_OPERATOR_PROFILE_NAME);

        UniversalResponse response = new UniversalResponse(
                "✅ Вы стали оператором! 🎉\n\n" +
                        "Теперь заполните ваш профиль.\n\n" +
                        "Введите ваше **имя**:"
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }
    private UniversalResponse handleOperatorProfile(Long userId, String text, DialogState state) {
        User user = userService.findByTelegramId(userId).orElse(null);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        UniversalResponse response;

        switch (state) {
            case AWAITING_OPERATOR_PROFILE_NAME -> {
                user.setFirstName(text.trim());
                userService.saveUser(user);
                stateService.setState(userId, DialogState.AWAITING_OPERATOR_PROFILE_PHONE);
                response = new UniversalResponse("📱 Введите ваш **номер телефона** (или 'пропустить'):");
                response.addButton("⏭️ Пропустить", "skip_profile_phone");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case AWAITING_OPERATOR_PROFILE_PHONE -> {
                if (!text.equals("skip_profile_phone")) {
                    String phone = text.replaceAll("[^0-9+]", "");
                    if (!phone.matches("^[+]?[0-9]{10,15}$")) {
                        response = new UniversalResponse("❌ Неверный формат. Попробуйте снова:");
                        response.addButton("⏭️ Пропустить", "skip_profile_phone");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                    user.setPhone(phone);
                    userService.saveUser(user);
                }
                stateService.setState(userId, DialogState.AWAITING_OPERATOR_PROFILE_WHATSAPP);
                response = new UniversalResponse("📱 Введите ваш **WhatsApp** (или 'пропустить'):");
                response.addButton("⏭️ Пропустить", "skip_profile_whatsapp");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case AWAITING_OPERATOR_PROFILE_WHATSAPP -> {
                if (!text.equals("skip_profile_whatsapp")) {
                    user.setWhatsapp(text.trim());
                    userService.saveUser(user);
                }
                stateService.setState(userId, DialogState.AWAITING_OPERATOR_PROFILE_EMAIL);
                response = new UniversalResponse("📧 Введите ваш **Email** (или 'пропустить'):");
                response.addButton("⏭️ Пропустить", "skip_profile_email");
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case AWAITING_OPERATOR_PROFILE_EMAIL -> {
                if (!text.equals("skip_profile_email")) {
                    user.setEmail(text.trim());
                    userService.saveUser(user);
                }

                stateService.clearState(userId);

                // Уведомляем директора
                notifyDirector(user);

                response = new UniversalResponse("✅ Профиль сохранён! Добро пожаловать в команду! 🎉");
                response.addButton("📋 Мои заявки", "menu_requests");
                response.addButton("🏠 Главное меню", "main_menu");
                return response;
            }

            default -> {
                return responseWithMainMenu("❌ Что-то пошло не так.");
            }
        }
    }
}
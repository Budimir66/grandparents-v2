package org.grandparents.service;

import org.grandparents.bot.max.MaxWebhookHandler;
import org.grandparents.dto.UniversalMessage;
import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.*;
import org.grandparents.repository.BonusTransactionRepository;
import org.grandparents.repository.ComplaintRepository;
import org.grandparents.repository.OperatorReactionRepository;
import org.grandparents.repository.RatingRepository;
import org.grandparents.statemachine.DialogState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
    private final BonusTransactionRepository bonusTransactionRepository;
    private final ComplaintRepository complaintRepository;
    private final RatingRepository ratingRepository;
    private final RatingService ratingService;

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
                      InvitationService invitationService,
                      BonusTransactionRepository bonusTransactionRepository,
                      ComplaintRepository complaintRepository,
                      RatingRepository ratingRepository,
                      RatingService ratingService) {
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
        this.bonusTransactionRepository = bonusTransactionRepository;
        this.complaintRepository = complaintRepository;
        this.ratingRepository = ratingRepository;
        this.ratingService = ratingService;
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
            // Ищем приглашение по токену
            // Токен = название пансионата + 4 цифры
            // Убираем цифры, оставляем название для поиска пансионата
            String cleanText = text.replaceAll("\\s+\\d+$", "").trim();
            CareHome careHome = careHomeService.findByNameIgnoreCase(cleanText);
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
        return user != null &&
                (user.getAccessLevel() == AccessLevel.OPERATOR ||
                        user.getAccessLevel() == AccessLevel.MANAGER);  // ← ДОБАВЛЯЕМ MANAGER
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
                    "📌 Просматривать статистику\n" +
                    "📌 Работать с заявками как супер-оператор\n\n" +
                    "Выберите действие:");

            response.addButtonFullRow("👥 Операторы", "manager_operators");
            response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");  // ← НОВАЯ КНОПКА
            response.addButtonFullRow("⭐ Супер оператор", "super_operator_menu");
            response.addButtonFullRow("📊 Статистика", "manager_stats");
            response.addButtonFullRow("❓ Помощь", "help");
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
            // ===== ПРОВЕРКА НА БЛОКИРОВКУ =====
            if (user.getIsBlocked() != null && user.getIsBlocked()) {
                String blockedReason = user.getBlockedReason() != null
                        ? user.getBlockedReason()
                        : "Администратор временно ограничил доступ.";

                response = new UniversalResponse(
                        "⛔ **Доступ ограничен!**\n\n" +
                                "Ваш аккаунт был заблокирован.\n\n" +
                                "💬 Причина: " + blockedReason + "\n\n" +
                                "📌 Для восстановления доступа обратитесь к руководителю."
                );
                response.addButtonFullRow("❓ Помощь", "help");
                return response;
            }

            int newRequests = (int) elderService.countActiveElders();
            int bonus = user.getBonusPoints();

            response.setText("📊 **Вы вошли как оператор**\n\n" +
                    "💰 **Ваш баланс:** " + bonus + " баллов\n" +
                    "📨 **Новых заявок:** " + newRequests + "\n\n" +
                    "Хорошей работы! 🚀");
            response.addButton("📋 Заявки", "menu_requests");
            response.addButton("🏢 Мои пансионаты", "my_carehomes");
            response.addButton("📊 Моя статистика", "my_stats");
            response.addButton("👤 Мой профиль", "my_profile");
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
            response.addButton("🚨 Жалобы", "admin_complaints");  // ← НОВАЯ КНОПКА
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

        if (callbackData.equals("request_contact_from_max")) {
            return handleRequestContactFromMax(userId);
        }

        if (callbackData.equals("confirm_delete_completed_yes")) {
            Elder elder = stateService.getTempElder(userId);
            if (elder == null) {
                return responseWithMainMenu("❌ Заявка не найдена.");
            }
            elderService.deleteElder(elder.getId());
            stateService.clearState(userId);
            return responseWithMainMenu("✅ Заявка #" + elder.getId() + " удалена.");
        }

        if (callbackData.equals("confirm_delete_completed_no")) {
            stateService.clearState(userId);
            return responseWithMainMenu("❌ Удаление отменено.");
        }

        // ===== УДАЛЕНИЕ ЗАВЕРШЁННОЙ ЗАЯВКИ =====
        if (callbackData.startsWith("delete_completed_elder_")) {
            String elderIdStr = callbackData.substring("delete_completed_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return handleDeleteCompletedElder(userId, elderId);
        }

        // ===== ОЦЕНКА ЗАЯВКИ (ДЛЯ ОПЕРАТОРОВ) =====
        if (callbackData.startsWith("rate_elder_")) {
            String elderIdStr = callbackData.substring("rate_elder_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return handleRateElder(userId, elderId);
        }

        // ===== СВЯЗАТЬСЯ С ОПЕРАТОРОМ =====
        if (callbackData.startsWith("contact_operator_")) {
            String operatorIdStr = callbackData.substring("contact_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return handleContactOperator(userId, operatorId);
        }

        // ===== ЖАЛОБЫ (АДМИН) =====
        if (callbackData.equals("admin_complaints")) {
            return showComplaintsList(userId);
        }

// ===== ПРОСМОТР КОНКРЕТНОЙ ЖАЛОБЫ =====
        if (callbackData.startsWith("admin_view_complaint_")) {
            String complaintIdStr = callbackData.substring("admin_view_complaint_".length());
            Long complaintId = Long.parseLong(complaintIdStr);
            return showComplaintDetails(userId, complaintId);
        }

// ===== ИЗМЕНЕНИЕ СТАТУСА ЖАЛОБЫ =====
        if (callbackData.startsWith("admin_resolve_complaint_")) {
            String complaintIdStr = callbackData.substring("admin_resolve_complaint_".length());
            Long complaintId = Long.parseLong(complaintIdStr);
            return resolveComplaint(userId, complaintId);
        }

// ===== ЖАЛОБА =====
        if (callbackData.startsWith("complaint_")) {
            String elderIdStr = callbackData.substring("complaint_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return handleComplaint(userId, elderId);
        }

        // ===== ВЫБОР ЗВЁЗД ДЛЯ ОЦЕНКИ =====
        if (callbackData.startsWith("rate_stars_")) {
            // Формат: rate_stars_1_12 (где 1 — количество звёзд, 12 — elderId)
            String[] parts = callbackData.split("_");
            if (parts.length < 4) {
                return responseWithMainMenu("❌ Ошибка формата оценки.");
            }
            try {
                int stars = Integer.parseInt(parts[2]);
                Long elderId = Long.parseLong(parts[3]);
                return handleSaveRating(userId, elderId, stars);
            } catch (NumberFormatException e) {
                return responseWithMainMenu("❌ Ошибка формата оценки.");
            }
        }
        // ===== ОЦЕНКА АВТОРА =====
        if (callbackData.startsWith("rate_author_")) {
            String elderIdStr = callbackData.substring("rate_author_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return handleRateAuthor(userId, elderId);
        }

        // ===== НОВЫЙ БЛОК: СУПЕР ОПЕРАТОР =====
        if (callbackData.equals("super_operator_menu")) {
            return handleSuperOperatorMenu(userId);
        }

        // ===== УДАЛЕНИЕ АККАУНТА ОПЕРАТОРА =====
        if (callbackData.equals("delete_my_account")) {
            return confirmDeleteOperatorProfile(userId);
        }

        // В методе handleCallback (в начале, после проверки на help и другие)

// ===== УБРАТЬ ИЗ ИНТЕРЕСНЫХ =====
        if (callbackData.startsWith("remove_from_interested_")) {
            String elderIdStr = callbackData.substring("remove_from_interested_".length());
            Long elderId = Long.parseLong(elderIdStr);
            return handleRemoveFromInterested(userId, elderId);
        }

// ===== ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ =====
        if (callbackData.startsWith("confirm_delete_account_")) {
            String action = callbackData.substring("confirm_delete_account_".length());
            boolean confirm = "yes".equals(action);
            return deleteOperatorProfile(userId, confirm);
        }

        // ===== ВВОД ТЕЛЕФОНА ВРУЧНУЮ =====
        if (callbackData.equals("enter_phone_manually")) {
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

        // ===== ЗАЯВКИ ОПЕРАТОРА (ВСЕ ТИПЫ) =====
        if (callbackData.startsWith("operator_requests_created_") ||
                callbackData.startsWith("operator_requests_in_progress_") ||
                callbackData.startsWith("operator_requests_interested_") ||
                callbackData.startsWith("operator_requests_completed_")) {

            // Извлекаем operatorId из конца строки
            String[] parts = callbackData.split("_");
            String operatorIdStr = parts[parts.length - 1]; // последняя часть — это ID
            Long operatorId = Long.parseLong(operatorIdStr);

            String type;
            if (callbackData.startsWith("operator_requests_created_")) {
                type = "created";
            } else if (callbackData.startsWith("operator_requests_in_progress_")) {
                type = "in_progress";
            } else if (callbackData.startsWith("operator_requests_interested_")) {
                type = "interested";
            } else {
                type = "completed";
            }

            return showOperatorRequestsByType(userId, operatorId, type);
        }

        // ===== БЛОКИРОВКА ОПЕРАТОРА =====
        if (callbackData.startsWith("block_operator_")) {
            String operatorIdStr = callbackData.substring("block_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return handleBlockOperator(userId, operatorId);
        }

// ===== РАЗБЛОКИРОВКА ОПЕРАТОРА =====
        if (callbackData.startsWith("unblock_operator_")) {
            String operatorIdStr = callbackData.substring("unblock_operator_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return handleUnblockOperator(userId, operatorId);
        }

// ===== ЗАЯВКИ ОПЕРАТОРА =====
        if (callbackData.startsWith("operator_requests_")) {
            String operatorIdStr = callbackData.substring("operator_requests_".length());
            Long operatorId = Long.parseLong(operatorIdStr);
            return showOperatorRequests(userId, operatorId);
        }

        // ===== ПРОПУСК ШАГОВ АНКЕТЫ =====
        if (callbackData.startsWith("skip_profile_")) {
            DialogState currentState = stateService.getState(userId);
            return handleDialogInput(userId, chatId, callbackData, currentState);
        }

        // ===== ОДОБРЕНИЕ ПАНСИОНАТА (АДМИН) =====
        if (callbackData.startsWith("approve_carehome_")) {
            String careHomeIdStr = callbackData.substring("approve_carehome_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return adminService.handleApproveCarehome(userId, careHomeId);
        }

// ===== ОТКЛОНЕНИЕ ПАНСИОНАТА (АДМИН) =====
        if (callbackData.startsWith("reject_carehome_")) {
            String careHomeIdStr = callbackData.substring("reject_carehome_".length());
            Long careHomeId = Long.parseLong(careHomeIdStr);
            return handleRejectCarehome(userId, careHomeId);
        }

// ===== УДАЛЕНИЕ ПРИГЛАШЕНИЯ =====
        if (callbackData.startsWith("delete_invitation_")) {
            Long careHomeId = Long.parseLong(callbackData.substring("delete_invitation_".length()));
            return handleDeleteInvitation(userId, careHomeId);
        }

        // ===== ПРОПУСК ШАГОВ АНКЕТЫ =====
        if (callbackData.startsWith("skip_profile_")) {
            // Получаем текущее состояние
            DialogState currentState = stateService.getState(userId);
            // Передаём в handleDialogInput с текстом = callbackData
            return handleDialogInput(userId, chatId, callbackData, currentState);
        }

// ===== ПРИГЛАШЕНИЕ ОПЕРАТОРА =====
        if (callbackData.equals("invite_operator")) {
            return careHomeManagementService.inviteOperator(userId);
        }
        // ===== ВЫБОР ПАНСИОНАТА ДЛЯ ПРИГЛАШЕНИЯ =====
        if (callbackData.startsWith("invite_carehome_")) {
            String param = callbackData.substring("invite_carehome_".length());
            UniversalResponse response;
            if ("all".equals(param)) {
                response = careHomeManagementService.createInvitation(userId, null);
            } else {
                Long careHomeId = Long.parseLong(param);
                response = careHomeManagementService.createInvitation(userId, careHomeId);
            }
            if (response != null) {
                return response;
            }
            return null;  // ← ничего не отправляем
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
                tempElder.setStatus(ElderStatus.NEW);
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
            stateService.setViewingFromInterested(userId, false);
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

            case AWAITING_COMPLAINT_REASON -> {
                Elder elder = stateService.getTempElder(userId);
                if (elder == null) {
                    return responseWithMainMenu("❌ Заявка не найдена.");
                }

                // ===== ПОЛУЧАЕМ ВНУТРЕННИЙ ID ПОЛЬЗОВАТЕЛЯ =====
                User complainant = userService.findByTelegramId(userId).orElse(null);
                if (complainant == null) {
                    stateService.clearState(userId);
                    return responseWithMainMenu("❌ Пользователь не найден.");
                }

                // ===== ОПРЕДЕЛЯЕМ АВТОРА =====
                Long targetId = elder.getCreatedBy();
                String targetName = "Неизвестный (автор не найден)";

                if (targetId != null) {
                    User target = userService.findById(targetId);
                    if (target != null) {
                        targetName = target.getFirstName();
                    }
                }

                // ===== СОХРАНЯЕМ ЖАЛОБУ =====
                Complaint complaint = new Complaint();
                complaint.setComplainantId(complainant.getId());
                complaint.setTargetId(targetId != null ? targetId : 0L);
                complaint.setElderId(elder.getId());
                complaint.setReason(text != null ? text : "Без причины");
                complaint.setStatus("PENDING");
                complaint.setCreatedAt(LocalDateTime.now());
                complaintRepository.save(complaint);

                // ===== УВЕДОМЛЯЕМ АДМИНИСТРАТОРОВ =====
                notifyAdminsAboutComplaint(complaint);

                stateService.clearState(userId);

                // ===== ОТВЕТ ПОЛЬЗОВАТЕЛЮ (ВОЗВРАЩАЕМ!) =====
                String responseText = "✅ **Жалоба отправлена администратору.**\n\n" +
                        "📋 **Номер жалобы:** #" + complaint.getId() + "\n" +
                        "📋 **Заявка #" + elder.getId() + "**\n" +
                        "👤 **Автор:** " + targetName + "\n" +
                        "💬 **Причина:** " + complaint.getReason() + "\n\n" +
                        "Администратор рассмотрит жалобу в ближайшее время.";

                response = new UniversalResponse(responseText);
                response.addButtonFullRow("🔍 Поиск заявок", "find_requests");
                response.addButtonFullRow("📋 Мои заявки", "my_requests");
                response.addButtonFullRow("🏠 Главное меню", "main_menu");
                return response;  // ← ВАЖНО: возвращаем ответ!
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

        // ===== ПРОВЕРЯЕМ, ОТКРЫЛ ЛИ ОПЕРАТОР ЗАЯВКУ ИЗ "ИНТЕРЕСНЫХ" =====
        boolean viewingFromInterested = stateService.isViewingFromInterested(userId);

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

        // ===== ПОКАЗЫВАЕМ ИМЯ И КОНТАКТЫ =====
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

        // ===== СОЗДАЁМ ОТВЕТ =====
        UniversalResponse response = new UniversalResponse(card);

        // ===== КНОПКИ ДЛЯ ЗАВЕРШЁННЫХ ЗАЯВОК =====
        if (elder.getStatus() == ElderStatus.COMPLETED && isOperator) {
            // Проверяем, что оператор вёл эту заявку
            isAssigned = false;
            if (elder.getAssignedOperatorIds() != null && !elder.getAssignedOperatorIds().isEmpty()) {
                String[] ids = elder.getAssignedOperatorIds().split(",");
                for (String id : ids) {
                    if (id.trim().equals(String.valueOf(userId))) {
                        isAssigned = true;
                        break;
                    }
                }
            }
            if (isAssigned) {
                response.addButtonFullRow("🗑️ Удалить заявку", "delete_completed_elder_" + elderId);
            }
        }

        // ===== КНОПКИ ДЛЯ ОПЕРАТОРА =====
        if (isOperator && !isAuthor) {
            // ===== ОБЪЯВЛЯЕМ ПЕРЕМЕННУЮ ЗДЕСЬ (ОДИН РАЗ) =====
            boolean isAssignedToElder = false;
            if (elder.getAssignedOperatorIds() != null && !elder.getAssignedOperatorIds().isEmpty()) {
                String[] ids = elder.getAssignedOperatorIds().split(",");
                for (String id : ids) {
                    if (id.trim().equals(String.valueOf(userId))) {
                        isAssignedToElder = true;
                        break;
                    }
                }
            }

            // ===== ЕСЛИ ОПЕРАТОР СМОТРИТ ИЗ "ИНТЕРЕСНЫХ" =====
            if (viewingFromInterested) {
                response.addButtonFullRow("⭐ Убрать из интересных", "remove_from_interested_" + elderId);
            } else {
                // ===== ПОКАЗЫВАЕМ КНОПКИ ТОЛЬКО ЕСЛИ ЗАЯВКА НЕ ВЗЯТА ЭТИМ ОПЕРАТОРОМ =====
                if (!isAssignedToElder) {
                    if (elder.getStatus() == ElderStatus.NEW ||
                            elder.getStatus() == ElderStatus.OFFERED ||
                            elder.getStatus() == ElderStatus.IN_PROGRESS) {

                        response.addButtonFullRow("✅ Взять в работу", "take_elder_" + elderId);
                        response.addButton("👍 Интересно", "interested_elder_" + elderId);
                        response.addButton("👎 Не подходит", "not_interested_elder_" + elderId);
                    }
                }
            }

            // ===== КНОПКИ ДЛЯ ОПЕРАТОРА, КОТОРЫЙ УЖЕ ВЗЯЛ ЗАЯВКУ =====
            if (isAssignedToElder && elder.getStatus() == ElderStatus.IN_PROGRESS) {
                response.addButtonFullRow("📨 Отправить запрос на закрытие", "request_complete_elder_" + elderId);
                response.addButtonFullRow("📱 Связаться через MAX", "contact_client_" + elderId);
            }
                // ===== ЕСЛИ ЗАЯВКА СОЗДАНА ОПЕРАТОРОМ — ДОБАВЛЯЕМ "ОЦЕНИТЬ ЗАЯВКУ" =====
                if (elder.getCreatedBy() != null) {
                    User author = getUserOrNull(elder.getCreatedBy());
                    if (author != null && isOperator(author)) {
                        // Проверяем, не оценил ли уже
                        boolean alreadyRated = ratingRepository.findByRaterIdAndElderId(userId, elderId).isPresent();
                        if (!alreadyRated) {
                            response.addButtonFullRow("⭐ Оценить заявку", "rate_elder_" + elderId);
                        }
                    }
                }
            }


        // ===== ЕСЛИ ОПЕРАТОР ЗАВЕРШИЛ ЗАЯВКУ — ПОКАЗАТЬ КНОПКУ ДЛЯ ОЦЕНКИ =====
        if (isOperator && isAssigned && elder.getStatus() == ElderStatus.COMPLETED) {
            boolean alreadyRated = ratingRepository.findByRaterIdAndElderId(userId, elderId).isPresent();
            if (!alreadyRated) {
                response.addButtonFullRow("⭐ Оценить автора", "rate_author_" + elderId);
            }
        }

        // ===== КНОПКИ ДЛЯ АВТОРА =====
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

        // ===== ОБЩИЕ КНОПКИ =====
        if (viewingFromInterested) {
            response.addButtonFullRow("⭐ Интересные заявки", "my_requests_interested");
        } else {
            response.addButtonFullRow("🔍 Поиск заявок", "find_requests");
        }
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }
    // ============================================================
    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С ПАНСИОНАТАМИ (АДМИН) =====
    // ============================================================


    private UniversalResponse handleRejectCarehome(Long userId, Long careHomeId) {
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

        // ===== ОДОБРЯЕМ ЗАЯВКУ =====
        elder.setStatus(ElderStatus.NEW);
        elder.setAcceptedBy(userId);
        elder.setAcceptedAt(LocalDateTime.now());
        elderService.updateElder(elder);

        // ===== РАССЫЛКА ОПЕРАТОРАМ =====
        notificationService.notifyOperators(elder);
        log.info("📢 Рассылка операторам отправлена для заявки #{}", elder.getId());

        // ===== УВЕДОМЛЕНИЕ КЛИЕНТА =====
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
                        "📢 Уведомления отправлены операторам.\n" +
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

        // ===== 1. Получаем ID заявок, которые оператор уже отметил как "Интересные" =====
        List<Long> interestedElderIds = reactionRepository.findInterestedElderIdsByOperatorId(userId);

        // ===== 2. Получаем все активные заявки, исключая уже "Интересные" =====
        List<Elder> allElders = elderService.findActiveElders();

        // ===== СОРТИРУЕМ: НОВЫЕ СВЕРХУ =====
        allElders = allElders.stream()
                .sorted((e1, e2) -> {
                    LocalDateTime d1 = e1.getCreatedAt();
                    LocalDateTime d2 = e2.getCreatedAt();
                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return 1;   // null считается старше
                    if (d2 == null) return -1;  // null считается старше
                    return d2.compareTo(d1);    // новые сверху
                })
                .collect(Collectors.toList());

        // Фильтруем: исключаем заявки, которые уже в "Интересных" у этого оператора
        List<Elder> filteredElders = allElders.stream()
                .filter(elder -> !interestedElderIds.contains(elder.getId()))
                .filter(elder -> {
                    // ===== 3. Применяем остальные фильтры (город, бюджет, дата) =====
                    boolean filterTodayOnly = stateService.isFilterTodayOnly(userId);

                    // Фильтр по дате
                    if (filterTodayOnly) {
                        LocalDate today = LocalDate.now();
                        if (elder.getCreatedAt() == null) {
                            return false;
                        }
                        // Сравниваем только дату (без времени)
                        LocalDate elderDate = elder.getCreatedAt().toLocalDate();
                        if (!elderDate.equals(today)) {
                            return false;
                        }
                    }

                    // Фильтр по городу/региону
                    String city = user.getPreferredCity();
                    String region = user.getPreferredRegion();
                    boolean hasCityFilter = city != null && !city.trim().isEmpty();
                    boolean hasRegionFilter = region != null && !region.trim().isEmpty();

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
                    Double budgetMin = user.getBudgetMin();
                    Double budgetMax = user.getBudgetMax();
                    if (budgetMin != null && elder.getBudget() < budgetMin) return false;
                    if (budgetMax != null && elder.getBudget() > budgetMax) return false;

                    return true;
                })
                .collect(Collectors.toList());

        // ===== 4. Формируем ответ =====
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

            // Показываем активные фильтры
            if (user.getPreferredCity() != null) {
                message.append("📍 Город: ").append(user.getPreferredCity()).append("\n");
            }
            if (user.getPreferredRegion() != null) {
                message.append("📍 Регион: ").append(user.getPreferredRegion()).append("\n");
            }
            if (user.getBudgetMin() != null || user.getBudgetMax() != null) {
                String budgetText = "";
                if (user.getBudgetMin() != null && user.getBudgetMax() != null) {
                    budgetText = user.getBudgetMin() + " - " + user.getBudgetMax() + " руб.";
                } else if (user.getBudgetMin() != null) {
                    budgetText = "от " + user.getBudgetMin() + " руб.";
                } else if (user.getBudgetMax() != null) {
                    budgetText = "до " + user.getBudgetMax() + " руб.";
                }
                message.append("💰 Бюджет: ").append(budgetText).append("\n");
            }
            if (stateService.isFilterTodayOnly(userId)) {
                message.append("📅 Только за сегодня\n");
            }

            if (user.getPreferredCity() != null || user.getPreferredRegion() != null ||
                    user.getBudgetMin() != null || user.getBudgetMax() != null ||
                    stateService.isFilterTodayOnly(userId)) {
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

        // ===== 5. Кнопки управления фильтрами =====
        if (stateService.isFilterTodayOnly(userId)) {
            response.addButtonFullRow("📅 Показать все", "filter_today_off");
        } else {
            response.addButtonFullRow("📅 Только за сегодня", "filter_today_on");
        }

        boolean hasFilters = user.getPreferredCity() != null || user.getPreferredRegion() != null ||
                user.getBudgetMin() != null || user.getBudgetMax() != null ||
                stateService.isFilterTodayOnly(userId);
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
                elder.getStatus() != ElderStatus.DELETED) {
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
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        List<Elder> elders = new ArrayList<>();
        String title = "";

        // ============================================================
        // ===== ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ ПРОВЕРКИ =====
        // Проверяет, есть ли оператор в assigned_operator_ids
        // ============================================================
        java.util.function.Predicate<Elder> isOperatorAssigned = elder -> {
            // Проверяем новое поле assigned_operator_ids
            if (elder.getAssignedOperatorIds() != null && !elder.getAssignedOperatorIds().isEmpty()) {
                String[] ids = elder.getAssignedOperatorIds().split(",");
                for (String id : ids) {
                    if (id.trim().equals(String.valueOf(userId))) {
                        return true;
                    }
                }
            }
            // Проверяем старое поле assigned_operator_id (для совместимости)
            return elder.getAssignedOperatorId() != null && elder.getAssignedOperatorId().equals(userId);
        };

        switch (type) {
            case "created" -> {
                elders = elderService.findByCreatedBy(userId);
                // Исключаем заявки, которые оператор взял сам
                elders = elders.stream()
                        .filter(elder -> !isOperatorAssigned.test(elder))
                        .collect(Collectors.toList());
                title = "📤 **Переданные заявки**\n\nЗаявки, которые вы создали:";
            }

            case "in_progress" -> {
                List<Elder> allActive = elderService.findActiveElders(); // Все активные заявки (не COMPLETED, DELETED, EXPIRED)
                elders = allActive.stream()
                        .filter(isOperatorAssigned)
                        .filter(elder -> elder.getCreatedBy() == null || !elder.getCreatedBy().equals(userId))
                        // ===== ДОБАВЛЯЕМ ФИЛЬТР: ТОЛЬКО IN_PROGRESS =====
                        .filter(elder -> elder.getStatus() == ElderStatus.IN_PROGRESS)
                        .collect(Collectors.toList());
                title = "📋 **Заявки в работе**\n\nЗаявки, которые вы взяли:";
            }

            case "interested" -> {
                List<OperatorReaction> reactions = reactionRepository
                        .findByOperatorIdAndReaction(userId, "INTERESTED");
                for (OperatorReaction reaction : reactions) {
                    Elder elder = elderService.findById(reaction.getElderId());
                    if (elder != null && !isOperatorAssigned.test(elder)) {
                        elders.add(elder);
                    }
                }
                title = "⭐ **Интересные заявки**\n\nЗаявки, которые вас заинтересовали:";
            }

            case "completed" -> {
                // Ищем заявки, где оператор есть в assigned_operator_ids И статус COMPLETED
                List<Elder> allCompleted = elderService.findByStatus(ElderStatus.COMPLETED);
                elders = allCompleted.stream()
                        .filter(isOperatorAssigned)
                        .collect(Collectors.toList());
                title = "✅ **Завершённые заявки**\n\nЗаявки, которые вы закрыли:";
            }

            default -> {
                return responseWithBackAndMainMenu("❌ Неизвестный раздел.", "my_requests");
            }
        }

        // ============================================================
        // ФОРМИРУЕМ ОТВЕТ
        // ============================================================
        if (elders.isEmpty()) {
            UniversalResponse response = new UniversalResponse(title + "\n\n📭 Здесь пока пусто.");
            response.addButtonFullRow("📋 Мои заявки", "my_requests");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        UniversalResponse response = new UniversalResponse(title);

        // Показываем до 10 заявок
        int maxButtons = Math.min(elders.size(), 10);
        for (int i = 0; i < maxButtons; i++) {
            Elder elder = elders.get(i);
            String buttonText = elder.getFullName() + " | " + elder.getBudget() + " руб. | " + elder.getAge() + " лет";
            response.addButtonFullRow(buttonText, "view_elder_" + elder.getId());
        }

        // Если заявок больше 10, сохраняем список в searchResults для пагинации
        if (elders.size() > 10) {
            response.addButtonFullRow("📋 Показать ещё...", "find_requests_more");
            stateService.setSearchResults(userId, elders);
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
        User user = userService.findByTelegramId(userId).orElse(null);
        if (user != null) {
            careHome.setProposedBy(user.getId());  // сохраняем id пользователя (8)
        }
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
        response.addButtonFullRow("🗑️ Удалить аккаунт", "delete_my_account");
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
        // ===== НАХОДИМ АКТИВНОЕ ПРИГЛАШЕНИЕ =====
        Invitation invitation = invitationService.findFirstByCareHomeId(careHomeId);
        if (invitation == null) {
            return responseWithMainMenu("❌ Нет активного приглашения для этого пансионата.");
        }

        // Проверяем, не является ли пользователь уже оператором
        User user = userService.findByTelegramId(userId).orElse(null);
        if (user != null && user.getAccessLevel() == AccessLevel.OPERATOR) {
            return responseWithMainMenu("✅ Вы уже являетесь оператором.");
        }

        if (user == null) {
            user = new User();
            user.setTelegramId(userId);
        }

        user.setAccessLevel(AccessLevel.OPERATOR);
        user.setRegistered(true);
        user.setRegisteredAt(LocalDateTime.now());

        // ===== ПРИВЯЗКА К ПАНСИОНАТУ =====
        // Используем careHomeId менеджера, который создал приглашение
        User manager = userService.findById(invitation.getCreatedBy());
        if (manager != null && manager.getCareHomeId() != null) {
            user.setCareHomeId(manager.getCareHomeId());
        } else {
            user.setCareHomeId(careHomeId);
        }
        user.setCreatedBy(invitation.getCreatedBy());

        if (user.getBonusPoints() == 0) {
            user.setBonusPoints(10);
        }
        userService.saveUser(user);

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
            case AWAITING_OPERATOR_PROFILE_NAME:
                user.setFirstName(text.trim());
                userService.saveUser(user);
                stateService.setState(userId, DialogState.AWAITING_OPERATOR_PROFILE_PHONE);
                response = new UniversalResponse("📱 Введите номер телефона (или 'пропустить'):");
                response.addButton("⏭️ Пропустить", "skip_profile_phone");
                response.addButton("❌ Отменить", "cancel_action");
                return response;

            case AWAITING_OPERATOR_PROFILE_PHONE:
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
                response = new UniversalResponse("📱 Введите WhatsApp (или 'пропустить'):");
                response.addButton("⏭️ Пропустить", "skip_profile_whatsapp");
                response.addButton("❌ Отменить", "cancel_action");
                return response;

            case AWAITING_OPERATOR_PROFILE_WHATSAPP:
                if (!text.equals("skip_profile_whatsapp")) {
                    user.setWhatsapp(text.trim());
                    userService.saveUser(user);
                }
                stateService.setState(userId, DialogState.AWAITING_OPERATOR_PROFILE_EMAIL);
                response = new UniversalResponse("📧 Введите Email (или 'пропустить'):");
                response.addButton("⏭️ Пропустить", "skip_profile_email");
                response.addButton("❌ Отменить", "cancel_action");
                return response;

            case AWAITING_OPERATOR_PROFILE_EMAIL:
                if (!text.equals("skip_profile_email")) {
                    user.setEmail(text.trim());
                    userService.saveUser(user);
                }

                stateService.clearState(userId);

                // ===== УВЕДОМЛЯЕМ ДИРЕКТОРА =====
                notifyDirector(user);

                response = new UniversalResponse("✅ Профиль сохранён! 🎉");
                response.addButton("📋 Мои заявки", "menu_requests");
                response.addButton("🏠 Главное меню", "main_menu");
                return response;

            default:
                return responseWithMainMenu("❌ Что-то пошло не так.");
        }
    }
    private void notifyDirector(User operator) {
        log.info("📨 Отправляем уведомление директору для оператора {}", operator.getId());

        if (operator.getCreatedBy() == null) {
            log.warn("⚠️ У оператора нет createdBy (директор не указан)");
            return;
        }

        // ===== НАХОДИМ ДИРЕКТОРА ПО ID =====
        User director = userService.findById(operator.getCreatedBy());
        if (director == null) {
            log.warn("⚠️ Директор с ID {} не найден", operator.getCreatedBy());
            return;
        }

        log.info("👤 Директор найден: {} ({})", director.getFirstName(), director.getTelegramId());

        CareHome careHome = careHomeService.findById(operator.getCareHomeId());

        // ❌ УДАЛИТЬ ЭТОТ БЛОК — он дублирует поиск директора!
        // List<User> directors = userService.findByCareHomeIdAndAccessLevel(...);
        // if (directors.isEmpty()) { ... }

        // Находим активное приглашение
        Invitation invitation = invitationService.findFirstByCareHomeId(operator.getCareHomeId());
        boolean hasActiveInvitation = invitation != null && invitationService.isValid(invitation);

        String message = """
            ✅ **Новый оператор зарегистрировался и заполнил профиль!**

            👤 Имя: **%s**
            📱 Телефон: **%s**
            📱 WhatsApp: **%s**
            ✈️ Telegram: **@%s**
            📧 Email: **%s**
            🏢 Пансионат: **%s**

            Теперь оператор может работать с заявками.
            """.formatted(
                operator.getFirstName() != null ? operator.getFirstName() : "Не указано",
                operator.getPhone() != null ? operator.getPhone() : "Не указан",
                operator.getWhatsapp() != null ? operator.getWhatsapp() : "Не указан",
                operator.getTelegramUsername() != null ? operator.getTelegramUsername() : "Не указан",
                operator.getEmail() != null ? operator.getEmail() : "Не указан",
                careHome != null ? careHome.getName() : "Не указан"
        );

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("👤 Карточка оператора", "view_operator_" + operator.getId());
        response.addButtonFullRow("📋 Список операторов", "manager_operators");

        if (hasActiveInvitation) {
            response.addButtonFullRow("🗑️ Удалить приглашение", "delete_invitation_" + operator.getCareHomeId());
        }

        Long chatId = director.getChatId() != null ? director.getChatId() : director.getTelegramId();
        log.info("📤 Отправляем уведомление директору в чат {}", chatId);
        messageSender.sendMessage(chatId, response);
    }
    private UniversalResponse handleDeleteInvitation(Long userId, Long careHomeId) {
        // ===== НАХОДИМ ДИРЕКТОРА ПО TELEGRAM_ID =====
        User user = userService.findByTelegramId(userId).orElse(null);
        if (user == null || user.getAccessLevel() != AccessLevel.MANAGER) {
            return responseWithMainMenu("❌ Только директор может удалять приглашения.");
        }

        Invitation invitation = invitationService.findFirstByCareHomeId(careHomeId);
        if (invitation == null) {
            return responseWithMainMenu("❌ Нет активного приглашения для этого пансионата.");
        }

        // ===== СРАВНИВАЕМ ID ПОЛЬЗОВАТЕЛЯ В БД =====
        if (!invitation.getCreatedBy().equals(user.getId())) {
            return responseWithMainMenu("❌ Это не ваше приглашение.");
        }

        invitationService.deleteInvitation(invitation.getId());

        CareHome careHome = careHomeService.findById(careHomeId);

        return responseWithMainMenu(
                "✅ Приглашение для пансионата **" + careHome.getName() + "** удалено.\n\n" +
                        "📌 Уже зарегистрированные операторы продолжают работать."
        );
    }
    private UniversalResponse handleBlockOperator(Long userId, Long operatorId) {
        User user = getUserOrNull(userId);
        if (user == null || user.getAccessLevel() != AccessLevel.MANAGER) {
            return responseWithMainMenu("❌ Только директор может блокировать операторов.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithMainMenu("❌ Оператор не найден.");
        }

        operator.setIsActive(false);
        operator.setIsBlocked(true);
        operator.setBlockedAt(LocalDateTime.now());
        operator.setBlockedReason("Заблокирован руководителем");
        userService.saveUser(operator);

        return responseWithMainMenu("🔒 Оператор **" + operator.getFirstName() + "** заблокирован.");
    }
    private UniversalResponse handleUnblockOperator(Long userId, Long operatorId) {
        User user = getUserOrNull(userId);
        if (user == null || user.getAccessLevel() != AccessLevel.MANAGER) {
            return responseWithMainMenu("❌ Только директор может разблокировать операторов.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithMainMenu("❌ Оператор не найден.");
        }

        operator.setIsActive(true);
        operator.setIsBlocked(false);
        operator.setBlockedAt(null);
        operator.setBlockedReason(null);
        userService.saveUser(operator);

        return responseWithMainMenu("🔓 Оператор **" + operator.getFirstName() + "** разблокирован.");
    }
    private UniversalResponse showOperatorRequests(Long userId, Long operatorId) {
        User user = getUserOrNull(userId);
        if (user == null || user.getAccessLevel() != AccessLevel.MANAGER) {
            return responseWithMainMenu("❌ Только директор может просматривать заявки операторов.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithMainMenu("❌ Оператор не найден.");
        }

        // ===== ПОКАЗЫВАЕМ МЕНЮ ЗАЯВОК ОПЕРАТОРА =====
        UniversalResponse response = new UniversalResponse(
                "📋 **Заявки оператора " + operator.getFirstName() + "**\n\n" +
                        "Выберите раздел:"
        );
        response.addButtonFullRow("📤 Переданные заявки", "operator_requests_created_" + operatorId);
        response.addButtonFullRow("📋 В работе", "operator_requests_in_progress_" + operatorId);
        response.addButtonFullRow("⭐ Интересные", "operator_requests_interested_" + operatorId);
        response.addButtonFullRow("✅ Завершённые", "operator_requests_completed_" + operatorId);
        response.addButtonFullRow("🔙 Назад к оператору", "view_operator_" + operatorId);
        response.addButtonFullRow("📋 Список операторов", "manager_operators");
        return response;
    }
    private UniversalResponse showOperatorRequestsByType(Long directorId, Long operatorId, String type) {
        User director = getUserOrNull(directorId);
        if (director == null || director.getAccessLevel() != AccessLevel.MANAGER) {
            return responseWithMainMenu("❌ Только директор может просматривать заявки операторов.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithMainMenu("❌ Оператор не найден.");
        }

        // ===== ПОЛУЧАЕМ ЗАЯВКИ ОПЕРАТОРА ПО ТИПУ =====
        List<Elder> elders = new ArrayList<>();
        String title = "";

        switch (type) {
            case "created" -> {
                elders = elderService.findByCreatedBy(operator.getTelegramId());
                title = "📤 **Переданные заявки оператора " + operator.getFirstName() + "**";
            }
            case "in_progress" -> {
                elders = elderService.findByAssignedOperatorId(operator.getTelegramId());
                elders = elders.stream()
                        .filter(e -> e.getStatus() == ElderStatus.IN_PROGRESS || e.getStatus() == ElderStatus.ACCEPTED)
                        .collect(Collectors.toList());
                title = "📋 **Заявки в работе у оператора " + operator.getFirstName() + "**";
            }
            case "interested" -> {
                // НЕ СТАВИМ ФЛАГ! Это для директора
                List<OperatorReaction> reactions = reactionRepository
                        .findByOperatorIdAndReaction(operatorId, "INTERESTED");
                for (OperatorReaction reaction : reactions) {
                    Elder elder = elderService.findById(reaction.getElderId());
                    if (elder != null && elder.getAssignedOperatorId() == null) {
                        if (elder.getCreatedBy() == null || !elder.getCreatedBy().equals(operatorId)) {
                            elders.add(elder);
                        }
                    }
                }
                title = "⭐ **Интересные заявки оператора " + operator.getFirstName() + "**";
            }
            case "completed" -> {
                elders = elderService.findByCompletedBy(operator.getTelegramId());
                title = "✅ **Завершённые заявки оператора " + operator.getFirstName() + "**";
            }
            default -> {
                return responseWithMainMenu("❌ Неизвестный раздел.");
            }
        }

        if (elders.isEmpty()) {
            UniversalResponse response = new UniversalResponse(title + "\n\n📭 У оператора пока нет таких заявок.");
            response.addButtonFullRow("🔙 Назад", "operator_requests_" + operatorId);
            response.addButtonFullRow("📋 Список операторов", "manager_operators");
            return response;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📌 Всего заявок: ").append(elders.size()).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        UniversalResponse response = new UniversalResponse(sb.toString());

        for (Elder elder : elders) {
            String statusIcon = getStatusIcon(elder.getStatus());
            String buttonText = elder.getFullName() + " (" + elder.getAge() + " лет) " + statusIcon;
            response.addButtonFullRow(buttonText, "view_elder_" + elder.getId());
        }

        response.addButtonFullRow("🔙 Назад", "operator_requests_" + operatorId);
        response.addButtonFullRow("📋 Список операторов", "manager_operators");
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
    private UniversalResponse confirmDeleteOperatorProfile(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (!isOperator(user)) {
            return responseWithMainMenu("❌ Доступно только для операторов.");
        }

        UniversalResponse response = new UniversalResponse(
                "⚠️ **Вы уверены, что хотите удалить свой аккаунт?**\n\n" +
                        "👤 Имя: " + user.getFirstName() + "\n" +
                        "🏢 Пансионат: " + getCareHomeName(user.getCareHomeId()) + "\n" +
                        "💰 Баллов: " + user.getBonusPoints() + "\n\n" +
                        "❗ Это действие нельзя отменить!\n\n" +
                        "Все ваши заявки и статистика будут удалены."
        );
        response.addButtonFullRow("✅ Да, удалить", "confirm_delete_account_yes");
        response.addButtonFullRow("❌ Отменить", "confirm_delete_account_no");
        return response;
    }
    private UniversalResponse deleteOperatorProfile(Long userId, boolean confirm) {
        if (!confirm) {
            return responseWithMainMenu("❌ Удаление отменено.");
        }

        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (!isOperator(user)) {
            return responseWithMainMenu("❌ Доступно только для операторов.");
        }

        // ===== ПРОВЕРЯЕМ, ЕСТЬ ЛИ У ОПЕРАТОРА АКТИВНЫЕ ЗАЯВКИ =====
        List<Elder> activeElders = elderService.findByAssignedOperatorId(user.getTelegramId());
        long inProgress = activeElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.IN_PROGRESS)
                .count();

        if (inProgress > 0) {
            UniversalResponse response = new UniversalResponse(
                    "❌ **Нельзя удалить аккаунт!**\n\n" +
                            "У вас есть активные заявки в работе (" + inProgress + " шт.).\n\n" +
                            "Сначала завершите или передайте заявки."
            );
            response.addButtonFullRow("📋 Мои заявки", "my_requests");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        // ===== УДАЛЯЕМ ПОЛЬЗОВАТЕЛЯ =====
        String userName = user.getFirstName();
        userService.deleteUser(user.getId());

        log.info("🗑️ Оператор {} (ID: {}) удалил свой аккаунт", userName, userId);

        UniversalResponse response = new UniversalResponse(
                "✅ Ваш аккаунт успешно удалён.\n\n" +
                        "🙏 Спасибо за работу!\n\n" +
                        "Вы всегда можете зарегистрироваться снова."
        );
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    /**
     * Возвращает отфильтрованный список заявок для оператора
     * (исключая уже отмеченные как "Интересные")
     */
    private List<Elder> getFilteredEldersForOperator(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) return new ArrayList<>();

        // Получаем ID "Интересных" заявок
        List<Long> interestedElderIds = reactionRepository.findInterestedElderIdsByOperatorId(userId);

        // Все активные заявки
        return elderService.findActiveElders().stream()
                // Исключаем "Интересные"
                .filter(elder -> !interestedElderIds.contains(elder.getId()))
                // Сортировка: новые сверху
                .sorted((e1, e2) -> {
                    LocalDateTime d1 = e1.getCreatedAt();
                    LocalDateTime d2 = e2.getCreatedAt();
                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    return d2.compareTo(d1);
                })
                // ===== ОСТАЛЬНЫЕ ФИЛЬТРЫ (город, бюджет, дата) =====
                .filter(elder -> {
                    boolean filterTodayOnly = stateService.isFilterTodayOnly(userId);

                    // Фильтр по дате
                    if (filterTodayOnly) {
                        LocalDateTime today = LocalDateTime.now();
                        LocalDateTime startOfDay = today.withHour(0).withMinute(0).withSecond(0).withNano(0);
                        if (elder.getCreatedAt() == null || elder.getCreatedAt().isBefore(startOfDay)) {
                            return false;
                        }
                    }

                    // Фильтр по городу/региону
                    String city = user.getPreferredCity();
                    String region = user.getPreferredRegion();
                    boolean hasCityFilter = city != null && !city.trim().isEmpty();
                    boolean hasRegionFilter = region != null && !region.trim().isEmpty();

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
                    Double budgetMin = user.getBudgetMin();
                    Double budgetMax = user.getBudgetMax();
                    if (budgetMin != null && elder.getBudget() < budgetMin) return false;
                    if (budgetMax != null && elder.getBudget() > budgetMax) return false;

                    return true;
                })
                .collect(Collectors.toList());
    }
    /**
     * Убирает заявку из списка "Интересные" для оператора
     */
    private UniversalResponse handleRemoveFromInterested(Long userId, Long elderId) {
        // Находим ВСЕ записи для этого оператора и заявки
        List<OperatorReaction> reactions = reactionRepository
                .findAllByOperatorIdAndElderId(userId, elderId);

        if (reactions.isEmpty()) {
            return responseWithMainMenu("❌ Этой заявки нет в вашем списке 'Интересных'.");
        }

        // Удаляем ВСЕ найденные записи (включая дубликаты)
        reactionRepository.deleteAll(reactions);

        log.info("🗑️ Удалено {} записей 'Интересно' для оператора {} и заявки {}",
                reactions.size(), userId, elderId);

        // Сбрасываем флаг
        stateService.setViewingFromInterested(userId, false);

        UniversalResponse response = new UniversalResponse(
                "❌ Заявка #" + elderId + " убрана из списка 'Интересных'."
        );
        response.addButtonFullRow("⭐ Интересные заявки", "my_requests_interested");
        response.addButtonFullRow("🔍 Поиск заявок", "find_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Показывает меню оператора для менеджера (режим "Супер оператор")
     */
    private UniversalResponse handleSuperOperatorMenu(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER) {
            return responseWithMainMenu("❌ Доступно только для менеджеров.");
        }

        UniversalResponse response = new UniversalResponse(
                "📊 **Режим супер-оператора**\n\n" +
                        "💰 **Ваш баланс:** " + user.getBonusPoints() + " баллов\n" +
                        "📨 **Новых заявок:** " + elderService.countActiveElders() + "\n\n" +
                        "💡 Вы можете работать с заявками как оператор.\n" +
                        "📌 Для взятия заявки потребуется заполнить профиль.\n\n" +
                        "Выберите действие:"
        );

        // ===== ТОЛЬКО ОПЕРАТОРСКИЕ КНОПКИ (БЕЗ "Мои пансионаты") =====
        response.addButtonFullRow("📋 Заявки", "menu_requests");
        response.addButtonFullRow("📊 Моя статистика", "my_stats");
        response.addButtonFullRow("👤 Мой профиль", "my_profile");
        response.addButtonFullRow("❓ Помощь", "help");

        response.addButtonFullRow("🔙 Назад в панель менеджера", "main_menu");
        return response;
    }
    // Метод для обработки жалобы
    private UniversalResponse handleComplaint(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // Проверяем, что пользователь — оператор
        User user = getUserOrNull(userId);
        if (!isOperator(user)) {
            return responseWithMainMenu("❌ Только операторы могут жаловаться.");
        }

        // Проверяем, что пользователь не автор
        if (elder.getCreatedBy() != null && elder.getCreatedBy().equals(userId)) {
            return responseWithMainMenu("❌ Вы не можете пожаловаться на свою заявку.");
        }

        // Сохраняем заявку в состоянии для ввода причины
        stateService.setTempElder(userId, elder);
        stateService.setState(userId, DialogState.AWAITING_COMPLAINT_REASON);

        UniversalResponse response = new UniversalResponse(
                "🚨 **Жалоба на заявку #" + elderId + "**\n\n" +
                        "Опишите причину жалобы (кратко):\n" +
                        "Например: 'Фейковая заявка', 'Клиент не существует' и т.д."
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }
    private void notifyAdminsAboutComplaint(Complaint complaint) {
        List<User> admins = userService.findByAccessLevel(AccessLevel.ADMIN);
        if (admins.isEmpty()) {
            log.warn("⚠️ Нет администраторов для уведомления о жалобе");
            return;
        }

        // ===== ПОЛУЧАЕМ ДАННЫЕ (используем внутренние ID) =====
        User complainant = userService.findById(complaint.getComplainantId());
        Elder elder = elderService.findById(complaint.getElderId());

        if (elder == null) {
            log.warn("⚠️ Заявка #{} не найдена для жалобы", complaint.getElderId());
            return;
        }

        // ===== ОПРЕДЕЛЯЕМ АВТОРА =====
        String targetName = "Неизвестный (автор не найден)";
        Long targetId = complaint.getTargetId();
        if (targetId != null && targetId != 0L) {
            User target = userService.findById(targetId);
            if (target != null) {
                targetName = target.getFirstName();
            }
        }

        String complainantName = complainant != null ? complainant.getFirstName() : "Неизвестный";

        String message = "🚨 **Новая жалоба!**\n\n" +
                "📋 **Заявка #" + elder.getId() + "**\n" +
                "👤 **Жалобу подал:** " + complainantName + "\n" +
                "👤 **На кого жалуются:** " + targetName + "\n" +
                "💬 **Причина:** " + complaint.getReason() + "\n\n" +
                "Выберите действие:";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("🔍 Посмотреть заявку", "view_elder_" + elder.getId());
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");

        for (User admin : admins) {
            try {
                Long chatId = admin.getChatId() != null ? admin.getChatId() : admin.getTelegramId();
                messageSender.sendMessage(chatId, response);
                log.info("📨 Жалоба отправлена администратору {}", admin.getTelegramId());
            } catch (Exception e) {
                log.error("❌ Ошибка отправки жалобы администратору {}: {}", admin.getTelegramId(), e.getMessage());
            }
        }
    }
    /**
     * Показывает варианты оценки автора (1–5 звёзд)
     */
    private UniversalResponse handleRateAuthor(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // Проверяем, что заявка завершена
        if (elder.getStatus() != ElderStatus.COMPLETED) {
            return responseWithMainMenu("❌ Оценить автора можно только после завершения заявки.");
        }

        // Проверяем, что пользователь — оператор, который вёл заявку
        if (elder.getAssignedOperatorId() == null || !elder.getAssignedOperatorId().equals(userId)) {
            return responseWithMainMenu("❌ Вы не вели эту заявку.");
        }

        // Проверяем, что автор существует
        User author = getUserOrNull(elder.getCreatedBy());
        if (author == null) {
            return responseWithMainMenu("❌ Автор заявки не найден.");
        }

        // Проверяем, не оценил ли уже оператор эту заявку
        if (ratingRepository.findByRaterIdAndElderId(userId, elderId).isPresent()) {
            return responseWithMainMenu("⭐ Вы уже оценили эту заявку.");
        }

        // Сохраняем заявку в состоянии для оценки
        stateService.setTempElder(userId, elder);
        stateService.setState(userId, DialogState.AWAITING_RATING);

        UniversalResponse response = new UniversalResponse(
                "⭐ **Оцените автора заявки #" + elderId + "**\n\n" +
                        "👤 **Автор:** " + author.getFirstName() + "\n" +
                        "📊 **Текущий рейтинг:** " + String.format("%.1f", author.getRating()) + " ⭐\n\n" +
                        "Выберите оценку (1–5 звёзд):"
        );

        // Кнопки для оценки (1–5 звёзд)
        response.addButtonFullRow("⭐ 1 звезда", "rate_stars_1_" + elderId);
        response.addButtonFullRow("⭐⭐ 2 звезды", "rate_stars_2_" + elderId);
        response.addButtonFullRow("⭐⭐⭐ 3 звезды", "rate_stars_3_" + elderId);
        response.addButtonFullRow("⭐⭐⭐⭐ 4 звезды", "rate_stars_4_" + elderId);
        response.addButtonFullRow("⭐⭐⭐⭐⭐ 5 звёзд", "rate_stars_5_" + elderId);

        response.addButtonFullRow("❌ Отменить", "cancel_action");
        return response;
    }
    /**
     * Сохраняет оценку автора
     */
    private UniversalResponse handleSaveRating(Long userId, Long elderId, int stars) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // Проверяем, что заявка не удалена и не истекла
        if (elder.getStatus() == ElderStatus.DELETED || elder.getStatus() == ElderStatus.EXPIRED) {
            return responseWithMainMenu("❌ Заявка удалена или истекла.");
        }

        // Проверяем, что пользователь — оператор, который ведёт заявку
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
        if (!isAssigned) {
            return responseWithMainMenu("❌ Вы не ведёте эту заявку.");
        }

        // ===== ПОЛУЧАЕМ ВНУТРЕННИЕ ID =====
        User rater = getUserOrNull(userId);
        if (rater == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        User author = getUserOrNull(elder.getCreatedBy());
        if (author == null) {
            log.warn("⚠️ Автор заявки #{} с Telegram ID {} не найден", elderId, elder.getCreatedBy());
            return responseWithMainMenu("❌ Автор заявки не найден.");
        }

        // Проверяем, что автор — оператор
        if (!isOperator(author)) {
            return responseWithMainMenu("❌ Заявка создана не оператором.");
        }

        // Проверяем, не оценил ли уже
        if (ratingRepository.findByRaterIdAndElderId(rater.getId(), elderId).isPresent()) {
            return responseWithMainMenu("⭐ Вы уже оценили эту заявку.");
        }

        // ===== СОХРАНЯЕМ ОЦЕНКУ (ПЕРЕДАЁМ ВНУТРЕННИЕ ID!) =====
        try {
            ratingService.addRating(rater.getId(), author.getId(), elderId, stars);
        } catch (IllegalStateException e) {
            return responseWithMainMenu("❌ " + e.getMessage());
        }

        // Очищаем состояние
        stateService.clearState(userId);

        // Формируем ответ
        String starsEmoji = "⭐".repeat(stars);
        String message = "✅ **Оценка сохранена!**\n\n" +
                "👤 **Автор:** " + author.getFirstName() + "\n" +
                "📊 **Ваша оценка:** " + starsEmoji + " (" + stars + " звёзд" + (stars == 1 ? "а" : "") + ")\n" +
                "📊 **Новый рейтинг автора:** " + String.format("%.1f", author.getRating()) + " ⭐\n\n";

        if (stars == 5) {
            message += "🎉 Автор получил +1 балл за отличную заявку!";
        } else if (stars == 1) {
            message += "⚠️ Автор потерял 1 балл за низкое качество заявки.";
        } else {
            message += "Спасибо за оценку!";
        }

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("🔍 Поиск заявок", "find_requests");
        response.addButtonFullRow("📋 Мои заявки", "my_requests");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    private String getUserName(Long userId) {
        if (userId == null) return "Неизвестный";
        User user = userService.findById(userId);
        return user != null ? user.getFirstName() : "Неизвестный";
    }
    /**
     * Показывает список всех жалоб для администратора
     */
    private UniversalResponse showComplaintsList(Long userId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        // ===== ПОЛУЧАЕМ ВСЕ ЖАЛОБЫ =====
        List<Complaint> complaints = complaintRepository.findAllByOrderByCreatedAtDesc();

        if (complaints.isEmpty()) {
            UniversalResponse response = new UniversalResponse("📭 **Жалоб пока нет.**\n\nВсе чисто! 🎉");
            response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        // ===== ГРУППИРУЕМ ПО СТАТУСУ =====
        long pendingCount = complaints.stream().filter(c -> "PENDING".equals(c.getStatus())).count();
        long reviewedCount = complaints.stream().filter(c -> "REVIEWED".equals(c.getStatus())).count();
        long resolvedCount = complaints.stream().filter(c -> "RESOLVED".equals(c.getStatus())).count();

        StringBuilder sb = new StringBuilder();
        sb.append("🚨 **Управление жалобами**\n\n");
        sb.append("📊 **Статистика:**\n");
        sb.append("   ⏳ Ожидают: ").append(pendingCount).append("\n");
        sb.append("   👀 Просмотрены: ").append(reviewedCount).append("\n");
        sb.append("   ✅ Решены: ").append(resolvedCount).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // ===== ФИЛЬТР: ПОКАЗЫВАЕМ ТОЛЬКО ОЖИДАЮЩИЕ (PENDING) =====
        List<Complaint> pendingComplaints = complaints.stream()
                .filter(c -> "PENDING".equals(c.getStatus()))
                .collect(Collectors.toList());

        if (pendingComplaints.isEmpty()) {
            sb.append("✅ Новых жалоб нет. Все обработаны!");
        } else {
            sb.append("📋 **Новые жалобы (").append(pendingComplaints.size()).append("):**\n\n");
            for (Complaint complaint : pendingComplaints) {
                User complainant = userService.findById(complaint.getComplainantId());
                User target = userService.findById(complaint.getTargetId());
                Elder elder = elderService.findById(complaint.getElderId());

                String complainantName = complainant != null ? complainant.getFirstName() : "Неизвестный";
                String targetName = target != null ? target.getFirstName() : "Неизвестный";

                sb.append("🆔 #").append(complaint.getId())
                        .append(" | 📋 Заявка #").append(complaint.getElderId())
                        .append(" | 👤 ").append(complainantName)
                        .append(" → ").append(targetName)
                        .append("\n");
                sb.append("   💬 \"").append(complaint.getReason().length() > 30 ? complaint.getReason().substring(0, 30) + "..." : complaint.getReason()).append("\"\n");
                sb.append("   📅 ").append(complaint.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
            }
        }

        UniversalResponse response = new UniversalResponse(sb.toString());

        // ===== КНОПКИ ДЛЯ КАЖДОЙ ЖАЛОБЫ =====
        for (Complaint complaint : pendingComplaints) {
            response.addButtonFullRow(
                    "🔍 Жалоба #" + complaint.getId() + " (Заявка #" + complaint.getElderId() + ")",
                    "admin_view_complaint_" + complaint.getId()
            );
        }

        // ===== КНОПКИ УПРАВЛЕНИЯ =====
        response.addButtonFullRow("📊 Показать все жалобы", "admin_complaints_all");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Показывает детали конкретной жалобы
     */
    private UniversalResponse showComplaintDetails(Long userId, Long complaintId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) {
            return responseWithMainMenu("❌ Жалоба не найдена.");
        }

        User complainant = userService.findById(complaint.getComplainantId());
        User target = userService.findById(complaint.getTargetId());
        Elder elder = elderService.findById(complaint.getElderId());

        String complainantName = complainant != null ? complainant.getFirstName() : "Неизвестный";
        String targetName = target != null ? target.getFirstName() : "Неизвестный";

        String statusEmoji = switch (complaint.getStatus()) {
            case "PENDING" -> "⏳";
            case "REVIEWED" -> "👀";
            case "RESOLVED" -> "✅";
            default -> "❓";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("🚨 **Жалоба #").append(complaint.getId()).append("**\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📋 **Заявка #** ").append(complaint.getElderId()).append("\n");
        sb.append("👤 **Подал:** ").append(complainantName).append("\n");
        sb.append("👤 **На кого:** ").append(targetName).append("\n");
        sb.append("💬 **Причина:** ").append(complaint.getReason()).append("\n");
        sb.append("📅 **Дата:** ").append(complaint.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
        sb.append("📌 **Статус:** ").append(statusEmoji).append(" ").append(complaint.getStatus()).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");

        // Если есть информация о решении
        if ("RESOLVED".equals(complaint.getStatus()) && complaint.getResolvedBy() != null) {
            User resolver = userService.findById(complaint.getResolvedBy());
            String resolverName = resolver != null ? resolver.getFirstName() : "Неизвестный";
            sb.append("✅ **Решена:** ").append(resolverName);
            if (complaint.getResolvedAt() != null) {
                sb.append(" | ").append(complaint.getResolvedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            }
            sb.append("\n");
        }

        UniversalResponse response = new UniversalResponse(sb.toString());

        // ===== КНОПКИ =====
        if ("PENDING".equals(complaint.getStatus())) {
            response.addButtonFullRow("🔍 Посмотреть заявку", "view_elder_" + complaint.getElderId());
            response.addButtonFullRow("✅ Отметить как решённую", "admin_resolve_complaint_" + complaintId);
        } else {
            response.addButtonFullRow("🔍 Посмотреть заявку", "view_elder_" + complaint.getElderId());
        }

        response.addButtonFullRow("🔙 Назад к списку жалоб", "admin_complaints");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Отмечает жалобу как решённую
     */
    private UniversalResponse resolveComplaint(Long userId, Long complaintId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) {
            return responseWithMainMenu("❌ Жалоба не найдена.");
        }

        complaint.setStatus("RESOLVED");
        complaint.setResolvedBy(userId);
        complaint.setResolvedAt(LocalDateTime.now());
        complaintRepository.save(complaint);

        UniversalResponse response = new UniversalResponse(
                "✅ **Жалоба #" + complaintId + " отмечена как решённая.**\n\n" +
                        "👤 **Администратор:** " + admin.getFirstName() + "\n" +
                        "📅 **Дата:** " + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        );
        response.addButtonFullRow("🔙 Назад к списку жалоб", "admin_complaints");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    private UniversalResponse handleContactOperator(Long clientId, Long operatorId) {
        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithMainMenu("❌ Оператор не найден.");
        }

        // Проверяем, есть ли у оператора контакты
        String contactInfo = "📞 **Контактные данные оператора:**\n\n";
        contactInfo += "👤 **Имя:** " + (operator.getFirstName() != null ? operator.getFirstName() : "Не указано") + "\n";
        contactInfo += "📱 **Телефон:** " + (operator.getPhone() != null ? operator.getPhone() : "Не указан") + "\n";
        contactInfo += "📱 **WhatsApp:** " + (operator.getWhatsapp() != null ? operator.getWhatsapp() : "Не указан") + "\n";
        contactInfo += "✈️ **Telegram:** " + (operator.getTelegramUsername() != null ? "@" + operator.getTelegramUsername() : "Не указан") + "\n";
        contactInfo += "📧 **Email:** " + (operator.getEmail() != null ? operator.getEmail() : "Не указан");

        UniversalResponse response = new UniversalResponse(contactInfo);
        response.addButtonFullRow("🔙 Назад к заявке", "my_request");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    private UniversalResponse handleRateElder(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // ===== УБИРАЕМ ПРОВЕРКУ НА COMPLETED! =====
        // Оценивать можно в любой момент, пока заявка активна
        if (elder.getStatus() == ElderStatus.DELETED || elder.getStatus() == ElderStatus.EXPIRED) {
            return responseWithMainMenu("❌ Заявка удалена или истекла.");
        }

        // Проверяем, что пользователь — оператор, который ведёт заявку
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
        if (!isAssigned) {
            return responseWithMainMenu("❌ Вы не ведёте эту заявку.");
        }

        // Проверяем, что автор — оператор
        User author = getUserOrNull(elder.getCreatedBy());
        if (author == null || !isOperator(author)) {
            return responseWithMainMenu("❌ Заявка создана не оператором.");
        }

        // Проверяем, не оценил ли уже
        if (ratingRepository.findByRaterIdAndElderId(userId, elderId).isPresent()) {
            return responseWithMainMenu("⭐ Вы уже оценили эту заявку.");
        }

        // Сохраняем заявку в состоянии
        stateService.setTempElder(userId, elder);
        stateService.setState(userId, DialogState.AWAITING_RATING);

        // ===== ПОКАЗЫВАЕМ ТЕКУЩИЙ СТАТУС =====
        String statusText = switch (elder.getStatus()) {
            case NEW -> "🟢 Новая";
            case OFFERED -> "🟡 Предложена";
            case IN_PROGRESS -> "🟠 В работе";
            case COMPLETED -> "✅ Завершена";
            default -> elder.getStatus().toString();
        };

        UniversalResponse response = new UniversalResponse(
                "⭐ **Оцените заявку #" + elderId + "**\n\n" +
                        "👤 **Автор:** " + author.getFirstName() + "\n" +
                        "📊 **Рейтинг автора:** " + String.format("%.1f", author.getRating()) + " ⭐\n" +
                        "📌 **Статус заявки:** " + statusText + "\n\n" +
                        "Выберите оценку (1–5 звёзд):"
        );
        response.addButtonFullRow("⭐ 1 звезда", "rate_stars_1_" + elderId);
        response.addButtonFullRow("⭐⭐ 2 звезды", "rate_stars_2_" + elderId);
        response.addButtonFullRow("⭐⭐⭐ 3 звезды", "rate_stars_3_" + elderId);
        response.addButtonFullRow("⭐⭐⭐⭐ 4 звезды", "rate_stars_4_" + elderId);
        response.addButtonFullRow("⭐⭐⭐⭐⭐ 5 звёзд", "rate_stars_5_" + elderId);
        response.addButtonFullRow("❌ Отменить", "cancel_action");
        return response;
    }
    private UniversalResponse handleDeleteCompletedElder(Long userId, Long elderId) {
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            return responseWithMainMenu("❌ Заявка не найдена.");
        }

        // Проверяем, что заявка завершена
        if (elder.getStatus() != ElderStatus.COMPLETED) {
            return responseWithMainMenu("❌ Удалить можно только завершённую заявку.");
        }

        // Проверяем, что пользователь — оператор, который вёл заявку
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
        if (!isAssigned) {
            return responseWithMainMenu("❌ Вы не вели эту заявку.");
        }

        // ===== ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ =====
        stateService.setTempElder(userId, elder);
        stateService.setState(userId, DialogState.CONFIRM_DELETE_COMPLETED);

        UniversalResponse response = new UniversalResponse(
                "⚠️ **Вы уверены, что хотите удалить заявку #" + elderId + "?**\n\n" +
                        "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                        "📌 **Статус:** Завершена\n\n" +
                        "Это действие нельзя отменить!"
        );
        response.addButtonFullRow("✅ Да, удалить", "confirm_delete_completed_yes");
        response.addButtonFullRow("❌ Отменить", "confirm_delete_completed_no");
        return response;
    }
}
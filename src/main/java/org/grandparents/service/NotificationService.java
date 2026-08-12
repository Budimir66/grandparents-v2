package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.AccessLevel;
import org.grandparents.model.Elder;
import org.grandparents.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserService userService;
    private final MessageSender messageSender;

    public NotificationService(UserService userService,
                               MessageSender messageSender) {
        this.userService = userService;
        this.messageSender = messageSender;
    }

    public void notifyOperators(Elder elder) {
        log.info("📢 ===== НАЧАЛО РАССЫЛКИ УВЕДОМЛЕНИЙ =====");
        log.info("📢 Заявка #{}", elder.getId());

        try {
            List<User> allOperators = userService.findAllByAccessLevel(AccessLevel.OPERATOR);
            log.info("👥 Всего операторов в БД: {}", allOperators.size());

            if (allOperators.isEmpty()) {
                log.warn("⚠️ Нет зарегистрированных операторов для уведомления.");
                return;
            }

            List<User> filteredOperators = allOperators.stream()
                    .filter(operator -> isNotificationsEnabled(operator))
                    .filter(operator -> isTimeAllowed(operator))
                    .filter(operator -> matchesLocation(operator, elder))
                    .filter(operator -> matchesBudget(operator, elder))
                    .collect(Collectors.toList());

            log.info("👥 Отфильтровано операторов: {}", filteredOperators.size());

            if (filteredOperators.isEmpty()) {
                log.info("⚠️ Нет операторов, подходящих под фильтры.");
                return;
            }

            String message = "📢 **Новая заявка!**\n\n" +
                    "📋 **Заявка #" + elder.getId() + "**\n" +
                    "👤 **Имя:** " + elder.getFullName() + "\n" +
                    "🎂 **Возраст:** " + elder.getAge() + " лет\n" +
                    "💰 **Бюджет:** " + elder.getBudget() + " руб.\n" +
                    "📍 **Локация:** " + elder.getPreferredLocation() + "\n" +
                    "💊 **Здоровье:** " + elder.getHealthCondition() + "\n" +
                    "📝 **Пожелания:** " + elder.getRequirements() + "\n\n" +
                    "Выберите действие:";

            UniversalResponse response = new UniversalResponse(message);
            response.addButtonFullRow("✅ Взять в работу", "take_elder_" + elder.getId());
            response.addButtonFullRow("👍 Интересно", "interested_elder_" + elder.getId());
            response.addButtonFullRow("👎 Не подходит", "not_interested_elder_" + elder.getId());
            response.addButtonFullRow("🏠 Главное меню", "main_menu");

            int sentCount = 0;
            int skippedCount = 0;

            for (User operator : filteredOperators) {
                Long operatorId = operator.getTelegramId();

                boolean isAuthor = false;
                if (elder.getClientTelegramId() != null && elder.getClientTelegramId().equals(operatorId)) {
                    isAuthor = true;
                }
                if (elder.getCreatedBy() != null && elder.getCreatedBy().equals(operatorId)) {
                    isAuthor = true;
                }

                if (isAuthor) {
                    skippedCount++;
                    continue;
                }

                try {
                    log.info("📨 Отправляем оператору: {} ({})", operatorId, operator.getFirstName());
                    messageSender.sendMessage(operatorId, response);
                    sentCount++;
                } catch (Exception e) {
                    log.error("❌ Ошибка отправки оператору {}: {}", operatorId, e.getMessage());
                }
            }

            log.info("✅ Отправлено: {} операторам", sentCount);
            log.info("⏭️ Пропущено (авторы): {}", skippedCount);
            log.info("📢 ===== КОНЕЦ РАССЫЛКИ =====");

        } catch (Exception e) {
            log.error("❌ Ошибка рассылки уведомлений: {}", e.getMessage(), e);
        }
    }

    // ============================================================
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================================

    private boolean isNotificationsEnabled(User operator) {
        if (operator.getNotificationsEnabled() == null) {
            return true;
        }
        return operator.getNotificationsEnabled();
    }

    private boolean isTimeAllowed(User operator) {
        String from = operator.getNotifyFrom();
        String to = operator.getNotifyTo();

        if (from == null || to == null) {
            return true;
        }

        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(from);
            LocalTime end = LocalTime.parse(to);
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean matchesLocation(User operator, Elder elder) {
        String operatorCity = operator.getPreferredCity();
        String operatorRegion = operator.getPreferredRegion();
        String elderCity = elder.getCity() != null ? elder.getCity().toLowerCase() : "";
        String elderLocation = elder.getPreferredLocation() != null ? elder.getPreferredLocation().toLowerCase() : "";
        String elderRegion = elder.getRegion() != null ? elder.getRegion().toLowerCase() : "";

        boolean hasCityFilter = operatorCity != null && !operatorCity.trim().isEmpty();
        boolean hasRegionFilter = operatorRegion != null && !operatorRegion.trim().isEmpty();

        if (!hasCityFilter && !hasRegionFilter) {
            return true;
        }

        if (hasCityFilter) {
            String searchCity = operatorCity.toLowerCase().trim();
            if (elderCity.contains(searchCity) || elderLocation.contains(searchCity)) {
                return true;
            }
        }

        if (hasRegionFilter) {
            String searchRegion = operatorRegion.toLowerCase().trim();
            if (elderRegion.contains(searchRegion) || elderLocation.contains(searchRegion)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesBudget(User operator, Elder elder) {
        Double min = operator.getBudgetMin();
        Double max = operator.getBudgetMax();
        double elderBudget = elder.getBudget();

        if (min == null && max == null) {
            return true;
        }

        if (min != null && elderBudget < min) {
            return false;
        }
        if (max != null && elderBudget > max) {
            return false;
        }

        return true;
    }
}
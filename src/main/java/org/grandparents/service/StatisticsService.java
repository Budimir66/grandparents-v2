package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.AccessLevel;
import org.grandparents.model.CareHome;
import org.grandparents.model.Elder;
import org.grandparents.model.ElderStatus;
import org.grandparents.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

    private final UserService userService;
    private final ElderService elderService;
    private final CareHomeService careHomeService;  // ← ДОБАВЛЕНО

    public StatisticsService(UserService userService,
                             ElderService elderService,
                             CareHomeService careHomeService) {
        this.userService = userService;
        this.elderService = elderService;
        this.careHomeService = careHomeService;  // ← ДОБАВЛЕНО
    }

    // ============================================================
    // ===== СТАТИСТИКА ОПЕРАТОРА =====
    // ============================================================

    public UniversalResponse showMyStatistics(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (!isOperator(user)) {
            return responseWithMainMenu("❌ Статистика доступна только операторам.");
        }

        // ===== ОСНОВНАЯ СТАТИСТИКА =====
        int totalCreated = Math.toIntExact(elderService.countByCreatedBy(userId));
        int totalTaken = user.getTotalTaken() != null ? user.getTotalTaken() : 0;
        int totalCompleted = user.getTotalCompleted() != null ? user.getTotalCompleted() : 0;
        int totalInterested = user.getTotalInterested() != null ? user.getTotalInterested() : 0;
        int totalNotInterested = user.getTotalNotInterested() != null ? user.getTotalNotInterested() : 0;
        int bonusPoints = user.getBonusPoints();
        int totalEarned = user.getTotalEarnedBonus() != null ? user.getTotalEarnedBonus() : 0;
        double totalRevenue = user.getTotalRevenue() != null ? user.getTotalRevenue() : 0.0;

        // ===== ЗАЯВКИ В РАБОТЕ =====
        List<Elder> activeElders = elderService.findByAssignedOperatorId(user.getTelegramId());
        long inProgress = activeElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.IN_PROGRESS)
                .count();

        // ===== РЕЙТИНГ =====
        int rating = calculateRating(user);
        String ratingStars = getRatingStars(rating);

        // ===== КОНВЕРСИЯ =====
        double conversion = totalTaken > 0 ? (double) totalCompleted / totalTaken * 100 : 0;

        // ===== ФОРМИРУЕМ ОТВЕТ =====
        String stats = "📊 **Моя статистика**\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📤 **Создано заявок:** " + totalCreated + "\n" +
                "📋 **Взято в работу:** " + totalTaken + "\n" +
                "📋 **В работе сейчас:** " + inProgress + "\n" +
                "✅ **Завершено:** " + totalCompleted + "\n" +
                "📊 **Конверсия:** " + String.format("%.1f", conversion) + "%\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👍 **Интересно:** " + totalInterested + "\n" +
                "👎 **Не подходит:** " + totalNotInterested + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "💰 **Текущий баланс:** " + bonusPoints + " баллов\n" +
                "🏆 **Всего заработано:** " + totalEarned + " баллов\n" +
                "📈 **Общий доход:** " + String.format("%,.0f", totalRevenue) + " руб.\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "⭐ **Рейтинг:** " + ratingStars + " (" + rating + " место)\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "💡 Чем больше заявок вы закрываете, тем выше ваш рейтинг!\n" +
                "💡 Конверсия показывает, сколько взятых заявок вы завершили.";

        UniversalResponse response = new UniversalResponse(stats);
        response.addButton("📋 Заявки", "menu_requests");
        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Суммарная статистика по всем пансионатам MANAGER (для главного меню)
     */
    public UniversalResponse showManagerStats(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Статистика доступна только директорам и администраторам.");
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
            return responseWithMainMenu("❌ У вас нет пансионатов для статистики.");
        }

        // ===== СОБИРАЕМ СУММАРНУЮ СТАТИСТИКУ =====
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **Суммарная статистика по всем пансионатам**\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🏢 **Количество пансионатов:** ").append(careHomes.size()).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        long totalOperators = 0;
        long totalActiveOperators = 0;
        long totalElders = 0;
        long totalInProgress = 0;
        long totalCompleted = 0;
        long totalExpired = 0;
        double totalRevenue = 0.0;
        double totalBudget = 0.0;

        for (CareHome careHome : careHomes) {
            // Операторы
            List<User> operators = userService.findByCareHomeId(careHome.getId());
            totalOperators += operators.size();
            totalActiveOperators += operators.stream()
                    .filter(op -> op.getIsActive() != null && op.getIsActive())
                    .count();

            // Заявки
            List<Elder> elders = elderService.findByCareHomeId(careHome.getId());
            totalElders += elders.size();
            totalInProgress += elders.stream()
                    .filter(e -> e.getStatus() == ElderStatus.IN_PROGRESS)
                    .count();
            totalCompleted += elders.stream()
                    .filter(e -> e.getStatus() == ElderStatus.COMPLETED)
                    .count();
            totalExpired += elders.stream()
                    .filter(e -> e.getStatus() == ElderStatus.EXPIRED)
                    .count();

            // Финансы
            if (careHome.getMonthlyRevenue() != null) {
                totalRevenue += careHome.getMonthlyRevenue();
            }
            totalBudget += careHome.getPriceFrom();
        }

        long totalOperatorsBlocked = totalOperators - totalActiveOperators;

        // ===== ВЫВОД =====
        sb.append("👥 **Операторы:** ").append(totalOperators).append("\n");
        sb.append("   🟢 Активные: ").append(totalActiveOperators).append("\n");
        sb.append("   🔴 Неактивные: ").append(totalOperatorsBlocked).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        sb.append("📋 **Заявки:** ").append(totalElders).append("\n");
        sb.append("   📤 В работе: ").append(totalInProgress).append("\n");
        sb.append("   ✅ Завершено: ").append(totalCompleted).append("\n");
        sb.append("   ⏰ Просрочено: ").append(totalExpired).append("\n");

        double conversion = totalElders > 0 ? (double) totalCompleted / totalElders * 100 : 0;
        sb.append("   📊 Конверсия: ").append(String.format("%.1f", conversion)).append("%\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        sb.append("💰 **Финансы:**\n");
        sb.append("   💵 Общий доход: ").append(String.format("%,.0f", totalRevenue)).append(" руб.\n");
        sb.append("   💰 Средний бюджет: ").append(String.format("%,.0f", totalBudget / careHomes.size())).append(" руб.\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        sb.append("📈 **Средняя нагрузка на пансионат:**\n");
        sb.append("   📋 Заявок на пансионат: ")
                .append(String.format("%.1f", (double) totalElders / careHomes.size())).append("\n");
        sb.append("   👥 Операторов на пансионат: ")
                .append(String.format("%.1f", (double) totalOperators / careHomes.size())).append("\n");

        UniversalResponse response = new UniversalResponse(sb.toString());
        response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    // ============================================================
    // ===== СТАТИСТИКА ДЛЯ ДИРЕКТОРА (MANAGER) =====
    // ============================================================

    /**
     * Статистика по конкретному пансионату (из карточки)
     */
    /**
     * Статистика по конкретному пансионату (из карточки)
     */
    public UniversalResponse showManagerStatsForCarehome(Long userId, Long careHomeId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Статистика доступна только директорам и администраторам.");
        }

        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithMainMenu("❌ Пансионат не найден.");
        }

        // Проверяем доступ
        boolean isOwner = careHome.getProposedBy() != null && careHome.getProposedBy().equals(userId);
        boolean isAdmin = user.getAccessLevel() == AccessLevel.ADMIN;
        if (!isAdmin && !isOwner) {
            return responseWithMainMenu("❌ У вас нет доступа к этому пансионату.");
        }

        // ===== 1. ИНФОРМАЦИЯ О ПАНСИОНАТЕ =====
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **Статистика пансионата**\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🏢 **").append(careHome.getName()).append("**\n");
        sb.append("📍 ").append(careHome.getAddress()).append("\n");

        if (careHome.getSubscriptionEnd() != null) {
            long daysLeft = java.time.Duration.between(LocalDateTime.now(), careHome.getSubscriptionEnd()).toDays();
            sb.append("📆 Подписка до: ").append(careHome.getSubscriptionEnd())
                    .append(" (осталось ").append(Math.max(0, daysLeft)).append(" дн.)\n");
        }
        sb.append("🟢 Статус: ").append(careHome.getIsActive() ? "Активен" : "Неактивен").append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // ===== 2. ОПЕРАТОРЫ =====
        List<User> operators = userService.findByCareHomeId(careHomeId);
        long totalOperators = operators.size();
        long activeOperators = operators.stream()
                .filter(op -> op.getIsActive() != null && op.getIsActive())
                .count();
        long blockedOperators = totalOperators - activeOperators;

        sb.append("👥 **Операторы:** ").append(totalOperators).append("\n");
        sb.append("🟢 Активные: ").append(activeOperators).append("\n");
        sb.append("🔴 Неактивные: ").append(blockedOperators).append("\n");

        double avgActivity = operators.stream()
                .mapToInt(op -> op.getTotalCompleted() != null ? op.getTotalCompleted() : 0)
                .average()
                .orElse(0);
        sb.append("📈 Средняя активность: ").append(String.format("%.1f", avgActivity))
                .append(" заявок/оператор\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // ===== 3. ЗАЯВКИ =====
        List<Elder> allElders = elderService.findByCareHomeId(careHomeId);
        long totalElders = allElders.size();
        long inProgress = allElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.IN_PROGRESS)
                .count();
        long completed = allElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.COMPLETED)
                .count();
        long expired = allElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.EXPIRED)
                .count();

        sb.append("📋 **Заявки:** ").append(totalElders).append("\n");
        sb.append("📤 В работе: ").append(inProgress).append("\n");
        sb.append("✅ Завершено: ").append(completed).append("\n");
        sb.append("⏰ Просрочено: ").append(expired).append("\n");

        double conversion = totalElders > 0 ? (double) completed / totalElders * 100 : 0;
        sb.append("📊 Конверсия: ").append(String.format("%.1f", conversion)).append("%\n");

        double avgCompletionTime = allElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.COMPLETED && e.getCompletedAt() != null && e.getCreatedAt() != null)
                .mapToDouble(e -> java.time.Duration.between(e.getCreatedAt(), e.getCompletedAt()).toDays())
                .average()
                .orElse(0);
        sb.append("📈 Среднее время закрытия: ")
                .append(String.format("%.1f", avgCompletionTime)).append(" дн.\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // ===== 4. ФИНАНСЫ =====
        Double monthlyRevenue = careHome.getMonthlyRevenue() != null ? careHome.getMonthlyRevenue() : 0.0;

        double avgPrice = allElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.COMPLETED && e.getPrice() != null)
                .mapToDouble(Elder::getPrice)
                .average()
                .orElse(0);

        sb.append("💰 **Финансовые показатели:**\n\n");
        sb.append("💵 Средний чек: ").append(String.format("%,.0f", avgPrice)).append(" руб.\n");
        sb.append("📊 Доход за месяц: ").append(String.format("%,.0f", monthlyRevenue)).append(" руб.\n");

        double weeklyRevenue = monthlyRevenue / 4.3;
        sb.append("📈 Доход за неделю: ").append(String.format("%,.0f", weeklyRevenue)).append(" руб.\n");

        double dailyRevenue = monthlyRevenue / 30;
        sb.append("📊 Доход за день: ").append(String.format("%,.0f", dailyRevenue)).append(" руб.\n");

        OptionalDouble maxPrice = allElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.COMPLETED && e.getPrice() != null)
                .mapToDouble(Elder::getPrice)
                .max();
        OptionalDouble minPrice = allElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.COMPLETED && e.getPrice() != null)
                .mapToDouble(Elder::getPrice)
                .min();

        if (maxPrice.isPresent()) {
            sb.append("🏆 Самый дорогой контракт: ")
                    .append(String.format("%,.0f", maxPrice.getAsDouble())).append(" руб.\n");
        }
        if (minPrice.isPresent()) {
            sb.append("📉 Самый бюджетный контракт: ")
                    .append(String.format("%,.0f", minPrice.getAsDouble())).append(" руб.\n");
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // ===== 5. РЕЙТИНГ ОПЕРАТОРОВ (ТОП-3) =====
        sb.append("🏆 **Рейтинг операторов:**\n\n");

        List<User> sortedOperators = operators.stream()
                .filter(op -> op.getTotalCompleted() != null && op.getTotalCompleted() > 0)
                .sorted((o1, o2) -> {
                    int c1 = o1.getTotalCompleted() != null ? o1.getTotalCompleted() : 0;
                    int c2 = o2.getTotalCompleted() != null ? o2.getTotalCompleted() : 0;
                    return Integer.compare(c2, c1);
                })
                .limit(3)
                .collect(Collectors.toList());

        if (sortedOperators.isEmpty()) {
            sb.append("📭 Нет данных об операторах.\n");
        } else {
            String[] medals = {"🥇", "🥈", "🥉"};
            for (int i = 0; i < sortedOperators.size(); i++) {
                User op = sortedOperators.get(i);
                int operatorCompleted = op.getTotalCompleted() != null ? op.getTotalCompleted() : 0;
                int bonus = op.getBonusPoints();
                double revenue = op.getTotalRevenue() != null ? op.getTotalRevenue() : 0.0;

                sb.append(medals[i]).append(" **").append(op.getFirstName()).append("**\n");
                sb.append("   ✅ Завершено: ").append(operatorCompleted).append(" заявок\n");
                sb.append("   💰 Баллы: ").append(bonus).append("\n");
                sb.append("   📈 Доход: ").append(String.format("%,.0f", revenue)).append(" руб.\n");
                sb.append("   ⭐ Рейтинг: ").append(getRatingStars(operatorCompleted)).append("\n\n");
            }
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // ===== 6. ДИНАМИКА ЗАЯВОК =====
        sb.append("📈 **Динамика заявок (последние 7 дней):**\n\n");

        LocalDateTime now = LocalDateTime.now();
        String[] days = {"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"};
        int[] counts = new int[7];

        for (Elder elder : allElders) {
            if (elder.getCreatedAt() != null) {
                LocalDateTime created = elder.getCreatedAt();
                if (created.isAfter(now.minusDays(7))) {
                    int dayOfWeek = created.getDayOfWeek().getValue() - 1;
                    if (dayOfWeek >= 0 && dayOfWeek < 7) {
                        counts[dayOfWeek]++;
                    }
                }
            }
        }

        int maxCount = Arrays.stream(counts).max().orElse(1);
        for (int i = 0; i < 7; i++) {
            int bars = (int) Math.ceil((double) counts[i] / maxCount * 10);
            String bar = "█".repeat(Math.max(0, Math.min(10, bars)));
            String empty = "░".repeat(Math.max(0, 10 - bars));
            sb.append(days[i]).append(": ").append(bar).append(empty)
                    .append(" ").append(counts[i]).append(" заявок\n");
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // ===== 7. ПРОГНОЗ =====
        sb.append("🔮 **Прогноз на следующий месяц:**\n\n");

        double forecastRevenue = monthlyRevenue * 1.1;
        sb.append("📈 Ожидаемый доход: ")
                .append(String.format("%,.0f", forecastRevenue)).append(" руб.\n");

        long monthlyElders = allElders.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(now.minusMonths(1)))
                .count();
        long forecastElders = (long) Math.ceil(monthlyElders * 1.05);
        sb.append("📋 Ожидаемое количество заявок: ").append(forecastElders).append("\n");

        double forecastAvgPrice = avgPrice * 1.02;
        sb.append("💰 Прогнозируемый средний чек: ")
                .append(String.format("%,.0f", forecastAvgPrice)).append(" руб.\n\n");
        sb.append("📊 Прогноз основан на данных за последний месяц.\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━");

        UniversalResponse response = new UniversalResponse(sb.toString());
        response.addButtonFullRow("🔙 Назад к пансионату", "view_my_carehome_" + careHomeId);
        response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
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

    private UniversalResponse responseWithMainMenu(String text) {
        UniversalResponse response = new UniversalResponse(text);
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }


    private String getRatingStars(int rating) {
        if (rating == 0) return "☆☆☆☆☆ (нет данных)";
        if (rating <= 3) return "⭐☆☆☆☆ (новичок)";
        if (rating <= 5) return "⭐⭐☆☆☆ (активный)";
        if (rating <= 10) return "⭐⭐⭐☆☆ (опытный)";
        if (rating <= 20) return "⭐⭐⭐⭐☆ (профессионал)";
        return "⭐⭐⭐⭐⭐ (легенда!)";
    }
    private int calculateRating(User user) {
        if (user.getTotalCompleted() == null || user.getTotalCompleted() == 0) {
            return 0;
        }

        List<User> operators = userService.findAllByAccessLevel(AccessLevel.OPERATOR);
        operators.sort((u1, u2) -> {
            int c1 = u1.getTotalCompleted() != null ? u1.getTotalCompleted() : 0;
            int c2 = u2.getTotalCompleted() != null ? u2.getTotalCompleted() : 0;
            return Integer.compare(c2, c1);
        });

        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i).getTelegramId().equals(user.getTelegramId())) {
                return i + 1;
            }
        }
        return operators.size();
    }
}
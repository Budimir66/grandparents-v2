package org.grandparents.service;

import org.grandparents.model.Elder;
import org.grandparents.model.User;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationFilterService {

    /**
     * Фильтрует операторов по их настройкам
     */
    public List<User> filterOperators(List<User> operators, Elder elder) {
        return operators.stream()
                .filter(operator -> isNotificationsEnabled(operator))
                .filter(operator -> isTimeAllowed(operator))
                .filter(operator -> matchesLocation(operator, elder))
                .filter(operator -> matchesBudget(operator, elder))
                .collect(Collectors.toList());
    }

    /**
     * Проверяет, включены ли уведомления у оператора
     */
    private boolean isNotificationsEnabled(User operator) {
        // Если настройка не задана — считаем, что включены (по умолчанию true)
        if (operator.getNotificationsEnabled() == null) {
            return true;
        }
        return operator.getNotificationsEnabled();
    }

    /**
     * Проверяет, разрешено ли отправлять уведомление в текущее время
     */
    private boolean isTimeAllowed(User operator) {
        String from = operator.getNotifyFrom();
        String to = operator.getNotifyTo();

        // Если время не задано — разрешено всегда
        if (from == null || to == null) {
            return true;
        }

        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(from);
            LocalTime end = LocalTime.parse(to);

            // Если сейчас в интервале [start, end] — разрешено
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (Exception e) {
            // Если ошибка парсинга — разрешено
            return true;
        }
    }

    /**
     * Проверяет, совпадает ли локация оператора с локацией заявки
     */
    private boolean matchesLocation(User operator, Elder elder) {
        String operatorCity = operator.getPreferredCity();
        String operatorRegion = operator.getPreferredRegion();
        String elderCity = elder.getCity();
        String elderRegion = elder.getRegion();

        // Если у оператора не заданы настройки локации — пропускаем всех
        if (operatorCity == null && operatorRegion == null) {
            return true;
        }

        // Если у заявки нет локации — пропускаем (нельзя проверить)
        if (elderCity == null && elderRegion == null) {
            return true;
        }

        // Проверяем город
        if (operatorCity != null && elderCity != null) {
            if (operatorCity.equalsIgnoreCase(elderCity)) {
                return true;
            }
        }

        // Проверяем регион
        if (operatorRegion != null && elderRegion != null) {
            if (operatorRegion.equalsIgnoreCase(elderRegion)) {
                return true;
            }
        }

        // Если ни город, ни регион не совпали
        return false;
    }

    /**
     * Проверяет, соответствует ли бюджет заявки настройкам оператора
     */
    private boolean matchesBudget(User operator, Elder elder) {
        Double min = operator.getBudgetMin();
        Double max = operator.getBudgetMax();
        double elderBudget = elder.getBudget();

        // Если бюджет не задан — пропускаем всех
        if (min == null && max == null) {
            return true;
        }

        // Проверяем минимальный бюджет
        if (min != null && elderBudget < min) {
            return false;
        }

        // Проверяем максимальный бюджет
        if (max != null && elderBudget > max) {
            return false;
        }

        return true;
    }
}
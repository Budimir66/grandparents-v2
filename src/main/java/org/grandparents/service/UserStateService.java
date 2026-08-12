package org.grandparents.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.grandparents.model.CareHome;
import org.grandparents.model.Elder;
import org.grandparents.statemachine.DialogState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserStateService {
    private static final Logger log = LoggerFactory.getLogger(UserStateService.class);

    // ===== ПАГИНАЦИЯ ДЛЯ ПОИСКА ЗАЯВОК =====
    private final Map<Long, Integer> currentPage = new ConcurrentHashMap<>();

    // ===== СОСТОЯНИЯ И ВРЕМЕННЫЕ ДАННЫЕ =====
    private final Map<Long, DialogState> states = new ConcurrentHashMap<>();
    private final Map<Long, Elder> tempElders = new ConcurrentHashMap<>();

    // ===== ID ПОСЛЕДНЕГО СООБЩЕНИЯ БОТА =====
    private final Map<Long, String> lastBotMessageId = new ConcurrentHashMap<>();

    private final Map<Long, String> tempCareHomeDescription = new ConcurrentHashMap<>();
    private final Map<Long, String> tempCareHomeSpecialization = new ConcurrentHashMap<>();
    private final Map<Long, Double> tempCareHomePrice = new ConcurrentHashMap<>();  // ← ОДНО ОБЪЯВЛЕНИЕ

    // ===== ВРЕМЕННЫЕ ДАННЫЕ ДЛЯ РЕГИСТРАЦИИ ОПЕРАТОРА =====
    private final Map<Long, String> tempCareHomeName = new ConcurrentHashMap<>();
    private final Map<Long, String> tempCareHomeAddress = new ConcurrentHashMap<>();
    private final Map<Long, String> tempCareHomePhone = new ConcurrentHashMap<>();

    // ===== ВРЕМЕННЫЙ ТЕЛЕФОН ОПЕРАТОРА =====
    private final Map<Long, String> tempOperatorPhone = new ConcurrentHashMap<>();

    private final Map<Long, Long> editingCareHomeId = new ConcurrentHashMap<>();

    // ===== ЦЕЛЬ ЗАПРОСА КОНТАКТА =====
    private final Map<Long, String> tempPurpose = new ConcurrentHashMap<>();

    // ===== ВРЕМЕННЫЕ ДАННЫЕ ДЛЯ НАСТРОЕК =====
    private final Map<Long, Double> tempBudgetMin = new HashMap<>();
    private final Map<Long, String> tempTimeFrom = new HashMap<>();

    // ===== РЕДАКТИРОВАНИЕ ПАНСИОНАТА =====
    private final Map<Long, CareHome> editingCareHome = new HashMap<>();

    // ===== РЕДАКТИРОВАНИЕ БОНУСОВ =====
    private final Map<Long, String> editingBonusKey = new HashMap<>();

    // ===== САЙТ ПАНСИОНАТА =====
    private final Map<Long, String> tempCareHomeWebsite = new HashMap<>();

    // ===== МЕТОДЫ =====
    public void setCurrentPage(Long userId, int page) {
        currentPage.put(userId, page);
    }

    public int getCurrentPage(Long userId) {
        return currentPage.getOrDefault(userId, 0);
    }
    public void setLastBotMessageId(Long userId, String messageId) {
        lastBotMessageId.put(userId, messageId);
    }

    public String getLastBotMessageId(Long userId) {
        return lastBotMessageId.get(userId);
    }

    public void setTempPurpose(Long userId, String purpose) {
        tempPurpose.put(userId, purpose);
    }

    public String getTempPurpose(Long userId) {
        return tempPurpose.get(userId);
    }

    public DialogState getState(Long userId) {
        return states.getOrDefault(userId, DialogState.START);
    }

    public void setState(Long userId, DialogState state) {
        states.put(userId, state);
    }

    public void clearState(Long userId) {
        states.remove(userId);
        tempElders.remove(userId);
        tempCareHomeName.remove(userId);
        tempCareHomeAddress.remove(userId);
        tempCareHomePhone.remove(userId);
        tempCareHomeDescription.remove(userId);
        tempCareHomeSpecialization.remove(userId);
        tempCareHomePrice.remove(userId);
        editingCareHomeId.remove(userId);
        tempOperatorPhone.remove(userId);
        tempPurpose.remove(userId);
        tempCareHomeWebsite.remove(userId);
        lastBotMessageId.remove(userId);

    }

    public Elder getTempElder(Long userId) {
        return tempElders.get(userId);
    }

    public void setTempElder(Long userId, Elder elder) {
        tempElders.put(userId, elder);
    }

    public void setTempCareHomeName(Long userId, String name) {
        tempCareHomeName.put(userId, name);
    }

    public String getTempCareHomeName(Long userId) {
        return tempCareHomeName.get(userId);
    }

    public void setTempCareHomeAddress(Long userId, String address) {
        tempCareHomeAddress.put(userId, address);
    }

    public String getTempCareHomeAddress(Long userId) {
        return tempCareHomeAddress.get(userId);
    }

    public void setTempCareHomePhone(Long userId, String phone) {
        tempCareHomePhone.put(userId, phone);
    }

    public String getTempCareHomePhone(Long userId) {
        return tempCareHomePhone.get(userId);
    }

    public void setTempCareHomeDescription(Long userId, String description) {
        tempCareHomeDescription.put(userId, description);
    }

    public String getTempCareHomeDescription(Long userId) {
        return tempCareHomeDescription.get(userId);
    }

    public void setTempCareHomeSpecialization(Long userId, String specialization) {
        tempCareHomeSpecialization.put(userId, specialization);
    }

    public String getTempCareHomeSpecialization(Long userId) {
        return tempCareHomeSpecialization.get(userId);
    }

    public void setTempCareHomePrice(Long userId, Double price) {
        tempCareHomePrice.put(userId, price);
    }

    public Double getTempCareHomePrice(Long userId) {
        return tempCareHomePrice.get(userId);
    }

    public void clearTempCareHomePrice(Long userId) {
        tempCareHomePrice.remove(userId);
    }

    public void setTempCareHomeWebsite(Long userId, String website) {
        tempCareHomeWebsite.put(userId, website);
    }

    public String getTempCareHomeWebsite(Long userId) {
        return tempCareHomeWebsite.get(userId);
    }

    public void setEditingCareHomeId(Long userId, Long careHomeId) {
        editingCareHomeId.put(userId, careHomeId);
    }

    public Long getEditingCareHomeId(Long userId) {
        return editingCareHomeId.get(userId);
    }

    public void setTempOperatorPhone(Long userId, String phone) {
        tempOperatorPhone.put(userId, phone);
    }

    public String getTempOperatorPhone(Long userId) {
        return tempOperatorPhone.get(userId);
    }

    public void clearTempPurpose(Long userId) {
        tempPurpose.remove(userId);
        log.info("🧹 Очищен tempPurpose для пользователя " + userId);
    }

    public void setTempBudgetMin(Long userId, Double value) {
        tempBudgetMin.put(userId, value);
    }

    public Double getTempBudgetMin(Long userId) {
        return tempBudgetMin.get(userId);
    }

    public void setTempTimeFrom(Long userId, String value) {
        tempTimeFrom.put(userId, value);
    }

    public String getTempTimeFrom(Long userId) {
        return tempTimeFrom.get(userId);
    }

    public void setEditingCareHome(Long userId, CareHome careHome) {
        editingCareHome.put(userId, careHome);
    }

    public CareHome getEditingCareHome(Long userId) {
        return editingCareHome.get(userId);
    }

    public void clearEditingCareHome(Long userId) {
        editingCareHome.remove(userId);
    }

    public void setEditingBonusKey(Long userId, String actionKey) {
        editingBonusKey.put(userId, actionKey);
    }

    public String getEditingBonusKey(Long userId) {
        return editingBonusKey.get(userId);
    }

    public void clearEditingBonusKey(Long userId) {
        editingBonusKey.remove(userId);
    }
    public void clearLastBotMessageId(Long userId) {
        lastBotMessageId.remove(userId);
    }
    // ===== ПАГИНАЦИЯ =====
    private final Map<Long, List<Elder>> searchResults = new ConcurrentHashMap<>();
    private final Map<Long, Integer> searchOffset = new ConcurrentHashMap<>();

    public void setSearchResults(Long userId, List<Elder> elders) {
        searchResults.put(userId, new ArrayList<>(elders));
    }

    public List<Elder> getSearchResults(Long userId) {
        return searchResults.get(userId);
    }

    public void clearSearchResults(Long userId) {
        searchResults.remove(userId);
    }

    public void setSearchOffset(Long userId, Integer offset) {
        searchOffset.put(userId, offset);
    }

    public Integer getSearchOffset(Long userId) {
        return searchOffset.getOrDefault(userId, 0);
    }

    public void clearSearchOffset(Long userId) {
        searchOffset.remove(userId);
    }

    public void clearCurrentPage(Long userId) {
        currentPage.remove(userId);
    }
    // ===== РЕДАКТИРОВАНИЕ ОПЕРАТОРА =====
    private final Map<Long, Long> editingOperatorId = new ConcurrentHashMap<>();

    public void setEditingOperatorId(Long userId, Long operatorId) {
        editingOperatorId.put(userId, operatorId);
    }

    public Long getEditingOperatorId(Long userId) {
        return editingOperatorId.get(userId);
    }

    public void clearEditingOperatorId(Long userId) {
        editingOperatorId.remove(userId);
    }
    // ===== ФИЛЬТР ПО ДАТЕ =====
    private final Map<Long, Boolean> filterTodayOnly = new ConcurrentHashMap<>();

    public void setFilterTodayOnly(Long userId, boolean enabled) {
        filterTodayOnly.put(userId, enabled);
        log.info("📅 setFilterTodayOnly: userId={}, enabled={}", userId, enabled);
    }

    public boolean isFilterTodayOnly(Long userId) {
        boolean value = filterTodayOnly.getOrDefault(userId, false);
        log.info("📅 isFilterTodayOnly: userId={}, value={}", userId, value);
        return value;
    }

    public void clearFilterTodayOnly(Long userId) {
        filterTodayOnly.remove(userId);
    }
}
package org.grandparents.service;

import org.grandparents.model.BonusSetting;
import org.grandparents.repository.BonusSettingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BonusSettingService {

    private final BonusSettingRepository bonusSettingRepository;

    public BonusSettingService(BonusSettingRepository bonusSettingRepository) {
        this.bonusSettingRepository = bonusSettingRepository;
    }

    /**
     * Получить значение бонуса по ключу
     */
    public int getBonusValue(String actionKey) {
        BonusSetting setting = bonusSettingRepository.findByActionKey(actionKey)
                .orElseThrow(() -> new RuntimeException("Настройка не найдена: " + actionKey));
        return setting.getValue();
    }

    /**
     * Получить все настройки бонусов
     */
    public List<BonusSetting> findAll() {
        return bonusSettingRepository.findAll();
    }

    /**
     * Обновить значение бонуса
     */
    public void updateBonusValue(String actionKey, int newValue, Long adminId) {
        BonusSetting setting = bonusSettingRepository.findByActionKey(actionKey)
                .orElseThrow(() -> new RuntimeException("Настройка не найдена: " + actionKey));
        setting.setValue(newValue);
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(adminId);
        bonusSettingRepository.save(setting);
        System.out.println("💰 Бонус обновлён: " + actionKey + " = " + newValue + " (админ " + adminId + ")");
    }

    /**
     * Получить настройку по ключу
     */
    public BonusSetting findByActionKey(String actionKey) {
        return bonusSettingRepository.findByActionKey(actionKey).orElse(null);
    }
}
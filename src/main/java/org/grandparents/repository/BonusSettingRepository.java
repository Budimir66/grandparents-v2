package org.grandparents.repository;

import org.grandparents.model.BonusSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BonusSettingRepository extends JpaRepository<BonusSetting, Long> {
    Optional<BonusSetting> findByActionKey(String actionKey);
}
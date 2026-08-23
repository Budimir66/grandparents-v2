package org.grandparents.repository;

import org.grandparents.model.CareHome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью CareHome (пансионаты)
 */
@Repository
public interface CareHomeRepository extends JpaRepository<CareHome, Long> {

    /**
     * Найти все активные пансионаты
     * @return список активных пансионатов
     */
    List<CareHome> findByIsActiveTrue();

    /**
     * Найти пансионаты по названию (содержит подстроку)
     * @param name часть названия
     * @return список пансионатов
     */
    List<CareHome> findByNameContainingIgnoreCase(String name);

    /**
     * Найти пансионаты с ценой меньше или равной указанной
     * @param price максимальная цена
     * @return список пансионатов
     */
    List<CareHome> findByPriceFromLessThanEqual(double price);

    /**
     * Найти пансионаты с активной подпиской
     * @return список пансионатов с подпиской
     */
    List<CareHome> findByIsSubscribedTrue();

    /**
     * Найти пансионаты с рейтингом выше указанного
     * @param rating минимальный рейтинг
     * @return список пансионатов
     */
    List<CareHome> findByRatingGreaterThanEqual(double rating);

    CareHome findByName(String name);
    CareHome findByNameIgnoreCase(String name);
    List<CareHome> findByIsActiveTrueAndIsSubscribedTrue();
    List<CareHome> findByProposedBy(Long proposedBy);
    List<CareHome> findByIsActiveTrueAndStatus(String status);
}
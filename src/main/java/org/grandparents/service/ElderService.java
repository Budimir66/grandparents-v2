package org.grandparents.service;

import org.grandparents.model.Elder;
import org.grandparents.model.ElderStatus;
import org.grandparents.model.User;
import org.grandparents.model.CareHome;
import org.grandparents.repository.ElderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ElderService {

    private final ElderRepository elderRepository;
    private final UserService userService;
    private final CareHomeService careHomeService;

    public ElderService(ElderRepository elderRepository,
                        UserService userService,
                        CareHomeService careHomeService) {
        this.elderRepository = elderRepository;
        this.userService = userService;
        this.careHomeService = careHomeService;
    }

    // ===== БАЗОВЫЕ CRUD =====

    public Elder createElder(Elder elder) {
        if (elder.getCreatedAt() == null) {
            elder.setCreatedAt(LocalDateTime.now());
        }
        elder.setUpdatedAt(LocalDateTime.now());
        return elderRepository.save(elder);
    }

    public Elder findById(Long id) {
        return elderRepository.findById(id).orElse(null);
    }

    public Elder updateElder(Elder elder) {
        elder.setUpdatedAt(LocalDateTime.now());
        return elderRepository.save(elder);
    }

    public void deleteElder(Long id) {
        elderRepository.deleteById(id);
    }

    // ===== ПОИСК ПО СТАТУСАМ =====

    public List<Elder> findByStatus(ElderStatus status) {
        return elderRepository.findByStatus(status);
    }

    public List<Elder> findActiveElders() {

        return elderRepository.findByStatusNotIn(List.of(
                ElderStatus.COMPLETED,
                ElderStatus.EXPIRED,
                ElderStatus.DELETED,
                ElderStatus.PENDING,
                ElderStatus.AWAITING_CONFIRMATION
        ));
    }

    public List<Elder> findByStatusIn(List<ElderStatus> statuses) {
        return elderRepository.findByStatusIn(statuses);
    }

    // ===== ПОИСК ПО ПОЛЬЗОВАТЕЛЯМ =====

    public List<Elder> findByClientTelegramId(Long clientTelegramId) {
        return elderRepository.findByClientTelegramId(clientTelegramId);
    }

    public List<Elder> findByCreatedBy(Long userId) {
        return elderRepository.findByCreatedBy(userId);
    }

    public List<Elder> findByAssignedOperatorId(Long userId) {
        return elderRepository.findByAssignedOperatorId(userId);
    }

    public List<Elder> findByCompletedBy(Long userId) {
        return elderRepository.findByCompletedBy(userId);
    }

    public List<Elder> findByCareHomeId(Long careHomeId) {
        return elderRepository.findByCareHomeId(careHomeId);
    }

    public List<Elder> findByOfferedBy(Long userId) {
        return elderRepository.findByOfferedBy(userId);
    }

    public List<Elder> findByAcceptedBy(Long userId) {
        return elderRepository.findByAcceptedBy(userId);
    }

    // ===== ПОИСК ПО ЛОКАЦИИ И БЮДЖЕТУ =====

    public List<Elder> findByLocation(String location) {
        return elderRepository.findByPreferredLocationContainingIgnoreCase(location);
    }

    public List<Elder> findByBudget(double minBudget) {
        return elderRepository.findByBudgetGreaterThanEqual(minBudget);
    }

    // ===== РАБОТА СО СТАТУСАМИ =====

    public Elder acceptElder(Long elderId, Long operatorId) {
        Elder elder = findById(elderId);
        if (elder == null) {
            throw new RuntimeException("Заявка не найдена");
        }
        if (elder.getStatus() != ElderStatus.NEW && elder.getStatus() != ElderStatus.OFFERED) {
            throw new RuntimeException("Заявка не может быть принята (статус: " + elder.getStatus() + ")");
        }
        elder.setStatus(ElderStatus.ACCEPTED);
        elder.setAcceptedBy(operatorId);
        elder.setAcceptedAt(LocalDateTime.now());
        elder.setUpdatedAt(LocalDateTime.now());
        return elderRepository.save(elder);
    }

    public Elder offerElder(Long elderId, Long operatorId) {
        Elder elder = findById(elderId);
        if (elder == null) {
            throw new RuntimeException("Заявка не найдена");
        }
        if (!elder.canBeOffered()) {
            throw new RuntimeException("Заявка не может быть предложена (статус: " + elder.getStatus() + ")");
        }
        elder.setStatus(ElderStatus.OFFERED);
        elder.setOfferedBy(operatorId);
        elder.setUpdatedAt(LocalDateTime.now());
        return elderRepository.save(elder);
    }

    @Transactional
    public void markElderAsExpired(Elder elder) {
        elder.setStatus(ElderStatus.EXPIRED);
        elder.setUpdatedAt(LocalDateTime.now());
        elderRepository.save(elder);
    }

    // ===== СТАТИСТИКА =====

    public long countAll() {
        return elderRepository.count();
    }

    public long countByStatus(ElderStatus status) {
        return elderRepository.countByStatus(status);
    }

    public long countByStatusIn(List<ElderStatus> statuses) {
        return elderRepository.countByStatusIn(statuses);
    }

    public long countActiveElders() {
        return elderRepository.countActiveElders(
                List.of(ElderStatus.NEW, ElderStatus.OFFERED, ElderStatus.IN_PROGRESS, ElderStatus.PENDING)
        );
    }

    public long countByCreatedBy(Long userId) {
        return elderRepository.countByCreatedBy(userId);
    }

    public List<Elder> findExpiredElders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(14);
        return elderRepository.findByStatusAndCreatedAtBefore(ElderStatus.NEW, cutoff);
    }

    public List<Elder> findAllExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(14);
        List<ElderStatus> activeStatuses = List.of(
                ElderStatus.NEW,
                ElderStatus.OFFERED,
                ElderStatus.EDITED
        );
        return elderRepository.findByStatusInAndCreatedAtBefore(activeStatuses, cutoff);
    }

    public List<Elder> findByExpiresAtBeforeAndStatusNot(LocalDateTime date, ElderStatus status) {
        return elderRepository.findByExpiresAtBeforeAndStatusNot(date, status);
    }

    public List<Elder> findAll() {
        return elderRepository.findAll();
    }

    // ===== ФИНАНСЫ =====

    @Transactional
    public void completeElderWithPrice(Long elderId, Double price) {
        Elder elder = findById(elderId);
        if (elder == null) {
            throw new RuntimeException("Заявка не найдена");
        }

        elder.setPrice(price);
        elder.setStatus(ElderStatus.COMPLETED);
        elder.setCompletedAt(LocalDateTime.now());
        elderRepository.save(elder);

        // Начисляем доход оператору
        if (elder.getAssignedOperatorId() != null) {
            User operator = userService.findByTelegramId(elder.getAssignedOperatorId()).orElse(null);
            if (operator != null) {
                operator.addRevenue(price);
                userService.saveUser(operator);
            }
        }

        // Начисляем доход пансионату
        if (elder.getCareHomeId() != null) {
            CareHome careHome = careHomeService.findById(elder.getCareHomeId());
            if (careHome != null) {
                careHome.addMonthlyRevenue(price);
                careHomeService.save(careHome);
            }
        }
    }
    public List<Elder> findActiveEldersSortedByDate() {
        return elderRepository.findByStatusNotInOrderByCreatedAtDesc(
                List.of(ElderStatus.COMPLETED, ElderStatus.DELETED, ElderStatus.EXPIRED)
        );
    }

}
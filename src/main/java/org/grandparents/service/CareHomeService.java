package org.grandparents.service;

import org.grandparents.model.CareHome;
import org.grandparents.repository.CareHomeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CareHomeService {

    private final CareHomeRepository careHomeRepository;

    public CareHomeService(CareHomeRepository careHomeRepository) {
        this.careHomeRepository = careHomeRepository;
    }

    public List<CareHome> findAll() {
        return careHomeRepository.findAll();
    }

    public CareHome findById(Long id) {
        return careHomeRepository.findById(id).orElse(null);
    }

    // ===== ПОИСК ПО ЧАСТИ НАЗВАНИЯ (для списка) =====
    public List<CareHome> findByName(String name) {
        return careHomeRepository.findByNameContainingIgnoreCase(name);
    }

    // ===== ПОИСК ПО ТОЧНОМУ НАЗВАНИЮ (для регистрации оператора) =====
    public CareHome findByNameExact(String name) {
        return careHomeRepository.findByName(name);
    }

    public CareHome save(CareHome careHome) {
        return careHomeRepository.save(careHome);
    }
    public List<CareHome> findAllActiveWithSubscription() {
        return careHomeRepository.findByIsActiveTrueAndIsSubscribedTrue();
    }
    public List<CareHome> findByProposedBy(Long userId) {
        return careHomeRepository.findByProposedBy(userId);
    }
    public void delete(Long id) {
        careHomeRepository.deleteById(id);
    }
    public List<CareHome> findActive() {
        return careHomeRepository.findByIsActiveTrueAndStatus("APPROVED");
    }
    public CareHome findByNameIgnoreCase(String name) {
        return careHomeRepository.findByNameIgnoreCase(name);
    }
}
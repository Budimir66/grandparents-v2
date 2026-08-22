package org.grandparents.service;

import org.grandparents.model.Invitation;
import org.grandparents.repository.InvitationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;

    public InvitationService(InvitationRepository invitationRepository) {
        this.invitationRepository = invitationRepository;
    }

    @Transactional
    public Invitation createInvitation(Long careHomeId, Long createdBy) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(3);
        Invitation invitation = new Invitation(token, careHomeId, createdBy, expiresAt);
        return invitationRepository.save(invitation);
    }

    public Invitation findByToken(String token) {
        return invitationRepository.findByToken(token).orElse(null);
    }

    public Invitation findById(Long id) {
        return invitationRepository.findById(id).orElse(null);
    }

    public Invitation findFirstByCareHomeId(Long careHomeId) {
        return invitationRepository
                .findFirstByCareHomeIdAndUsedFalseAndDeletedFalseOrderByCreatedAtDesc(careHomeId)
                .orElse(null);
    }

    public boolean hasActiveInvitation(Long careHomeId) {
        return invitationRepository
                .existsByCareHomeIdAndUsedFalseAndDeletedFalseAndExpiresAtAfter(careHomeId, LocalDateTime.now());
    }

    @Transactional
    public void markAsUsed(Invitation invitation, Long usedBy) {
        invitation.setUsed(true);
        invitation.setUsedAt(LocalDateTime.now());
        invitation.setUsedBy(usedBy);
        invitationRepository.save(invitation);
    }

    @Transactional
    public void deleteInvitation(Long invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Приглашение не найдено"));
        invitation.setDeleted(true);
        invitationRepository.save(invitation);
    }

    public boolean isValid(Invitation invitation) {
        if (invitation == null) return false;
        if (invitation.isUsed()) return false;
        if (invitation.isDeleted()) return false;
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) return false;
        return true;
    }
}
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

    public Invitation findByCareHomeId(Long careHomeId) {
        return invitationRepository.findByCareHomeIdAndUsedFalse(careHomeId).orElse(null);
    }

    @Transactional
    public void markAsUsed(Invitation invitation, Long usedBy) {
        invitation.setUsed(true);
        invitation.setUsedAt(LocalDateTime.now());
        invitation.setUsedBy(usedBy);
        invitationRepository.save(invitation);
    }

    public boolean isValid(Invitation invitation) {
        if (invitation == null) return false;
        if (invitation.isUsed()) return false;
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) return false;
        return true;
    }
}
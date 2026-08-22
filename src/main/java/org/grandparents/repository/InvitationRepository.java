package org.grandparents.repository;

import org.grandparents.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);
    Optional<Invitation> findByCareHomeIdAndUsedFalse(Long careHomeId);
    long countByCareHomeIdAndUsedFalse(Long careHomeId);
}
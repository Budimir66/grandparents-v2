package org.grandparents.repository;

import org.grandparents.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByStatus(String status);
    List<Complaint> findByTargetId(Long targetId);
    List<Complaint> findByTargetIdAndStatus(Long targetId, String status);
    // ComplaintRepository.java
    List<Complaint> findAllByOrderByCreatedAtDesc();
}
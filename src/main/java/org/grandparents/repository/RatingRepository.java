package org.grandparents.repository;

import org.grandparents.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByRaterIdAndElderId(Long raterId, Long elderId);
    List<Rating> findByTargetId(Long targetId);

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.targetId = :targetId")
    Double getAverageRatingForUser(Long targetId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.targetId = :targetId")
    Integer getTotalRatingsForUser(Long targetId);
}
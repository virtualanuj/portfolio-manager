package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.RefreshStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshRunRepository extends JpaRepository<RefreshRunEntity, UUID> {

    List<RefreshRunEntity> findByStatus(RefreshStatus status);

    List<RefreshRunEntity> findByStatusAndStartedAtBefore(RefreshStatus status, Instant cutoff);

    Optional<RefreshRunEntity> findFirstByOrderByStartedAtDesc();
}

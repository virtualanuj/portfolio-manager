package com.portfoliomanager.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRepository extends JpaRepository<PriceEntity, UUID> {}

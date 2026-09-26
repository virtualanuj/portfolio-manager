package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TargetAllocationRepository
        extends JpaRepository<TargetAllocationEntity, AssetType> {}

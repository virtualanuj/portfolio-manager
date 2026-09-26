package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.AssetType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, UUID> {

    boolean existsBySymbolAndAssetType(String symbol, AssetType assetType);

    boolean existsBySymbolAndAssetTypeAndIdNot(String symbol, AssetType assetType, UUID id);

    List<InstrumentEntity> findAllByOrderBySymbolAsc();
}

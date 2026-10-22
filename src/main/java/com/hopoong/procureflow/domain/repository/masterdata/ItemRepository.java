package com.hopoong.procureflow.domain.repository.masterdata;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.domain.entity.masterdata.Item;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    Optional<Item> findByIdAndOrgId(UUID id, String orgId);

    List<Item> findAllByOrgIdAndIdIn(String orgId, Collection<UUID> ids);

    boolean existsByOrgIdAndItemCode(String orgId, String itemCode);

    List<Item> findAllByOrgIdOrderByItemCode(String orgId);
}

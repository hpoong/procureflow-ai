package com.hopoong.procureflow.domain.repository.masterdata;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.domain.entity.masterdata.Item;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    Optional<Item> findByIdAndOrgId(UUID id, UUID orgId);

    List<Item> findAllByOrgIdAndIdIn(UUID orgId, Collection<UUID> ids);
}

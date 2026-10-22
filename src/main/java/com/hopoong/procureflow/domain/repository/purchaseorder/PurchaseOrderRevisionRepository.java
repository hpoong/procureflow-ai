package com.hopoong.procureflow.domain.repository.purchaseorder;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderRevision;
import com.hopoong.procureflow.domain.entity.purchaseorder.RevisionStatus;

public interface PurchaseOrderRevisionRepository extends JpaRepository<PurchaseOrderRevision, UUID> {

    List<PurchaseOrderRevision> findByPoIdOrderByRevisionDesc(UUID poId);

    Optional<PurchaseOrderRevision> findByPoIdAndRevision(UUID poId, Integer revision);

    boolean existsByPoIdAndRevisionStatusIn(UUID poId, Collection<RevisionStatus> statuses);

    @Query("SELECT max(r.revision) FROM PurchaseOrderRevision r WHERE r.poId = :poId")
    Integer findMaxRevision(@Param("poId") UUID poId);
}

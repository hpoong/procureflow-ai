package com.hopoong.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRequest;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderCreateFacade;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderCreateService;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderReplayService;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter;

class PurchaseOrderCreateFacadeTest {

    private final PurchaseOrderSnapshotWriter writer = mock(PurchaseOrderSnapshotWriter.class);
    private final PurchaseOrderReplayService replay = mock(PurchaseOrderReplayService.class);
    private final PurchaseOrderCreateService create = mock(PurchaseOrderCreateService.class);
    private final PurchaseOrderCreateFacade facade = new PurchaseOrderCreateFacade(writer, replay, create);

    @Test
    void rethrowsOtherConstraintViolationsWithoutReplay() {
        RequestActor actor = new RequestActor(UUID.randomUUID(), UUID.randomUUID());
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest(null, null, null, null, null, null, null,
                null, null, List.of());
        when(writer.requestHash(any(), any())).thenReturn("h");
        when(replay.find(any(), anyString(), anyString())).thenReturn(Optional.empty());
        DataIntegrityViolationException violation = new DataIntegrityViolationException("x",
                new ConstraintViolationException("x", new SQLException("x"), "ck_purchase_order_line_status"));
        when(create.create(any(), anyString(), anyString(), any(), any())).thenThrow(violation);

        assertThatThrownBy(() -> facade.create(actor, "k", UUID.randomUUID(), request)).isSameAs(violation);
        verify(replay, times(1)).find(any(), anyString(), anyString());
    }
}

package com.hopoong.procureflow.api.purchaseorder.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderRepository;

@Component
public class PurchaseOrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Clock clock;

    public PurchaseOrderNumberGenerator(PurchaseOrderRepository purchaseOrderRepository, Clock clock) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.clock = clock;
    }

    public String next() {
        long sequence = purchaseOrderRepository.nextPoNumberSequence();
        return "PO-" + LocalDate.now(clock).format(DATE_FORMAT) + "-" + String.format("%08d", sequence);
    }
}

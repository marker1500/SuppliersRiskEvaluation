package by.bsuir.coursework.server.service;

import java.time.LocalDate;

public record RiskContext(
        LocalDate dueDate,
        long quantityUnits,
        double contractAmount,
        OrderRiskStage stage,
        long delayedShipments,
        double supplierReliability
) {
    public RiskContext(LocalDate dueDate, long quantityUnits, double contractAmount,
                       OrderRiskStage stage, long delayedShipments) {
        this(dueDate, quantityUnits, contractAmount, stage, delayedShipments, 50.0);
    }
}
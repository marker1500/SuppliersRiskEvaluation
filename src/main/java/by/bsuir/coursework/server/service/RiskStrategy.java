package by.bsuir.coursework.server.service;

import by.bsuir.coursework.server.domain.ContractEntity;

public interface RiskStrategy {
    /**
     * @deprecated Использовать {@link #evaluate(RiskContext)} — учитывает объём, этап и срок.
     */
    @Deprecated(forRemoval = false)
    double calculate(ContractEntity contract, long delayedShipments);

    RiskAssessmentResult evaluate(RiskContext context);
}

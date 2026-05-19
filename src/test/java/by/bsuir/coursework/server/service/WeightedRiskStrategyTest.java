package by.bsuir.coursework.server.service;

import by.bsuir.coursework.server.domain.ContractEntity;
import by.bsuir.coursework.server.domain.SupplierEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class WeightedRiskStrategyTest {
    private final WeightedRiskStrategy strategy = new WeightedRiskStrategy();

    @Test
    void shouldReturnHigherRiskForDelayedShipments() {
        ContractEntity contract = new ContractEntity(
            "C-001",
            new SupplierEntity("Supplier X", 75.0),
            LocalDate.now().plusDays(2),
            80_000,
            85_000L
        );

        double lowDelay = strategy.calculate(contract, 1);
        double highDelay = strategy.calculate(contract, 8);

        Assertions.assertTrue(highDelay > lowDelay);
        Assertions.assertTrue(highDelay <= 100.0);
    }

    /** Крупный объём, близкий дедлайн, начальный этап без задержок — высокий риск. */
    @Test
    void highRiskForLargeVolumeTightDeadlineInitialStage() {
        RiskAssessmentResult result = strategy.evaluate(new RiskContext(
            LocalDate.now().plusDays(2),
            100_000L,
            120_000.0,
            OrderRiskStage.INITIAL,
            0L
        ));
        Assertions.assertEquals("HIGH", result.riskLevel());
        Assertions.assertTrue(result.riskScore() >= 70.0);
        Assertions.assertTrue(result.summaryRu().contains("100"));
    }

    @Test
    void completedOrdersShouldYieldLowRiskScore() {
        RiskAssessmentResult result = strategy.evaluate(new RiskContext(
            LocalDate.now().plusDays(90),
            5_000L,
            50_000.0,
            OrderRiskStage.COMPLETED,
            0L
        ));
        Assertions.assertEquals("LOW", result.riskLevel());
        Assertions.assertTrue(result.riskScore() < 35.0);
    }
}

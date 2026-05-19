package by.bsuir.coursework.server.service;

import by.bsuir.coursework.server.domain.ContractEntity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class WeightedRiskStrategy implements RiskStrategy {

    @Override
    @Deprecated(forRemoval = false)
    public double calculate(ContractEntity contract, long delayedShipments) {
        RiskContext ctx = new RiskContext(
            contract.getDueDate(),
            contract.getQuantityUnits(),
            contract.getAmount(),
            delayedShipments > 0 ? OrderRiskStage.ISSUES_DELAYED : OrderRiskStage.INITIAL,
            delayedShipments
        );
        return evaluate(ctx).riskScore();
    }

    @Override
    public RiskAssessmentResult evaluate(RiskContext ctx) {
        LocalDate today = LocalDate.now();
        long daysToDue = ChronoUnit.DAYS.between(today, ctx.dueDate());

        // Срочность: чем меньше дней до дедлайна или просрочка — тем выше (0..~1)
        double urgency = daysToDue < 0
            ? Math.min(1.0, 1.0 + (-daysToDue) / 30.0)
            : Math.min(1.0, 1.0 - Math.min(daysToDue, 30) / 30.0);

        // Дополнительный «удар» если до дедлайна 1–3 дня при начальном этапе
        if (daysToDue >= 1 && daysToDue <= 3 && ctx.stage() == OrderRiskStage.INITIAL) {
            urgency = Math.min(1.0, urgency + 0.35);
        }

        double volumeSeverity = Math.min(1.0, ctx.quantityUnits() / 100_000.0);
        double amountSeverity = Math.min(1.0, ctx.contractAmount() / 200_000.0);
        double delaySeverity = Math.min(1.0, ctx.delayedShipments() / 5.0);

        double stageScore = switch (ctx.stage()) {
            case INITIAL -> urgency > 0.75 && volumeSeverity > 0.6 ? 0.92
                : urgency > 0.6 ? 0.75
                    : volumeSeverity > 0.85 ? 0.45 : 0.25;
            case IN_PROGRESS -> 0.45;
            case ISSUES_DELAYED -> Math.min(1.0, 0.55 + delaySeverity * 0.35);
            case COMPLETED -> 0.05;
        };

        // То же состояние, что пример: огромный объём + несколько дней + начальный этап
        double initialHighVolumeBurst = (ctx.stage() == OrderRiskStage.INITIAL
            && volumeSeverity >= 0.85
            && daysToDue <= 7 && daysToDue >= -2) ? 25.0 : 0.0;

        double gross = urgency * 32 + volumeSeverity * 28 + amountSeverity * 10
            + delaySeverity * 20 + stageScore * 30 + initialHighVolumeBurst;

        double cappedGuess = Math.min(100.0, Math.max(0.0, gross));
        String ru = summaryLines(daysToDue, ctx.quantityUnits(), ctx.stage(),
            urgency, delaySeverity,
            cappedGuess);

        return RiskAssessmentResult.fromRaw(gross, ru);
    }

    private String summaryLines(
        long daysToDue,
        long qty,
        OrderRiskStage stage,
        double urgency,
        double delaySeverity,
        double cappedScorePreview
    ) {
        double volumeSeverity = Math.min(1.0, qty / 100_000.0);
        StringBuilder sb = new StringBuilder();
        if (daysToDue < 0) {
            sb.append("Заказ просрочен на ").append(-daysToDue).append(" дн. Отдельное внимание к исполнению.\n");
        } else if (daysToDue <= 3) {
            sb.append("До дедлайна осталось ").append(daysToDue).append(" дн. — высокая срочность.\n");
        } else {
            sb.append("До дедлайна около ").append(daysToDue).append(" дн.\n");
        }
        sb.append("Объём заказа: ").append(qty).append(" ед. Серьёзность по объёму: ")
            .append(String.format("%.0f%%", volumeSeverity * 100)).append(" от эталона 100 000 ед.\n");

        sb.append("Этап исполнения: ").append(stageRu(stage)).append(".\n");
        if (stage == OrderRiskStage.INITIAL && volumeSeverity >= 0.85 && urgency >= 0.55) {
            sb.append("Сочетание крупного заказа с начальной стадией и близким дедлайном резко повышает риск.\n");
        }
        if (delaySeverity > 0.01) {
            sb.append("Учитываются задержки по поставкам (DELAYED).\n");
        }
        double clampedGuess = cappedScorePreview;
        String preview = clampedGuess >= 70 ? "ожидаемо высокий" : clampedGuess >= 40 ? "средний" : "умеренный/низкий";
        sb.append("Условная оценка риска (~").append(Math.round(clampedGuess)).append(" баллов): ").append(preview).append(".");

        return sb.toString();
    }

    private static String stageRu(OrderRiskStage s) {
        return switch (s) {
            case INITIAL -> "начальный (поставки в ожидании / PLANNED)";
            case IN_PROGRESS -> "в процессе (перевозка / промежуточные статусы)";
            case ISSUES_DELAYED -> "есть задержания (DELAYED)";
            case COMPLETED -> "завершён (все DELIVERED)";
        };
    }
}

package by.bsuir.coursework.server.service;

import by.bsuir.coursework.server.domain.ContractEntity;
import by.bsuir.coursework.server.domain.IncidentEntity;
import by.bsuir.coursework.server.domain.RiskAssessmentEntity;
import by.bsuir.coursework.server.domain.ShipmentEntity;
import by.bsuir.coursework.server.domain.SupplierEntity;
import by.bsuir.coursework.server.infra.JpaUtil;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SupplyService {
    private final RiskStrategy riskStrategy;

    public SupplyService(RiskStrategy riskStrategy) {
        this.riskStrategy = riskStrategy;
    }



    public Map<String, Object> createSupplier(String name, double reliabilityScore) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            // Проверяем, есть ли уже поставщик с таким именем
            Long existing = em.createQuery(
                            "select count(s) from SupplierEntity s where s.name = :name", Long.class)
                    .setParameter("name", name)
                    .getSingleResult();

            if (existing > 0) {
                throw new IllegalArgumentException("Supplier with name '" + name + "' already exists");
            }

            em.getTransaction().begin();
            SupplierEntity supplier = new SupplierEntity(name, reliabilityScore);
            em.persist(supplier);
            em.getTransaction().commit();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("supplierId", supplier.getId());
            result.put("name", supplier.getName());
            result.put("score", supplier.getReliabilityScore());

            System.out.println("Supplier created: " + name + " (ID: " + supplier.getId() + ")");

            return result;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public Map<String, Object> createContract(
        String contractNumber,
        long supplierId,
        LocalDate dueDate,
        double amount,
        long quantityUnits
    ) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            SupplierEntity supplier = em.find(SupplierEntity.class, supplierId);
            if (supplier == null) {
                throw new IllegalArgumentException("Supplier not found");
            }
            em.getTransaction().begin();
            ContractEntity contract = new ContractEntity(contractNumber, supplier, dueDate, amount, quantityUnits);
            em.persist(contract);
            em.getTransaction().commit();
            return Map.of(
                "contractId", contract.getId(),
                "number", contractNumber,
                "quantityUnits", quantityUnits
            );
        } finally {
            em.close();
        }
    }

    public Map<String, Object> createShipment(long contractId, String status, LocalDate plannedDate, LocalDate actualDate) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            ContractEntity contract = em.find(ContractEntity.class, contractId);
            if (contract == null) {
                throw new IllegalArgumentException("Contract not found");
            }
            em.getTransaction().begin();
            ShipmentEntity shipment = new ShipmentEntity(contract, status, plannedDate, actualDate);
            em.persist(shipment);
            em.getTransaction().commit();
            return Map.of("shipmentId", shipment.getId(), "status", status);
        } finally {
            em.close();
        }
    }

    public Map<String, Object> updateShipmentStatus(long shipmentId, String status) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            em.getTransaction().begin();
            ShipmentEntity shipment = em.find(ShipmentEntity.class, shipmentId);
            if (shipment == null) {
                throw new IllegalArgumentException("Shipment not found");
            }
            shipment.setStatus(status);
            em.getTransaction().commit();
            return Map.of("shipmentId", shipmentId, "newStatus", status);
        } finally {
            em.close();
        }
    }

    public Map<String, Object> calculateContractRisk(long contractId) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            ContractEntity contract = em.find(ContractEntity.class, contractId);
            if (contract == null) {
                throw new IllegalArgumentException("Contract not found");
            }

            long delayed = delayedCount(em, contractId);
            RiskContext ctx = riskContextForContract(em, contract, delayed);
            RiskAssessmentResult assessed = riskStrategy.evaluate(ctx);

            em.getTransaction().begin();
            RiskAssessmentEntity assessment = new RiskAssessmentEntity(
                contract,
                assessed.riskScore(),
                assessed.riskLevel()
            );
            em.persist(assessment);
            em.getTransaction().commit();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("contractId", contractId);
            out.put("riskScore", assessed.riskScore());
            out.put("riskLevel", assessed.riskLevel());
            out.put("summary", assessed.summaryRu());
            out.put("stage", ctx.stage().name());
            return out;
        } finally {
            em.close();
        }
    }

    public Map<String, Object> getDashboard() {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            // Основные показатели
            long contracts = em.createQuery("select count(c) from ContractEntity c", Long.class).getSingleResult();
            long shipments = em.createQuery("select count(s) from ShipmentEntity s", Long.class).getSingleResult();
            long delayed = em.createQuery("select count(s) from ShipmentEntity s where s.status = 'DELAYED'", Long.class).getSingleResult();
            long overdue = em.createQuery("select count(s) from ShipmentEntity s where s.status = 'OVERDUE'", Long.class).getSingleResult();
            long completed = em.createQuery("select count(s) from ShipmentEntity s where s.status = 'DELIVERED'", Long.class).getSingleResult();

            // Получаем все контракты
            List<ContractEntity> allContracts = em.createQuery(
                            "SELECT c FROM ContractEntity c", ContractEntity.class)
                    .getResultList();

            long highRisk = 0, mediumRisk = 0, lowRisk = 0;

            for (ContractEntity contract : allContracts) {
                // Получаем последнюю оценку риска для контракта
                Double riskScore = null;
                try {
                    Object result = em.createQuery(
                                    "SELECT ra.riskScore FROM RiskAssessmentEntity ra WHERE ra.contract.id = :contractId ORDER BY ra.createdAt DESC")
                            .setParameter("contractId", contract.getId())
                            .setMaxResults(1)
                            .getSingleResult();
                    riskScore = (Double) result;
                } catch (Exception e) {
                    // Нет оценки риска
                }

                if (riskScore != null) {
                    if (riskScore >= 70) highRisk++;
                    else if (riskScore >= 40) mediumRisk++;
                    else lowRisk++;
                } else {
                    // Нет оценки - считаем по умолчанию низким
                    lowRisk++;
                }
            }

            double averageRisk = 0;
            try {
                Object avgResult = em.createQuery(
                                "SELECT AVG(ra.riskScore) FROM RiskAssessmentEntity ra")
                        .getSingleResult();
                if (avgResult != null) averageRisk = (Double) avgResult;
            } catch (Exception e) {
                averageRisk = 0;
            }

            // Активные контракты
            long activeContracts = em.createQuery(
                            "select count(c) from ContractEntity c where c.dueDate >= current_date", Long.class)
                    .getSingleResult();

            // Просроченные контракты
            long overdueContracts = em.createQuery(
                            "select count(c) from ContractEntity c where c.dueDate < current_date", Long.class)
                    .getSingleResult();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contracts", contracts);
            result.put("shipments", shipments);
            result.put("delayed", delayed);
            result.put("overdue", overdue);
            result.put("completed", completed);
            result.put("highRisk", highRisk);
            result.put("mediumRisk", mediumRisk);
            result.put("lowRisk", lowRisk);
            result.put("averageRisk", Math.round(averageRisk * 10) / 10.0);
            result.put("activeContracts", activeContracts);
            result.put("overdueContracts", overdueContracts);

            return result;
        } finally {
            em.close();
        }
    }

    private double calculateAverageRisk(EntityManager em) {
        try {
            Double avg = em.createQuery(
                            "select avg(ra.riskScore) from RiskAssessmentEntity ra " +
                                    "where ra.createdAt = (select max(ra2.createdAt) from RiskAssessmentEntity ra2 where ra2.contract.id = ra.contract.id)",
                            Double.class)
                    .getSingleResult();
            return avg != null ? avg : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public Map<String, Object> getOrders() {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            List<ContractEntity> contracts = em.createQuery(
                            "SELECT c FROM ContractEntity c", ContractEntity.class)
                    .getResultList();

            List<Map<String, Object>> orders = new ArrayList<>();
            for (ContractEntity c : contracts) {
                // Получаем поставки
                List<ShipmentEntity> shipments = em.createQuery(
                                "SELECT s FROM ShipmentEntity s WHERE s.contract.id = :contractId",
                                ShipmentEntity.class)
                        .setParameter("contractId", c.getId())
                        .getResultList();

                // Определяем статус заказа
                String orderStatus = "PLANNED";
                for (ShipmentEntity s : shipments) {
                    if ("OVERDUE".equals(s.getStatus())) {
                        orderStatus = "OVERDUE";
                        break;
                    }
                    if ("DELAYED".equals(s.getStatus())) {
                        orderStatus = "DELAYED";
                    } else if ("IN_TRANSIT".equals(s.getStatus()) && !"DELAYED".equals(orderStatus)) {
                        orderStatus = "IN_TRANSIT";
                    } else if ("DELIVERED".equals(s.getStatus()) && "PLANNED".equals(orderStatus)) {
                        orderStatus = "DELIVERED";
                    } else if ("CANCELLED".equals(s.getStatus())) {
                        orderStatus = "CANCELLED";
                    }
                }

                // Получаем последнюю оценку риска
                RiskAssessmentEntity latestRisk = null;
                try {
                    latestRisk = em.createQuery(
                                    "SELECT ra FROM RiskAssessmentEntity ra WHERE ra.contract.id = :contractId ORDER BY ra.createdAt DESC",
                                    RiskAssessmentEntity.class)
                            .setParameter("contractId", c.getId())
                            .setMaxResults(1)
                            .getSingleResult();
                } catch (Exception e) {
                    // Нет оценки
                }

                double riskScore = latestRisk != null ? latestRisk.getRiskScore() : 0;
                String riskLevel = latestRisk != null ? latestRisk.getRiskLevel() : "LOW";

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", c.getId());
                row.put("number", c.getContractNumber());
                row.put("supplier", c.getSupplier().getName());
                row.put("dueDate", c.getDueDate().toString());
                row.put("amount", c.getAmount());
                row.put("quantityUnits", c.getQuantityUnits());
                row.put("shipmentStatus", orderStatus);
                row.put("riskScore", riskScore);
                row.put("riskLevel", riskLevel);

                orders.add(row);
            }

            return Map.of("orders", orders);
        } finally {
            em.close();
        }
    }

    private static String stageLabelRu(OrderRiskStage stage) {
        return switch (stage) {
            case INITIAL -> "Начальный этап";
            case IN_PROGRESS -> "В процессе";
            case ISSUES_DELAYED -> "Есть задержки";
            case COMPLETED -> "Завершён";
        };
    }

    private static long delayedCount(EntityManager em, long contractId) {
        return em.createQuery(
                "select count(s) from ShipmentEntity s where s.contract.id = :contractId and s.status = 'DELAYED'",
                Long.class)
            .setParameter("contractId", contractId)
            .getSingleResult();
    }

    private static RiskContext riskContextForContract(EntityManager em, ContractEntity contract, long delayedCount) {
        List<String> statuses = em.createQuery(
                "select s.status from ShipmentEntity s where s.contract.id = :contractId",
                String.class
            )
            .setParameter("contractId", contract.getId())
            .getResultList();

        OrderRiskStage stage = aggregateStage(statuses, delayedCount);
        return new RiskContext(
            contract.getDueDate(),
            contract.getQuantityUnits(),
            contract.getAmount(),
            stage,
            delayedCount
        );
    }

    static OrderRiskStage aggregateStage(List<String> statuses, long delayedCountNonEmpty) {
        if (statuses == null || statuses.isEmpty()) {
            return OrderRiskStage.INITIAL;
        }

        Set<String> set = new HashSet<>();
        for (String st : statuses) {
            if (st != null) set.add(st.trim().toUpperCase());
        }

        // Проверяем на OVERDUE (просрочка)
        if (set.contains("OVERDUE")) {
            return OrderRiskStage.ISSUES_DELAYED;
        }

        // Проверяем на DELAYED
        if (delayedCountNonEmpty > 0 || set.contains("DELAYED")) {
            return OrderRiskStage.ISSUES_DELAYED;
        }

        // Все поставки DELIVERED
        if (!set.isEmpty() && set.stream().allMatch(x -> x.equals("DELIVERED"))) {
            return OrderRiskStage.COMPLETED;
        }

        // Все поставки PLANNED
        if (set.stream().allMatch(x -> x.equals("PLANNED"))) {
            return OrderRiskStage.INITIAL;
        }

        // Есть IN_TRANSIT
        if (set.contains("IN_TRANSIT")) {
            return OrderRiskStage.IN_PROGRESS;
        }

        return OrderRiskStage.IN_PROGRESS;
    }

    public Map<String, Object> getSupplierScore(long supplierId) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            SupplierEntity supplier = em.find(SupplierEntity.class, supplierId);
            if (supplier == null) {
                throw new IllegalArgumentException("Supplier not found");
            }
            return Map.of("supplierId", supplierId, "score", supplier.getReliabilityScore());
        } finally {
            em.close();
        }
    }

    public Map<String, Object> createIncident(long shipmentId, String severity, String description) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            ShipmentEntity shipment = em.find(ShipmentEntity.class, shipmentId);
            if (shipment == null) {
                throw new IllegalArgumentException("Shipment not found");
            }
            em.getTransaction().begin();
            IncidentEntity incident = new IncidentEntity(shipment, severity, description);
            em.persist(incident);
            em.getTransaction().commit();
            return Map.of("incidentId", incident.getId(), "severity", severity);
        } finally {
            em.close();
        }
    }

    public Map<String, Object> escalateIncident(long incidentId) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            em.getTransaction().begin();
            IncidentEntity incident = em.find(IncidentEntity.class, incidentId);
            if (incident == null) {
                throw new IllegalArgumentException("Incident not found");
            }
            incident.escalate();
            em.getTransaction().commit();
            return Map.of("incidentId", incidentId, "escalated", true);
        } finally {
            em.close();
        }
    }

    public Map<String, Object> calculatePenalty(long contractId, long overdueDays) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            ContractEntity contract = em.find(ContractEntity.class, contractId);
            if (contract == null) {
                throw new IllegalArgumentException("Contract not found");
            }
            double penalty = contract.getAmount() * 0.001 * overdueDays;
            return Map.of("contractId", contractId, "overdueDays", overdueDays, "penalty", penalty);
        } finally {
            em.close();
        }
    }

    public Map<String, Object> getAudit() {
        Map<String, Object> audit = new HashMap<>();
        audit.put("event", "Audit logging stub is active");
        audit.put("generatedAt", LocalDate.now().toString());
        return audit;
    }

    public Map<String, Object> subscribeAlerts(String channel) {
        return Map.of("subscribed", true, "channel", channel);
    }
    // Добавить в класс SupplyService:

    public Map<String, Object> updateContract(long contractId, Map<String, Object> updates) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            em.getTransaction().begin();
            ContractEntity contract = em.find(ContractEntity.class, contractId);
            if (contract == null) {
                throw new IllegalArgumentException("Contract not found");
            }

            // Используем reflection для обновления полей
            try {
                if (updates.containsKey("dueDate")) {
                    var field = ContractEntity.class.getDeclaredField("dueDate");
                    field.setAccessible(true);
                    field.set(contract, LocalDate.parse(String.valueOf(updates.get("dueDate"))));
                }
                if (updates.containsKey("amount")) {
                    var field = ContractEntity.class.getDeclaredField("amount");
                    field.setAccessible(true);
                    field.set(contract, ((Number) updates.get("amount")).doubleValue());
                }
                if (updates.containsKey("quantityUnits")) {
                    var field = ContractEntity.class.getDeclaredField("quantityUnits");
                    field.setAccessible(true);
                    field.set(contract, ((Number) updates.get("quantityUnits")).longValue());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to update contract", e);
            }

            em.merge(contract);
            em.getTransaction().commit();
            return Map.of("contractId", contractId, "updated", true);
        } finally {
            em.close();
        }
    }

    // В SupplyService.java добавьте метод, если его нет
    public Map<String, Object> getAllSuppliers() {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            List<SupplierEntity> suppliers = em.createQuery(
                            "SELECT s FROM SupplierEntity s ORDER BY s.id", SupplierEntity.class)
                    .getResultList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (SupplierEntity s : suppliers) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", s.getId());
                map.put("name", s.getName());
                map.put("score", s.getReliabilityScore());
                result.add(map);
            }

            return Map.of("suppliers", result);
        } finally {
            em.close();
        }
    }
    public Map<String, Object> updateContractStatus(long contractId, String newStatus) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            em.getTransaction().begin();

            // Находим заказ
            ContractEntity contract = em.find(ContractEntity.class, contractId);
            if (contract == null) {
                throw new IllegalArgumentException("Contract not found: " + contractId);
            }

            // Находим все поставки по этому заказу
            List<ShipmentEntity> shipments = em.createQuery(
                            "SELECT s FROM ShipmentEntity s WHERE s.contract.id = :contractId",
                            ShipmentEntity.class)
                    .setParameter("contractId", contractId)
                    .getResultList();

            if (shipments.isEmpty()) {
                throw new IllegalArgumentException("No shipments found for contract: " + contractId);
            }

            // Обновляем статус первой поставки
            ShipmentEntity shipmentToUpdate = shipments.get(0);
            String oldStatus = shipmentToUpdate.getStatus();
            shipmentToUpdate.setStatus(newStatus);
            em.merge(shipmentToUpdate);

            // Пересчитываем риск после изменения статуса
            long delayedCount = countDelayedShipments(em, contractId);

            List<String> statuses = em.createQuery(
                            "select s.status from ShipmentEntity s where s.contract.id = :contractId",
                            String.class)
                    .setParameter("contractId", contractId)
                    .getResultList();

            OrderRiskStage stage = aggregateStage(statuses, delayedCount);

            RiskContext ctx = new RiskContext(
                    contract.getDueDate(),
                    contract.getQuantityUnits(),
                    contract.getAmount(),
                    stage,
                    delayedCount,
                    contract.getSupplier().getReliabilityScore()
            );

            RiskAssessmentResult assessed = new WeightedRiskStrategy().evaluate(ctx);

            // Сохраняем новую оценку риска
            RiskAssessmentEntity assessment = new RiskAssessmentEntity(contract, assessed.riskScore(), assessed.riskLevel());
            em.persist(assessment);

            em.getTransaction().commit();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contractId", contractId);
            result.put("contractNumber", contract.getContractNumber());
            result.put("oldStatus", oldStatus);
            result.put("newStatus", newStatus);
            result.put("shipmentId", shipmentToUpdate.getId());
            result.put("riskScore", assessed.riskScore());
            result.put("riskLevel", assessed.riskLevel());
            result.put("summary", assessed.summaryRu());

            return result;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    // Добавьте этот вспомогательный метод
    private long countDelayedShipments(EntityManager em, long contractId) {
        return em.createQuery(
                        "SELECT count(s) FROM ShipmentEntity s WHERE s.contract.id = :contractId AND (s.status = 'DELAYED' OR s.status = 'OVERDUE')",
                        Long.class)
                .setParameter("contractId", contractId)
                .getSingleResult();
    }
}

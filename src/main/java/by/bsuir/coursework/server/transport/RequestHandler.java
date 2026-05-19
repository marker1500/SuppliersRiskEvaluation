package by.bsuir.coursework.server.transport;

import by.bsuir.coursework.common.ApiRequest;
import by.bsuir.coursework.common.ApiResponse;
import by.bsuir.coursework.common.CommandType;
import by.bsuir.coursework.common.Role;
import by.bsuir.coursework.server.domain.UserEntity;
import by.bsuir.coursework.server.service.AuthService;
import by.bsuir.coursework.server.service.SupplyService;
import by.bsuir.coursework.server.service.WeightedRiskStrategy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestHandler {
    private final AuthService authService = new AuthService();
    private final SupplyService supplyService = new SupplyService(new WeightedRiskStrategy());

    public ApiResponse handle(ApiRequest request) {
        try {
            // Проверка checksum (опционально)
            // if (!ChecksumUtil.checksum(request).equals(request.getChecksum())) {
            //     return ApiResponse.error(request.getRequestId(), "Invalid checksum");
            // }

            CommandType command = request.getCommandType();
            if (command == null) {
                return ApiResponse.error(request.getRequestId(), "Unknown command");
            }

            Map<String, Object> payload = request.getPayload() == null ? Map.of() : request.getPayload();

            // Команды без авторизации
            switch (command) {
                case REGISTER -> {
                    return handleRegister(request, payload);
                }
                case LOGIN -> {
                    return handleLogin(request, payload);
                }
            }

            // Все остальные команды требуют авторизации
            String token = request.getAuthToken();
            if (token == null || token.isBlank()) {
                return ApiResponse.error(request.getRequestId(), "Authorization required");
            }

            var authResult = authService.authenticate(token);
            if (authResult.isEmpty()) {
                return ApiResponse.error(request.getRequestId(), "Invalid or expired token");
            }
            UserEntity user = authResult.get();

            // Обработка команд с авторизацией
            return switch (command) {
                case GET_DASHBOARD -> handleGetDashboard(request, user);
                case GET_ORDERS -> handleGetOrders(request, user);
                case CREATE_SUPPLIER -> {
                    checkAdmin(user);
                    yield handleCreateSupplier(request, payload);
                }
                case CREATE_CONTRACT -> {
                    checkAdmin(user);
                    yield handleCreateContract(request, payload);
                }
                case CREATE_SHIPMENT -> handleCreateShipment(request, payload);
                case UPDATE_SHIPMENT_STATUS -> handleUpdateShipmentStatus(request, payload);
                case CALCULATE_CONTRACT_RISK -> handleCalculateRisk(request, payload);
                case GET_SUPPLIER_SCORE -> handleGetSupplierScore(request, payload);
                case CREATE_INCIDENT -> handleCreateIncident(request, payload);
                case ESCALATE_INCIDENT -> handleEscalateIncident(request, payload);
                case CALCULATE_PENALTY -> handleCalculatePenalty(request, payload);
                case GET_AUDIT -> handleGetAudit(request, user);
                case UPDATE_CONTRACT_STATUS -> handleUpdateContractStatus(request, payload);
                case SUBSCRIBE_ALERTS -> handleSubscribeAlerts(request, payload);
                case UPDATE_CONTRACT -> {
                    checkAdmin(user);
                    yield handleUpdateContract(request, payload);
                }
                case GET_SUPPLIERS -> handleGetSuppliers(request, user);
                default -> ApiResponse.error(request.getRequestId(), "Command not implemented");
            };
        } catch (Exception e) {
            return ApiResponse.error(request.getRequestId(), "Server error: " + e.getMessage());
        }
    }

    private ApiResponse handleRegister(ApiRequest request, Map<String, Object> payload) {
        try {
            String username = (String) payload.get("username");
            String password = (String) payload.get("password");

            if (username == null || password == null) {
                return ApiResponse.error(request.getRequestId(), "Username and password required");
            }

            authService.registerEmployee(username, password);
            return ApiResponse.ok(request.getRequestId(), "Employee registered successfully", Map.of());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(request.getRequestId(), e.getMessage());
        }
    }

    private ApiResponse handleLogin(ApiRequest request, Map<String, Object> payload) {
        String username = (String) payload.get("username");
        String password = (String) payload.get("password");

        if (username == null || password == null) {
            return ApiResponse.error(request.getRequestId(), "Username and password required");
        }

        var result = authService.login(username, password);
        if (result.isEmpty()) {
            return ApiResponse.error(request.getRequestId(), "Invalid credentials");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("token", result.get().token());
        data.put("username", result.get().user().getUsername());
        data.put("roles", result.get().user().getRoles());

        return ApiResponse.ok(request.getRequestId(), "Login successful", data);
    }

    private ApiResponse handleGetDashboard(ApiRequest request, UserEntity user) {
        Map<String, Object> data = supplyService.getDashboard();
        return ApiResponse.ok(request.getRequestId(), "Dashboard data", data);
    }

    private ApiResponse handleGetOrders(ApiRequest request, UserEntity user) {
        Map<String, Object> data = supplyService.getOrders();
        return ApiResponse.ok(request.getRequestId(), "Orders list", data);
    }

    private ApiResponse handleCreateSupplier(ApiRequest request, Map<String, Object> payload) {
        try {
            String name = (String) payload.get("name");
            Double score = null;

            if (payload.get("score") instanceof Number) {
                score = ((Number) payload.get("score")).doubleValue();
            } else if (payload.get("score") instanceof String) {
                score = Double.parseDouble((String) payload.get("score"));
            }

            if (name == null || name.isBlank()) {
                return ApiResponse.error(request.getRequestId(), "Name is required");
            }
            if (score == null || score < 0 || score > 100) {
                return ApiResponse.error(request.getRequestId(), "Score must be between 0 and 100");
            }

            Map<String, Object> data = supplyService.createSupplier(name, score);
            return ApiResponse.ok(request.getRequestId(), "Supplier created successfully", data);

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error(request.getRequestId(), "Error creating supplier: " + e.getMessage());
        }
    }

    private ApiResponse handleCreateContract(ApiRequest request, Map<String, Object> payload) {
        String number = (String) payload.get("number");
        Long supplierId = payload.get("supplierId") instanceof Number ? ((Number) payload.get("supplierId")).longValue() : null;
        String dueDateStr = (String) payload.get("dueDate");
        Double amount = payload.get("amount") instanceof Number ? ((Number) payload.get("amount")).doubleValue() : null;
        Long quantityUnits = payload.get("quantityUnits") instanceof Number ? ((Number) payload.get("quantityUnits")).longValue() : null;

        if (number == null || supplierId == null || dueDateStr == null || amount == null) {
            return ApiResponse.error(request.getRequestId(), "Missing required fields");
        }

        LocalDate dueDate = LocalDate.parse(dueDateStr);
        long qty = quantityUnits != null ? quantityUnits : 0;

        Map<String, Object> data = supplyService.createContract(number, supplierId, dueDate, amount, qty);
        return ApiResponse.ok(request.getRequestId(), "Contract created", data);
    }

    private ApiResponse handleCreateShipment(ApiRequest request, Map<String, Object> payload) {
        Long contractId = payload.get("contractId") instanceof Number ? ((Number) payload.get("contractId")).longValue() : null;
        String status = (String) payload.get("status");
        String plannedDateStr = (String) payload.get("plannedDate");
        String actualDateStr = (String) payload.get("actualDate");

        if (contractId == null || status == null || plannedDateStr == null) {
            return ApiResponse.error(request.getRequestId(), "Missing required fields");
        }

        LocalDate plannedDate = LocalDate.parse(plannedDateStr);
        LocalDate actualDate = actualDateStr != null ? LocalDate.parse(actualDateStr) : null;

        Map<String, Object> data = supplyService.createShipment(contractId, status, plannedDate, actualDate);
        return ApiResponse.ok(request.getRequestId(), "Shipment created", data);
    }

    private ApiResponse handleUpdateShipmentStatus(ApiRequest request, Map<String, Object> payload) {
        Long shipmentId = payload.get("shipmentId") instanceof Number ? ((Number) payload.get("shipmentId")).longValue() : null;
        String status = (String) payload.get("status");

        if (shipmentId == null || status == null) {
            return ApiResponse.error(request.getRequestId(), "Shipment ID and status required");
        }

        Map<String, Object> data = supplyService.updateShipmentStatus(shipmentId, status);
        return ApiResponse.ok(request.getRequestId(), "Shipment status updated", data);
    }

    private ApiResponse handleCalculateRisk(ApiRequest request, Map<String, Object> payload) {
        Long contractId = payload.get("contractId") instanceof Number ? ((Number) payload.get("contractId")).longValue() : null;

        if (contractId == null) {
            return ApiResponse.error(request.getRequestId(), "Contract ID required");
        }

        Map<String, Object> data = supplyService.calculateContractRisk(contractId);
        return ApiResponse.ok(request.getRequestId(), "Risk calculated", data);
    }

    private ApiResponse handleGetSupplierScore(ApiRequest request, Map<String, Object> payload) {
        Long supplierId = payload.get("supplierId") instanceof Number ? ((Number) payload.get("supplierId")).longValue() : null;

        if (supplierId == null) {
            return ApiResponse.error(request.getRequestId(), "Supplier ID required");
        }

        Map<String, Object> data = supplyService.getSupplierScore(supplierId);
        return ApiResponse.ok(request.getRequestId(), "Supplier score", data);
    }

    private ApiResponse handleCreateIncident(ApiRequest request, Map<String, Object> payload) {
        Long shipmentId = payload.get("shipmentId") instanceof Number ? ((Number) payload.get("shipmentId")).longValue() : null;
        String severity = (String) payload.get("severity");
        String description = (String) payload.get("description");

        if (shipmentId == null || severity == null || description == null) {
            return ApiResponse.error(request.getRequestId(), "Missing required fields");
        }

        Map<String, Object> data = supplyService.createIncident(shipmentId, severity, description);
        return ApiResponse.ok(request.getRequestId(), "Incident created", data);
    }

    private ApiResponse handleEscalateIncident(ApiRequest request, Map<String, Object> payload) {
        Long incidentId = payload.get("incidentId") instanceof Number ? ((Number) payload.get("incidentId")).longValue() : null;

        if (incidentId == null) {
            return ApiResponse.error(request.getRequestId(), "Incident ID required");
        }

        Map<String, Object> data = supplyService.escalateIncident(incidentId);
        return ApiResponse.ok(request.getRequestId(), "Incident escalated", data);
    }

    private ApiResponse handleCalculatePenalty(ApiRequest request, Map<String, Object> payload) {
        Long contractId = payload.get("contractId") instanceof Number ? ((Number) payload.get("contractId")).longValue() : null;
        Long overdueDays = payload.get("overdueDays") instanceof Number ? ((Number) payload.get("overdueDays")).longValue() : null;

        if (contractId == null || overdueDays == null) {
            return ApiResponse.error(request.getRequestId(), "Contract ID and overdue days required");
        }

        Map<String, Object> data = supplyService.calculatePenalty(contractId, overdueDays);
        return ApiResponse.ok(request.getRequestId(), "Penalty calculated", data);
    }

    private ApiResponse handleGetAudit(ApiRequest request, UserEntity user) {
        Map<String, Object> data = supplyService.getAudit();
        return ApiResponse.ok(request.getRequestId(), "Audit log", data);
    }

    private ApiResponse handleSubscribeAlerts(ApiRequest request, Map<String, Object> payload) {
        String channel = (String) payload.get("channel");
        if (channel == null) {
            channel = "in-app";
        }
        Map<String, Object> data = supplyService.subscribeAlerts(channel);
        return ApiResponse.ok(request.getRequestId(), "Subscribed to " + channel, data);
    }

    private ApiResponse handleUpdateContract(ApiRequest request, Map<String, Object> payload) {
        Long contractId = payload.get("contractId") instanceof Number ? ((Number) payload.get("contractId")).longValue() : null;

        if (contractId == null) {
            return ApiResponse.error(request.getRequestId(), "Contract ID required");
        }

        Map<String, Object> updates = new HashMap<>(payload);
        updates.remove("contractId");

        Map<String, Object> data = supplyService.updateContract(contractId, updates);
        return ApiResponse.ok(request.getRequestId(), "Contract updated", data);
    }

    private ApiResponse handleGetSuppliers(ApiRequest request, UserEntity user) {
        Map<String, Object> data = supplyService.getAllSuppliers();
        return ApiResponse.ok(request.getRequestId(), "Suppliers list", data);
    }
    // Убедитесь, что в RequestHandler.java есть обработка UPDATE_CONTRACT_STATUS
    private ApiResponse handleUpdateContractStatus(ApiRequest request, Map<String, Object> payload) {
        try {
            System.out.println("=== UPDATE_CONTRACT_STATUS received ===");
            System.out.println("Payload: " + payload);

            Long contractId = null;
            if (payload.get("contractId") instanceof Number) {
                contractId = ((Number) payload.get("contractId")).longValue();
            } else if (payload.get("contractId") instanceof String) {
                contractId = Long.parseLong((String) payload.get("contractId"));
            }

            String status = (String) payload.get("status");

            System.out.println("contractId=" + contractId + ", status=" + status);

            if (contractId == null || status == null) {
                return ApiResponse.error(request.getRequestId(), "Contract ID and status required");
            }

            Map<String, Object> data = supplyService.updateContractStatus(contractId, status);
            System.out.println("Result: " + data);

            return ApiResponse.ok(request.getRequestId(), "Contract status updated", data);
        } catch (Exception e) {
            System.err.println("Error in updateContractStatus: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.error(request.getRequestId(), "Error: " + e.getMessage());
        }
    }
    private void checkAdmin(UserEntity user) {
        if (!user.getRoles().contains(Role.ADMIN)) {
            throw new SecurityException("Admin role required");
        }
    }
}
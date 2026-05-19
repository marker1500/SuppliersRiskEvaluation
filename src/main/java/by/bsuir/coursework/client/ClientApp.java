// mvn -DskipTests javafx:run
package by.bsuir.coursework.client;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.stage.Modality;
import by.bsuir.coursework.common.ApiRequest;
import by.bsuir.coursework.common.ApiResponse;
import by.bsuir.coursework.common.AppConfig;
import by.bsuir.coursework.common.CommandType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Modality;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

import static by.bsuir.coursework.common.CommandType.UPDATE_CONTRACT_STATUS;

public class ClientApp extends Application {
    private final TcpClient client = new TcpClient(
            System.getProperty("app.host", "localhost"),
            Integer.parseInt(System.getProperty("app.port", "9090"))
    );
    private String authToken;
    private String currentUser;
    private boolean isAdmin;
    private Stage mainStage;
    private TextArea globalLogArea;
    private TableView<OrderRow> ordersTable;
    private Stage ordersListStage;

    @Override
    public void start(Stage stage) {
        showAuthScreen(stage);
    }

    private void showAuthScreen(Stage stage) {
        Label title = new Label("Supply & Contract Risk System");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Оконный клиент (JavaFX) для работы с сервером по TCP/JSON");
        subtitle.getStyleClass().add("subtitle");

        TextField username = new TextField();
        username.setPromptText("Логин");

        PasswordField password = new PasswordField();
        password.setPromptText("Пароль");

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefRowCount(15);

        Label inlineStatus = new Label();
        inlineStatus.getStyleClass().add("subtitle");

        Button loginBtn = new Button("Войти");
        loginBtn.getStyleClass().addAll("button", "primary");
        loginBtn.setOnAction(e -> sendLogin(username.getText(), password.getText(), stage, output));

        Button registerBtn = new Button("Зарегистрировать сотрудника");
        registerBtn.getStyleClass().addAll("button", "ghost");
        registerBtn.setOnAction(e -> sendRegisterEmployee(username.getText(), password.getText(), output, inlineStatus));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Логин"), 0, 0);
        form.add(username, 1, 0);
        form.add(new Label("Пароль"), 0, 1);
        form.add(password, 1, 1);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setHgrow(password, Priority.ALWAYS);



        HBox actions = new HBox(10, registerBtn, loginBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(10, title, subtitle, new Separator(), form, actions, inlineStatus, new Separator(), output);
        card.getStyleClass().add("card");
        card.setMaxWidth(680);

        VBox shell = new VBox(card);
        shell.getStyleClass().addAll("app-shell");
        shell.setPadding(new Insets(22));
        shell.setAlignment(Pos.TOP_CENTER);

        Scene scene = new Scene(shell, 900, 700);
        attachStyles(scene);
        stage.setScene(scene);
        stage.setTitle("Supply Contract Risk System");
        stage.show();
    }

    private void sendRegisterEmployee(String user, String pass, TextArea output, Label inlineStatus) {
        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
            output.appendText("Нужно указать логин и пароль." + System.lineSeparator());
            showInline(inlineStatus, false, "Укажите логин и пароль для регистрации.");
            return;
        }
        showInline(inlineStatus, true, "Регистрация отправлена на сервер…");
        ApiRequest request = new ApiRequest();
        request.setCommandType(CommandType.REGISTER);
        request.setPayload(Map.of("username", user, "password", pass));
        client.send(request).thenAccept(response -> Platform.runLater(() -> {
            output.appendText("[РЕГИСТРАЦИЯ] " + response.getMessage() + System.lineSeparator());
            output.appendText(System.lineSeparator());
            showInline(inlineStatus, response.isSuccess(), response.getMessage());
        }));
    }

    private void sendLogin(String user, String pass, Stage stage, TextArea output) {
        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
            output.appendText("Нужно указать логин и пароль." + System.lineSeparator());
            return;
        }
        ApiRequest request = new ApiRequest();
        request.setCommandType(CommandType.LOGIN);
        request.setPayload(Map.of("username", user, "password", pass));
        client.send(request).thenAccept(response -> Platform.runLater(() -> {
            output.appendText("[ВХОД] " + response.getMessage() + System.lineSeparator());
            if (response.isSuccess()) {
                this.authToken = String.valueOf(response.getData().get("token"));
                this.currentUser = String.valueOf(response.getData().getOrDefault("username", user));
                this.isAdmin = response.getData().get("roles") != null && String.valueOf(response.getData().get("roles")).contains("ADMIN");
                this.mainStage = stage;
                showMainScreen(stage, this.currentUser);
            } else {
                output.appendText(System.lineSeparator());
            }
        }));
    }

    private void showMainScreen(Stage stage, String username) {
        this.currentUser = username;

        globalLogArea = new TextArea();
        globalLogArea.setEditable(false);
        globalLogArea.setWrapText(true);
        globalLogArea.setPrefRowCount(12);
        VBox.setVgrow(globalLogArea, Priority.ALWAYS);

        Label userLabel = new Label();
        Label connLabel = new Label();
        updateStatusLabels(userLabel, connLabel);

        Button toggleLogBtn = new Button("Показать журнал");
        toggleLogBtn.getStyleClass().addAll("button", "ghost");

        Button showOrdersListBtn = new Button("Список заказов");
        showOrdersListBtn.getStyleClass().addAll("button", "primary");
        showOrdersListBtn.setOnAction(e -> showOrdersListWindow());

        Button logoutBtn = new Button("Выйти");
        logoutBtn.getStyleClass().addAll("button", "danger");
        logoutBtn.setOnAction(e -> {
            authToken = null;
            currentUser = null;
            if (ordersListStage != null && ordersListStage.isShowing()) {
                ordersListStage.close();
            }
            showAuthScreen(stage);
        });

        HBox statusBar = new HBox(12, userLabel, connLabel, spacer(), showOrdersListBtn, toggleLogBtn, logoutBtn);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        // В методе showMainScreen, при создании вкладок, добавьте:

        TabPane tabs = new TabPane();
        tabs.getTabs().add(tab("📊 Панель", buildDashboardTab()));
        tabs.getTabs().add(tab("📋 Заказы", buildOrdersManagementTab()));
        tabs.getTabs().add(tab("➕ Создать заказ", buildCreateOrderTab()));  // ← НОВАЯ ВКЛАДКА
        if (isAdmin) {
            tabs.getTabs().add(tab("🏭 Поставщики", buildSuppliersManagementTab()));
            tabs.getTabs().add(tab("✏️ Редактор заказов", buildOrdersEditTab()));
        }
        tabs.getTabs().add(tab("❓ Справка", buildHelpTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox logCard = labeledCard("Служебный журнал", globalLogArea);
        logCard.setManaged(false);
        logCard.setVisible(false);
        toggleLogBtn.setText("Показать журнал");

        VBox main = new VBox(0, tabs, new Separator(), logCard, statusBar);
        main.getStyleClass().addAll("app-shell");
        main.setPadding(new Insets(14));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        toggleLogBtn.setOnAction(e -> {
            boolean show = !logCard.isManaged();
            logCard.setManaged(show);
            logCard.setVisible(show);
            toggleLogBtn.setText(show ? "Скрыть журнал" : "Показать журнал");
        });

        Scene scene = new Scene(main, 1280, 900);
        attachStyles(scene);
        stage.setScene(scene);
        stage.setTitle("Supply Contract Risk System — Workspace");
    }

    private void showOrdersListWindow() {
        if (ordersListStage != null && ordersListStage.isShowing()) {
            ordersListStage.toFront();
            return;
        }

        ordersListStage = new Stage();
        ordersListStage.initModality(Modality.NONE);
        ordersListStage.initOwner(mainStage);
        ordersListStage.setTitle("Список всех заказов");

        TableView<OrderRow> table = new TableView<>();
        setupOrdersTable(table);

        Button refreshBtn = new Button("Обновить");
        refreshBtn.getStyleClass().addAll("button", "primary");
        refreshBtn.setOnAction(e -> loadAllOrdersIntoTable(table));

        Button closeBtn = new Button("Закрыть");
        closeBtn.getStyleClass().addAll("button", "ghost");
        closeBtn.setOnAction(e -> ordersListStage.close());

        HBox buttons = new HBox(10, refreshBtn, closeBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10,
                new Label("Полный список заказов с расчетом риска"),
                table,
                buttons
        );
        root.setPadding(new Insets(15));
        VBox.setVgrow(table, Priority.ALWAYS);

        Scene scene = new Scene(root, 1200, 700);
        attachStyles(scene);
        ordersListStage.setScene(scene);
        ordersListStage.show();

        loadAllOrdersIntoTable(table);
    }

    private void setupOrdersTable(TableView<OrderRow> table) {
        table.getColumns().clear();

        TableColumn<OrderRow, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().id()));

        TableColumn<OrderRow, String> colNum = new TableColumn<>("Номер");
        colNum.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().number()));

        TableColumn<OrderRow, String> colSup = new TableColumn<>("Поставщик");
        colSup.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().supplier()));

        TableColumn<OrderRow, String> colDue = new TableColumn<>("Срок");
        colDue.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().dueDate()));

        TableColumn<OrderRow, Long> colQty = new TableColumn<>("Объём");
        colQty.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().quantityUnits()));

        TableColumn<OrderRow, Double> colAmt = new TableColumn<>("Сумма");
        colAmt.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().amount()));

        TableColumn<OrderRow, String> colStage = new TableColumn<>("Этап");
        colStage.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().stageLabel()));

        TableColumn<OrderRow, String> colStatus = new TableColumn<>("Статус");
        colStatus.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().shipmentStatus()));

        TableColumn<OrderRow, Double> colRisk = new TableColumn<>("Риск");
        colRisk.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().riskScore()));

        TableColumn<OrderRow, String> colLvl = new TableColumn<>("Уровень");
        colLvl.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(levelRu(c.getValue().riskLevel())));

        table.getColumns().addAll(colId, colNum, colSup, colDue, colQty, colAmt, colStage, colStatus, colRisk, colLvl);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Подсветка высокого риска
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(OrderRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("risk-row-high");
                if (empty || row == null) return;
                if ("HIGH".equalsIgnoreCase(row.riskLevel())) {
                    getStyleClass().add("risk-row-high");
                }
            }
        });
    }

    private void loadAllOrdersIntoTable(TableView<OrderRow> table) {
        sendAndUpdateOrders(table, CommandType.GET_ORDERS, Map.of());
    }

    private void sendAndUpdateOrders(TableView<OrderRow> table, CommandType type, Map<String, Object> payload) {
        if (authToken == null || authToken.isBlank()) {
            if (globalLogArea != null) globalLogArea.appendText("Нет авторизации.\n");
            return;
        }
        ApiRequest req = new ApiRequest();
        req.setCommandType(type);
        req.setPayload(payload);
        req.setAuthToken(authToken);
        if (globalLogArea != null) globalLogArea.appendText("→ [" + type + "] отправка...\n");
        client.send(req).thenAccept(resp -> Platform.runLater(() -> {
            if (globalLogArea != null) renderResponse(type, resp, globalLogArea);
            if (!resp.isSuccess()) return;
            List<OrderRow> rows = rowsFromOrdersData(resp.getData().get("orders"));
            table.setItems(FXCollections.observableArrayList(rows));
        }));
    }

    private VBox buildDashboardTab() {
        // Создаем области для отображения статистики
        Label contractsCount = new Label("Загрузка...");
        Label shipmentsCount = new Label("Загрузка...");
        Label delayedCount = new Label("Загрузка...");
        Label highRiskCount = new Label("Загрузка...");
        Label mediumRiskCount = new Label("Загрузка...");
        Label lowRiskCount = new Label("Загрузка...");

        // Стилизуем
        contractsCount.getStyleClass().add("stat-value");
        shipmentsCount.getStyleClass().add("stat-value");
        delayedCount.getStyleClass().add("stat-value");

        // Создаем карточки со статистикой
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(20);
        statsGrid.setVgap(15);
        statsGrid.setAlignment(Pos.CENTER);

        // Ряд 1: Основные показатели
        VBox contractsCard = createStatCard("📋 Всего заказов", contractsCount, "#2c5f8a");
        VBox shipmentsCard = createStatCard("🚚 Всего поставок", shipmentsCount, "#28a745");
        VBox delayedCard = createStatCard("⚠️ Просроченные поставки", delayedCount, "#dc3545");

        statsGrid.add(contractsCard, 0, 0);
        statsGrid.add(shipmentsCard, 1, 0);
        statsGrid.add(delayedCard, 2, 0);

        // Ряд 2: Распределение рисков
        Label riskTitle = new Label("Распределение заказов по уровням риска");
        riskTitle.getStyleClass().add("stat-title");

        HBox riskBox = new HBox(15);
        VBox highCard = createRiskCard("🔴 Высокий риск", highRiskCount, "#fee2e2", "#dc3545");
        VBox mediumCard = createRiskCard("🟡 Средний риск", mediumRiskCount, "#fff3cd", "#ffc107");
        VBox lowCard = createRiskCard("🟢 Низкий риск", lowRiskCount, "#d4edda", "#28a745");
        riskBox.getChildren().addAll(highCard, mediumCard, lowCard);
        riskBox.setAlignment(Pos.CENTER);

        // Кнопка обновления
        Button refreshBtn = new Button("🔄 Обновить статистику");
        refreshBtn.getStyleClass().addAll("button", "primary");
        refreshBtn.setOnAction(e -> updateDashboardStats(
                contractsCount, shipmentsCount, delayedCount,
                highRiskCount, mediumRiskCount, lowRiskCount
        ));



        // Останавливаем обновление при закрытии вкладки (опционально)
        refreshBtn.setOnAction(e -> updateDashboardStats(
                contractsCount, shipmentsCount, delayedCount,
                highRiskCount, mediumRiskCount, lowRiskCount
        ));

        VBox content = new VBox(20,
                new Label("📊 Дашборд системы управления рисками"),
                new Separator(),
                statsGrid,
                new Separator(),
                riskTitle,
                riskBox,
                new Separator(),
                refreshBtn,
                new Label("🔄 Данные обновляются автоматически каждые 30 секунд")
        );
        content.setAlignment(Pos.TOP_CENTER);

        // Первоначальная загрузка
        Platform.runLater(() -> updateDashboardStats(
                contractsCount, shipmentsCount, delayedCount,
                highRiskCount, mediumRiskCount, lowRiskCount
        ));

        return wrapTabContent(labeledCard("Статистика и мониторинг", content));
    }

    private VBox createStatCard(String title, Label valueLabel, String color) {
        VBox card = new VBox(5);
        card.getStyleClass().add("stat-card");
        card.setPadding(new Insets(15));
        card.setAlignment(Pos.CENTER);
        card.setMinWidth(180);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");

        valueLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private VBox createRiskCard(String title, Label valueLabel, String bgColor, String textColor) {
        VBox card = new VBox(5);
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 8px;");
        card.setPadding(new Insets(15));
        card.setAlignment(Pos.CENTER);
        card.setMinWidth(150);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");

        valueLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private void updateDashboardStats(Label contractsCount, Label shipmentsCount, Label delayedCount,
                                      Label highRiskCount, Label mediumRiskCount, Label lowRiskCount) {
        if (authToken == null || authToken.isBlank()) {
            return;
        }

        ApiRequest request = new ApiRequest();
        request.setCommandType(CommandType.GET_DASHBOARD);
        request.setAuthToken(authToken);
        request.setPayload(Map.of());

        client.send(request).thenAccept(response -> Platform.runLater(() -> {
            if (response.isSuccess()) {
                Map<String, Object> data = response.getData();

                // Обновляем основные показатели
                contractsCount.setText(String.valueOf(data.getOrDefault("contracts", 0)));
                shipmentsCount.setText(String.valueOf(data.getOrDefault("shipments", 0)));
                delayedCount.setText(String.valueOf(data.getOrDefault("delayed", 0)));

                // Если сервер возвращает распределение по рискам
                if (data.containsKey("highRisk")) {
                    highRiskCount.setText(String.valueOf(data.get("highRisk")));
                    mediumRiskCount.setText(String.valueOf(data.get("mediumRisk")));
                    lowRiskCount.setText(String.valueOf(data.get("lowRisk")));
                }
            } else {
                contractsCount.setText("Ошибка");
                shipmentsCount.setText("Ошибка");
                delayedCount.setText("Ошибка");
            }
        }));
    }

    private VBox buildOrdersManagementTab() {
        ordersTable = new TableView<>();
        setupOrdersTable(ordersTable);


        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("PLANNED", "IN_TRANSIT", "DELIVERED", "CANCELLED", "OVERDUE", "DELAYED");
        statusCombo.setValue("IN_TRANSIT");

        TextField orderIdField = new TextField();
        orderIdField.setPromptText("ID заказа");

        Button updateStatusBtn = new Button("Изменить статус");
        updateStatusBtn.getStyleClass().addAll("button", "primary");
        updateStatusBtn.setOnAction(e -> {
            // Пробуем получить ID из текстового поля
            String idText = orderIdField.getText().trim();
            if (!idText.isEmpty()) {
                try {
                    long contractId = Long.parseLong(idText);
                    sendUpdateContractStatus(contractId, statusCombo.getValue());
                    return;
                } catch (NumberFormatException ex) {
                    // ignore
                }
            }

            // Если не ввели ID, берем выбранный из таблицы
            OrderRow selected = ordersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                globalLogArea.appendText("❌ Выберите заказ в таблице или введите ID\n");
                return;
            }
            sendUpdateContractStatus(selected.id(), statusCombo.getValue());
        });

        Button refreshBtn = new Button("Обновить список");
        refreshBtn.getStyleClass().addAll("button", "ghost");
        refreshBtn.setOnAction(e -> loadAllOrdersIntoTable(ordersTable));

        Button calcRiskBtn = new Button("Пересчитать риск");
        calcRiskBtn.getStyleClass().addAll("button", "ghost");
        calcRiskBtn.setOnAction(e -> {
            OrderRow selected = ordersTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                globalLogArea.appendText("❌ Выберите заказ в таблице\n");
                return;
            }
            send(CommandType.CALCULATE_CONTRACT_RISK, Map.of("contractId", selected.id()));
        });

        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(10);
        controls.add(new Label("ID заказа:"), 0, 0);
        controls.add(orderIdField, 1, 0);
        controls.add(new Label("Новый статус:"), 0, 1);
        controls.add(statusCombo, 1, 1);
        controls.add(updateStatusBtn, 2, 1);
        controls.add(refreshBtn, 0, 2);
        controls.add(calcRiskBtn, 1, 2);

        VBox.setVgrow(ordersTable, Priority.ALWAYS);

        return wrapTabContent(labeledCard("Управление заказами",
                new VBox(10, controls, new Separator(), ordersTable)));
    }

    private VBox buildSuppliersManagementTab() {
        TableView<SupplierRow> supplierTable = new TableView<>();

        TableColumn<SupplierRow, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().id()));
        TableColumn<SupplierRow, String> colName = new TableColumn<>("Название");
        colName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name()));
        TableColumn<SupplierRow, Double> colScore = new TableColumn<>("Рейтинг");
        colScore.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().score()));

        supplierTable.getColumns().addAll(colId, colName, colScore);

        TextField nameField = new TextField();
        nameField.setPromptText("Название поставщика");
        TextField scoreField = new TextField();
        scoreField.setPromptText("Рейтинг (0-100)");

        Button addBtn = new Button("Добавить поставщика");
        addBtn.getStyleClass().addAll("button", "primary");
        addBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                globalLogArea.appendText("❌ Введите название поставщика\n");
                return;
            }

            Double score;
            try {
                score = Double.parseDouble(scoreField.getText().trim());
                if (score < 0 || score > 100) {
                    globalLogArea.appendText("❌ Рейтинг должен быть от 0 до 100\n");
                    return;
                }
            } catch (NumberFormatException ex) {
                globalLogArea.appendText("❌ Некорректный рейтинг\n");
                return;
            }

            // Очищаем поля
            nameField.clear();
            scoreField.clear();

            // Отправляем запрос на создание поставщика
            sendCreateSupplier(name, score, supplierTable);
        });

        Button refreshBtn = new Button("Обновить список");
        refreshBtn.getStyleClass().addAll("button", "ghost");
        refreshBtn.setOnAction(e -> loadSuppliers(supplierTable));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Название:"), 0, 0);
        form.add(nameField, 1, 0);
        form.add(new Label("Рейтинг:"), 0, 1);
        form.add(scoreField, 1, 1);
        form.add(addBtn, 0, 2);
        form.add(refreshBtn, 1, 2);

        VBox.setVgrow(supplierTable, Priority.ALWAYS);

        // Загружаем поставщиков при открытии вкладки
        Platform.runLater(() -> loadSuppliers(supplierTable));

        return wrapTabContent(labeledCard("Управление поставщиками",
                new VBox(10, form, new Separator(), supplierTable)));
    }

    private void sendCreateSupplier(String name, double score, TableView<SupplierRow> table) {
        if (authToken == null || authToken.isBlank()) {
            globalLogArea.appendText("❌ Нет авторизации\n");
            return;
        }

        ApiRequest request = new ApiRequest();
        request.setCommandType(CommandType.CREATE_SUPPLIER);
        request.setAuthToken(authToken);
        request.setPayload(Map.of("name", name, "score", score));

        globalLogArea.appendText(String.format("→ [CREATE_SUPPLIER] Добавление поставщика: %s (рейтинг: %.1f)\n", name, score));

        client.send(request).thenAccept(response -> Platform.runLater(() -> {
            if (response.isSuccess()) {
                globalLogArea.appendText("✓ [CREATE_SUPPLIER] " + response.getMessage() + "\n");
                // Обновляем таблицу после успешного добавления
                loadSuppliers(table);
                // Показываем уведомление
                showTemporaryMessage("✅ Поставщик \"" + name + "\" добавлен");
            } else {
                globalLogArea.appendText("❌ [CREATE_SUPPLIER] " + response.getMessage() + "\n");
                showTemporaryMessage("❌ Ошибка: " + response.getMessage());
            }
            globalLogArea.appendText("\n");
        }));
    }

    private void loadSuppliers(TableView<SupplierRow> table) {
        if (authToken == null || authToken.isBlank()) {
            globalLogArea.appendText("❌ Нет авторизации для загрузки поставщиков\n");
            return;
        }

        ApiRequest request = new ApiRequest();
        request.setCommandType(CommandType.GET_SUPPLIERS);
        request.setAuthToken(authToken);
        request.setPayload(Map.of());

        globalLogArea.appendText("→ [GET_SUPPLIERS] Загрузка списка поставщиков...\n");

        client.send(request).thenAccept(response -> Platform.runLater(() -> {
            if (response.isSuccess() && response.getData() != null) {
                Object suppliersObj = response.getData().get("suppliers");
                List<SupplierRow> suppliers = new ArrayList<>();

                if (suppliersObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            Long id = ((Number) map.get("id")).longValue();
                            String name = (String) map.get("name");
                            Double score = ((Number) map.get("score")).doubleValue();
                            suppliers.add(new SupplierRow(id, name, score));
                        }
                    }
                }

                table.setItems(FXCollections.observableArrayList(suppliers));
                globalLogArea.appendText(String.format("✓ [GET_SUPPLIERS] Загружено %d поставщиков\n\n", suppliers.size()));
            } else {
                globalLogArea.appendText("❌ [GET_SUPPLIERS] " + response.getMessage() + "\n\n");
                // Для теста показываем демо-данные, если сервер еще не готов
                showDemoSuppliers(table);
            }
        }));
    }

    private void showDemoSuppliers(TableView<SupplierRow> table) {
        List<SupplierRow> demo = List.of(
                new SupplierRow(1L, "БелСнаб", 84.0),
                new SupplierRow(2L, "МинскЛогистик", 72.5),
                new SupplierRow(3L, "GrodnoParts", 66.0)
        );
        table.setItems(FXCollections.observableArrayList(demo));
        globalLogArea.appendText("ℹ️ Загружены демо-данные поставщиков (сервер не вернул список)\n");
    }

    private void showTemporaryMessage(String message) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initOwner(mainStage);
        popup.setTitle("Уведомление");

        VBox root = new VBox(10);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        Label label = new Label(message);
        label.getStyleClass().add("subtitle");

        Button okBtn = new Button("OK");
        okBtn.getStyleClass().addAll("button", "primary");
        okBtn.setOnAction(e -> popup.close());

        root.getChildren().addAll(label, okBtn);

        Scene scene = new Scene(root, 300, 150);
        attachStyles(scene);
        popup.setScene(scene);
        popup.show();

        // Автоматическое закрытие через 2 секунды
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (popup.isShowing()) popup.close();
        }));
        timeline.play();
    }

    // Добавьте этот метод в класс ClientApp
    private VBox buildCreateOrderTab() {
        Label title = new Label("Создание нового заказа");
        title.getStyleClass().add("title");

        // Поля для ввода
        TextField orderNumberField = new TextField();
        orderNumberField.setPromptText("Например: Z-2026-013");

        // Выпадающий список поставщиков
        ComboBox<SupplierItem> supplierCombo = new ComboBox<>();
        supplierCombo.setPromptText("Выберите поставщика");
        supplierCombo.setPrefWidth(300);

        DatePicker dueDatePicker = new DatePicker(LocalDate.now().plusDays(30));
        dueDatePicker.setPromptText("Дата исполнения");

        TextField amountField = new TextField();
        amountField.setPromptText("Сумма контракта (например: 150000)");

        TextField quantityField = new TextField();
        quantityField.setPromptText("Объём в единицах (например: 25000)");

        // Кнопка создания
        Button createBtn = new Button("✅ Создать заказ");
        createBtn.getStyleClass().addAll("button", "primary");
        createBtn.setPrefWidth(200);

        // Область для результата
        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPrefRowCount(8);
        resultArea.setPromptText("Результат создания заказа будет отображаться здесь...");

        // Загружаем список поставщиков при открытии вкладки
        loadSuppliersForCombo(supplierCombo);

        // Обработчик создания заказа
        createBtn.setOnAction(e -> {
            // Валидация
            String orderNumber = orderNumberField.getText().trim();
            if (orderNumber.isEmpty()) {
                showError(resultArea, "Введите номер заказа");
                return;
            }

            SupplierItem selectedSupplier = supplierCombo.getValue();
            if (selectedSupplier == null) {
                showError(resultArea, "Выберите поставщика из списка");
                return;
            }

            LocalDate dueDate = dueDatePicker.getValue();
            if (dueDate == null) {
                showError(resultArea, "Выберите дату исполнения");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountField.getText().trim());
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showError(resultArea, "Введите корректную сумму (положительное число)");
                return;
            }

            long quantity;
            try {
                quantity = Long.parseLong(quantityField.getText().trim());
                if (quantity <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showError(resultArea, "Введите корректный объём (положительное целое число)");
                return;
            }

            // Создаем заказ
            createOrder(orderNumber, selectedSupplier.getId(), dueDate, amount, quantity, resultArea);
        });

        // Кнопка обновления списка поставщиков
        Button refreshSuppliersBtn = new Button("🔄 Обновить список поставщиков");
        refreshSuppliersBtn.getStyleClass().addAll("button", "ghost");
        refreshSuppliersBtn.setOnAction(e -> loadSuppliersForCombo(supplierCombo));

        // Форма ввода
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.setPadding(new Insets(20));

        int row = 0;
        form.add(new Label("Номер заказа:*"), 0, row);
        form.add(orderNumberField, 1, row);
        form.add(new Label("(уникальный)"), 2, row);

        row++;
        form.add(new Label("Поставщик:*"), 0, row);
        form.add(supplierCombo, 1, row);
        form.add(refreshSuppliersBtn, 2, row);

        row++;
        form.add(new Label("Дата исполнения:*"), 0, row);
        form.add(dueDatePicker, 1, row);

        row++;
        form.add(new Label("Сумма контракта:*"), 0, row);
        form.add(amountField, 1, row);
        form.add(new Label("(в рублях)"), 2, row);

        row++;
        form.add(new Label("Объём поставки:*"), 0, row);
        form.add(quantityField, 1, row);
        form.add(new Label("(в единицах товара)"), 2, row);

        row++;
        form.add(createBtn, 1, row);

        // Информационная панель
        VBox infoBox = new VBox(8);
        infoBox.getStyleClass().add("info-box");
        infoBox.setPadding(new Insets(15));

        Label infoTitle = new Label("ℹ️ Информация");
        infoTitle.getStyleClass().add("subtitle");

        Label infoText = new Label(
                "• Номер заказа должен быть уникальным\n" +
                        "• Для выбора поставщика необходимо нажать на выпадающий список\n" +
                        "• Если список поставщиков пуст - нажмите 'Обновить список поставщиков'\n" +
                        "• Дата исполнения влияет на расчёт риска\n" +
                        "• После создания заказа он появится в списке заказов"
        );
        infoText.setWrapText(true);
        infoBox.getChildren().addAll(infoTitle, infoText);

        VBox content = new VBox(20, title, form, new Separator(), resultArea, infoBox);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        return wrapTabContent(labeledCard("Создание заказа", content));
    }

    // Класс для хранения информации о поставщике в ComboBox
    private static class SupplierItem {
        private final Long id;
        private final String name;
        private final double score;

        public SupplierItem(Long id, String name, double score) {
            this.id = id;
            this.name = name;
            this.score = score;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public double getScore() { return score; }

        @Override
        public String toString() {
            return String.format("%s (рейтинг: %.1f)", name, score);
        }
    }

    // Загрузка поставщиков для выпадающего списка
    private void loadSuppliersForCombo(ComboBox<SupplierItem> combo) {
        if (authToken == null || authToken.isBlank()) {
            combo.setItems(FXCollections.observableArrayList());
            combo.setPromptText("Нет авторизации");
            return;
        }

        combo.setPromptText("Загрузка поставщиков...");

        ApiRequest request = new ApiRequest();
        request.setCommandType(CommandType.GET_SUPPLIERS);
        request.setAuthToken(authToken);
        request.setPayload(Map.of());

        client.send(request).thenAccept(response -> Platform.runLater(() -> {
            if (response.isSuccess() && response.getData() != null) {
                Object suppliersObj = response.getData().get("suppliers");
                List<SupplierItem> suppliers = new ArrayList<>();

                if (suppliersObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            try {
                                Long id = ((Number) map.get("id")).longValue();
                                String name = (String) map.get("name");
                                Double score = ((Number) map.get("score")).doubleValue();
                                suppliers.add(new SupplierItem(id, name, score));
                            } catch (Exception e) {
                                System.err.println("Error parsing supplier: " + e.getMessage());
                            }
                        }
                    }
                }

                combo.setItems(FXCollections.observableArrayList(suppliers));
                if (suppliers.isEmpty()) {
                    combo.setPromptText("Нет поставщиков");
                    globalLogArea.appendText("⚠️ Список поставщиков пуст. Добавьте поставщиков в соответствующей вкладке.\n");
                } else {
                    combo.setPromptText("Выберите поставщика");
                    globalLogArea.appendText(String.format("✓ Загружено %d поставщиков\n", suppliers.size()));
                }
            } else {
                combo.setPromptText("Ошибка загрузки");
                combo.setItems(FXCollections.observableArrayList());
                globalLogArea.appendText("❌ Не удалось загрузить поставщиков: " + response.getMessage() + "\n");
            }
        }));
    }

    // Создание заказа
    private void createOrder(String orderNumber, Long supplierId, LocalDate dueDate,
                             double amount, long quantity, TextArea resultArea) {
        if (authToken == null || authToken.isBlank()) {
            showError(resultArea, "Нет авторизации. Выполните вход заново.");
            return;
        }

        resultArea.setText("⏳ Отправка запроса на создание заказа...\n");

        Map<String, Object> payload = Map.of(
                "number", orderNumber,
                "supplierId", supplierId,
                "dueDate", dueDate.toString(),
                "amount", amount,
                "quantityUnits", quantity
        );

        ApiRequest request = new ApiRequest();
        request.setCommandType(CommandType.CREATE_CONTRACT);
        request.setAuthToken(authToken);
        request.setPayload(payload);

        globalLogArea.appendText(String.format(
                "\n→ [CREATE_CONTRACT] Создание заказа: %s, поставщик ID: %d, сумма: %.2f, объём: %d%n",
                orderNumber, supplierId, amount, quantity
        ));

        client.send(request).thenAccept(response -> Platform.runLater(() -> {
            if (response.isSuccess()) {
                resultArea.setText(String.format(
                        "✅ ЗАКАЗ УСПЕШНО СОЗДАН!\n\n" +
                                "Номер заказа: %s\n" +
                                "ID в системе: %s\n" +
                                "Поставщик ID: %d\n" +
                                "Дата исполнения: %s\n" +
                                "Сумма: %.2f руб.\n" +
                                "Объём: %d ед.\n\n" +
                                "Заказ появится в списке после обновления.",
                        orderNumber,
                        response.getData().getOrDefault("contractId", "?"),
                        supplierId,
                        dueDate,
                        amount,
                        quantity
                ));
                globalLogArea.appendText("✓ [CREATE_CONTRACT] " + response.getMessage() + "\n");


            } else {
                showError(resultArea, "Ошибка: " + response.getMessage());
                globalLogArea.appendText("❌ [CREATE_CONTRACT] " + response.getMessage() + "\n");
            }
            globalLogArea.appendText("\n");
        }));
    }

    private void showError(TextArea area, String message) {
        area.setText("❌ " + message);
    }

    private VBox buildOrdersEditTab() {
        TextField orderIdField = new TextField();
        orderIdField.setPromptText("ID заказа");
        TextField dueDateField = new TextField();
        dueDateField.setPromptText("Новая дата (YYYY-MM-DD)");
        TextField amountField = new TextField();
        amountField.setPromptText("Новая сумма");
        TextField quantityField = new TextField();
        quantityField.setPromptText("Новый объём");

        Button updateBtn = new Button("Обновить заказ");
        updateBtn.getStyleClass().addAll("button", "primary");
        updateBtn.setOnAction(e -> {
            Long id = parseLong(orderIdField.getText(), "ID заказа", globalLogArea);
            if (id == null) return;
            Map<String, Object> updates = new HashMap<>();
            if (!dueDateField.getText().isBlank()) updates.put("dueDate", dueDateField.getText());
            if (!amountField.getText().isBlank()) {
                Double amt = parseDouble(amountField.getText(), "сумма", globalLogArea);
                if (amt != null) updates.put("amount", amt);
            }
            if (!quantityField.getText().isBlank()) {
                Long qty = parseLong(quantityField.getText(), "объём", globalLogArea);
                if (qty != null) updates.put("quantityUnits", qty);
            }
            updates.put("contractId", id);
            send(CommandType.UPDATE_CONTRACT, updates);
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("ID заказа:"), 0, 0);
        form.add(orderIdField, 1, 0);
        form.add(new Label("Новая дата:"), 0, 1);
        form.add(dueDateField, 1, 1);
        form.add(new Label("Новая сумма:"), 0, 2);
        form.add(amountField, 1, 2);
        form.add(new Label("Новый объём:"), 0, 3);
        form.add(quantityField, 1, 3);
        form.add(updateBtn, 0, 4);

        return wrapTabContent(labeledCard("Редактирование заказов (ADMIN)", form));
    }



    private void sendUpdateContractStatus(long contractId, String status) {
        if (authToken == null || authToken.isBlank()) {
            globalLogArea.appendText("❌ Нет авторизации\n");
            return;
        }

        Map<String, Object> payload = Map.of(
                "contractId", contractId,
                "status", status
        );

        send(CommandType.UPDATE_CONTRACT_STATUS, payload);
    }

    private void send(CommandType type, Map<String, Object> payload) {
        if (authToken == null || authToken.isBlank()) {
            globalLogArea.appendText("Нет авторизации. Выполните вход заново.\n");
            return;
        }
        ApiRequest request = new ApiRequest();
        request.setCommandType(type);
        request.setAuthToken(authToken);
        request.setPayload(payload);
        globalLogArea.appendText("→ [" + type + "] отправка...\n");
        client.send(request).thenAccept(response -> Platform.runLater(() -> {
            renderResponse(type, response, globalLogArea);
            // После обновления статуса - обновляем таблицу заказов
            if (type == CommandType.UPDATE_CONTRACT_STATUS && ordersTable != null) {
                loadAllOrdersIntoTable(ordersTable);
            }
        }));
    }

    private void renderResponse(CommandType type, ApiResponse response, TextArea output) {
        output.appendText("← [" + type + "] " + (response.isSuccess() ? "OK" : "ОШИБКА") + ": " + response.getMessage() + "\n");
        String body = formatResponseHumanReadable(type, response);
        if (!body.isBlank()) {
            output.appendText(body + "\n");
        }
        output.appendText("\n");
    }

    private String formatResponseHumanReadable(CommandType type, ApiResponse response) {
        Map<String, Object> d = response.getData();
        if (d == null || d.isEmpty() || !response.isSuccess()) return "";

        return switch (type) {
            case GET_DASHBOARD -> {
                Long c = toLongFlexible(d.get("contracts"));
                Long s = toLongFlexible(d.get("shipments"));
                Long del = toLongFlexible(d.get("delayed"));
                yield String.format("""
                    Заказов (контрактов): %d
                    Поставок всего: %d
                    С задержкой (DELAYED): %d""",
                        c != null ? c : 0L, s != null ? s : 0L, del != null ? del : 0L);
            }
            case GET_ORDERS -> formatOrdersHuman(d.get("orders"));
            case UPDATE_CONTRACT_STATUS -> {
                Long contractId = toLongFlexible(d.get("contractId"));
                String oldStatus = stringVal(d.get("oldStatus"));
                String newStatus = stringVal(d.get("newStatus"));
                yield String.format("Заказ ID %d: статус изменён с '%s' на '%s'",
                        contractId != null ? contractId : 0, oldStatus, newStatus);
            }
            case CALCULATE_CONTRACT_RISK -> {
                Long id = toLongFlexible(d.get("contractId"));
                Double score = toDoubleFlexible(d.get("riskScore"));
                String summary = stringVal(d.get("summary"));
                yield String.format("Заказ ID %d: риск %.1f/100\n%s", id != null ? id : 0, score != null ? score : 0, summary);
            }
            default -> "";
        };
    }

    private String formatOrdersHuman(Object ordersRaw) {
        if (!(ordersRaw instanceof List<?> list) || list.isEmpty()) return "Заказы не найдены.";
        StringBuilder sb = new StringBuilder();
        sb.append("Список заказов (итого ").append(list.size()).append("):\n\n");
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) m;
            String num = stringVal(row.get("number"));
            String sup = stringVal(row.get("supplier"));
            String due = stringVal(row.get("dueDate"));
            Double r = toDoubleFlexible(row.get("riskScore"));
            sb.append("- № ").append(num).append(": ").append(sup).append(", срок ").append(due);
            if (r != null) sb.append(" | Риск ").append(round1(r));
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<OrderRow> rowsFromOrdersData(Object raw) {
        List<OrderRow> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) m;
            Long id = toLongFlexible(row.get("id"));
            if (id == null) continue;
            Double amt = toDoubleFlexible(row.get("amount"));
            double amount = amt != null ? amt : 0;
            Long qty = toLongFlexible(row.get("quantityUnits"));
            long qtyL = qty != null ? qty : 0;
            Double r = toDoubleFlexible(row.get("riskScore"));
            double risk = r != null ? r : 0;
            String status = stringVal(row.getOrDefault("shipmentStatus", "UNKNOWN"));
            out.add(new OrderRow(
                    id,
                    stringVal(row.get("number")),
                    stringVal(row.get("supplier")),
                    stringVal(row.get("dueDate")),
                    qtyL,
                    amount,
                    stringVal(row.getOrDefault("stageLabel", row.get("stage"))),
                    risk,
                    stringVal(row.get("riskLevel")),
                    stringVal(row.get("riskSummary")),
                    status
            ));
        }
        return out;
    }

    private record OrderRow(Long id, String number, String supplier, String dueDate,
                            long quantityUnits, double amount, String stageLabel,
                            double riskScore, String riskLevel, String riskSummary, String shipmentStatus) {}

    private record SupplierRow(Long id, String name, double score) {}

    private VBox buildHelpTab() {
        TextArea help = new TextArea("""
            Supply Contract Risk System - Руководство пользователя
            
            ВОЗМОЖНОСТИ:
            
            1. Просмотр заказов:
               - Кнопка "Список заказов" открывает отдельное окно со всеми заказами
               - В таблице отображается информация о заказе, поставщике, статусе и риске
            
            2. Управление заказами (все пользователи):
               - Изменение статуса поставки (PLANNED, IN_TRANSIT, DELIVERED, CANCELLED, OVERDUE)
               - Пересчет риска по заказу
            
            3. Административные функции (только ADMIN):
               - Добавление и редактирование поставщиков
               - Редактирование параметров заказа (дата, сумма, объём)
            
            4. Оценка риска:
               - Автоматический расчет на основе объёма, срока и этапа выполнения
               - Высокий риск подсвечивается красным в таблице
            
            КОМАНДЫ:
            - GET_ORDERS - получить список заказов
            - UPDATE_SHIPMENT_STATUS - изменить статус поставки
            - CALCULATE_CONTRACT_RISK - пересчитать риск
            - CREATE_SUPPLIER - добавить поставщика (ADMIN)
            - UPDATE_CONTRACT - обновить заказ (ADMIN)
            
            Подробная информация отображается в служебном журнале.
            """);
        help.setEditable(false);
        help.setWrapText(true);
        return wrapTabContent(labeledCard("Справка", help));
    }

    // Вспомогательные методы
    private static Long toLongFlexible(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); }
        catch (Exception e) { return null; }
    }

    private static Double toDoubleFlexible(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); }
        catch (Exception e) { return null; }
    }

    private static String stringVal(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String round1(double v) { return String.format(Locale.US, "%.1f", v); }
    private static String levelRu(String code) {
        if (code == null) return "";
        return switch (code.toUpperCase()) {
            case "HIGH" -> "высокий";
            case "MEDIUM" -> "средний";
            case "LOW" -> "низкий";
            default -> code;
        };
    }

    private Long parseLong(String s, String field, TextArea out) {
        try {
            if (s == null || s.isBlank()) throw new NumberFormatException();
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            if (out != null) out.appendText("Некорректное поле \"" + field + "\": ожидается целое число.\n");
            return null;
        }
    }

    private Double parseDouble(String s, String field, TextArea out) {
        try {
            if (s == null || s.isBlank()) throw new NumberFormatException();
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            if (out != null) out.appendText("Некорректное поле \"" + field + "\": ожидается число.\n");
            return null;
        }
    }

    private void updateStatusLabels(Label userLabel, Label connLabel) {
        String host = System.getProperty("app.host", "localhost");
        String port = System.getProperty("app.port", "9090");
        userLabel.setText("Пользователь: " + (currentUser == null ? "—" : currentUser) + (isAdmin ? " (ADMIN)" : " (EMPLOYEE)"));
        connLabel.setText("Сервер: " + host + ":" + port);
    }

    private void showInline(Label label, boolean ok, String text) {
        label.getStyleClass().removeAll("error-text", "success-text");
        label.getStyleClass().add(ok ? "success-text" : "error-text");
        label.setText(text == null ? "" : text);
    }

    private Tab tab(String title, VBox content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private VBox wrapTabContent(VBox card) {
        VBox root = new VBox(12, card);
        root.setPadding(new Insets(12));
        root.setFillWidth(true);
        return root;
    }

    private VBox labeledCard(String title, javafx.scene.Node content) {
        Label h = new Label(title);
        h.getStyleClass().add("title");
        h.setStyle("-fx-font-size: 16px;");
        VBox box = new VBox(10, h, content);
        box.getStyleClass().add("card");
        return box;
    }

    private HBox spacer() {
        HBox box = new HBox();
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void attachStyles(Scene scene) {
        String css = getClass().getResource("/client/app.css") != null
                ? getClass().getResource("/client/app.css").toExternalForm()
                : null;
        if (css != null) scene.getStylesheets().add(css);
    }

    @Override
    public void stop() { client.close(); }

    public static void main(String[] args) {
        AppConfig.apply(args);
        launch(args);
    }
}
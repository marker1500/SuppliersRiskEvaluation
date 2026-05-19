package by.bsuir.coursework.server;

import by.bsuir.coursework.common.AppConfig;
import by.bsuir.coursework.server.infra.DataSeeder;
import by.bsuir.coursework.server.transport.TcpServer;
import by.bsuir.coursework.server.service.AuthService;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        // Загрузка конфигурации
        AppConfig.apply(args);

        // Подготовка БД с тестовыми данными
        prepareDatabase();

        // Запуск сервера
        int port = Integer.parseInt(System.getProperty("app.port", "9090"));
        int threads = Integer.parseInt(System.getProperty("app.threads", "16"));

        System.out.println("=== Supply Contract Risk System Server ===");
        System.out.println("Starting server on port " + port);
        System.out.println("Using " + threads + " threads");
        System.out.println("=========================================");

        new TcpServer(port, threads).start();
    }

    private static void prepareDatabase() {
        // Проверяем, нужно ли сбросить пользователей
        boolean resetUsers = Boolean.parseBoolean(System.getProperty("app.users.reset", "false"));
        if (resetUsers) {
            String adminUser = System.getProperty("app.admin.user", "admin");
            String adminPass = System.getProperty("app.admin.password", "admin123");
            new AuthService().resetUsersToSingleAdmin(adminUser, adminPass);
            System.out.println("Users reset: only admin remains (" + adminUser + ")");
        } else {
            // Создаем администратора, если его нет
            try {
                new AuthService().ensureSingleAdmin(
                        System.getProperty("app.admin.user", "admin"),
                        System.getProperty("app.admin.password", "admin123")
                );
            } catch (Exception e) {
                // Already exists, ignore
            }
        }


        DataSeeder.seedIfEmpty();
    }
}
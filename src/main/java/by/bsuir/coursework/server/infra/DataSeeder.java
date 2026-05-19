package by.bsuir.coursework.server.infra;

import by.bsuir.coursework.common.Role;
import by.bsuir.coursework.server.domain.ContractEntity;
import by.bsuir.coursework.server.domain.ShipmentEntity;
import by.bsuir.coursework.server.domain.SupplierEntity;
import by.bsuir.coursework.server.domain.UserEntity;
import jakarta.persistence.EntityManager;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public final class DataSeeder {

    private DataSeeder() {
    }

    public static void seedIfEmpty() {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            // Проверяем, есть ли уже данные
            long existingContracts = em.createQuery("select count(c) from ContractEntity c", Long.class).getSingleResult();
            if (existingContracts > 0) {
                System.out.println("Database already contains data, skipping seed.");
                return;
            }

            System.out.println("Seeding database with initial data...");
            em.getTransaction().begin();

            // 1. Создаем тестового администратора (если нет пользователей)
            long users = em.createQuery("select count(u) from UserEntity u", Long.class).getSingleResult();
            if (users == 0) {
                String adminHash = hashPassword("admin123");
                UserEntity admin = new UserEntity("admin", adminHash, Set.of(Role.ADMIN));
                em.persist(admin);

                String employeeHash = hashPassword("user123");
                UserEntity employee = new UserEntity("user", employeeHash, Set.of(Role.EMPLOYEE));
                em.persist(employee);

                System.out.println("Created admin (admin/admin123) and employee (user/user123)");
            }

            // 2. Создаем поставщиков
            SupplierEntity s1 = new SupplierEntity("БелСнаб", 84.0);
            SupplierEntity s2 = new SupplierEntity("МинскЛогистик", 72.5);
            SupplierEntity s3 = new SupplierEntity("GrodnoParts", 66.0);
            SupplierEntity s4 = new SupplierEntity("ВитебскТорг", 91.0);
            SupplierEntity s5 = new SupplierEntity("ГомельСбыт", 58.5);
            SupplierEntity s6 = new SupplierEntity("БрестОпт", 79.0);

            em.persist(s1);
            em.persist(s2);
            em.persist(s3);
            em.persist(s4);
            em.persist(s5);
            em.persist(s6);

            // 3. Создаем заказы (контракты) - используем ДРУГОЕ имя переменной
            LocalDate today = LocalDate.now();

            List<ContractEntity> contractList = List.of(
                    // Просроченные заказы (высокий риск)
                    new ContractEntity("Z-2026-001", s1, today.minusDays(5), 120_000.0, 100_000),
                    new ContractEntity("Z-2026-002", s2, today.minusDays(12), 95_000.0, 50_000),
                    new ContractEntity("Z-2026-003", s3, today.minusDays(20), 210_000.0, 150_000),

                    // Срочные заказы (1-3 дня, высокий риск)
                    new ContractEntity("Z-2026-004", s1, today.plusDays(2), 45_000.0, 8_500),
                    new ContractEntity("Z-2026-005", s4, today.plusDays(1), 67_000.0, 12_000),
                    new ContractEntity("Z-2026-006", s5, today.plusDays(3), 23_000.0, 5_000),

                    // Заказы со средним сроком
                    new ContractEntity("Z-2026-007", s2, today.plusDays(15), 78_000.0, 15_000),
                    new ContractEntity("Z-2026-008", s6, today.plusDays(25), 156_000.0, 30_000),
                    new ContractEntity("Z-2026-009", s3, today.plusDays(30), 34_000.0, 7_000),

                    // Долгосрочные заказы (низкий риск)
                    new ContractEntity("Z-2026-010", s4, today.plusDays(60), 89_000.0, 10_000),
                    new ContractEntity("Z-2026-011", s1, today.plusDays(90), 250_000.0, 45_000),
                    new ContractEntity("Z-2026-012", s5, today.plusDays(120), 112_000.0, 20_000)
            );

            for (ContractEntity contract : contractList) {
                em.persist(contract);
            }

            // 4. Создаем поставки (shipments) для каждого заказа
            // Заказ 1 (просрочен) - 2 поставки, одна просрочена
            em.persist(new ShipmentEntity(contractList.get(0), "DELAYED", today.minusDays(10), null));
            em.persist(new ShipmentEntity(contractList.get(0), "PLANNED", today.plusDays(5), null));

            // Заказ 2 (просрочен) - 1 просроченная поставка
            em.persist(new ShipmentEntity(contractList.get(1), "DELAYED", today.minusDays(15), null));
            em.persist(new ShipmentEntity(contractList.get(1), "IN_TRANSIT", today.minusDays(5), null));

            // Заказ 3 (давно просрочен) - 2 просроченные
            em.persist(new ShipmentEntity(contractList.get(2), "DELAYED", today.minusDays(25), null));
            em.persist(new ShipmentEntity(contractList.get(2), "DELAYED", today.minusDays(18), null));
            em.persist(new ShipmentEntity(contractList.get(2), "PLANNED", today.plusDays(10), null));

            // Заказ 4 (срочный, 2 дня) - только планируется
            em.persist(new ShipmentEntity(contractList.get(3), "PLANNED", today.plusDays(1), null));

            // Заказ 5 (срочный, 1 день) - в пути
            em.persist(new ShipmentEntity(contractList.get(4), "IN_TRANSIT", today, null));

            // Заказ 6 (срочный, 3 дня) - планируется
            em.persist(new ShipmentEntity(contractList.get(5), "PLANNED", today.plusDays(2), null));

            // Заказ 7 (15 дней) - в пути
            em.persist(new ShipmentEntity(contractList.get(6), "IN_TRANSIT", today.plusDays(10), null));
            em.persist(new ShipmentEntity(contractList.get(6), "PLANNED", today.plusDays(20), null));

            // Заказ 8 (25 дней) - в пути
            em.persist(new ShipmentEntity(contractList.get(7), "IN_TRANSIT", today.plusDays(15), null));

            // Заказ 9 (30 дней) - планируется
            em.persist(new ShipmentEntity(contractList.get(8), "PLANNED", today.plusDays(25), null));

            // Заказ 10 (60 дней) - несколько поставок разного статуса
            em.persist(new ShipmentEntity(contractList.get(9), "PLANNED", today.plusDays(30), null));
            em.persist(new ShipmentEntity(contractList.get(9), "PLANNED", today.plusDays(50), null));

            // Заказ 11 (90 дней) - только планируется
            em.persist(new ShipmentEntity(contractList.get(10), "PLANNED", today.plusDays(60), null));

            // Заказ 12 (120 дней) - завершенная поставка
            em.persist(new ShipmentEntity(contractList.get(11), "DELIVERED", today.minusDays(30), today.minusDays(25)));

            em.getTransaction().commit();

            System.out.println("Database seeded successfully!");
            System.out.println("- Suppliers: " + 6);
            System.out.println("- Contracts: " + contractList.size());
            System.out.println("- Shipments: " + 16);
            System.out.println("- Users: admin/admin123, user/user123");

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            System.err.println("Error seeding database: " + e.getMessage());
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }
}
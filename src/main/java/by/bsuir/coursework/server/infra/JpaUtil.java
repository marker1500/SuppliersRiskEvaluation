package by.bsuir.coursework.server.infra;

import by.bsuir.coursework.server.domain.ContractEntity;
import by.bsuir.coursework.server.domain.IncidentEntity;
import by.bsuir.coursework.server.domain.RiskAssessmentEntity;
import by.bsuir.coursework.server.domain.ShipmentEntity;
import by.bsuir.coursework.server.domain.SupplierEntity;
import by.bsuir.coursework.server.domain.UserEntity;
import org.hibernate.cfg.Configuration;

import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;

public final class JpaUtil {
    private static final EntityManagerFactory EMF = buildEmf();

    private JpaUtil() {
    }

    private static EntityManagerFactory buildEmf() {
        String jdbcUrl = System.getProperty(
            "app.db.url",
            "jdbc:postgresql://localhost:5432/supply_db"
        );
        String jdbcUser = System.getProperty("app.db.user", "postgres");
        String jdbcPass = System.getProperty("app.db.password", "1111");

        String driver = jdbcUrl.startsWith("jdbc:postgresql:")
            ? "org.postgresql.Driver"
            : "org.h2.Driver";
        String dialect = jdbcUrl.startsWith("jdbc:postgresql:")
            ? "org.hibernate.dialect.PostgreSQLDialect"
            : "org.hibernate.dialect.H2Dialect";

        Properties props = new Properties();
        props.put("hibernate.connection.url", jdbcUrl);
        props.put("hibernate.connection.username", jdbcUser);
        props.put("hibernate.connection.password", jdbcPass);
        props.put("hibernate.connection.driver_class", driver);
        props.put("hibernate.dialect", dialect);
        props.put("hibernate.hbm2ddl.auto", "update");
        props.put("hibernate.show_sql", "false");

        Configuration cfg = new Configuration();
        cfg.setProperties(props);
        cfg.addAnnotatedClass(UserEntity.class);
        cfg.addAnnotatedClass(SupplierEntity.class);
        cfg.addAnnotatedClass(ContractEntity.class);
        cfg.addAnnotatedClass(ShipmentEntity.class);
        cfg.addAnnotatedClass(RiskAssessmentEntity.class);
        cfg.addAnnotatedClass(IncidentEntity.class);
        return cfg.buildSessionFactory();
    }

    public static EntityManagerFactory emf() {
        return EMF;
    }
}

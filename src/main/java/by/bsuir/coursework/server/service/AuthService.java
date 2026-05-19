package by.bsuir.coursework.server.service;

import by.bsuir.coursework.common.Role;
import by.bsuir.coursework.server.domain.UserEntity;
import by.bsuir.coursework.server.infra.JpaUtil;
import jakarta.persistence.EntityManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private final Map<String, UserEntity> activeTokens = new ConcurrentHashMap<>();

    public record LoginResult(String token, UserEntity user) {
    }

    public void resetUsersToSingleAdmin(String username, String rawPassword) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
                throw new IllegalArgumentException("Логин и пароль обязательны");
            }

            em.getTransaction().begin();
            // order matters: element-collection table first, then base table
            em.createNativeQuery("delete from user_roles").executeUpdate();
            em.createNativeQuery("delete from users").executeUpdate();

            UserEntity admin = new UserEntity(username, hash(rawPassword), Set.of(Role.ADMIN));
            em.persist(admin);
            em.getTransaction().commit();

            activeTokens.clear();
        } finally {
            em.close();
        }
    }

    public UserEntity registerEmployee(String username, String rawPassword) {
        return register(username, rawPassword, Set.of(Role.EMPLOYEE), false);
    }

    public UserEntity ensureSingleAdmin(String username, String rawPassword) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
                throw new IllegalArgumentException("Логин и пароль обязательны");
            }

            UserEntity byUsername = em.createQuery("select u from UserEntity u where u.username = :username", UserEntity.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst()
                .orElse(null);

            long admins = em.createQuery(
                    "select count(u) from UserEntity u join u.roles r where r = :role",
                    Long.class
                )
                .setParameter("role", Role.ADMIN)
                .getSingleResult();

            // already have an admin somewhere -> do nothing
            if (admins > 0) {
                return byUsername;
            }

            em.getTransaction().begin();
            if (byUsername == null) {
                UserEntity admin = new UserEntity(username, hash(rawPassword), Set.of(Role.ADMIN));
                em.persist(admin);
                em.getTransaction().commit();
                return admin;
            }

            // Promote existing user to admin and reset password to known one
            byUsername.getRoles().clear();
            byUsername.getRoles().add(Role.ADMIN);
            setPasswordHash(byUsername, hash(rawPassword));
            em.merge(byUsername);
            em.getTransaction().commit();
            return byUsername;
        } finally {
            em.close();
        }
    }

    private UserEntity register(String username, String rawPassword, Set<Role> roles, boolean enforceSingleAdmin) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
                throw new IllegalArgumentException("Логин и пароль обязательны");
            }
            em.getTransaction().begin();
            boolean exists = em.createQuery("select count(u) from UserEntity u where u.username = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult() > 0;
            if (exists) {
                throw new IllegalArgumentException("Пользователь с таким логином уже существует");
            }
            if (enforceSingleAdmin) {
                long admins = em.createQuery(
                        "select count(u) from UserEntity u join u.roles r where r = :role",
                        Long.class
                    )
                    .setParameter("role", Role.ADMIN)
                    .getSingleResult();
                if (admins > 0) {
                    throw new IllegalArgumentException("Администратор уже создан");
                }
            }
            UserEntity user = new UserEntity(username, hash(rawPassword), roles);
            em.persist(user);
            em.getTransaction().commit();
            return user;
        } finally {
            em.close();
        }
    }

    public Optional<LoginResult> login(String username, String rawPassword) {
        EntityManager em = JpaUtil.emf().createEntityManager();
        try {
            UserEntity user = em.createQuery("select u from UserEntity u where u.username = :username", UserEntity.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst()
                .orElse(null);
            if (user == null || !user.getPasswordHash().equals(hash(rawPassword))) {
                return Optional.empty();
            }
            String token = UUID.randomUUID().toString();
            activeTokens.put(token, user);
            return Optional.of(new LoginResult(token, user));
        } finally {
            em.close();
        }
    }

    public Optional<UserEntity> authenticate(String token) {
        return Optional.ofNullable(activeTokens.get(token));
    }

    public boolean hasAnyRole(UserEntity user, Role... needed) {
        for (Role role : needed) {
            if (user.getRoles().contains(role)) {
                return true;
            }
        }
        return false;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    private void setPasswordHash(UserEntity user, String hash) {
        // keep UserEntity immutable on API level, but allow controlled update here
        try {
            var f = UserEntity.class.getDeclaredField("passwordHash");
            f.setAccessible(true);
            f.set(user, hash);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось обновить пароль администратора", e);
        }
    }
}

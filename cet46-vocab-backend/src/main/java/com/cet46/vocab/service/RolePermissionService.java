package com.cet46.vocab.service;

import com.cet46.vocab.common.PageResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import com.cet46.vocab.config.SecurityRbacProperties;
import com.cet46.vocab.security.GlobalCloudModelPermissions;
import com.cet46.vocab.security.PrivateCloudModelPermissions;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RolePermissionService {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionService.class);
    private static final List<String> MANAGED_ROLES = List.of("ADMIN", "USER");
    private static final List<String> MANAGED_PERMISSIONS = List.of(
            PrivateCloudModelPermissions.CREATE,
            PrivateCloudModelPermissions.EDIT,
            PrivateCloudModelPermissions.DELETE,
            PrivateCloudModelPermissions.TOGGLE,
            GlobalCloudModelPermissions.CREATE,
            GlobalCloudModelPermissions.EDIT,
            GlobalCloudModelPermissions.DELETE
    );

    private final JdbcTemplate jdbcTemplate;
    private final SecurityRbacProperties securityRbacProperties;

    public RolePermissionService(JdbcTemplate jdbcTemplate,
                                 SecurityRbacProperties securityRbacProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityRbacProperties = securityRbacProperties;
    }

    @PostConstruct
    public void init() {
        applyToRuntime(readFromDbOrDefault());
    }

    public List<RolePermissionItem> listRolePermissions() {
        Map<String, List<String>> current = readFromDbOrDefault();
        List<RolePermissionItem> out = new ArrayList<>();
        for (String role : MANAGED_ROLES) {
            out.add(new RolePermissionItem(role, new ArrayList<>(current.getOrDefault(role, List.of()))));
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRolePermissions(Map<String, List<String>> input, Long actorUserId) {
        Map<String, List<String>> normalized = normalizeRolePermissions(input);
        Map<String, List<String>> before = readFromDbOrDefault();

        jdbcTemplate.update("DELETE FROM rbac_role_permission WHERE role_code IN (?, ?)", "ADMIN", "USER");
        for (String role : MANAGED_ROLES) {
            List<String> permissions = normalized.getOrDefault(role, List.of());
            for (String permission : permissions) {
                jdbcTemplate.update(
                        "INSERT INTO rbac_role_permission(role_code, permission_code) VALUES (?, ?)",
                        role,
                        permission
                );
            }
        }

        for (String role : MANAGED_ROLES) {
            List<String> beforeList = before.getOrDefault(role, List.of());
            List<String> afterList = normalized.getOrDefault(role, List.of());
            if (beforeList.equals(afterList)) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO rbac_role_permission_audit(actor_user_id, role_code, before_permissions, after_permissions) VALUES (?, ?, ?, ?)",
                    actorUserId,
                    role,
                    String.join(",", beforeList),
                    String.join(",", afterList)
            );
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    applyToRuntime(normalized);
                }
            });
        } else {
            applyToRuntime(normalized);
        }
    }

    public PageResult<RolePermissionAuditItem> listAuditLogs(Integer page, Integer size) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null ? 10 : Math.min(Math.max(size, 1), 100);
        int offset = (pageNo - 1) * pageSize;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM rbac_role_permission_audit", Long.class);
        List<RolePermissionAuditItem> list = jdbcTemplate.query(
                "SELECT id, actor_user_id, role_code, before_permissions, after_permissions, changed_at " +
                        "FROM rbac_role_permission_audit ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapAudit(rs),
                pageSize,
                offset
        );
        return new PageResult<>(total == null ? 0 : total, pageNo, pageSize, list);
    }

    public void checkAuthorityOrThrow(Set<String> authorities, String required) {
        if (authorities == null || !authorities.contains(required)) {
            throw new AccessDeniedException("forbidden");
        }
    }

    private RolePermissionAuditItem mapAudit(ResultSet rs) throws SQLException {
        RolePermissionAuditItem item = new RolePermissionAuditItem();
        item.setId(rs.getLong("id"));
        item.setActorUserId(rs.getObject("actor_user_id") == null ? null : rs.getLong("actor_user_id"));
        item.setRole(rs.getString("role_code"));
        item.setBeforePermissions(splitPermissions(rs.getString("before_permissions")));
        item.setAfterPermissions(splitPermissions(rs.getString("after_permissions")));
        item.setChangedAt(rs.getTimestamp("changed_at") == null ? null : rs.getTimestamp("changed_at").toLocalDateTime());
        return item;
    }

    private List<String> splitPermissions(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : csv.split(",")) {
            String value = item == null ? "" : item.trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private Map<String, List<String>> normalizeRolePermissions(Map<String, List<String>> input) {
        Map<String, Set<String>> collected = new LinkedHashMap<>();
        for (String role : MANAGED_ROLES) {
            collected.put(role, new LinkedHashSet<>());
        }

        Map<String, List<String>> source = input == null ? Map.of() : input;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }
            String role = entry.getKey().trim().toUpperCase(Locale.ROOT);
            if (!collected.containsKey(role)) {
                throw new IllegalArgumentException("unsupported role: " + role);
            }
            List<String> permissions = entry.getValue();
            if (permissions == null) {
                continue;
            }
            for (String permission : permissions) {
                if (!StringUtils.hasText(permission)) {
                    continue;
                }
                String normalized = permission.trim().toUpperCase(Locale.ROOT);
                if (!MANAGED_PERMISSIONS.contains(normalized)) {
                    throw new IllegalArgumentException("unsupported permission: " + normalized);
                }
                collected.get(role).add(normalized);
            }
        }

        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String role : MANAGED_ROLES) {
            List<String> ordered = new ArrayList<>();
            for (String permission : MANAGED_PERMISSIONS) {
                if (collected.get(role).contains(permission)) {
                    ordered.add(permission);
                }
            }
            out.put(role, ordered);
        }
        return out;
    }

    private Map<String, List<String>> readFromDbOrDefault() {
        Map<String, Set<String>> collected = new LinkedHashMap<>();
        for (String role : MANAGED_ROLES) {
            collected.put(role, new LinkedHashSet<>());
        }

        if (isRbacRolePermissionTableReady()) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT role_code, permission_code FROM rbac_role_permission WHERE role_code IN (?, ?)",
                    "ADMIN",
                    "USER"
            );
            for (Map<String, Object> row : rows) {
                String role = String.valueOf(row.get("role_code")).trim().toUpperCase(Locale.ROOT);
                String permission = String.valueOf(row.get("permission_code")).trim().toUpperCase(Locale.ROOT);
                if (collected.containsKey(role) && MANAGED_PERMISSIONS.contains(permission)) {
                    collected.get(role).add(permission);
                }
            }
        }

        boolean hasDbData = collected.values().stream().anyMatch(set -> !set.isEmpty());
        if (!hasDbData) {
            for (String role : MANAGED_ROLES) {
                Set<String> defaults = securityRbacProperties.resolvePermissions(role);
                for (String permission : MANAGED_PERMISSIONS) {
                    if (defaults.contains(permission)) {
                        collected.get(role).add(permission);
                    }
                }
            }
        }

        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String role : MANAGED_ROLES) {
            List<String> ordered = new ArrayList<>();
            for (String permission : MANAGED_PERMISSIONS) {
                if (collected.get(role).contains(permission)) {
                    ordered.add(permission);
                }
            }
            out.put(role, ordered);
        }
        return out;
    }

    private boolean isRbacRolePermissionTableReady() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class,
                    "rbac_role_permission"
            );
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            log.warn("check rbac_role_permission table failed, fallback to default RBAC permissions", ex);
            return false;
        }
    }
    private void applyToRuntime(Map<String, List<String>> permissions) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (String role : MANAGED_ROLES) {
            copy.put(role, new ArrayList<>(permissions.getOrDefault(role, List.of())));
        }
        securityRbacProperties.setRolePermissions(copy);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RolePermissionItem {
        private String role;
        private List<String> permissions;
    }

    @Data
    public static class RolePermissionAuditItem {
        private Long id;
        private Long actorUserId;
        private String role;
        private List<String> beforePermissions;
        private List<String> afterPermissions;
        private LocalDateTime changedAt;
    }
}


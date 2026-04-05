package com.cet46.vocab.service;

import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.config.SecurityRbacProperties;
import com.cet46.vocab.security.PrivateCloudModelPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SecurityRbacProperties securityRbacProperties;

    private RolePermissionService rolePermissionService;

    @BeforeEach
    void setUp() {
        rolePermissionService = new RolePermissionService(jdbcTemplate, securityRbacProperties);
    }

    @Test
    void listRolePermissionsShouldFallbackToConfigWhenDbEmpty() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?"),
                eq(Integer.class),
                eq("rbac_role_permission")
        )).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), ArgumentMatchers.<Object>any(), ArgumentMatchers.<Object>any()))
                .thenReturn(List.of());
        when(securityRbacProperties.resolvePermissions("ADMIN"))
                .thenReturn(new LinkedHashSet<>(Set.of(
                        PrivateCloudModelPermissions.TOGGLE,
                        PrivateCloudModelPermissions.CREATE
                )));
        when(securityRbacProperties.resolvePermissions("USER"))
                .thenReturn(new LinkedHashSet<>(Set.of(PrivateCloudModelPermissions.EDIT)));

        List<RolePermissionService.RolePermissionItem> result = rolePermissionService.listRolePermissions();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("ADMIN", result.get(0).getRole());
        assertEquals(List.of(
                PrivateCloudModelPermissions.CREATE,
                PrivateCloudModelPermissions.TOGGLE
        ), result.get(0).getPermissions());
        assertEquals("USER", result.get(1).getRole());
        assertEquals(List.of(PrivateCloudModelPermissions.EDIT), result.get(1).getPermissions());
    }

    @Test
    void updateRolePermissionsShouldRejectUnsupportedPermissionBoundary() {
        Map<String, List<String>> badInput = Map.of("USER", List.of("INVALID_PERMISSION"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> rolePermissionService.updateRolePermissions(badInput, 1L)
        );

        assertEquals("unsupported permission: INVALID_PERMISSION", ex.getMessage());
    }

    @Test
    void updateRolePermissionsShouldNotWriteAuditWhenNoChange() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?"),
                eq(Integer.class),
                eq("rbac_role_permission")
        )).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), ArgumentMatchers.<Object>any(), ArgumentMatchers.<Object>any()))
                .thenReturn(List.of(
                        Map.of("role_code", "ADMIN", "permission_code", PrivateCloudModelPermissions.CREATE),
                        Map.of("role_code", "USER", "permission_code", PrivateCloudModelPermissions.EDIT)
                ));

        rolePermissionService.updateRolePermissions(
                Map.of(
                        "ADMIN", List.of(PrivateCloudModelPermissions.CREATE),
                        "USER", List.of(PrivateCloudModelPermissions.EDIT)
                ),
                2L
        );

        verify(jdbcTemplate, never()).update(
                eq("INSERT INTO rbac_role_permission_audit(actor_user_id, role_code, before_permissions, after_permissions) VALUES (?, ?, ?, ?)"),
                any(), any(), any(), any()
        );
    }

    @Test
    void listAuditLogsShouldClampPageAndSizeBoundaries() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM rbac_role_permission_audit", Long.class)).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
                .thenReturn(List.of());

        PageResult<RolePermissionService.RolePermissionAuditItem> result = rolePermissionService.listAuditLogs(0, 1000);

        assertNotNull(result);
        assertEquals(1, result.getPage());
        assertEquals(100, result.getSize());
        verify(jdbcTemplate).query(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq(100),
                eq(0)
        );
    }
}

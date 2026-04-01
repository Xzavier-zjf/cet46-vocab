package com.cet46.vocab.controller;

import com.cet46.vocab.common.GlobalExceptionHandler;
import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.security.JwtAuthFilter;
import com.cet46.vocab.service.RolePermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
@Import({GlobalExceptionHandler.class, AdminUserControllerMockMvcTest.MockSecurityConfig.class})
class AdminUserControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private RolePermissionService rolePermissionService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0, ServletRequest.class), invocation.getArgument(1, ServletResponse.class));
            return null;
        }).when(jwtAuthFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRolePermissions_shouldReturnRolePermissionsForAdmin() throws Exception {
        when(rolePermissionService.listRolePermissions()).thenReturn(List.of(
                new RolePermissionService.RolePermissionItem("ADMIN", List.of("PRIVATE_CLOUD_MODEL_CREATE")),
                new RolePermissionService.RolePermissionItem("USER", List.of())
        ));

        mockMvc.perform(get("/admin/users/role-permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].permissions[0]").value("PRIVATE_CLOUD_MODEL_CREATE"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getRolePermissions_shouldForbidNonAdmin() throws Exception {
        mockMvc.perform(get("/admin/users/role-permissions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRolePermissions_shouldRejectEmptyItemsByRequestValidation() throws Exception {
        String body = """
                {
                  "items": []
                }
                """;

        mockMvc.perform(put("/admin/users/role-permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("items")));

        verifyNoInteractions(rolePermissionService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRolePermissions_shouldReturnBadRequestForUnsupportedRole() throws Exception {
        doThrow(new IllegalArgumentException("unsupported role: GUEST"))
                .when(rolePermissionService).updateRolePermissions(anyMap(), any());

        String body = """
                {
                  "items": [
                    {
                      "role": "GUEST",
                      "permissions": ["PRIVATE_CLOUD_MODEL_CREATE"]
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/admin/users/role-permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("unsupported role: GUEST"));

        verify(rolePermissionService).updateRolePermissions(anyMap(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listRolePermissionAudits_shouldPassBoundaryParameters() throws Exception {
        RolePermissionService.RolePermissionAuditItem auditItem = new RolePermissionService.RolePermissionAuditItem();
        auditItem.setId(10L);
        auditItem.setActorUserId(1L);
        auditItem.setRole("ADMIN");
        auditItem.setBeforePermissions(List.of("PRIVATE_CLOUD_MODEL_CREATE"));
        auditItem.setAfterPermissions(List.of("PRIVATE_CLOUD_MODEL_CREATE", "PRIVATE_CLOUD_MODEL_EDIT"));
        auditItem.setChangedAt(LocalDateTime.of(2026, 4, 1, 10, 0));

        when(rolePermissionService.listAuditLogs(0, 101))
                .thenReturn(new PageResult<>(1, 1, 100, List.of(auditItem)));

        mockMvc.perform(get("/admin/users/role-permissions/audits")
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.list[0].role").value("ADMIN"));

        verify(rolePermissionService).listAuditLogs(0, 101);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MockSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated()
                    );
            return http.build();
        }
    }
}
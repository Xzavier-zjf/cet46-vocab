package com.cet46.vocab.controller;

import com.cet46.vocab.common.GlobalExceptionHandler;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.security.JwtAuthFilter;
import com.cet46.vocab.service.UserService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({GlobalExceptionHandler.class, UserControllerMockMvcTest.MockSecurityConfig.class})
class UserControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

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
    void updatePrivateCloudModelFull_shouldForbidWithoutEditPermission() throws Exception {
        String body = """
                {
                  "modelKey": "qwen-max",
                  "displayName": "Qwen Max",
                  "enabled": true
                }
                """;

        mockMvc.perform(put("/user/llm/cloud-models/private/9/full")
                        .with(authWith("PRIVATE_CLOUD_MODEL_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message", containsString("Access Denied")));

        verifyNoInteractions(userService);
    }

    @Test
    void updatePrivateCloudModelFull_shouldReturnForbiddenCodeWhenMissingTogglePermission() throws Exception {
        String body = """
                {
                  "modelKey": "qwen-max",
                  "displayName": "Qwen Max",
                  "enabled": true
                }
                """;

        mockMvc.perform(put("/user/llm/cloud-models/private/9/full")
                        .with(authWith("PRIVATE_CLOUD_MODEL_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("forbidden"));

        verifyNoInteractions(userService);
    }

    @Test
    void updatePrivateCloudModelFull_shouldSucceedWithEditAndTogglePermission() throws Exception {
        CloudLlmModel saved = CloudLlmModel.builder()
                .id(9L)
                .provider("bailian")
                .modelKey("qwen-max")
                .displayName("Qwen Max")
                .enabled(true)
                .isDefault(false)
                .visibility("USER_PRIVATE")
                .ownerUserId(1001L)
                .build();
        when(userService.updatePrivateCloudModel(1001L, 9L, "qwen-max", "Qwen Max", true)).thenReturn(saved);

        String body = """
                {
                  "modelKey": "qwen-max",
                  "displayName": "Qwen Max",
                  "enabled": true
                }
                """;

        mockMvc.perform(put("/user/llm/cloud-models/private/9/full")
                        .with(authWith("PRIVATE_CLOUD_MODEL_EDIT", "PRIVATE_CLOUD_MODEL_TOGGLE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.modelKey").value("qwen-max"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.visibility").value("USER_PRIVATE"));

        verify(userService).updatePrivateCloudModel(eq(1001L), eq(9L), eq("qwen-max"), eq("Qwen Max"), eq(true));
    }

    @Test
    void updatePrivateCloudModelFull_shouldRejectBlankModelKeyByRequestValidation() throws Exception {
        String body = """
                {
                  "modelKey": "   ",
                  "displayName": "Qwen Max",
                  "enabled": false
                }
                """;

        mockMvc.perform(put("/user/llm/cloud-models/private/9/full")
                        .with(authWith("PRIVATE_CLOUD_MODEL_EDIT", "PRIVATE_CLOUD_MODEL_TOGGLE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("modelKey")));

        verifyNoInteractions(userService);
    }

    private RequestPostProcessor authWith(String... authorities) {
        List<GrantedAuthority> grantedAuthorities = Arrays.stream(authorities)
                .map(item -> (GrantedAuthority) new SimpleGrantedAuthority(item))
                .toList();
        Authentication authentication = new UsernamePasswordAuthenticationToken("1001", "N/A", grantedAuthorities);
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
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
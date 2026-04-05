package com.cet46.vocab.controller;

import com.cet46.vocab.common.GlobalExceptionHandler;
import com.cet46.vocab.dto.response.CloudLlmHealthResponse;
import com.cet46.vocab.dto.response.LlmUsageStatsResponse;
import com.cet46.vocab.entity.CloudLlmModel;
import com.cet46.vocab.security.JwtAuthFilter;
import com.cet46.vocab.service.LlmUsageStatsService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private LlmUsageStatsService llmUsageStatsService;

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
                  \"modelKey\": \"qwen-max\",
                  \"displayName\": \"Qwen Max\",
                  \"enabled\": true
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
                  \"modelKey\": \"qwen-max\",
                  \"displayName\": \"Qwen Max\",
                  \"enabled\": true
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
        when(userService.updatePrivateCloudModel(1001L, 9L, null, "qwen-max", "Qwen Max", null, null, null, null, null, true))
                .thenReturn(saved);

        String body = """
                {
                  \"modelKey\": \"qwen-max\",
                  \"displayName\": \"Qwen Max\",
                  \"enabled\": true
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

        verify(userService).updatePrivateCloudModel(
                eq(1001L),
                eq(9L),
                eq(null),
                eq("qwen-max"),
                eq("Qwen Max"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(true)
        );
    }

    @Test
    void updatePrivateCloudModelFull_shouldRejectBlankModelKeyByRequestValidation() throws Exception {
        String body = """
                {
                  \"modelKey\": \"   \",
                  \"displayName\": \"Qwen Max\",
                  \"enabled\": false
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

    @Test
    void previewCloudModelHealth_shouldForbidWithoutPermission() throws Exception {
        String body = """
                {
                  \"provider\": \"bailian\",
                  \"modelKey\": \"qwen-max\",
                  \"baseUrl\": \"https://dashscope.aliyuncs.com/compatible-mode/v1\",
                  \"path\": \"/chat/completions\",
                  \"apiKey\": \"sk-123\"
                }
                """;

        mockMvc.perform(post("/user/llm/cloud-models/preview-health")
                        .with(authWith())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message", containsString("Access Denied")));

        verifyNoInteractions(userService);
    }

    @Test
    void previewCloudModelHealth_shouldSucceedWithCreatePermission() throws Exception {
        CloudLlmHealthResponse response = CloudLlmHealthResponse.builder()
                .currentProvider("bailian")
                .runtimeSource("MANUAL_PRECHECK")
                .model("qwen-max")
                .dnsOk(true)
                .authOk(true)
                .modelOk(true)
                .latencyMs(123L)
                .message("云端连通正常")
                .build();
        when(userService.previewCloudLlmHealth(
                1001L,
                "bailian",
                "qwen-max",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "/chat/completions",
                "openai-compatible",
                "sk-123"
        )).thenReturn(response);

        String body = """
                {
                  \"provider\": \"bailian\",
                  \"modelKey\": \"qwen-max\",
                  \"baseUrl\": \"https://dashscope.aliyuncs.com/compatible-mode/v1\",
                  \"path\": \"/chat/completions\",
                  \"protocol\": \"openai-compatible\",
                  \"apiKey\": \"sk-123\"
                }
                """;

        mockMvc.perform(post("/user/llm/cloud-models/preview-health")
                        .with(authWith("PRIVATE_CLOUD_MODEL_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.model").value("qwen-max"))
                .andExpect(jsonPath("$.data.message").value("云端连通正常"))
                .andExpect(jsonPath("$.data.latencyMs").value(123));

        verify(userService).previewCloudLlmHealth(
                eq(1001L),
                eq("bailian"),
                eq("qwen-max"),
                eq("https://dashscope.aliyuncs.com/compatible-mode/v1"),
                eq("/chat/completions"),
                eq("openai-compatible"),
                eq("sk-123")
        );
    }

    @Test
    void getCloudModelUsage_shouldReturnUserScopedStats() throws Exception {
        LlmUsageStatsResponse response = LlmUsageStatsResponse.builder()
                .viewRole("USER")
                .summary(LlmUsageStatsResponse.Summary.builder()
                        .totalCallsToday(2L)
                        .totalCalls7d(5L)
                        .totalCalls30d(9L)
                        .publicCalls30d(6L)
                        .privateCalls30d(3L)
                        .activeModels(2)
                        .build())
                .build();
        when(llmUsageStatsService.getUserCloudUsage(1001L)).thenReturn(response);

        mockMvc.perform(get("/user/llm/usage")
                        .with(authWith()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.viewRole").value("USER"))
                .andExpect(jsonPath("$.data.summary.totalCalls30d").value(9))
                .andExpect(jsonPath("$.data.summary.privateCalls30d").value(3));

        verify(llmUsageStatsService).getUserCloudUsage(1001L);
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

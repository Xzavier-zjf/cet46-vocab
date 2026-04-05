package com.cet46.vocab.controller;

import com.cet46.vocab.common.GlobalExceptionHandler;
import com.cet46.vocab.dto.response.LlmUsageStatsResponse;
import com.cet46.vocab.llm.LlmAsyncService;
import com.cet46.vocab.llm.LlmCacheService;
import com.cet46.vocab.mapper.Cet4WordMapper;
import com.cet46.vocab.mapper.Cet6WordMapper;
import com.cet46.vocab.mapper.WordMetaMapper;
import com.cet46.vocab.security.JwtAuthFilter;
import com.cet46.vocab.service.CloudLlmModelService;
import com.cet46.vocab.service.LlmUsageStatsService;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@Import({GlobalExceptionHandler.class, AdminControllerUsageMockMvcTest.MockSecurityConfig.class})
class AdminControllerUsageMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LlmAsyncService llmAsyncService;

    @MockBean
    private LlmCacheService llmCacheService;

    @MockBean
    private WordMetaMapper wordMetaMapper;

    @MockBean
    private Cet4WordMapper cet4WordMapper;

    @MockBean
    private Cet6WordMapper cet6WordMapper;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private CloudLlmModelService cloudLlmModelService;

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
    @WithMockUser(roles = "ADMIN")
    void getCloudUsage_shouldReturnAdminScopedStats() throws Exception {
        LlmUsageStatsResponse response = LlmUsageStatsResponse.builder()
                .viewRole("ADMIN")
                .summary(LlmUsageStatsResponse.Summary.builder()
                        .totalCalls30d(18L)
                        .publicCalls30d(18L)
                        .activeUsers(3)
                        .build())
                .build();
        when(llmUsageStatsService.getAdminCloudUsage()).thenReturn(response);

        mockMvc.perform(get("/admin/llm/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.viewRole").value("ADMIN"))
                .andExpect(jsonPath("$.data.summary.totalCalls30d").value(18))
                .andExpect(jsonPath("$.data.summary.activeUsers").value(3));

        verify(llmUsageStatsService).getAdminCloudUsage();
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCloudUsage_shouldForbidNonAdmin() throws Exception {
        mockMvc.perform(get("/admin/llm/usage"))
                .andExpect(status().isForbidden());
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

package com.cet46.vocab.security;

import com.cet46.vocab.config.SecurityRbacProperties;
import com.cet46.vocab.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SecurityRbacProperties securityRbacProperties;

    public JwtAuthFilter(JwtUtils jwtUtils,
                         RedisTemplate<String, Object> redisTemplate,
                         SecurityRbacProperties securityRbacProperties) {
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
        this.securityRbacProperties = securityRbacProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = jwtUtils.getUserIdFromToken(token);
            String role = normalizeRole(jwtUtils.getRoleFromToken(token));

            String redisKey = "token:user:" + userId;
            Object cachedToken = redisTemplate.opsForValue().get(redisKey);
            if (cachedToken != null && token.equals(String.valueOf(cachedToken))) {
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                new ArrayList<>(buildAuthorities(role))
                        );
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private Set<GrantedAuthority> buildAuthorities(String role) {
        LinkedHashSet<GrantedAuthority> result = new LinkedHashSet<>();
        result.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String permission : securityRbacProperties.resolvePermissions(role)) {
            result.add(new SimpleGrantedAuthority(permission));
        }
        return result;
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "USER";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}

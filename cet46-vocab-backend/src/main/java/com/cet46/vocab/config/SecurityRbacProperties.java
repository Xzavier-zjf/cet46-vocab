package com.cet46.vocab.config;

import com.cet46.vocab.security.PrivateCloudModelPermissions;
import com.cet46.vocab.security.GlobalCloudModelPermissions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "security.rbac")
public class SecurityRbacProperties {

    private Map<String, List<String>> rolePermissions = defaultRolePermissions();

    public Set<String> resolvePermissions(String role) {
        String roleKey = normalizeRole(role);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        List<String> configured = rolePermissions.get(roleKey);
        if (configured != null) {
            for (String item : configured) {
                if (StringUtils.hasText(item)) {
                    result.add(item.trim());
                }
            }
        }
        return result;
    }

    private Map<String, List<String>> defaultRolePermissions() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(PrivateCloudModelPermissions.ORDERED);
        all.addAll(GlobalCloudModelPermissions.ORDERED);
        defaults.put("ADMIN", all);
        defaults.put("USER", all);
        return defaults;
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "USER";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }
}

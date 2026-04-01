package com.cet46.vocab.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cet46.vocab.common.PageResult;
import com.cet46.vocab.common.Result;
import com.cet46.vocab.common.ResultCode;
import com.cet46.vocab.entity.User;
import com.cet46.vocab.mapper.UserMapper;
import com.cet46.vocab.service.RolePermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RolePermissionService rolePermissionService;

    public AdminUserController(UserMapper userMapper,
                               PasswordEncoder passwordEncoder,
                               RolePermissionService rolePermissionService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping
    public Result<PageResult<AdminUserItem>> listUsers(
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null ? 10 : Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, k).or().like(User::getNickname, k));
        }
        wrapper.orderByDesc(User::getCreatedAt).orderByDesc(User::getId);

        Page<User> users = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<AdminUserItem> list = users.getRecords().stream().map(this::toItem).toList();
        PageResult<AdminUserItem> result = new PageResult<>(users.getTotal(), pageNo, pageSize, list);
        return Result.success(result);
    }

    @GetMapping("/role-permissions")
    public Result<List<RolePermissionItem>> getRolePermissions() {
        List<RolePermissionItem> items = rolePermissionService.listRolePermissions().stream()
                .map(item -> new RolePermissionItem(item.getRole(), item.getPermissions()))
                .toList();
        return Result.success(items);
    }

    @PutMapping("/role-permissions")
    public Result<Void> updateRolePermissions(@Valid @RequestBody UpdateRolePermissionsRequest req,
                                              Authentication authentication) {
        Map<String, List<String>> permissions = new LinkedHashMap<>();
        for (RolePermissionItem item : req.getItems()) {
            permissions.put(item.getRole().trim().toUpperCase(Locale.ROOT), item.getPermissions());
        }
        if (permissions.isEmpty()) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "items must include at least one role");
        }
        rolePermissionService.updateRolePermissions(permissions, currentUserId(authentication));
        return Result.success();
    }

    @GetMapping("/role-permissions/audits")
    public Result<PageResult<RolePermissionAuditItem>> listRolePermissionAudits(
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        PageResult<RolePermissionService.RolePermissionAuditItem> pageData = rolePermissionService.listAuditLogs(page, size);
        List<RolePermissionAuditItem> list = pageData.getList().stream().map(item -> {
            RolePermissionAuditItem out = new RolePermissionAuditItem();
            out.setId(item.getId());
            out.setActorUserId(item.getActorUserId());
            out.setRole(item.getRole());
            out.setBeforePermissions(item.getBeforePermissions());
            out.setAfterPermissions(item.getAfterPermissions());
            out.setChangedAt(item.getChangedAt());
            return out;
        }).toList();
        return Result.success(new PageResult<>(pageData.getTotal(), pageData.getPage(), pageData.getSize(), list));
    }

    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable("id") Long userId,
                                   @Valid @RequestBody UpdateRoleRequest req,
                                   Authentication authentication) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "\u7528\u6237\u4E0D\u5B58\u5728");
        }
        String role = normalizeRole(req.getRole());
        if (role == null) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "\u89D2\u8272\u5FC5\u987B\u662F ADMIN \u6216 USER");
        }
        Long currentUserId = currentUserId(authentication);
        if (currentUserId != null && currentUserId.equals(userId) && !"ADMIN".equals(role)) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "\u4E0D\u80FD\u5C06\u5F53\u524D\u767B\u5F55\u7BA1\u7406\u5458\u964D\u7EA7\u4E3A USER");
        }
        user.setRole(role);
        userMapper.updateById(user);
        return Result.success();
    }

    @PutMapping("/{id}/password/reset")
    public Result<Void> resetPassword(@PathVariable("id") Long userId,
                                      @Valid @RequestBody ResetPasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "\u7528\u6237\u4E0D\u5B58\u5728");
        }
        String newPassword = req.getNewPassword() == null ? "" : req.getNewPassword().trim();
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "\u65B0\u5BC6\u7801\u957F\u5EA6\u9700\u4E3A 6-64 \u4F4D");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable("id") Long userId, Authentication authentication) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "\u7528\u6237\u4E0D\u5B58\u5728");
        }
        Long currentUserId = currentUserId(authentication);
        if (currentUserId != null && currentUserId.equals(userId)) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "\u4E0D\u80FD\u5220\u9664\u5F53\u524D\u767B\u5F55\u8D26\u53F7");
        }
        userMapper.deleteById(userId);
        return Result.success();
    }

    private AdminUserItem toItem(User user) {
        AdminUserItem item = new AdminUserItem();
        item.setId(user.getId());
        item.setUsername(user.getUsername());
        item.setNickname(user.getNickname());
        item.setRole(user.getRole());
        item.setLlmStyle(user.getLlmStyle());
        item.setLlmProvider(user.getLlmProvider());
        item.setDailyTarget(user.getDailyTarget());
        item.setCreatedAt(user.getCreatedAt());
        item.setUpdatedAt(user.getUpdatedAt());
        return item;
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String value = role.trim().toUpperCase(Locale.ROOT);
        return ("ADMIN".equals(value) || "USER".equals(value)) ? value : null;
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getPrincipal().toString());
        } catch (Exception ex) {
            return null;
        }
    }

    @Data
    public static class AdminUserItem {
        private Long id;
        private String username;
        private String nickname;
        private String role;
        private String llmStyle;
        private String llmProvider;
        private Integer dailyTarget;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class UpdateRoleRequest {
        @NotBlank
        private String role;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String newPassword;
    }

    @Data
    public static class RolePermissionItem {
        @NotBlank
        private String role;
        private List<String> permissions;

        public RolePermissionItem() {
        }

        public RolePermissionItem(String role, List<String> permissions) {
            this.role = role;
            this.permissions = permissions;
        }
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

    @Data
    public static class UpdateRolePermissionsRequest {
        @NotEmpty
        private List<@NotNull @Valid RolePermissionItem> items;
    }
}


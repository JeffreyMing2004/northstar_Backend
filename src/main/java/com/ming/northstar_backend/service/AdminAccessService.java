package com.ming.northstar_backend.service;

import com.ming.northstar_backend.entity.AdminGrant;
import com.ming.northstar_backend.repository.AdminGrantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminAccessService {

    public static final int MAX_ADMINS = 3;
    private final Set<String> adminUsernames;
    private final AdminGrantRepository adminGrantRepository;

    public AdminAccessService(@Value("${admin.usernames:}") String adminUsernames,
                              AdminGrantRepository adminGrantRepository) {
        this.adminUsernames = Arrays.stream(adminUsernames.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
        if (this.adminUsernames.size() > MAX_ADMINS) {
            throw new IllegalStateException("管理员配置最多只能有3位");
        }
        this.adminGrantRepository = adminGrantRepository;
    }

    public boolean isAdmin(String username) {
        String value = normalize(username);
        return !value.isBlank()
            && (adminUsernames.contains(value) || adminGrantRepository.findByUsernameIgnoreCase(value).isPresent());
    }

    public String roleFor(String username) {
        return isAdmin(username) ? "admin" : "user";
    }

    public boolean isConfiguredAdmin(String username) {
        String value = normalize(username);
        return !value.isBlank() && adminUsernames.contains(value);
    }

    public int getAdminCount() {
        long dynamicAdmins = adminGrantRepository.findAll().stream()
            .map(AdminGrant::getUsername)
            .map(this::normalize)
            .filter(value -> !value.isBlank() && !adminUsernames.contains(value))
            .count();
        return (int) Math.min(Integer.MAX_VALUE, adminUsernames.size() + dynamicAdmins);
    }

    public int getMaxAdmins() {
        return MAX_ADMINS;
    }

    @Transactional
    public synchronized void grantAdmin(String username) {
        String value = normalize(username);
        if (value.isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (isConfiguredAdmin(value)) {
            throw new RuntimeException("该玩家是内置管理员，无需分配");
        }
        if (isAdmin(value)) {
            throw new RuntimeException("该玩家已经是管理员");
        }
        if (getAdminCount() >= MAX_ADMINS) {
            throw new RuntimeException("管理员最多只能有3位");
        }
        adminGrantRepository.save(new AdminGrant(value));
    }

    @Transactional
    public synchronized void revokeAdmin(String username) {
        String value = normalize(username);
        if (value.isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (isConfiguredAdmin(value)) {
            throw new RuntimeException("内置管理员不能取消");
        }
        if (!isAdmin(value)) {
            throw new RuntimeException("该玩家不是管理员");
        }
        adminGrantRepository.deleteByUsernameIgnoreCase(value);
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}

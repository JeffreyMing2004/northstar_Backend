package com.ming.northstar_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminAccessService {

    private final Set<String> adminUsernames;

    public AdminAccessService(@Value("${admin.usernames:}") String adminUsernames) {
        this.adminUsernames = Arrays.stream(adminUsernames.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String username) {
        return username != null && adminUsernames.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public String roleFor(String username) {
        return isAdmin(username) ? "admin" : "user";
    }
}

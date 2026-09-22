package com.ming.northstar_backend.security;

import com.ming.northstar_backend.service.AdminAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AdminAccessService adminAccessService;

    public JwtFilter(JwtUtil jwtUtil, AdminAccessService adminAccessService) {
        this.jwtUtil = jwtUtil;
        this.adminAccessService = adminAccessService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);
                List<GrantedAuthority> authorities = adminAccessService.isAdmin(username)
                    ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    : List.of();
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
                auth.setDetails(username);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}

package com.ming.northstar_backend.security;

import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.service.AdminAccessService;
import com.ming.northstar_backend.service.TokenRevocationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * 从 {@code Authorization: Bearer <jwt>} 还原登录态。
 *
 * <p><b>身份以数据库为准，令牌里的 username 只作参考</b>。修复前这里直接拿令牌里的
 * {@code username} 声明去查管理员名单，于是「JWT 密钥泄露 ⇒ 任意伪造管理员令牌」
 * 就从「能读数据」升级成了「能拿后台」（渗透报告 NS-01）。现在：</p>
 * <ol>
 *   <li>{@code sub}（userId）才是身份来源，用户名从 {@code users} 表反查；</li>
 *   <li>管理员判定用反查到的用户名，令牌里带什么名字都无法提权；</li>
 *   <li>用户已不存在时仍放进 SecurityContext（只是没有任何角色），
 *       这样 {@code /api/auth/profile} 能回 401 让前端自动登出，而不是被 Spring 拦成 403。</li>
 * </ol>
 *
 * <p>另外校验令牌是否已被吊销（改密 / 重置密码后立即生效，见
 * {@link TokenRevocationService}）：已吊销则直接回 401，不再进入业务代码。</p>
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AdminAccessService adminAccessService;
    private final TokenRevocationService revocationService;
    private final UserRepository userRepository;

    public JwtFilter(JwtUtil jwtUtil,
                     AdminAccessService adminAccessService,
                     TokenRevocationService revocationService,
                     UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.adminAccessService = adminAccessService;
        this.revocationService = revocationService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            Optional<Claims> claims = jwtUtil.tryParse(header.substring(BEARER_PREFIX.length()).trim());
            if (claims.isPresent()) {
                Claims payload = claims.get();
                Long userId = parseUserId(payload.getSubject());
                if (userId == null) {
                    chain.doFilter(request, response);
                    return;
                }
                if (revocationService.isRevoked(userId, payload.getIssuedAt())) {
                    writeUnauthorized(response);
                    return;
                }
                authenticate(userId, payload.get("username", String.class));
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Long userId, String tokenUsername) {
        User user = userRepository.findById(userId).orElse(null);
        // 只有数据库里存在的账号才可能拥有管理员角色；令牌里的用户名不参与判权
        String authoritative = user == null ? null : user.getUsername();
        List<GrantedAuthority> authorities = authoritative != null && adminAccessService.isAdmin(authoritative)
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        auth.setDetails(authoritative != null ? authoritative : tokenUsername);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Long parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(subject.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 令牌已被吊销：明确回 401，前端拦截器据此登出，避免停在「403 无权限」的死界面上。 */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"message\":\"登录状态已失效，请重新登录\",\"data\":null}");
    }
}

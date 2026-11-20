package de.tantive.timetracking.config;

import de.tantive.timetracking.domain.AppUser;
import de.tantive.timetracking.domain.Repos;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, Repos.Users users) throws Exception {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null); // resolve token eagerly so the cookie is always written
        http
            .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(handler)
                    .ignoringRequestMatchers("/api/auth/login", "/api/auth/forgot-password",
                            "/api/auth/reset-password", "/api/auth/accept-invite"))
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login", "/api/auth/forgot-password", "/api/auth/reset-password",
                        "/api/auth/accept-invite", "/api/auth/token-info", "/api/dev/**", "/api/public/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .httpBasic(b -> b.disable()).formLogin(f -> f.disable())
            .logout(l -> l.disable());
        http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        http.addFilterAfter(new ActiveUserFilter(users), CsrfCookieFilter.class);
        return http.build();
    }

    /** Forces the CSRF cookie to be written on every response. */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken t = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (t != null) t.getToken();
            chain.doFilter(req, res);
        }
    }

    /** A deactivated user is locked out immediately, even with a running session (A-08). */
    static class ActiveUserFilter extends OncePerRequestFilter {
        private final Repos.Users users;
        ActiveUserFilter(Repos.Users users) { this.users = users; }
        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            // only API calls are rejected; static files (lazy-loaded UI chunks) must stay loadable so the client can show the login page
            if (req.getRequestURI().startsWith("/api/") && auth != null && auth.getPrincipal() instanceof AppPrincipal p) {
                AppUser u = users.findById(p.userId()).orElse(null);
                if (u == null || !u.active) {
                    SecurityContextHolder.clearContext();
                    HttpSession s = req.getSession(false);
                    if (s != null) s.invalidate();
                    res.sendError(401);
                    return;
                }
            }
            chain.doFilter(req, res);
        }
    }
}

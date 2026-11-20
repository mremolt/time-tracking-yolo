package de.tantive.timetracking.service;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Login, lockout, invitation and password reset (A-01..A-04, D-02). */
@Service
@Transactional
public class AuthService {
    private final Repos.Users users;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final AuditService audit;
    @Value("${app.max-failed-logins}") int maxFailed;
    @Value("${app.lockout-minutes}") int lockoutMinutes;
    @Value("${app.min-password-length}") int minPassword;
    @Value("${app.invite-valid-hours}") int inviteHours;
    @Value("${app.reset-valid-hours}") int resetHours;
    @Value("${app.base-url}") String baseUrl;

    public AuthService(Repos.Users users, PasswordEncoder encoder, MailService mail, AuditService audit) {
        this.users = users; this.encoder = encoder; this.mail = mail; this.audit = audit;
    }

    public AppUser login(String email, String password, HttpServletRequest req) {
        AppUser u = email == null ? null : users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (u == null || !u.active || u.passwordHash == null) {
            // same answer and similar timing as for a wrong password
            encoder.matches(password == null ? "" : password, "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BEvVUkn1tRfwFZ0yVQ5s5eQYhJ6e");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        if (u.lockedUntil != null && u.lockedUntil.isAfter(Instant.now()))
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED");
        if (!encoder.matches(password == null ? "" : password, u.passwordHash)) {
            u.failedLogins++;
            if (u.failedLogins >= maxFailed) {
                u.lockedUntil = Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES);
                u.failedLogins = 0;
                audit.admin(null, "AppUser", u.id, "ACCOUNT_LOCKED", null, Map.of("email", u.email));
            }
            users.save(u);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        u.failedLogins = 0; u.lockedUntil = null;
        users.save(u);
        establishSession(u, req);
        return u;
    }

    private void establishSession(AppUser u, HttpServletRequest req) {
        req.getSession(true);
        req.changeSessionId();
        List<SimpleGrantedAuthority> auth = u.roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        var token = UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(u.id, u.email), null, auth);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(token);
        SecurityContextHolder.setContext(ctx);
        req.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
    }

    public void logout(HttpServletRequest req) {
        var s = req.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
    }

    public void validatePassword(String pw) {
        if (pw == null || pw.length() < minPassword) throw ApiException.bad("PASSWORD_TOO_SHORT", Map.of("min", minPassword));
        boolean letter = pw.chars().anyMatch(Character::isLetter), digit = pw.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) throw ApiException.bad("PASSWORD_WEAK");
    }

    private static String token() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public void invite(AppUser u) {
        u.inviteToken = token();
        u.inviteExpires = Instant.now().plus(inviteHours, ChronoUnit.HOURS);
        users.save(u);
        mail.send(u, "invite", u.firstName, baseUrl + "/accept-invite?token=" + u.inviteToken, inviteHours);
    }

    public void forgotPassword(String email) {
        // always succeeds silently, no account enumeration
        users.findByEmailIgnoreCase(email == null ? "" : email.trim()).filter(u -> u.active).ifPresent(u -> {
            u.resetToken = token();
            u.resetExpires = Instant.now().plus(resetHours, ChronoUnit.HOURS);
            users.save(u);
            mail.send(u, "reset", u.firstName, baseUrl + "/reset-password?token=" + u.resetToken, resetHours);
        });
    }

    public AppUser tokenOwner(String token, boolean invite) {
        if (token == null) throw ApiException.bad("TOKEN_INVALID");
        Optional<AppUser> o = invite ? users.findByInviteToken(token) : users.findByResetToken(token);
        AppUser u = o.orElseThrow(() -> ApiException.bad("TOKEN_INVALID"));
        Instant exp = invite ? u.inviteExpires : u.resetExpires;
        if (!u.active || exp == null || exp.isBefore(Instant.now())) throw ApiException.bad("TOKEN_INVALID");
        return u;
    }

    public void setPassword(String token, boolean invite, String password) {
        AppUser u = tokenOwner(token, invite);
        validatePassword(password);
        u.passwordHash = encoder.encode(password);
        u.failedLogins = 0; u.lockedUntil = null;
        if (invite) { u.inviteToken = null; u.inviteExpires = null; } else { u.resetToken = null; u.resetExpires = null; }
        users.save(u);
    }

    public void changePassword(AppUser u, String current, String next) {
        if (!encoder.matches(current == null ? "" : current, u.passwordHash)) throw ApiException.bad("PASSWORD_WRONG");
        validatePassword(next);
        u.passwordHash = encoder.encode(next);
        users.save(u);
    }
}

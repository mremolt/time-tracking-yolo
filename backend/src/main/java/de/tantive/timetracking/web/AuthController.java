package de.tantive.timetracking.web;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record Login(String email, String password) {}
    public record Forgot(String email) {}
    public record TokenPw(String token, String password) {}
    public record ChangePw(String current, String next) {}

    private final AuthService auth;
    private final Access access;

    public AuthController(AuthService auth, Access access) { this.auth = auth; this.access = access; }

    @PostMapping("/login")
    public Dtos.UserDto login(@RequestBody Login in, HttpServletRequest req) { return Dtos.UserDto.of(auth.login(in.email(), in.password(), req)); }

    @PostMapping("/logout")
    public void logout(HttpServletRequest req) { auth.logout(req); }

    @GetMapping("/me")
    public Dtos.UserDto me() { return Dtos.UserDto.of(access.me()); }

    @PostMapping("/forgot-password")
    public void forgot(@RequestBody Forgot in) { auth.forgotPassword(in.email()); }

    @GetMapping("/token-info")
    public Map<String, Object> tokenInfo(@RequestParam String token, @RequestParam boolean invite) {
        AppUser u = auth.tokenOwner(token, invite);
        return Map.of("firstName", u.firstName, "email", u.email);
    }

    @PostMapping("/reset-password")
    public void reset(@RequestBody TokenPw in) { auth.setPassword(in.token(), false, in.password()); }

    @PostMapping("/accept-invite")
    public void accept(@RequestBody TokenPw in) { auth.setPassword(in.token(), true, in.password()); }

    @PostMapping("/change-password")
    public void change(@RequestBody ChangePw in) { auth.changePassword(access.me(), in.current(), in.next()); }
}

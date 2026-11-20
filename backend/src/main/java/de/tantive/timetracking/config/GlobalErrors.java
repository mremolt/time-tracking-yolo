package de.tantive.timetracking.config;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice
public class GlobalErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return ResponseEntity.status(e.status).body(Map.of("code", e.code, "params", e.params));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldErrors().stream().map(f -> f.getField()).findFirst().orElse("");
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION", "params", Map.of("field", field)));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class, java.time.DateTimeException.class})
    ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("code", "BAD_REQUEST", "params", Map.of()));
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(Exception e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "FORBIDDEN", "params", Map.of()));
    }
}

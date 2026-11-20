package de.tantive.timetracking.config;

import org.springframework.http.HttpStatus;
import java.util.Map;

/** Error with a stable code; the frontend translates the code (DE/EN). */
public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public final Map<String, Object> params;

    public ApiException(HttpStatus status, String code, Map<String, Object> params) {
        super(code);
        this.status = status; this.code = code; this.params = params;
    }
    public ApiException(HttpStatus status, String code) { this(status, code, Map.of()); }

    public static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN"); }
    public static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND"); }
    public static ApiException bad(String code) { return new ApiException(HttpStatus.BAD_REQUEST, code); }
    public static ApiException bad(String code, Map<String, Object> p) { return new ApiException(HttpStatus.BAD_REQUEST, code, p); }
    public static ApiException conflict(String code) { return new ApiException(HttpStatus.CONFLICT, code); }
}

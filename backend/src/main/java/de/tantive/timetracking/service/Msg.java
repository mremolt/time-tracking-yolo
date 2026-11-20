package de.tantive.timetracking.service;

import java.util.Map;

/** Translatable message: key + parameters (translated by the client / report layer). */
public record Msg(String key, Map<String, Object> params) {
    public static Msg of(String key, Object... kv) {
        java.util.Map<String, Object> p = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) p.put((String) kv[i], kv[i + 1]);
        return new Msg(key, p);
    }
}

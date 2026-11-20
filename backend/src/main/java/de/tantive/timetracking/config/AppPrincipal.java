package de.tantive.timetracking.config;

import java.io.Serializable;

public record AppPrincipal(Long userId, String email) implements Serializable {}

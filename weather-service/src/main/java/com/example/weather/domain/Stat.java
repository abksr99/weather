package com.example.weather.domain;

import java.util.Locale;

public enum Stat {
    MIN, MAX, SUM, AVG;

    public static Stat fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return AVG;
        }
        try {
            return Stat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown stat '" + raw + "'. Allowed: min, max, sum, avg.");
        }
    }
}

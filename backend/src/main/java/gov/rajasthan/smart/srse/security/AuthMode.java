package gov.rajasthan.smart.srse.security;

/**
 * Matches {@code srse.auth-mode} / {@code SRSE_AUTH_MODE} config values
 * ({@code mock} | {@code local} | {@code rajsewadwar}). Mirrors {@code DataMode}'s pattern.
 */
public enum AuthMode {
    MOCK,
    LOCAL,
    RAJSEWADWAR
}

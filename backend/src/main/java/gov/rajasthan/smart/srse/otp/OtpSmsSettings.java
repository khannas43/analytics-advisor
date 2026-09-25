package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.config.ConnectionOverrideStore;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;

/** Persists SMS gateway fields until an {@link OtpSender} implementation exists (7.1a.7). */
@Component
public class OtpSmsSettings {

    public static final String PREFIX = "srse.otp.sms.";

    private final ConnectionOverrideStore overrideStore;

    public OtpSmsSettings(ConnectionOverrideStore overrideStore) {
        this.overrideStore = overrideStore;
    }

    public Optional<SmsConfig> activeConfig() {
        Properties props = overrideStore.load().orElseGet(Properties::new);
        String endpoint = props.getProperty(PREFIX + "endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SmsConfig(
                endpoint.trim(),
                blankToNull(props.getProperty(PREFIX + "username")),
                props.getProperty(PREFIX + "password"),
                blankToNull(props.getProperty(PREFIX + "sender-id"))));
    }

    public boolean passwordConfigured() {
        return overrideStore.load()
                .map(p -> p.getProperty(PREFIX + "password"))
                .filter(s -> s != null && !s.isBlank())
                .isPresent();
    }

    public void save(SmsConfig config, boolean mergePasswordFromExisting) {
        Properties updates = new Properties();
        updates.setProperty(PREFIX + "endpoint", config.endpoint());
        if (config.username() != null) {
            updates.setProperty(PREFIX + "username", config.username());
        }
        String password = config.password();
        if (mergePasswordFromExisting && (password == null || password.isBlank())) {
            password = overrideStore.load()
                    .map(p -> p.getProperty(PREFIX + "password"))
                    .orElse(null);
        }
        if (password != null && !password.isBlank()) {
            updates.setProperty(PREFIX + "password", password);
        }
        if (config.senderId() != null) {
            updates.setProperty(PREFIX + "sender-id", config.senderId());
        }
        overrideStore.save(updates);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record SmsConfig(String endpoint, String username, String password, String senderId) {
    }
}

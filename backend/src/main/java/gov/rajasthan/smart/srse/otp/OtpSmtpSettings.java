package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.config.ConnectionOverrideStore;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;

@Component
public class OtpSmtpSettings {

    public static final String PREFIX = "srse.otp.smtp.";

    private final ConnectionOverrideStore overrideStore;

    public OtpSmtpSettings(ConnectionOverrideStore overrideStore) {
        this.overrideStore = overrideStore;
    }

    public Optional<SmtpConfig> activeConfig() {
        Properties props = overrideStore.load().orElseGet(Properties::new);
        String host = props.getProperty(PREFIX + "host");
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SmtpConfig(
                host.trim(),
                intProp(props, PREFIX + "port", 587),
                blankToNull(props.getProperty(PREFIX + "username")),
                props.getProperty(PREFIX + "password"),
                blankToNull(props.getProperty(PREFIX + "from", props.getProperty(PREFIX + "from-address"))),
                boolProp(props, PREFIX + "tls", true)));
    }

    public boolean passwordConfigured() {
        return overrideStore.load()
                .map(p -> p.getProperty(PREFIX + "password"))
                .filter(s -> s != null && !s.isBlank())
                .isPresent();
    }

    public void save(SmtpConfig config, boolean mergePasswordFromExisting) {
        Properties updates = new Properties();
        updates.setProperty(PREFIX + "host", config.host());
        updates.setProperty(PREFIX + "port", String.valueOf(config.port()));
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
        if (config.fromAddress() != null) {
            updates.setProperty(PREFIX + "from", config.fromAddress());
        }
        updates.setProperty(PREFIX + "tls", String.valueOf(config.tls()));
        overrideStore.save(updates);
    }

    private static int intProp(Properties props, String key, int defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(raw.trim());
    }

    private static boolean boolProp(Properties props, String key, boolean defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record SmtpConfig(
            String host,
            int port,
            String username,
            String password,
            String fromAddress,
            boolean tls) {
    }
}

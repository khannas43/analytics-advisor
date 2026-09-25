package gov.rajasthan.smart.srse.otp;

import gov.rajasthan.smart.srse.config.ConnectionOverrideStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpSmtpSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripThroughConnectionOverrideStore() {
        Path file = tempDir.resolve("overrides.properties");
        ConnectionOverrideStore store = new ConnectionOverrideStore(file.toString());
        OtpSmtpSettings settings = new OtpSmtpSettings(store);

        assertTrue(settings.activeConfig().isEmpty());

        settings.save(
                new OtpSmtpSettings.SmtpConfig(
                        "smtp.example.com",
                        465,
                        "user",
                        "secret",
                        "noreply@example.com",
                        false),
                false);

        Optional<OtpSmtpSettings.SmtpConfig> loaded = settings.activeConfig();
        assertTrue(loaded.isPresent());
        OtpSmtpSettings.SmtpConfig cfg = loaded.get();
        assertEquals("smtp.example.com", cfg.host());
        assertEquals(465, cfg.port());
        assertEquals("user", cfg.username());
        assertEquals("secret", cfg.password());
        assertEquals("noreply@example.com", cfg.fromAddress());
        assertFalse(cfg.tls());
        assertTrue(settings.passwordConfigured());

        settings.save(
                new OtpSmtpSettings.SmtpConfig(
                        "smtp.example.com",
                        465,
                        "user",
                        "",
                        "noreply@example.com",
                        true),
                true);
        assertEquals("secret", settings.activeConfig().orElseThrow().password());
        assertTrue(settings.activeConfig().orElseThrow().tls());
    }
}

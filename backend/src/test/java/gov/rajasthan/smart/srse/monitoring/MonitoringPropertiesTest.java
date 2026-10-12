package gov.rajasthan.smart.srse.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MonitoringPropertiesTest {

    @Test
    void rejectsMaxRunningBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new MonitoringProperties(0, 30));
    }

    @Test
    void rejectsRecentMinutesBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new MonitoringProperties(1000, 0));
    }
}

package gov.rajasthan.smart.srse.metadata.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MetadataSyncPropertiesTest {

    @Test
    void rejectsTtlBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new MetadataSyncProperties(null, new MetadataSyncProperties.Snapshot(0)));
    }

    @Test
    void rejectsInvalidCron() {
        assertThrows(IllegalArgumentException.class,
                () -> new MetadataSyncProperties(new MetadataSyncProperties.Sync(true, "not a cron"), null));
    }
}

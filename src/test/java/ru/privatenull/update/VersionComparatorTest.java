package ru.privatenull.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {
    @Test
    void comparesSemanticVersionsWithDifferentPartCounts() {
        assertTrue(VersionComparator.compare("1.1", "1.0.9") > 0);
        assertEquals(0, VersionComparator.compare("v1.0", "1.0.0"));
    }

    @Test
    void releaseIsNewerThanSnapshot() {
        assertTrue(VersionComparator.compare("1.0", "1.0-SNAPSHOT") > 0);
    }
}

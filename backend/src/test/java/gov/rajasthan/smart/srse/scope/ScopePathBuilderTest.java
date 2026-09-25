package gov.rajasthan.smart.srse.scope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScopePathBuilderTest {

    @Test
    void rootPathFromCode() {
        assertEquals("/G/", ScopePathBuilder.deriveChildPath(null, "G"));
    }

    @Test
    void childPathAppendsToParent() {
        ScopeDimension dimension = new ScopeDimension("GEO", "Geography", 1);
        ScopeLevel level = new ScopeLevel(dimension, 1, "L1");
        ScopeNode parent = new ScopeNode(dimension, level, null, "G", "Root", "/G/");
        assertEquals("/G/JAIPUR/", ScopePathBuilder.deriveChildPath(parent, "JAIPUR"));
    }

    @Test
    void rejectsSlashInCode() {
        assertThrows(IllegalArgumentException.class, () -> ScopePathBuilder.deriveChildPath(null, "A/B"));
    }
}

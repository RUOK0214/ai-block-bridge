package io.github.ruok0214.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptKindTest {
    @Test void readsTheHeaderTheModWrites() {
        assertEquals(ScriptKind.SCRIPT,ScriptKind.of("# AI Block Bridge Script v1\n0 0 0 | minecraft:stone"));
        assertEquals(ScriptKind.TIMELINE,ScriptKind.of("# AI Block Bridge Timeline v1\n\n@tick 1\n"));
        assertEquals(ScriptKind.TEST,ScriptKind.of("# AI Block Bridge Test v1\n@case a\nwait 1"));
        assertEquals(ScriptKind.SETTLE_REPORT,ScriptKind.of("# AI Block Bridge Settle Report v1\n"));
        assertEquals(ScriptKind.TEST_REPORT,ScriptKind.of("# AI Block Bridge Test Report v1\n"));
    }

    @Test void testAndTestReportAreNotConfused() {
        assertNotEquals(ScriptKind.of("# AI Block Bridge Test v1"),ScriptKind.of("# AI Block Bridge Test Report v1"));
    }

    @Test void headerMaySitAfterBlankLinesOrAByteOrderMark() {
        assertEquals(ScriptKind.SCRIPT,ScriptKind.of("﻿\n\n# AI Block Bridge Script v1\n"));
    }

    @Test void aHeaderBuriedBelowTheFirstCommentsIsIgnored() {
        String buried="# a\n# b\n# c\n# d\n# e\n# AI Block Bridge Script v1\n";
        assertEquals(ScriptKind.UNKNOWN,ScriptKind.of(buried));
    }

    @Test void textWithoutAHeaderIsAccepted() {
        assertEquals(ScriptKind.UNKNOWN,ScriptKind.of("@case a\nwait 1"));
        assertNull(ScriptKind.misplaced("@case a\nwait 1",ScriptKind.TEST));
        assertNull(ScriptKind.misplaced("0 0 0 | minecraft:stone",ScriptKind.SCRIPT));
    }

    @Test void theMatchingEditorIsNotFlagged() {
        assertNull(ScriptKind.misplaced("# AI Block Bridge Test v1\n@case a\nwait 1",ScriptKind.TEST));
        assertNull(ScriptKind.misplaced("# AI Block Bridge Script v1\n0 0 0 | minecraft:stone",ScriptKind.SCRIPT));
    }

    @Test void aPlacementScriptInTheTestEditorIsFlagged() {
        String message=ScriptKind.misplaced("# AI Block Bridge Script v1\n0 0 0 | minecraft:stone",ScriptKind.TEST);
        assertNotNull(message);
        assertTrue(Messages.isEncoded(message));
    }

    @Test void aTestScriptInTheStructureEditorIsFlagged() {
        assertNotNull(ScriptKind.misplaced("# AI Block Bridge Test v1\n@case a",ScriptKind.SCRIPT));
    }

    @Test void reportsAreFlaggedInBothEditors() {
        assertNotNull(ScriptKind.misplaced("# AI Block Bridge Settle Report v1\n",ScriptKind.SCRIPT));
        assertNotNull(ScriptKind.misplaced("# AI Block Bridge Test Report v1\n",ScriptKind.TEST));
    }
}

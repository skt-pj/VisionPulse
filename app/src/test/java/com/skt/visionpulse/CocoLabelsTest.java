package com.skt.visionpulse;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CocoLabelsTest {
    @Test
    public void containsCoco80Labels() {
        assertEquals(80, CocoLabels.NAMES.length);
        assertEquals("person", CocoLabels.NAMES[0]);
        assertEquals("toothbrush", CocoLabels.NAMES[79]);
    }
}

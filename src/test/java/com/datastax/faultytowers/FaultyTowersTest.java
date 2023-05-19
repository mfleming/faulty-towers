package com.datastax.faultytowers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FaultyTowersTest {

    @Test
    public void testParsingLongCmdOptions() {
        String[] args = new String[] {"--prob", "0.5", "--pid", "1234"};
        FaultyTowers faultyTowers = FaultyTowers.buildFaultyTowers(args);
        assertEquals(faultyTowers.getPid(), "1234");
        assertEquals(faultyTowers.getThrowProbability(), 0.5, 0.0);
    }

    @Test
    public void testParsingShortCmdOptions() {
        String[] args = new String[] {"-p", "0.5", "-P", "1234"};
        FaultyTowers faultyTowers = FaultyTowers.buildFaultyTowers(args);
        assertEquals(faultyTowers.getPid(), "1234");
        assertEquals(faultyTowers.getThrowProbability(), 0.5, 0.0);
    }

    @Test
    public void testMissingPid() {
        String[] args = new String[] {"-p", "0.5"};
        FaultyTowers faultyTowers = FaultyTowers.buildFaultyTowers(args);
        assertNull(faultyTowers);
    }

    @Test
    public void testNonExistentArgument() {
        String[] args = new String[] {"-p", "0.5", "-P", "1234", "-x", "foo"};
        FaultyTowers faultyTowers = FaultyTowers.buildFaultyTowers(args);
        assertNull(faultyTowers);
    }
}

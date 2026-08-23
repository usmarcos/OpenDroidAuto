package it.smg.hu.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UsbAccessoryIdsTest {
    @Test
    public void acceptsAllSupportedAoapV1AndV2Products() {
        assertTrue(UsbAccessoryIds.isAoap(0x18D1, 0x2D00));
        assertTrue(UsbAccessoryIds.isAoap(0x18D1, 0x2D01));
        assertTrue(UsbAccessoryIds.isAoap(0x18D1, 0x2D04));
        assertTrue(UsbAccessoryIds.isAoap(0x18D1, 0x2D05));
    }

    @Test
    public void rejectsOtherVendorsAndGoogleProducts() {
        assertFalse(UsbAccessoryIds.isAoap(0x18D1, 0x4EE7));
        assertFalse(UsbAccessoryIds.isAoap(0x2717, 0x2D00));
    }
}

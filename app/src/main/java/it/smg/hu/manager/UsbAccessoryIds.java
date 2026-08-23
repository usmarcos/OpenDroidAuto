package it.smg.hu.manager;

/** Android Open Accessory identifiers accepted by the legacy head unit. */
final class UsbAccessoryIds {
    static final int GOOGLE_VENDOR_ID = 0x18D1;
    static final int ACCESSORY = 0x2D00;
    static final int ACCESSORY_ADB = 0x2D01;
    static final int ACCESSORY_AUDIO = 0x2D04;
    static final int ACCESSORY_AUDIO_ADB = 0x2D05;

    private UsbAccessoryIds() {}

    static boolean isAoap(int vendorId, int productId) {
        if (vendorId != GOOGLE_VENDOR_ID) {
            return false;
        }
        return productId == ACCESSORY || productId == ACCESSORY_ADB
                || productId == ACCESSORY_AUDIO || productId == ACCESSORY_AUDIO_ADB;
    }
}

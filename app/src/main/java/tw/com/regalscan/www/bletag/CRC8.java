/**
 * Quuppa Android Tag Emulation Demo application.
 *
 * Copyright 2015 Quuppa Oy
 *
 * Disclaimer
 * THE SOURCE CODE, DOCUMENTATION AND SPECIFICATIONS ARE PROVIDED “AS IS”. ALL LIABILITIES, WARRANTIES AND CONDITIONS, EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION TO THOSE CONCERNING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT
 * OF THIRD PARTY INTELLECTUAL PROPERTY RIGHTS ARE HEREBY EXCLUDED.
 */
package tw.com.regalscan.www.bletag;

public class CRC8 {
    public static final byte INITIAL_REGISTER_VALUE = (byte)0x00;

    public static byte simpleCRC(java.io.InputStream s, byte reg) throws java.io.IOException {
        byte bitMask = (byte)(1 << 7);

        // Process each message byte.
        int value = s.read();
        while (value != -1) {
            byte element = (byte)value;

            reg ^= element;
            for (int i = 0; i < 8; i++) {
                if ((reg & bitMask) != 0) {
                    reg = (byte)((reg << 1) ^ 0x97);
                }
                else {
                    reg <<= 1;
                }
            }
            value = s.read();
        }
        reg ^= 0x00;

        return reg;
    }
    public static byte simpleCRC(byte[] buffer, byte register) throws java.io.IOException {
        java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(buffer);
        return simpleCRC(stream, register);
    }
    public static byte simpleCRC(byte[] buffer) throws java.io.IOException {
        return simpleCRC(buffer, INITIAL_REGISTER_VALUE);
    }
}

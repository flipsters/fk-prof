package fk.prof.nfr;

import java.util.Random;

/**
 * Created by gaurav.ashok on 12/04/17.
 */
public class RndGen {

    private final static int totalChars = 26 + 26 + 10;
    private final static char[] chars = new char[totalChars];
    {
        for(int i = 0; i < 26; ++i) {
            chars[i] = (char)('a' + i);
        }
        for(int i = 0; i < 26; ++i) {
            chars[26 + i] = (char)('A' + i);
        }
        for(int i = 0; i < 10; ++i) {
            chars[52 + i] = (char)('0' + i);
        }
    }

    byte[] bytes = new byte[1024 * 16];
    int idx = bytes.length;
    Random rnd = new Random();

    public boolean check(float prob) {
        ensureRndBytes();

        int limit = (int)(prob * 256.0f);
        int r = toInt(bytes[idx]);

        idx++;

        return r < limit;
    }

    public int getInt(int bound) {
        ensureRndBytes();

        int r = toInt(bytes[idx]);
        return bound * r / 256;
    }

    public String getString(int length) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < length; ++i) {
            sb.append(chars[getInt(totalChars)]);
        }
        return sb.toString();
    }

    public float getFloat() {
        float r = (float)toInt(bytes[idx]);
        return r / 256.0f;
    }

    private void ensureRndBytes() {
        if(idx == bytes.length) {
            rnd.nextBytes(bytes);
            idx = 0;
        }
    }

    private int toInt(byte b) {
        return ((int)b) & 0xff;
    }
}


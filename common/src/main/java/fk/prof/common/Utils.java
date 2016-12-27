package fk.prof.common;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public class Utils {

    public static final int AGGREGATION_FILE_MAGIC_NUM = 0x19A9F5C2;
    public static final int AGGREGATION_FILE_VERSION = 1;

    private static HashFunction murmur3_32 = Hashing.murmur3_32();

    public static HashFunction getHashFunctionForMurmur3_32() {
        return murmur3_32;
    }
}

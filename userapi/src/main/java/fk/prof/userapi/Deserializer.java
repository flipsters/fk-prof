package fk.prof.userapi;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Adler32;

/**
 * @author gaurav.ashok
 */
public abstract class Deserializer<T> {
    abstract public T deserialize(InputStream in);

    public static int readInt32(InputStream in) throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public static long readInt64(InputStream in) throws IOException {
        byte[] readBuffer = new byte[8];
        readFully(in, readBuffer);
        return (((long)readBuffer[0] << 56) +
                ((long)(readBuffer[1] & 255) << 48) +
                ((long)(readBuffer[2] & 255) << 40) +
                ((long)(readBuffer[3] & 255) << 32) +
                ((long)(readBuffer[4] & 255) << 24) +
                ((readBuffer[5] & 255) << 16) +
                ((readBuffer[6] & 255) <<  8) +
                ((readBuffer[7] & 255) <<  0));
    }

    public static byte[] read(InputStream is) throws IOException {
        // read size
        int size = readInt32(is);
        // read size bytes
        byte[] bytes = new byte[size];
        readFully(is, bytes);

        long checksum = readInt64(is);

        Adler32 adler32 = new Adler32();
        adler32.update(size >> 24);
        adler32.update(size >> 16);
        adler32.update(size >> 8);
        adler32.update(size);

        adler32.update(bytes);

        if(adler32.getValue() != checksum) {
            throw new IOException("Checksum failed");
        }

        return bytes;
    }

    private static int readFully(InputStream is, byte[] bytes) throws IOException {
        int len = bytes.length;
        int n = 0;
        while (n < len) {
            int count = is.read(bytes, n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
        return n;
    }
}

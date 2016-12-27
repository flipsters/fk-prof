package fk.prof.aggregation.serialize;

import com.google.protobuf.Message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Adler32;

/**
 * @author gaurav.ashok
 */
public abstract class Serializer<T> {

    public abstract void serialize(T object, OutputStream os) throws IOException;

    public static void writeInt32(int value, OutputStream os) throws IOException {
        byte[] bytes = {(byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value};
        os.write(bytes);
    }

    public static void writeInt64(long value, OutputStream os) throws IOException {
        writeInt32((int)(value >> 32), os);
        writeInt32((int)value, os);
    }

    public static void write(Message message, OutputStream os) throws IOException {
        byte[] bytes = message.toByteArray();
        int size = bytes.length;

        Adler32 adler32 = new Adler32();
        adler32.update(size >> 24);
        adler32.update(size >> 16);
        adler32.update(size >> 8);
        adler32.update(size);

        adler32.update(bytes);

        writeInt32(size, os);
        os.write(bytes);
        writeInt64(adler32.getValue(), os);
    }
}

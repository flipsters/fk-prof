package fk.prof.storage.util;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Static class to provide helpful transformations for I/O Streams.
 * @author gaurav.ashok
 */
public final class StreamTransformer {

    /* Hide the constructor */
    private StreamTransformer() {}

    public static final int DEFAULT_PIPE_BUFFER_SIZE = 8192;
    public static final int DEFAULT_GZIP_BUFFER_SIZE = 512;

    public static OutputStream outputStreamFor(InputStream in) throws IOException {
        return outputStreamFor(in, DEFAULT_PIPE_BUFFER_SIZE);
    }

    public static OutputStream outputStreamFor(InputStream in, int pipeBufferSize) throws IOException {
        return new PipedOutputStream(new PipedInputStream(pipeBufferSize));
    }

    public static GZIPOutputStream zip(OutputStream out) throws IOException {
        return zip(out, DEFAULT_GZIP_BUFFER_SIZE);
    }

    public static GZIPOutputStream zip(OutputStream out, int zipBufferSize) throws IOException {
        return new GZIPOutputStream(out, zipBufferSize);
    }

    public static GZIPInputStream unzip(InputStream in) throws IOException {
        return new GZIPInputStream(in, DEFAULT_GZIP_BUFFER_SIZE);
    }

    public static GZIPInputStream unzip(InputStream in, int zipBufferSize) throws IOException {
        return new GZIPInputStream(in, zipBufferSize);
    }

    public static byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream zout = zip(out);
        zout.write(bytes);
        zout.close();
        return out.toByteArray();
    }
}

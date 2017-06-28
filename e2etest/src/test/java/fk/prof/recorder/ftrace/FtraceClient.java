package fk.prof.recorder.ftrace;

import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class FtraceClient {
  public static void main(String[] args) throws InterruptedException, IOException {
    java.io.File path = new java.io.File("/var/tmp/fkp-tracer.sock");
    int retries = 0;
    while (!path.exists()) {
      TimeUnit.MILLISECONDS.sleep(500L);
      retries++;
      if (retries > 5) {
        throw new IOException(String.format("File %s does not exist after retry", path.getAbsolutePath()));
      }
    }

    UnixSocketAddress address = new UnixSocketAddress(path);
    UnixSocketChannel channel = UnixSocketChannel.open(address);
    System.out.println("connected to " + channel.getRemoteSocketAddress());

  }
}

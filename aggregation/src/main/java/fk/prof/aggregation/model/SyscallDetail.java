package fk.prof.aggregation.model;

import com.tdunning.math.stats.MergingDigest;

public class SyscallDetail {
  private final static double DEFAULT_DIGEST_COMPRESSION = 100.0;

  private int count = 0;
  private long totalLatency = 0;
  private final MergingDigest latencyDigest;

  public SyscallDetail() {
    this(DEFAULT_DIGEST_COMPRESSION);
  }

  public SyscallDetail(double digestCompression) {
    latencyDigest = new MergingDigest(digestCompression);
  }

  public synchronized void addEntry(long latency) {
    count++;
    totalLatency += latency;
    latencyDigest.add(latency);
  }
}

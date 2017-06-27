package fk.prof.aggregation.model;

import com.tdunning.math.stats.MergingDigest;
import fk.prof.aggregation.proto.AggregatedProfileModel;

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

  public AggregatedProfileModel.SyscallProps.Builder buildPartialProto() {
    return AggregatedProfileModel.SyscallProps.newBuilder()
        .setCount(count)
        .setTotalLatency(totalLatency)
        .setLatencyDigest(CtxSwitchDetail.buildFromDigest(latencyDigest));
  }
}

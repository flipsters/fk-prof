package fk.prof.aggregation.model;

import com.google.protobuf.ByteString;
import com.koloboke.collect.map.hash.HashLongObjMap;
import com.koloboke.collect.map.hash.HashLongObjMaps;
import com.tdunning.math.stats.MergingDigest;
import fk.prof.aggregation.proto.AggregatedProfileModel;

import java.nio.ByteBuffer;
import java.util.Map;

public class CtxSwitchDetail {
  private final static int DEFAULT_DIGEST_COMPRESSION = 100;

  private int volCount = 0;
  private long volTotalLatency = 0;
  private int volMigrations = 0;
  private final MergingDigest volLatencyDigest;
  private final MergingDigest volWakeupLagDigest;

  private int involCount = 0;
  private long involTotalLatency = 0;
  private int involMigrations = 0;
  private final MergingDigest involLatencyDigest;

  private final HashLongObjMap<SyscallDetail> syscalls = HashLongObjMaps.newUpdatableMap();

  public CtxSwitchDetail() {
    volLatencyDigest = new MergingDigest(DEFAULT_DIGEST_COMPRESSION);
    volWakeupLagDigest = new MergingDigest(DEFAULT_DIGEST_COMPRESSION);
    involLatencyDigest = new MergingDigest(DEFAULT_DIGEST_COMPRESSION);
  }

  public void addEntry(boolean voluntary, boolean migrated, long latency, Long wakeupLag, Long syscallId) {
    if(voluntary) {
      volCount++;
      volTotalLatency += latency;
      volLatencyDigest.add(latency);
      if(wakeupLag != null) {
        volWakeupLagDigest.add(wakeupLag);
      }
      if(migrated) {
        volMigrations++;
      }
    } else {
      involCount++;
      involTotalLatency += latency;
      involLatencyDigest.add(latency);
      if(migrated) {
        involMigrations++;
      }
    }

    if(syscallId != null) {
      SyscallDetail syscallDetail = syscalls.get(syscallId.longValue());
      if(syscallDetail == null) {
        syscallDetail = new SyscallDetail();
        syscalls.put(syscallId.longValue(), syscallDetail);
      }
      syscallDetail.addEntry(latency);
    }
  }

  /**
   * Does not check equality of quantile digests
   * @param o
   * @return
   */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CtxSwitchDetail)) {
      return false;
    }

    CtxSwitchDetail other = (CtxSwitchDetail) o;
    return this.volCount == other.volCount
        && this.volTotalLatency == other.volTotalLatency
        && this.volMigrations == other.volMigrations
        && this.involCount == other.involCount
        && this.involTotalLatency == other.involTotalLatency
        && this.involMigrations == other.involMigrations;
  }

  protected AggregatedProfileModel.CtxSwitchNodeProps buildCtxSwitchNodePropsProto() {
    AggregatedProfileModel.CtxSwitchNodeProps.Builder builder = AggregatedProfileModel.CtxSwitchNodeProps.newBuilder();
    builder.setVolCount(volCount).setVolTotalLatency(volTotalLatency).setVolMigrations(volMigrations);
    builder.setInvolCount(involCount).setInvolTotalLatency(involTotalLatency).setInvolMigrations(involMigrations);
    builder.setVolLatencyDigest(buildFromDigest(volLatencyDigest));
    builder.setVolWakeupLagDigest(buildFromDigest(volWakeupLagDigest));
    builder.setInvolLatencyDigest(buildFromDigest(involLatencyDigest));

    for(Map.Entry<Long, SyscallDetail> syscall: syscalls.entrySet()) {
      builder.addSyscall(syscall.getValue().buildPartialProto().setSyscallId(syscall.getKey()).build());
    }

    return builder.build();
  }

  /**
   * Serialized bytes representing digest are copied in bytestring to maintain immutability
   * TODO: Explore writing directly to a stream from digest which is given as input to bytestring builder to avoid copy
   * @param digest
   * @return Serializes digest and returns a protobuf bytestring
   */
  public static ByteString buildFromDigest(MergingDigest digest) {
    ByteBuffer digestBuffer = ByteBuffer.wrap(new byte[digest.byteSize()]);
    digest.asBytes(digestBuffer);
    return ByteString.copyFrom(digestBuffer);
  }

}

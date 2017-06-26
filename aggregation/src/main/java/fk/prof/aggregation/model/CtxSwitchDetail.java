package fk.prof.aggregation.model;

import com.koloboke.collect.map.hash.HashLongObjMap;
import com.koloboke.collect.map.hash.HashLongObjMaps;
import com.tdunning.math.stats.MergingDigest;

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

}

package fk.prof.aggregation.stacktrace.cpusampling;

import java.util.concurrent.atomic.AtomicInteger;

public class CpuSamplingContextDetail {
    private CpuSamplingFrameNode threadRunRoot = null;
    private CpuSamplingFrameNode unclassifiableRoot = null;

    private final AtomicInteger samplesCount = new AtomicInteger(0);

    public CpuSamplingFrameNode getThreadRunRoot() {
        return this.threadRunRoot;
    }

    public void setThreadRunRoot(CpuSamplingFrameNode threadRunRoot) {
        this.threadRunRoot = threadRunRoot;
    }

    public CpuSamplingFrameNode getUnclassifiableRoot() {
        return this.unclassifiableRoot;
    }

    public void setUnclassifiableRoot(CpuSamplingFrameNode unclassifiableRoot) {
        this.unclassifiableRoot = unclassifiableRoot;
    }

    public int getSamplesCount() {
        return samplesCount.get();
    }
}

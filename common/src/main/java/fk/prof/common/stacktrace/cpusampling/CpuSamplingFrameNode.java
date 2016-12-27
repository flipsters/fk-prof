package fk.prof.common.stacktrace.cpusampling;

import fk.prof.common.stacktrace.TreeNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CpuSamplingFrameNode implements TreeNode<CpuSamplingFrameNode> {
    private final int methodId;
    private final int lineNumber;
    private final List<CpuSamplingFrameNode> children = new ArrayList<>(2);

    private final AtomicInteger onStackSamples = new AtomicInteger(0);
    private final AtomicInteger onCpuSamples = new AtomicInteger(0);

    public CpuSamplingFrameNode(int methodId, int lineNumber) {
        this.methodId = methodId;
        this.lineNumber = lineNumber;
    }

    public CpuSamplingFrameNode getOrAddChild(int childMethodId, int childLineNumber) {
        synchronized (children) {
            CpuSamplingFrameNode result = null;
            for(CpuSamplingFrameNode child : children) {
                if (child.methodId == childMethodId && child.lineNumber == childLineNumber) {
                    result = child;
                    break;
                }
            }

            if (result == null) {
                result = new CpuSamplingFrameNode(childMethodId, childLineNumber);
                children.add(result);
            }

            return result;
        }
    }

    public int getMethodId() {
        return this.methodId;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }

    public int getOnStackSamples() {
        return this.onStackSamples.get();
    }

    public int incrementOnStackSamples () {
        return this.onStackSamples.incrementAndGet();
    }

    public int getOnCpuSamples() {
        return this.onCpuSamples.get();
    }

    public int incrementOnCpuSamples () {
        return this.onCpuSamples.incrementAndGet();
    }

    @Override
    public int childCount() {
        return children.size();
    }

    @Override
    public Iterable<CpuSamplingFrameNode> children() {
        return children;
    }
}

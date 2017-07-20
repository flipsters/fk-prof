package fk.prof.userapi.model.tree;

import fk.prof.aggregation.proto.AggregatedProfileModel.FrameNode;
import fk.prof.userapi.Cacheable;
import fk.prof.userapi.model.Tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by gaurav.ashok on 01/06/17.
 */
public class CalleesTreeView implements Cacheable {

    private Tree<FrameNode> callTree;
    private List<IndexedTreeNode<HotMethodNode>> hotMethods;

    public CalleesTreeView(Tree<FrameNode> callTree) {
        this.callTree = callTree;
        findHotMethods();
    }

    public List<IndexedTreeNode<HotMethodNode>> getHotMethods() {
        return hotMethods;
    }

    public List<IndexedTreeNode<HotMethodNode>> getCallers(List<IndexedTreeNode<HotMethodNode>> originNodes, int depth) {
        List<IndexedTreeNode<HotMethodNode>> nodes = new ArrayList<>(originNodes.size());
        for(int i = 0; i < originNodes.size(); ++i) {
            IndexedTreeNode<HotMethodNode> origin = originNodes.get(i);
            nodes.add(getCaller(origin.getIdx(), 0, depth, origin.getData().sampleCount));
        }
        return nodes;
    }

    private IndexedTreeNode<HotMethodNode> getCaller(int idx, int i, int depth, int sampleCount) {
        int callerIdx;
        if(i < depth && (callerIdx = callTree.getParent(idx)) != -1) {
            FrameNode fn = callTree.get(callerIdx);
            IndexedTreeNode<HotMethodNode> caller = getCaller(callerIdx, i + 1, depth, sampleCount);
            return new IndexedTreeNode<>(callerIdx, new HotMethodNode(fn.getMethodId(), fn.getLineNo(), sampleCount),
                caller != null ? Collections.singletonList(caller) : null);
        }
        return null;
    }

    private void findHotMethods() {
        hotMethods = new ArrayList<>();
        callTree.foreach((i, fn) -> {
            int cpuSampleCount = fn.getCpuSamplingProps().getOnCpuSamples();
            if(cpuSampleCount > 0) {
                hotMethods.add(new IndexedTreeNode<>(i, new HotMethodNode(fn.getMethodId(), fn.getLineNo(), cpuSampleCount)));
            }
        });
    }

    public static class HotMethodNode {
        public final int methodId;
        public final int lineNo;
        public final int sampleCount;

        public HotMethodNode(int sampleCount) {
            this.sampleCount = sampleCount;
            this.methodId = -1;
            this.lineNo = -1;
        }

        public HotMethodNode(int methodId, int lineNo, int sampleCount) {
            this.methodId = methodId;
            this.lineNo = lineNo;
            this.sampleCount = sampleCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HotMethodNode that = (HotMethodNode) o;

            if (methodId != that.methodId) return false;
            if (lineNo != that.lineNo) return false;
            return sampleCount == that.sampleCount;
        }

        @Override
        public int hashCode() {
            int result = methodId;
            result = 31 * result + lineNo;
            result = 31 * result + sampleCount;
            return result;
        }
    }
}

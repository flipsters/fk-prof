package fk.prof.userapi.model.tree;

import fk.prof.aggregation.proto.AggregatedProfileModel.FrameNode;
import fk.prof.userapi.Cacheable;
import fk.prof.userapi.model.Tree;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by gaurav.ashok on 01/06/17.
 */
public class CalleesTreeView implements Cacheable {

    private Tree<FrameNode> callTree;
    private List<IndexedTreeNode<HotMethodNode>> hotMethods;

    public CalleesTreeView(Tree<FrameNode> callTree) {
        this.callTree = callTree;
        findOnCpuFrames();
    }

    public List<IndexedTreeNode<HotMethodNode>> getRootNodes() {
        return hotMethods;
    }

    public List<IndexedTreeNode<HotMethodNode>> getSubTree(List<Integer> ids, int depth, boolean autoExpand) {
        return new Expander(ids, depth, autoExpand).expand();
    }

    private void findOnCpuFrames() {
        hotMethods = new ArrayList<>();
        callTree.foreach((i, fn) -> {
            int cpuSampleCount = fn.getCpuSamplingProps().getOnCpuSamples();
            if(cpuSampleCount > 0) {
                hotMethods.add(new IndexedTreeNode<>(i, new HotMethodNode(fn)));
            }
        });
    }

    private class Expander {
        List<Integer> ids;
        int maxDepth;
        boolean autoExpand;

        Expander(List<Integer> ids, int maxDepth, boolean autoExpand) {
            this.ids = ids;
            this.maxDepth = maxDepth;
            this.autoExpand = autoExpand;
        }

        List<IndexedTreeNode<HotMethodNode>> expand() {
            Map<Integer, List<IndexedTreeNode<HotMethodNode>>> idxGroupedByMethodId = ids.stream()
                .map(e -> new IndexedTreeNode<>(e, new HotMethodNode(callTree.get(e))))
                .collect(Collectors.groupingBy(e -> e.getData().methodId));
            return idxGroupedByMethodId.values().stream().peek(this::expand).flatMap(List::stream).collect(Collectors.toList());
        }

        void expand(List<IndexedTreeNode<HotMethodNode>> nodes) {
            // next set of callers.
            List<IndexedTreeNode<HotMethodNode>> callers = new ArrayList<>(nodes);
            HashSet<Integer> methodidSet = new HashSet<>();

            for(int d = 0; d < maxDepth; ++d) {
                methodidSet.clear();

                for(int i = 0; i < nodes.size(); ++i) {
                    int callerId = callTree.getParent(callers.get(i).getIdx());
                    // if parent node exist, add it as a caller to the current caller, and update the current caller
                    if(callerId > 0) {
                        FrameNode fn = callTree.get(callerId);
                        IndexedTreeNode<HotMethodNode> caller = new IndexedTreeNode<>(callerId, new HotMethodNode(fn));
                        callers.get(i).setChildren(Collections.singletonList(caller));
                        callers.set(i, caller);
                        // add the methodid to set
                        methodidSet.add(fn.getMethodId());
                    }
                }
                // if there are > 1 distinct caller, stop autoExpansion
                if(autoExpand && methodidSet.size() > 1) {
                    return;
                }
            }
        }
    }


    public static class HotMethodNode {
        public final int methodId;
        public final int lineNo;
        public final int sampleCount;

        public HotMethodNode(FrameNode fn) {
            this.methodId = fn.getMethodId();
            this.lineNo = fn.getLineNo();
            this.sampleCount = fn.getCpuSamplingProps().getOnCpuSamples();
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

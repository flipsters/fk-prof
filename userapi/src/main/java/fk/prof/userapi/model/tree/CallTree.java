package fk.prof.userapi.model.tree;

import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.aggregation.proto.AggregatedProfileModel.FrameNode;
import fk.prof.userapi.Cacheable;
import fk.prof.userapi.model.Tree;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Created by gaurav.ashok on 01/06/17.
 */
public class CallTree implements Tree<FrameNode>, Cacheable {

    protected List<FrameNode> nodes;
    private int[] subtreeSizes;
    private int[] parentIds;

    public CallTree(List<FrameNode> frameNodes) {
        this.nodes = frameNodes;
        treeify();
    }

    public static CallTree parseFrom(InputStream in) throws IOException {
        int nodeCount = 1; // for root node
        int parsedNodeCount = 0;
        List<FrameNode> parsedFrameNodes = new ArrayList<>();
        do {
            AggregatedProfileModel.FrameNodeList frameNodeList = AggregatedProfileModel.FrameNodeList.parseDelimitedFrom(in);
            for(FrameNode node: frameNodeList.getFrameNodesList()) {
                nodeCount += node.getChildCount();
            }
            parsedNodeCount += frameNodeList.getFrameNodesCount();
            parsedFrameNodes.addAll(frameNodeList.getFrameNodesList());
        } while(parsedNodeCount < nodeCount && parsedNodeCount > 0);

        return new CallTree(parsedFrameNodes);
    }

    @Override
    public FrameNode get(int idx) {
        return nodes.get(idx);
    }

    @Override
    public int getChildrenSize(int idx) {
        return nodes.get(idx).getChildCount();
    }

    @Override
    public Iterable<Integer> getChildren(int idx) {
        return () -> new Iterator<Integer>() {
            private int childCount = getChildrenSize(idx);
            private int childCounter = 0;
            private int offset = 1;
            @Override
            public boolean hasNext() {
                return childCounter < childCount;
            }

            @Override
            public Integer next() {
                if(!hasNext()) {
                    throw new NoSuchElementException();
                }
                ++childCounter;
                int nextChildIdx = idx + offset;
                offset += subtreeSizes[nextChildIdx];
                return nextChildIdx;
            }
        };
    }

    @Override
    public int getMaxSize() {
        return nodes.size();
    }

    @Override
    public void foreach(Visitor<FrameNode> visitor) {
        for(int i = 0; i < nodes.size(); ++i) {
            visitor.visit(i, nodes.get(i));
        }
    }

    @Override
    public int getParent(int idx) {
        return parentIds[idx];
    }

    private void treeify() {
        subtreeSizes = new int[nodes.size()];
        parentIds = new int[nodes.size()];

        int treeSize = buildTree(0, 1, -1) - 1;
        if(treeSize != nodes.size()) {
            throw new IllegalStateException("not able to build calltree");
        }
    }

    private int buildTree(int idx, int childCount, int parentIdx) {
        int treeSize = 0;
        for(int i = 0; i < childCount; ++i) {
            AggregatedProfileModel.FrameNode child = nodes.get(idx + treeSize);
            parentIds[idx + treeSize] = parentIdx;

            int subTreeSize = buildTree(idx + 1 + treeSize, child.getChildCount(), idx + treeSize);
            subtreeSizes[idx + treeSize] = subTreeSize;
            treeSize += subTreeSize;
        }
        // add 1 for the node itself
        return treeSize + 1;
    }
}

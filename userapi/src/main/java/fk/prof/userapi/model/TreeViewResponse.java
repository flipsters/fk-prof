package fk.prof.userapi.model;

import fk.prof.userapi.model.tree.IndexedTreeNode;

import java.util.List;
import java.util.Map;

/**
 * Created by gaurav.ashok on 07/08/17.
 */
public class TreeViewResponse<T> {

    private List<IndexedTreeNode<T>> tree;
    private Map<Integer, String> methodLookup;

    public TreeViewResponse(List<IndexedTreeNode<T>> tree, Map<Integer, String> methodLookup) {
        this.tree = tree;
        this.methodLookup = methodLookup;
    }

    public List<IndexedTreeNode<T>> getTree() {
        return tree;
    }

    public Map<Integer, String> getMethodLookup() {
        return methodLookup;
    }
}

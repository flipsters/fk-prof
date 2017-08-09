package fk.prof.userapi.model.tree;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fk.prof.userapi.model.Tree;

import java.util.List;

/**
 * Created by gaurav.ashok on 01/06/17.
 */
public class IndexedTreeNode<T> {
    public IndexedTreeNode(int idx, T data) {
        this(idx, data, null);
    }

    public IndexedTreeNode(int idx, T data, List<IndexedTreeNode<T>> children) {
        this.idx = idx;
        this.data = data;
        this.children = children;
    }

    private final int idx;

    private final T data;

    private List<IndexedTreeNode<T>> children;

    public int getIdx() {
        return idx;
    }

    public T getData() {
        return data;
    }

    public List<IndexedTreeNode<T>> getChildren() {
        return children;
    }

    public void visit(Tree.Visitor<T> visitor) {
        visitor.visit(idx, data);
        if(children != null) {
            for(IndexedTreeNode node : children) {
                node.visit(visitor);
            }
        }
    }

    @JsonSerialize()
    public IndexedTreeNode<T> setChildren(List<IndexedTreeNode<T>> children) {
        this.children = children;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IndexedTreeNode<?> that = (IndexedTreeNode<?>) o;

        if (idx != that.idx) return false;
        return data.equals(that.data);
    }

    @Override
    public int hashCode() {
        int result = idx;
        result = 31 * result + data.hashCode();
        return result;
    }
}
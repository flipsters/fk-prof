package fk.prof.aggregation.stacktrace;

/**
 * @author gaurav.ashok
 */
public interface TreeNode<T> {
    int childCount();
    Iterable<T> children();
}

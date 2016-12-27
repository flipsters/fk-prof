package fk.prof.common.stacktrace;

/**
 * @author gaurav.ashok
 */
public interface TreeNode<T> {
    int childCount();
    Iterable<T> children();
}

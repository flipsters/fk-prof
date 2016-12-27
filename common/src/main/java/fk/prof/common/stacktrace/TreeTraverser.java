package fk.prof.common.stacktrace;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * @author gaurav.ashok
 */
public class TreeTraverser<T extends TreeNode<T>> {

    private Consumer<T> consumer;

    public TreeTraverser(Consumer<T> consumer) {
        this.consumer = consumer;
    }

    /**
     * node must not be null and must be ensured while building tree.
     * @param node
     */
    public void traverse(T node) {
        consumer.accept(node);
        Iterator<T> children = node.children().iterator();
        while(children.hasNext()) {
            T child = children.next();
            traverse(child);
        }
    }
}

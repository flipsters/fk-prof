package fk.prof.userapi.model;

/**
 * Created by gaurav.ashok on 01/06/17.
 */
public interface Tree<T> {

    T get(int idx);

    int getChildrenSize(int idx);

    Iterable<Integer> getChildren(int idx);

    int getParent(int idx);

    int getMaxSize();

    void foreach(Visitor<T> visitor);

    interface Visitor<T> {
        void visit(int idx, T node);
    }
}

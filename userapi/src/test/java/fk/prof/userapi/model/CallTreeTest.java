package fk.prof.userapi.model;

import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.aggregation.model.AggregationWindowStorage;
import fk.prof.aggregation.model.FinalizedAggregationWindow;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.aggregation.proto.AggregatedProfileModel.FrameNode;
import fk.prof.storage.AsyncStorage;
import fk.prof.userapi.api.AggregatedProfileLoader;
import fk.prof.userapi.api.MockAggregationWindow;
import fk.prof.userapi.model.tree.*;
import fk.prof.userapi.model.tree.CalleesTreeView.HotMethodNode;
import io.vertx.core.Future;

import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.*;

/**
 * Created by gaurav.ashok on 06/06/17.
 */
public class CallTreeTest {

    private static final String sampleStackTraces = "[\n" +
        "  [\"A()\",\"D()\",\"G()\"],\n" +
        "  [\"A()\",\"D()\"],[\"A()\",\"D()\"],[\"A()\",\"D()\"],[\"A()\",\"D()\"],[\"A()\",\"D()\"],[\"A()\",\"D()\"],\n" +
        "  [\"A()\", \"B()\", \"C()\"],[\"A()\", \"B()\", \"C()\"],\n" +
        "  [\"A()\",\"B()\"],[\"A()\",\"B()\"],[\"A()\",\"B()\"],[\"A()\",\"B()\"],[\"A()\",\"B()\"],\n" +
        "  [\"E()\", \"F()\", \"B()\", \"C()\"],[\"E()\", \"F()\", \"B()\", \"C()\"],[\"E()\", \"F()\", \"B()\", \"C()\"],\n" +
        "  [\"E()\", \"F()\", \"B()\"],[\"E()\", \"F()\", \"B()\"],\n" +
        "  [\"E()\", \"F()\", \"D()\"],[\"E()\", \"F()\", \"D()\"],[\"E()\", \"F()\", \"D()\"],[\"E()\", \"F()\", \"D()\"]\n" +
        "]";

    private static CallTree calltree;
    private static List<String> methodIdLookup;
    private static IndexedTreeNode<FrameNode> expectedTree;
    private static List<IndexedTreeNode<HotMethodNode>> expectedHotMethodsTree;

    @BeforeClass
    public static void setup() throws Exception {
        AsyncStorage asyncStorage = new MockAggregationWindow.HashMapBasedStorage();
        AggregationWindowStorage storage = MockAggregationWindow.buildMockAggregationWindowStorage(asyncStorage);

        String startime = "2017-03-01T07:00:00";
        ZonedDateTime startimeZ = ZonedDateTime.parse(startime + "Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
        FinalizedAggregationWindow window = MockAggregationWindow.buildAggregationWindow(startime, () -> sampleStackTraces, 1800);

        // serialize and store
        storage.store(window);

        // init loader
        AggregatedProfileLoader loader = new AggregatedProfileLoader(asyncStorage);

        Future<AggregatedProfileInfo> f1 =  Future.future();
        AggregatedProfileNamingStrategy file1 = new AggregatedProfileNamingStrategy("profiles", 1, "app1", "cluster1", "proc1", startimeZ, 1800, AggregatedProfileModel.WorkType.cpu_sample_work);
        // load
        loader.load(f1, file1);

        // get cpu sampling data
        AggregatedSamplesPerTraceCtx fullTraceDetail = f1.result().getAggregatedSamples("full-app-trace");
        methodIdLookup = fullTraceDetail.getMethodLookup();

        AggregatedOnCpuSamples cpusampleData = (AggregatedOnCpuSamples) fullTraceDetail.getAggregatedSamples();
        calltree = cpusampleData.getCallTree();

        // expected call tree
        expectedTree =
            node(0, 0, 0, 23,
                node(1, 1, 0, 0),
                node(2, mId("A()"), 0, 14,
                    node(3, mId("D()"), 6, 7,
                        node(4, mId("G()"), 1, 1)),
                    node(5, mId("B()"), 5, 7,
                        node(6, mId("C()"), 2, 2))),
                node(7, mId("E()"), 0, 9,
                    node(8, mId("F()"), 0, 9,
                        node(9, mId("B()"), 2, 5,
                            node(10, mId("C()"), 3, 3)),
                        node(11, mId("D()"), 4, 4))));

        // expected hotMethodTree
        expectedHotMethodsTree =
            Arrays.asList(
                hmnode(4, mId("G()"), 1,
                    hmnode(3, mId("D()"), 1,
                        hmnode(2, mId("A()"), 1,
                            hmnode(0, 0, 1)))),
                hmnode(3, mId("D()"), 6,
                    hmnode(2, mId("A()"), 6,
                        hmnode(0, 0, 6))),
                hmnode(6, mId("C()"), 2,
                    hmnode(5, mId("B()"), 2,
                        hmnode(2, mId("A()"), 2,
                            hmnode(0, 0, 2)))),
                hmnode(5, mId("B()"), 5,
                    hmnode(2, mId("A()"), 5,
                        hmnode(0, 0, 5))),
                hmnode(10, mId("C()"), 3,
                    hmnode(9, mId("B()"), 3,
                        hmnode(8, mId("F()"), 3,
                            hmnode(7, mId("E()"), 3,
                                hmnode(0, 0, 3))))),
                hmnode(9, mId("B()"), 2,
                    hmnode(8, mId("F()"), 2,
                        hmnode(7, mId("E()"), 2,
                            hmnode(0, 0, 2)))),
                hmnode(11, mId("D()"), 4,
                    hmnode(8, mId("F()"), 4,
                        hmnode(7, mId("E()"), 4,
                            hmnode(0, 0, 4)))));
    }

    @Test
    public void testCallTreeIsCorrect() {
        testTreeEquality(expectedTree, calltree);
    }

    @Test
    public void testCallTreeView() {
        CallTreeView ctv = new CallTreeView(calltree);
        /* children of root node */
        Matcher m = hasItems(toArray(expectedTree.getChildren()));
        Assert.assertThat(ctv.getSubTree(ctv.getRootNode().getIdx(), 1), m);

        /* E -> F -> B
                  -> D
         */
        List<IndexedTreeNode<FrameNode>> e_subtree = ctv.getSubTree(7, 2);
        testTreeEquality(e_subtree, expectedTree.getChildren().get(2).getChildren());

        /* A -> B -> C
             -> D -> G
         */
        List<IndexedTreeNode<FrameNode>> a_subtree = ctv.getSubTree(2, 10);
        testTreeEquality(a_subtree, expectedTree.getChildren().get(1).getChildren());

        /* full tree */
        List<IndexedTreeNode<FrameNode>> fulltree = ctv.getSubTree(0, 10);
        testTreeEquality(fulltree, expectedTree.getChildren());
    }

    @Test
    public void testSearchFilteredCallTreeView() {
        // subtree that contains B() in its branches.
        int b_id = mId("B()");
        List<Integer> hiddenNodes = Arrays.asList(1, 3, 11);

        FilteredTree<FrameNode> mt = new FilteredTree<>(calltree, (i, fn) -> fn.getMethodId() == b_id);
        CallTreeView ctv = new CallTreeView(mt);

        /* children of root node */
        Matcher m = hasItems(toArray(filterTree(expectedTree.getChildren(), hiddenNodes)));
        Assert.assertThat(ctv.getSubTree(ctv.getRootNode().getIdx(), 1), m);

        /* E -> F -> B
           Branch with D() [id:11] will not be visible
         */
        List<IndexedTreeNode<FrameNode>> e_subtree = ctv.getSubTree(7, 2);
        testTreeEquality(e_subtree,
            filterTree(expectedTree.getChildren().get(2).getChildren(), hiddenNodes));

        /* full tree
           Branch under nodes 1, 3, 11 will not be visible
         */
        List<IndexedTreeNode<FrameNode>> fulltree = ctv.getSubTree(0, 10);
        testTreeEquality(fulltree,
            filterTree(expectedTree.getChildren(), hiddenNodes));
    }

    @Test
    public void testHotMethodView() {
        CalleesTreeView hmtv = new CalleesTreeView(calltree);

        // all hotmethods
        List<IndexedTreeNode<HotMethodNode>> hm = hmtv.getHotMethods();
        testTreeEquality(hm, expectedHotMethodsTree);

        // get callers of C() [sampleCount:3]
        IndexedTreeNode<HotMethodNode> C3_hotMethod = hm.stream().filter(e -> e.getIdx() == 10).findFirst().get();
        List<IndexedTreeNode<HotMethodNode>> C3_callers_2deep = hmtv.getCallers(Arrays.asList(C3_hotMethod), 2);
        testTreeEquality(C3_callers_2deep, expectedHotMethodsTree.get(4).getChildren());

        // get callers of D() [sampleCount:6]
        IndexedTreeNode<HotMethodNode> D6_hotMethod = hm.stream().filter(e -> e.getIdx() == 3).findFirst().get();
        List<IndexedTreeNode<HotMethodNode>> D6_callers_1deep = hmtv.getCallers(Arrays.asList(D6_hotMethod), 1);
        testTreeEquality(D6_callers_1deep, expectedHotMethodsTree.get(1).getChildren());
    }

    @Test
    public void testSearchFilteredHotMethodView() {
        // subtree that contains D() in its branches.
        int d_id = mId("D()");
        List<Integer> hiddenNodes = Arrays.asList(1, 5, 9);

        FilteredTree<FrameNode> mt = new FilteredTree<>(calltree, (i, fn) -> fn.getMethodId() == d_id);
        CalleesTreeView hmtv = new CalleesTreeView(mt);

        /* all hotmethods
           only those branches will be visible which has D in it, i.e. 0, 1, 6th element from expectedHotMethodTree
         */
        List<IndexedTreeNode<HotMethodNode>> hm = hmtv.getHotMethods();
        testTreeEquality(hm,
            Arrays.asList(expectedHotMethodsTree.get(0), expectedHotMethodsTree.get(1), expectedHotMethodsTree.get(6)));

        // get callers of G() [sampleCount:1]
        IndexedTreeNode<HotMethodNode> G1_hotMethod = hm.stream().filter(e -> e.getIdx() == 4).findFirst().get();
        List<IndexedTreeNode<HotMethodNode>> G1_callers_5deep = hmtv.getCallers(Arrays.asList(G1_hotMethod), 5);
        testTreeEquality(G1_callers_5deep, expectedHotMethodsTree.get(0).getChildren());
    }

    private void testTreeEquality(IndexedTreeNode<FrameNode> node, CallTree callTree) {
        Assert.assertEquals(node.getData(), callTree.get(node.getIdx()));
        for(IndexedTreeNode<FrameNode> child: node.getChildren()) {
            testTreeEquality(child, callTree);
        }
    }

    private <T> void testTreeEquality(IndexedTreeNode<T> actual, IndexedTreeNode<T> expected) {
        Assert.assertEquals(expected, actual);
        if(actual.getChildren() != null) {
            testTreeEquality(actual.getChildren(), expected.getChildren());
        }
    }

    private <T> void testTreeEquality(List<IndexedTreeNode<T>> actual, List<IndexedTreeNode<T>> expected) {
        Assert.assertTrue(actual != null ? actual.size() == expected.size() : true);
        for(IndexedTreeNode<T> child: actual) {
            testTreeEquality(child, expected.get(expected.indexOf(child)));
        }
    }

    private static IndexedTreeNode<FrameNode> node(int id, int methodId, int cpuSamples, int stackSamples, IndexedTreeNode<FrameNode>... children) {
        return new IndexedTreeNode<>(id, FrameNode.newBuilder()
            .setChildCount(children == null ? 0 : children.length)
            .setMethodId(methodId)
            .setLineNo(0)
            .setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder()
                .setOnStackSamples(stackSamples)
                .setOnCpuSamples(cpuSamples)
            ).build(), children == null ? null : Arrays.asList(children));
    }

    private static IndexedTreeNode<HotMethodNode> hmnode(int id, int methodId, int samples, IndexedTreeNode<HotMethodNode>... children) {
        return new IndexedTreeNode<>(id, new HotMethodNode(methodId, 0, samples), children == null ? null : Arrays.asList(children));
    }

    private static IndexedTreeNode<FrameNode> filterTree(IndexedTreeNode<FrameNode> tree, List<Integer> except) {
        if(except.contains(tree.getIdx())) {
            return null;
        }
        return new IndexedTreeNode<>(tree.getIdx(), tree.getData(), filterTree(tree.getChildren(), except));
    }

    private static List<IndexedTreeNode<FrameNode>> filterTree(List<IndexedTreeNode<FrameNode>> nodes, List<Integer> except) {
        return nodes.stream().map(c -> filterTree(c, except)).filter(c -> c != null).collect(Collectors.toList());
    }

    private static int mId(String methodName) {
        return methodIdLookup.indexOf(methodName);
    }

    private <T> T[] toArray(List<T> list) {
        return (T[]) list.toArray();
    }
}

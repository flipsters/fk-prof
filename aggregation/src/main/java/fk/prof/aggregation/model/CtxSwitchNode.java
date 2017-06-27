package fk.prof.aggregation.model;

import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.aggregation.stacktrace.StacktraceFrameNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CtxSwitchNode extends StacktraceFrameNode<CtxSwitchNode> {
  private final int methodId;
  private final int lineNumber;
  private final List<CtxSwitchNode> children = new ArrayList<>();
  private CtxSwitchDetail data = null;

  public CtxSwitchNode(int methodId, int lineNumber) {
    this.methodId = methodId;
    this.lineNumber = lineNumber;
  }

  public CtxSwitchNode getOrAddChild(int childMethodId, int childLineNumber) {
    synchronized (children) {
      CtxSwitchNode result = null;
      Iterator<CtxSwitchNode> i = children.iterator();
      // Since count of children is going to be small for a node (in scale of tens usually),
      // sticking with arraylist impl of children with O(N) traversal
      while (i.hasNext()) {
        CtxSwitchNode child = i.next();
        if (child.methodId == childMethodId && child.lineNumber == childLineNumber) {
          result = child;
          break;
        }
      }

      if (result == null) {
        result = new CtxSwitchNode(childMethodId, childLineNumber);
        children.add(result);
      }

      return result;
    }
  }

  public synchronized void addEntry(boolean voluntary, boolean migrated, long latency, Long wakeupLag, Long syscallId) {
    if (data == null) {
      data = new CtxSwitchDetail();
    }
    data.addEntry(voluntary, migrated, latency, wakeupLag, syscallId);
  }

  /**
   * Does not check equality of quantile digests
   * @param o
   * @return
   */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CtxSwitchNode)) {
      return false;
    }

    CtxSwitchNode other = (CtxSwitchNode) o;
    return this.methodId == other.methodId
        && this.lineNumber == other.lineNumber
        && this.data.equals(other.data)
        && this.children.size() == other.children.size()
        && this.children.containsAll(other.children)
        && other.children.containsAll(this.children);
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + methodId;
    result = result * PRIME + lineNumber;
    return result;
  }

  protected AggregatedProfileModel.FrameNode buildFrameNodeProto() {
    return AggregatedProfileModel.FrameNode.newBuilder()
      .setMethodId(methodId)
      .setChildCount(children.size())
      .setLineNo(lineNumber)
      .setCtxSwitchProps(data == null ? AggregatedProfileModel.CtxSwitchNodeProps.getDefaultInstance() : data.buildCtxSwitchNodePropsProto())
      .build();
  }

  @Override
  protected Iterable<CtxSwitchNode> children() {
    return children;
  }
}

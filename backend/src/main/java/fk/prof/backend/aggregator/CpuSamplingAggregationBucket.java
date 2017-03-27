package fk.prof.backend.aggregator;

import com.codahale.metrics.Meter;
import fk.prof.aggregation.FinalizableBuilder;
import fk.prof.aggregation.model.MethodIdLookup;
import fk.prof.aggregation.model.CpuSamplingFrameNode;
import fk.prof.aggregation.model.CpuSamplingTraceDetail;
import fk.prof.aggregation.model.FinalizedCpuSamplingAggregationBucket;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.profile.RecordedProfileIndexes;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CpuSamplingAggregationBucket extends FinalizableBuilder<FinalizedCpuSamplingAggregationBucket> {
  private static Logger logger = LoggerFactory.getLogger(CpuSamplingAggregationBucket.class);
  private final MethodIdLookup methodIdLookup = new MethodIdLookup();
  private final ConcurrentHashMap<String, CpuSamplingTraceDetail> traceDetailLookup = new ConcurrentHashMap<>();

  int errored = 0;
  int[] errorHistogram = new int[11];

  /**
   * Aggregates stack samples in the bucket. Throws {@link AggregationFailure} if aggregation fails
   *
   * @param stackSampleWse
   */
  public void aggregate(Recorder.StackSampleWse stackSampleWse, RecordedProfileIndexes indexes, Meter mtrAggrFailures)
      throws AggregationFailure {

    logger.info("Aggregating samples: count: " + stackSampleWse.getStackSampleCount());

    try {
      for (Recorder.StackSample stackSample : stackSampleWse.getStackSampleList()) {

        if(stackSample.hasError()) {
          errored++;
          errorHistogram[stackSample.getError().getNumber()]++;
          continue;
        }

        for (Integer traceId : stackSample.getTraceIdList()) {//TODO: this is not necessarily the best way of doing this from temporal locality PoV (may be we want a de-duped DS), think thru this -jj
          String trace = indexes.getTrace(traceId);
          if (trace == null) {
            throw new AggregationFailure("Unknown trace id encountered in stack sample, aborting aggregation of this profile");
          }
          CpuSamplingTraceDetail traceDetail = traceDetailLookup.computeIfAbsent(trace,
              key -> new CpuSamplingTraceDetail()
          );

          List<Recorder.Frame> frames = stackSample.getFrameList();
          if (frames.size() > 0) {
            boolean framesSnipped = stackSample.getSnipped();
            CpuSamplingFrameNode currentNode = framesSnipped ? traceDetail.getUnclassifiableRoot() : traceDetail.getGlobalRoot();
            currentNode.incrementOnStackSamples();
            traceDetail.incrementSamples();

            //callee -> caller ordering in frames, so iterating bottom up in the list to merge in existing tree in root->leaf fashion
            for (int i = frames.size() - 1; i >= 0; i--) {
              Recorder.Frame frame = frames.get(i);
              String method = indexes.getMethod(frame.getMethodId());
              if (method == null) {
                throw new AggregationFailure("Unknown method id encountered in stack sample, aborting aggregation of this profile");
              }
              int methodId = methodIdLookup.getOrAdd(method);
              currentNode = currentNode.getOrAddChild(methodId, frame.getLineNo());
              currentNode.incrementOnStackSamples();
              //The first frame is the on-cpu frame so incrementing on-cpu samples count
              if (i == 0) {
                currentNode.incrementOnCpuSamples();
              }
            }
          }
        }
      }
    } catch (Exception ex) {
      mtrAggrFailures.mark();
      throw ex;
    }
    finally {
      logger.info("total errored samples count: " + errored);
      StringBuilder sb = new StringBuilder();
      for(int i = 0; i < errorHistogram.length; ++i) {
        sb.append(errorHistogram[i] + ",");
      }
      logger.info("distribution: [" + sb.toString() + "]");
    }
  }

  @Override
  protected FinalizedCpuSamplingAggregationBucket buildFinalizedEntity() {
    return new FinalizedCpuSamplingAggregationBucket(
        methodIdLookup,
        traceDetailLookup
    );
  }
}

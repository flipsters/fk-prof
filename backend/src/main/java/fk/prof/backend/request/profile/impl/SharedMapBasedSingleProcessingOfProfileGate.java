package fk.prof.backend.request.profile.impl;

import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.request.profile.ISingleProcessingOfProfileGate;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;

public class SharedMapBasedSingleProcessingOfProfileGate implements ISingleProcessingOfProfileGate {
  private static Logger logger = LoggerFactory.getLogger(SharedMapBasedSingleProcessingOfProfileGate.class);
  private final LocalMap<Long, Boolean> workIdsInPipeline;

  public SharedMapBasedSingleProcessingOfProfileGate(LocalMap<Long, Boolean> workIdsInPipeline) {
    this.workIdsInPipeline = workIdsInPipeline;
  }

  public void accept(long workId) throws AggregationFailure {
    Boolean existing = workIdsInPipeline.putIfAbsent(workId, true);
    if (existing != null) {
      logger.error(String.format("Profile for work_id=%d is already being aggregated in a separate request", workId));
      throw new AggregationFailure(String.format("Profile is already being aggregated for work_id=%d in a different request", workId));
    }
    if(logger.isDebugEnabled()) {
      logger.debug(String.format("Profile for work_id=%d eligible for aggregation", workId));
    }
  }

  public void finish(long workId) {
    workIdsInPipeline.remove(workId);
    if(logger.isDebugEnabled()) {
      logger.debug(String.format("Profile for work_id=%d removed from processing pipeline", workId));
    }
  }

}

package fk.prof.backend.model.policy.impl;

import fk.prof.backend.proto.PolicyDTO;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In memory replica of the ZK Policy store
 * Created by rohit.patiyal on 15/05/17.
 */
public class InMemoryPolicyCache {
  private static final Logger logger = LoggerFactory.getLogger(InMemoryPolicyCache.class);
  private final Map<String, Map<String, Map<String, PolicyDTO.PolicyDetails>>> processGroupAsTreeToPolicyLookup = new ConcurrentHashMap<>();

  public PolicyDTO.PolicyDetails get(String appId, String clusterId, String procName) {
    PolicyDTO.PolicyDetails policyDetails = null;
    try {
      policyDetails = processGroupAsTreeToPolicyLookup.get(appId).get(clusterId).get(procName);
    }catch (Exception ex){
      logger.error("No policy found for ProcessGroup : {},{},{} in InMemoryPolicyCache", ex, appId, clusterId, procName);
    }
    return policyDetails;
  }

  public void put(String appId, String clusterId, String procName, PolicyDTO.PolicyDetails policyDetails) {
    processGroupAsTreeToPolicyLookup.putIfAbsent(appId, new ConcurrentHashMap<>());
    processGroupAsTreeToPolicyLookup.get(appId).putIfAbsent(clusterId, new ConcurrentHashMap<>());
    processGroupAsTreeToPolicyLookup.get(appId).get(clusterId).put(procName, policyDetails);
  }

}

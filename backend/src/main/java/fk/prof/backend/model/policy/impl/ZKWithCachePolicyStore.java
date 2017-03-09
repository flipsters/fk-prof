package fk.prof.backend.model.policy.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.util.ZookeeperUtil;
import org.apache.curator.framework.CuratorFramework;
import policy.PolicyDetails;
import recording.Recorder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Zookeeper based implementation of the policy store
 * Created by rohit.patiyal on 07/03/17.
 */
public class ZKWithCachePolicyStore implements PolicyStore {

  private static final String DELIMITER = "/";
  private final CuratorFramework curatorClient;
  private String policyPath;
  private CachedPolicyStore cachedPolicies;

  public ZKWithCachePolicyStore(CuratorFramework curatorClient, String policyPath) {
    if (curatorClient == null) {
      throw new IllegalStateException("Curator client is required");
    }
    if (policyPath == null) {
      throw new IllegalStateException("Backend association path in zookeeper hierarchy is required");
    }
    this.curatorClient = curatorClient;
    this.policyPath = policyPath;
    this.cachedPolicies = populateCacheFromZK();
  }

  @Override
  public Map<Recorder.ProcessGroup, PolicyDetails> getAssociatedPolicies(String appId) {
    return cachedPolicies.get(appId);
  }

  @Override
  public Map<Recorder.ProcessGroup, PolicyDetails> getAssociatedPolicies(String appId, String clusterId) {
    return cachedPolicies.get(appId, clusterId);
  }

  @Override
  public Map<Recorder.ProcessGroup, PolicyDetails> getAssociatedPolicies(String appId, String clusterId, String process) {
    return cachedPolicies.get(appId, clusterId, process);
  }

  @Override
  public PolicyDetails getAssociatedPolicy(Recorder.ProcessGroup processGroup) {
    return getAssociatedPolicies(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName()).get(processGroup);
  }

  @Override
  public CompletableFuture<Void> setPolicy(Recorder.ProcessGroup processGroup, PolicyDetails policyDetails) {
    CompletableFuture<Void> future;
    String zNodePath = policyPath + DELIMITER + processGroup.getAppId() + DELIMITER + processGroup.getCluster() + DELIMITER + processGroup.getProcName();
    if (getAssociatedPolicy(processGroup) == null) {
      future = ZookeeperUtil.writeZNodeAsync(curatorClient, zNodePath, policyDetails.toByteArray(), true).whenComplete((result, ex) -> {
        if (ex != null) cachedPolicies.put(processGroup, policyDetails);
      });
    } else {
      future = ZookeeperUtil.writeZNodeAsync(curatorClient, zNodePath, policyDetails.toByteArray(), false).whenComplete((event, ex) -> {
        if (ex != null) cachedPolicies.set(processGroup, policyDetails);
      });

    }
    return future;
  }

  private CachedPolicyStore populateCacheFromZK() {
    CachedPolicyStore cachedPolicies = new CachedPolicyStore();
    try {
      for (String appId : curatorClient.getChildren().forPath(policyPath)) {
        for (String clusterId : curatorClient.getChildren().forPath(policyPath + DELIMITER + appId)) {
          for (String process : curatorClient.getChildren().forPath(policyPath + DELIMITER + appId + DELIMITER + clusterId)) {
            String zNodePath = policyPath + DELIMITER + appId + DELIMITER + clusterId + DELIMITER + process;
            ZookeeperUtil.readZNodeAsync(curatorClient, zNodePath).whenComplete((bytes, throwable) -> {
              try {
                PolicyDetails policyDetails = PolicyDetails.parseFrom(bytes);
                Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(appId).setCluster(clusterId).setProcName(process).build();
                cachedPolicies.put(processGroup, policyDetails);
              } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
              }
            });
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return cachedPolicies;
  }
}
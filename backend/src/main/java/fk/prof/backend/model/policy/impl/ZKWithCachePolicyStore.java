package fk.prof.backend.model.policy.impl;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.util.ZookeeperUtil;
import org.apache.curator.framework.CuratorFramework;
import policy.PolicyDetails;
import recording.Recorder;

import java.nio.charset.Charset;
import java.util.HashMap;
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

  ZKWithCachePolicyStore(CuratorFramework curatorClient, String policyPath) {
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

  private static String decode32(String str) {
    return new String(BaseEncoding.base32().decode(str), Charset.forName("utf-8"));
  }

  @Override
  public Map<Recorder.ProcessGroup, PolicyDetails> getAssociatedPolicies(String appId) {
    if (appId == null) return new HashMap<>();
    return cachedPolicies.get(appId);
  }

  @Override
  public Map<Recorder.ProcessGroup, PolicyDetails> getAssociatedPolicies(String appId, String clusterId) {
    if (appId == null || clusterId == null) return new HashMap<>();
    return cachedPolicies.get(appId, clusterId);
  }

  @Override
  public Map<Recorder.ProcessGroup, PolicyDetails> getAssociatedPolicies(String appId, String clusterId, String process) {
    if (appId == null || clusterId == null || process == null) return new HashMap<>();
    return cachedPolicies.get(appId, clusterId, process);
  }

  @Override
  public PolicyDetails getAssociatedPolicy(Recorder.ProcessGroup processGroup) {
    return getAssociatedPolicies(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName()).get(processGroup);
  }

  @Override
  public CompletableFuture<Void> setPolicy(Recorder.ProcessGroup processGroup, PolicyDetails policyDetails) {
    CompletableFuture<Void> future;
    String zNodePath = policyPath + DELIMITER + encode(processGroup.getAppId()) + DELIMITER + encode(processGroup.getCluster()) + DELIMITER + encode(processGroup.getProcName());
    if (getAssociatedPolicy(processGroup) == null) {
      future = ZookeeperUtil.writeZNodeAsync(curatorClient, zNodePath, policyDetails.toByteArray(), true).whenComplete((result, ex) -> {
        if (ex == null) {
          cachedPolicies.put(processGroup, policyDetails);
        }
      });
    } else {
      future = ZookeeperUtil.writeZNodeAsync(curatorClient, zNodePath, policyDetails.toByteArray(), false).whenComplete((event, ex) -> {
        if (ex == null) {
          cachedPolicies.set(processGroup, policyDetails);
        }
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

                Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(decode32(appId)).setCluster(decode32(clusterId)).setProcName(decode32(process)).build();
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

  private String encode(String str) {
    return BaseEncoding.base32().encode(str.getBytes(Charset.forName("utf-8")));
  }
}
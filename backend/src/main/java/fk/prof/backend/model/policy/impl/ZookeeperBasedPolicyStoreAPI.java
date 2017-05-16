package fk.prof.backend.model.policy.impl;

import com.google.common.io.BaseEncoding;
import fk.prof.backend.exception.PolicyException;
import fk.prof.backend.model.policy.PolicyStoreAPI;
import fk.prof.backend.proto.PolicyDTO;
import fk.prof.storage.impl.ZKAsyncStorage;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import recording.Recorder;

import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Zookeeper based implementation of the policy store
 * Created by rohit.patiyal on 15/05/17.
 */
public class ZookeeperBasedPolicyStoreAPI implements PolicyStoreAPI {
  private static final String POLICY_NODE_PREFIX = "v";
  private static Logger logger = LoggerFactory.getLogger(ZookeeperBasedPolicyStoreAPI.class);
  private static final String DELIMITER = "/";
  private final CuratorFramework curatorClient;
  private boolean initialized;
  private String policyPath;
  private InMemoryPolicyCache inMemoryPolicyCache;
  private ZKAsyncStorage zkAsyncStorage;
  private final ReentrantLock setPolicyLock = new ReentrantLock();

  public ZookeeperBasedPolicyStoreAPI(CuratorFramework curatorClient, String policyPath) {
    if (curatorClient == null) {
      throw new IllegalStateException("Curator client is required");
    }
    if (policyPath == null) {
      throw new IllegalStateException("Backend association path in zookeeper hierarchy is required");
    }
    this.curatorClient = curatorClient;
    zkAsyncStorage = new ZKAsyncStorage(curatorClient, CreateMode.PERSISTENT_SEQUENTIAL);
    this.policyPath = policyPath;
    this.initialized = false;
  }

  private boolean populateCacheFromZK() {
    inMemoryPolicyCache = new InMemoryPolicyCache();
    try {
      for (String appId : curatorClient.getChildren().forPath(policyPath)) {
        for (String clusterId : curatorClient.getChildren().forPath(policyPath + DELIMITER + appId)) {
          for (String procName : curatorClient.getChildren().forPath(policyPath + DELIMITER + appId + DELIMITER + clusterId)) {
            String zNodePath = policyPath + DELIMITER + appId + DELIMITER + clusterId + DELIMITER + procName + DELIMITER;
            zkAsyncStorage.fetchAsync(zNodePath).whenComplete((bytes, throwable) -> {
              try {
                PolicyDTO.PolicyDetails policyDetails = PolicyDTO.PolicyDetails.parseFrom(bytes);
                inMemoryPolicyCache.put(decode32(appId), decode32(clusterId), decode32(procName), policyDetails);
              } catch (java.io.IOException ex) {
                logger.error("PopulateCacheFromZK failed while parsing policyDetails for appId={} clusterId={} procName={} with error = {}", appId, clusterId, procName, ex);
              }
            });
          }
        }
      }
    } catch (Exception ex) {
      logger.error("PopulateCacheFromZK failed with error = {}", ex);
      return false;
    }
    return true;
  }

  @Override
  public void init() {
    synchronized (this) {
      if (!initialized) {
        if (populateCacheFromZK())
          initialized = true;
      }
    }
  }

  @Override
  public PolicyDTO.PolicyDetails getPolicy(Recorder.ProcessGroup processGroup) {
    return inMemoryPolicyCache.get(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName());
  }

  @Override
  public CompletableFuture<Void> setPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails policyDetails) {
    String appId = processGroup.getAppId();
    String clusterId = processGroup.getCluster();
    String procName = processGroup.getProcName();
    String zNodePath = policyPath + DELIMITER + encode(appId) + DELIMITER + encode(clusterId) + DELIMITER + encode(procName) + DELIMITER + POLICY_NODE_PREFIX;
    CompletableFuture<Void> future;
    if (policyDetails == null) {
      future = new CompletableFuture<>();
      future.completeExceptionally(new RuntimeException("Policy Details is null"));
      logger.error("Policy details is null while setting policy");
      return future;
    }
    try {
      boolean acquired = setPolicyLock.tryLock(1, TimeUnit.SECONDS);
      if (acquired) {
        future = zkAsyncStorage.storeAsync(zNodePath, policyDetails.toByteArray()).whenComplete((result, ex) -> {
          if (ex == null) {
            inMemoryPolicyCache.put(appId, clusterId, procName, policyDetails);
          } else {
            logger.error("Writing to ZK failed while setting policy with error = {}", ex);
          }
          setPolicyLock.unlock();
        });
      } else {
        future = new CompletableFuture<>();
        future.completeExceptionally(new PolicyException("Timeout while acquiring lock while setting policy for process_group=" + processGroup, true));
      }
    } catch (InterruptedException ex) {
      future = new CompletableFuture<>();
      future.completeExceptionally(new PolicyException("Interrupted while acquiring lock for setting policy for process_group=" + processGroup,true));
    } catch (Exception ex) {
      future = new CompletableFuture<>();
      future.completeExceptionally(new PolicyException("Unexpected error while setting policy for process_group=" + processGroup, true));
    }

    return future;
  }

  public String getPolicyPath() {
    return policyPath;
  }

  public boolean isInitialized() {
    return initialized;
  }

  private static String decode32(String str) {
    return new String(BaseEncoding.base32().decode(str), Charset.forName("utf-8"));
  }

  private static String encode(String str) {
    return BaseEncoding.base32().encode(str.getBytes(Charset.forName("utf-8")));
  }
}

package fk.prof.backend.model.policy.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.util.ZookeeperUtil;
import org.apache.curator.framework.CuratorFramework;
import policy.PolicyDetails;
import recording.Recorder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Zookeeper based implementation of the policy store
 * Created by rohit.patiyal on 07/03/17.
 */
public class ZookeeperBasedPolicyStore implements PolicyStore {

    private static final String DELIMITER = "/";
    private final ExecutorService executorService;
    private final CuratorFramework curatorClient;
    private String policyPath;

    public ZookeeperBasedPolicyStore(CuratorFramework curatorClient, ExecutorService executorService, String policyPath) {
        if (curatorClient == null) {
            throw new IllegalStateException("Curator client is required");
        }
        if (policyPath == null) {
            throw new IllegalStateException("Backend association path in zookeeper hierarchy is required");
        }
        this.executorService = executorService;
        this.curatorClient = curatorClient;
        this.policyPath = policyPath;
    }

    @Override
    public CompletableFuture<List<PolicyDetails>> getAssociatedPolicies(Recorder.ProcessGroup processGroup) {
        final String appId = processGroup.getAppId();
        final String cluster = processGroup.getCluster();
        final String proc = processGroup.getProcName();
        return CompletableFuture.supplyAsync(() -> {
            List<PolicyDetails> policies = new ArrayList<>();
            String zkNodePath;
            try {
                if (appId != null) {
                    if (cluster != null) {
                        if (proc != null) {
                            zkNodePath = policyPath + DELIMITER + appId + DELIMITER + cluster + DELIMITER + proc;
                            ZookeeperUtil.readZNodeAsync(curatorClient, zkNodePath).whenComplete((bytes, throwable) -> {
                                try {
                                    policies.add(PolicyDetails.parseFrom(bytes));
                                } catch (InvalidProtocolBufferException e) {
                                    e.printStackTrace();
                                }
                            });
                        } else {
                            zkNodePath = policyPath + DELIMITER + appId + DELIMITER + cluster;
                            for (String childProc : curatorClient.getChildren().forPath(zkNodePath)) {
                                ZookeeperUtil.readZNodeAsync(curatorClient, zkNodePath + DELIMITER + childProc).whenComplete((bytes, throwable) -> {
                                    try {
                                        policies.add(PolicyDetails.parseFrom(bytes));
                                    } catch (InvalidProtocolBufferException e) {
                                        e.printStackTrace();
                                    }
                                });

                            }
                        }
                    } else {
                        zkNodePath = policyPath + DELIMITER + appId;
                        for (String childCluster : curatorClient.getChildren().forPath(zkNodePath)) {
                            for (String childProc : curatorClient.getChildren().forPath(zkNodePath + DELIMITER + childCluster)) {
                                ZookeeperUtil.readZNodeAsync(curatorClient, zkNodePath + DELIMITER + childCluster + DELIMITER + childProc).whenComplete((bytes, throwable) -> {
                                    try {
                                        policies.add(PolicyDetails.parseFrom(bytes));
                                    } catch (InvalidProtocolBufferException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return policies;
        }, executorService);
    }
}

package fk.prof.userapi.api.cache;

import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Parser;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.userapi.proto.LoadInfoEntities;
import fk.prof.userapi.proto.LoadInfoEntities.ProfileResidencyInfo;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by gaurav.ashok on 01/08/17.
 */
public class ZkLoadInfoStore {
    private static final Logger logger = LoggerFactory.getLogger(ZkLoadInfoStore.class);
    private static final String distributedLockPath = "/global_mutex";

    private final String zkNodesInfoPath;
    private final CuratorFramework zookeeper;
    private final InterProcessSemaphoreMutex sharedMutex;

    private final AtomicReference<ConnectionState> connectionState;
    private final AtomicReference<LocalDateTime> lastZkLostTime;
    private final AtomicBoolean recentlyZkConnectionLost;

    private final byte[] myResidencyInfo;
    private Supplier<List<AggregatedProfileNamingStrategy>> cachedProfiles;

    public ZkLoadInfoStore(CuratorFramework zookeeper, String myIp, int port, Supplier<List<AggregatedProfileNamingStrategy>> cachedProfiles) {
        this.zookeeper = zookeeper;

        this.zkNodesInfoPath = "/nodesInfo/" + myIp + ":" + port;
        this.sharedMutex = new InterProcessSemaphoreMutex(zookeeper, distributedLockPath);
        this.lastZkLostTime = new AtomicReference<>(LocalDateTime.MIN);
        this.recentlyZkConnectionLost = new AtomicBoolean(false);

        this.myResidencyInfo = ProfileResidencyInfo.newBuilder().setIp(myIp).setPort(port).build().toByteArray();

        this.zookeeper.getConnectionStateListenable().addListener(this::zkStateChangeListener,
            Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("curator-state-listener").build()));

        this.cachedProfiles = cachedProfiles;

        this.connectionState = new AtomicReference<>(
            zookeeper.getZookeeperClient().isConnected() ? ConnectionState.Connected : ConnectionState.Disconnected);
    }

    public boolean ensureBasePathExists() throws Exception {
        ensureConnected();
        zookeeper.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(zkNodesInfoPath, buildNodeLoadInfo(0).toByteArray());
        if(zookeeper.checkExists().forPath("/profilesLoadStatus") == null) {
            zookeeper.create().withMode(CreateMode.PERSISTENT).forPath("/profilesLoadStatus");
            return true;
        }
        return false;
    }

    public boolean profileInfoExists(AggregatedProfileNamingStrategy profileName) throws Exception {
        ensureConnected();
        return zookeeper.checkExists().forPath(zkPathForProfile(profileName)) != null;
    }

    public boolean nodeInfoExists() throws Exception {
        ensureConnected();
        return zookeeper.checkExists().forPath(zkNodesInfoPath) != null;
    }

    public ProfileResidencyInfo readProfileResidencyInfo(AggregatedProfileNamingStrategy profileName) throws Exception {
        ensureConnected();
        try {
            return readFrom(zkPathForProfile(profileName), ProfileResidencyInfo.parser());
        } catch (KeeperException.NoNodeException e) {
            return null;
        }
    }

    public void updateProfileStatusToLoading(AggregatedProfileNamingStrategy profileName, boolean profileNodeExists) throws Exception {
        ensureConnected();
        LoadInfoEntities.NodeLoadInfo nodeLoadInfo = readFrom(zkNodesInfoPath, LoadInfoEntities.NodeLoadInfo.parser());
        CuratorTransactionFinal transaction = zookeeper.inTransaction().setData().forPath(zkNodesInfoPath, buildNodeLoadInfo(nodeLoadInfo.getProfilesLoaded() + 1).toByteArray()).and();
        if(profileNodeExists) {
            transaction = transaction.setData().forPath(zkPathForProfile(profileName), myResidencyInfo).and();
        }
        else {
            transaction = transaction.create().withMode(CreateMode.EPHEMERAL).forPath(zkPathForProfile(profileName), myResidencyInfo).and();
        }
        transaction.commit();
    }

    public void removeProfile(AggregatedProfileNamingStrategy profileName, boolean deleteProfileNode) throws Exception {
        ensureConnected();
        LoadInfoEntities.NodeLoadInfo nodeLoadInfo = readFrom(zkNodesInfoPath, LoadInfoEntities.NodeLoadInfo.parser());
        byte[] newData = buildNodeLoadInfo(nodeLoadInfo.getProfilesLoaded() - 1).toByteArray();
        if(deleteProfileNode) {
            zookeeper.inTransaction().setData().forPath(zkNodesInfoPath, newData)
                .and().delete().forPath(zkPathForProfile(profileName))
                .and().commit();
        }
        else {
            zookeeper.setData().forPath(zkNodesInfoPath, newData);
        }
    }

    public AutoCloseable getLock() throws Exception {
        return getLock(false);
    }

    public AutoCloseable getLock(Boolean canExpectAlreadyAcquired) throws Exception {
        ensureConnected();
        return new CloseableSharedLock(canExpectAlreadyAcquired);
    }

    public enum ConnectionState {
        Disconnected,
        Connected
    }

    private void ensureConnected() throws ZkStoreNotConnectedException {
        if(!ConnectionState.Connected.equals(connectionState.get())) {
            throw new ZkStoreNotConnectedException("connection lost recently: " + recentlyZkConnectionLost.get() + ", lastTimeOfLostConnection: " + lastZkLostTime.get().toString());
        }
    }

    private class CloseableSharedLock implements AutoCloseable {

        boolean lockAlreadyAcquired = false;

        CloseableSharedLock(boolean canExpectAlreadyAcquired) throws Exception {
            if (!canExpectAlreadyAcquired && sharedMutex.isAcquiredInThisProcess()) {
                throw new RuntimeException("Lock already acquired");
            }
            if (sharedMutex.isAcquiredInThisProcess()) {
                lockAlreadyAcquired = true;
            }
            else {
                sharedMutex.acquire();
            }
        }

        @Override
        public void close() {
            try {
                if (!lockAlreadyAcquired) {
                    sharedMutex.release();
                }
            }
            catch (Exception e) {
                logger.error("Unable to release lock", e);
            }
        }
    }

    private LoadInfoEntities.NodeLoadInfo buildNodeLoadInfo(int profileCount) {
        return LoadInfoEntities.NodeLoadInfo.newBuilder().setProfilesLoaded(profileCount).build();
    }

    private <T extends AbstractMessage> T readFrom(String path, Parser<T> parser) throws Exception {
        byte[] bytes = zookeeper.getData().forPath(path);
        return parser.parseFrom(bytes);
    }


    private String zkPathForProfile(AggregatedProfileNamingStrategy profileName) {
        return "/profilesLoadStatus/" +  BaseEncoding.base32().encode(profileName.toString().getBytes(Charset.forName("utf-8")));
    }

    private void zkStateChangeListener(CuratorFramework client, org.apache.curator.framework.state.ConnectionState newState) {
        if(org.apache.curator.framework.state.ConnectionState.RECONNECTED.equals(newState)) {
            try {
                if(recentlyZkConnectionLost.get()) {
                    reInit();
                    recentlyZkConnectionLost.set(false);
                    connectionState.set(ConnectionState.Connected);
                }
                else {
                    if(!nodeInfoExists()) {
                        // Assuming this should not happen. Putting a check so that we can be notified if this assumption breaks any time.
                        throw new RuntimeException("After zookeeper reconnection, my node was not found");
                    }
                    connectionState.set(ConnectionState.Connected);
                }
            }
            catch (Exception e) {
                logger.error("Error while reinitializing zookeeper after reconnection.", e);
            }
        }
        else if(org.apache.curator.framework.state.ConnectionState.LOST.equals(newState)) {
            connectionState.set(ConnectionState.Disconnected);
            lastZkLostTime.set(LocalDateTime.now());
            recentlyZkConnectionLost.set(true);
        }
        else if(org.apache.curator.framework.state.ConnectionState.SUSPENDED.equals(newState) || org.apache.curator.framework.state.ConnectionState.READ_ONLY.equals(newState)) {
            connectionState.set(ConnectionState.Disconnected);
        }
        logger.info("zookeeper state changed to \"{}\"", newState.name());
    }

    private void reInit() throws Exception {
        List<AggregatedProfileNamingStrategy> cachedProfiles = this.cachedProfiles.get();
        try(AutoCloseable lock = getLock()) {

            List<Boolean> exists;

            CuratorTransactionFinal transaction = zookeeper.inTransaction().check().forPath(zkNodesInfoPath).and();
            for(AggregatedProfileNamingStrategy profile: cachedProfiles) {
                transaction = transaction.check().forPath(zkPathForProfile(profile)).and();
            }
            exists = transaction.commit().stream().map(e -> e.getResultStat() != null).collect(Collectors.toList());

            // update data
            CuratorTransactionFinal writeTransaction;
            byte[] nodeInfo = buildNodeLoadInfo(cachedProfiles.size()).toByteArray();
            if(exists.get(0)) {
                writeTransaction = zookeeper.inTransaction().setData().forPath(zkNodesInfoPath, nodeInfo).and();
            }
            else {
                writeTransaction = zookeeper.inTransaction().create().forPath(zkNodesInfoPath, nodeInfo).and();
            }

            Iterator<AggregatedProfileNamingStrategy> profileNameIt = cachedProfiles.iterator();
            Iterator<Boolean> existIt = exists.iterator();
            // skip first for nodeinfo
            existIt.next();

            while(profileNameIt.hasNext()) {
                if(!existIt.next()) {
                    writeTransaction.create().forPath(zkPathForProfile(profileNameIt.next()), myResidencyInfo).and();
                }
            }

            writeTransaction.commit();
        }
    }
}
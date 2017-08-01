package fk.prof.userapi.api.cache;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Parser;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.userapi.proto.LoadInfoEntities;
import fk.prof.userapi.proto.LoadInfoEntities.ProfileResidencyInfo;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by gaurav.ashok on 01/08/17.
 */
public class ZookeeperLoadInfoStore {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperLoadInfoStore.class);
    private static final String distributedLockPath = "/global_mutex";

    private final String zkNodesInfoPath;
    private final CuratorFramework zookeeper;
    private final InterProcessSemaphoreMutex sharedMutex;

    private final AtomicReference<Mode> executionMode;
    private final AtomicLong lastZkLostTimestamp;
    private final AtomicBoolean recentlyZkConnectionLost;

    private String myIp;
    private int port;

    public ZookeeperLoadInfoStore(CuratorFramework zookeeper, WorkerExecutor executor, String myIp, int port) {
        this.zookeeper = zookeeper;

        this.zkNodesInfoPath = "/nodesInfo/" + myIp + ":" + port;
        this.sharedMutex = new InterProcessSemaphoreMutex(zookeeper, distributedLockPath);
        this.executionMode = new AtomicReference<>(Mode.ReadOnly);
        this.lastZkLostTimestamp = new AtomicLong(0);
        this.recentlyZkConnectionLost = new AtomicBoolean(false);

        this.myIp = myIp;
        this.port = port;

        this.zookeeper.getConnectionStateListenable().addListener(this::zkStateChangeListener);
    }

    public Boolean ensureBasePathExists() throws Exception {
        zookeeper.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(zkNodesInfoPath, buildNodeLoadInfo(0).toByteArray());
        if(zookeeper.checkExists().forPath("/profilesLoadStatus") == null) {
            zookeeper.create().withMode(CreateMode.PERSISTENT).forPath("/profilesLoadStatus");
            return true;
        }
        return false;
    }

    public Boolean profileInfoExists(AggregatedProfileNamingStrategy profileName) throws Exception {
        return zookeeper.checkExists().forPath(zkPathForProfile(profileName)) != null;
    }

    public Boolean NodeInfoExists() throws Exception {
        return zookeeper.checkExists().forPath(zkNodesInfoPath) != null;
    }

    public ProfileResidencyInfo readProfileResidencyInfo(AggregatedProfileNamingStrategy profileName) throws Exception {
        try {
            return readFrom(zkPathForProfile(profileName), ProfileResidencyInfo.parser());
        } catch (KeeperException.NoNodeException e) {
            return null;
        }
    }

    public void updateProfileStatusToLoaded(AggregatedProfileNamingStrategy profileName) throws Exception {
        // profile not cached anywhere
        zookeeper.setData().forPath(zkPathForProfile(profileName), buildResidencyInfo(ProfileResidencyInfo.LoadStatus.Loaded).toByteArray());
//        cache.put(toKey(profileName), profileLoadFuture);
    }

    public void updateProfileStatusToLoading(AggregatedProfileNamingStrategy profileName, boolean profileNodeExists) throws Exception {
        LoadInfoEntities.NodeLoadInfo nodeLoadInfo = readFrom(zkNodesInfoPath, LoadInfoEntities.NodeLoadInfo.parser());
        CuratorTransactionFinal transaction = zookeeper.inTransaction().setData().forPath(zkNodesInfoPath, buildNodeLoadInfo(nodeLoadInfo.getProfilesLoaded() + 1).toByteArray()).and();
        if(profileNodeExists) {
            transaction = transaction.setData().forPath(zkPathForProfile(profileName), buildResidencyInfo(ProfileResidencyInfo.LoadStatus.Loading).toByteArray()).and();
        }
        else {
            transaction = transaction.create().withMode(CreateMode.EPHEMERAL).forPath(zkPathForProfile(profileName), buildResidencyInfo(ProfileResidencyInfo.LoadStatus.Loading).toByteArray()).and();
        }
        transaction.commit();
//        cache.put(toKey(profileName), profileLoadFuture);
    }

    public void removeProfile(AggregatedProfileNamingStrategy profileName, boolean deleteProfileNode) throws Exception {
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

    public CloseableSharedLock getLock() throws Exception {
        return getLock(false);
    }

    public CloseableSharedLock getLock(Boolean canExpectAlreadyAcquired) throws Exception {
        return new CloseableSharedLock(canExpectAlreadyAcquired);
    }

    public enum Mode {
        ReadOnly,
        Normal
    }

    public class CloseableSharedLock implements AutoCloseable {

        boolean lockAlreadyAcquired = false;

        CloseableSharedLock() throws Exception {
            this(false);
        }

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

    private ProfileResidencyInfo buildResidencyInfo(ProfileResidencyInfo.LoadStatus loadStatus) {
        return ProfileResidencyInfo.newBuilder().setIp(myIp).setPort(port).setStatus(loadStatus).build();
    }

    private <T extends AbstractMessage> T readFrom(String path, Parser<T> parser) throws Exception {
        byte[] bytes = zookeeper.getData().forPath(path);
        return parser.parseFrom(bytes);
    }


    private String zkPathForProfile(AggregatedProfileNamingStrategy profileName) {
        return "/profilesLoadStatus/" +  BaseEncoding.base32().encode(profileName.toString().getBytes(Charset.forName("utf-8")));
    }

    private void zkStateChangeListener(CuratorFramework client, ConnectionState newState) {
        if(ConnectionState.RECONNECTED.equals(newState)) {
            if(recentlyZkConnectionLost.get()) {
//                zookeeper.inTransaction().check().
            }
            /*
            If previously lost:
                do cleanup / reinit. mode <- normal  (1)
            If previously suspended, read-only:
                check whether node exists. If yes, everything good & mode <- normal. If no, do (1)
             */

        }
        else if(ConnectionState.LOST.equals(newState)) {
            executionMode.set(Mode.ReadOnly);
            lastZkLostTimestamp.set(System.currentTimeMillis());
            recentlyZkConnectionLost.set(true);
        }
        else if(ConnectionState.SUSPENDED.equals(newState) || ConnectionState.READ_ONLY.equals(newState)) {
            executionMode.set(Mode.ReadOnly);
        }
        logger.info("zookeeper state changed to \"{}\"", newState.name());
    }
}

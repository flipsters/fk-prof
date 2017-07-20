package fk.prof.userapi.api.cache;

import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Parser;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.userapi.Configuration;
import fk.prof.userapi.Pair;
import fk.prof.userapi.api.AggregatedProfileLoader;
import fk.prof.userapi.model.AggregatedProfileInfo;
import fk.prof.userapi.model.AggregatedSamplesPerTraceCtx;
import fk.prof.userapi.Cacheable;
import fk.prof.userapi.model.tree.CallTreeView;
import fk.prof.userapi.model.tree.CalleesTreeView;
import fk.prof.userapi.proto.LoadInfoEntities.NodeLoadInfo;
import fk.prof.userapi.proto.LoadInfoEntities.ProfileResidencyInfo;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Created by gaurav.ashok on 21/06/17.
 */
public class ClusterAwareCache {

    private static final Logger logger = LoggerFactory.getLogger(ClusterAwareCache.class);
    private static final String distributedLockPath = "/cacheMutex";

    private final LocalProfileCache cache;
    private final Map<String, ArrayList<String>> loadedViews;

    private final WorkerExecutor workerExecutor;
    /*
    zookeeper node structure:
        /fk-prof-userapi                                                        (session namespace)
        |___/nodesInfo/{ip:port} -> NodeLoadInfo                                (ephemeral)
        |___/profilesLoadStatus/{profileName} -> ProfileResidencyInfo           (ephemeral)
     */
    private final CuratorFramework zookeeper;
    private final String myIp;
    private final int port;

    private final String zkNodesInfoPath;
    private final InterProcessSemaphoreMutex sharedMutex;

    public ClusterAwareCache(CuratorFramework zookeeper, WorkerExecutor workerExecutor, LocalProfileCache cache, Configuration config) {
        this.myIp = config.getIpAddress();
        this.port = config.getHttpConfig().getHttpPort();
        this.zookeeper = zookeeper;
        this.workerExecutor = workerExecutor;

        this.zkNodesInfoPath = "/nodesInfo/" + myIp + ":" + port;

        this.cache = cache;
        this.cache.setRemovalListener(this::doCleanUpOnEviction);
        this.loadedViews = new ConcurrentHashMap<>();

        this.sharedMutex = new InterProcessSemaphoreMutex(zookeeper, distributedLockPath);
    }

    public Future<Object> onClusterJoin() {
        Future<Object> future = Future.future();
        workerExecutor.executeBlocking(f -> {
            try {
                zookeeper.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(zkNodesInfoPath, buildNodeLoadInfo(0).toByteArray());
                if(zookeeper.checkExists().forPath("/profilesLoadStatus") == null) {
                    zookeeper.create().withMode(CreateMode.PERSISTENT).forPath("/profilesLoadStatus");
                }
                f.complete();
            }
            catch (Exception e) {
                logger.error("Error in onClusterJoin", e);
                f.fail(e);
            }
        }, future.completer());
        return future;
    }

    /*
    returns the aggregated profile if already cached, throws CachedProfileNotFoundException if found on other node,
    or loads the profile into memory and then returns and updating zookeeper accordingly
     */
    public Future<AggregatedProfileInfo> getAggregatedProfile(AggregatedProfileNamingStrategy profileName, AggregatedProfileLoader profileLoader) {
        Future<AggregatedProfileInfo> profileFuture = Future.future();
        String profileKey = toKey(profileName);

        Future<AggregatedProfileInfo> cachedProfileInfo = cache.get(profileKey);
        if (cachedProfileInfo != null) {
            logger.debug("found the cached profile");
            completeFuture(profileName, cachedProfileInfo, profileFuture);
        }
        else {
            logger.debug("checking zookeeper for residency");
            // check zookeeper if it is loaded somewhere else
            workerExecutor.executeBlocking(f -> {
                String pathForProfile = zkPathForProfile(profileName);
                try {
                    try (CloseableSharedLock csl = new CloseableSharedLock()) {
                        // check again
                        Future<AggregatedProfileInfo> _cachedProfileInfo = cache.get(profileKey);
                        if (_cachedProfileInfo != null) {
                            logger.debug("found cached future after getting shared lock");
                            completeFuture(profileName, _cachedProfileInfo, f);
                            return;
                        }

                        boolean staleNodeExists = false;
                        try {
                            //  still no cached profile. read zookeeper for remotely cached profile
                            ProfileResidencyInfo residencyInfo = readFrom(pathForProfile, ProfileResidencyInfo.parser());

                            // stale node exists. will update instead of create
                            if(residencyInfo.getIp().equals(myIp) && residencyInfo.getPort() == port) {
                                staleNodeExists = true;
                            }
                            else {
                                f.fail(new CachedProfileNotFoundException(residencyInfo.getIp(), residencyInfo.getPort()));
                                return;
                            }
                        }
                        catch (KeeperException.NoNodeException nne) {
                            // ignore
                        }

                        // profile not cached anywhere
                        // start the loading process
                        Future<AggregatedProfileInfo> loadProfileFuture = Future.future();
                        workerExecutor.executeBlocking(f2 -> profileLoader.load(f2, profileName), loadProfileFuture.completer());

                        // update LOADING status in zookeeper
                        updateProfileStatusToLoading(profileName, loadProfileFuture, staleNodeExists);

                        loadProfileFuture.setHandler(ar -> {
                            logger.info("Profile load complete. file: {}", profileName);
                            try {
                                // profile loading completed, update zookeeper with LOADED status
                                try (CloseableSharedLock csl2 = new CloseableSharedLock(true)) {
                                    updateProfileStatusToLoaded(profileName, loadProfileFuture);
                                }
                            }
                            catch (Exception e) {
                                logger.error("Error while updating zookeeper after load completes. file: {}", profileName, e);
                                // remove the entry from cache so that we dont leak memory
                                cache.invalidateProfile(profileKey);
                            }
                        });

                        logger.debug("started the profile loading. returning profileloadException");
                        f.fail(new ProfileLoadInProgressException(profileName));
                    }
                }
                catch (Exception e) {
                    logger.error("Error while checking zookeeper for remotely cached profile. file: {}", profileName, e);
                    f.fail(e);
                }
            }, profileFuture.completer());
        }

        return profileFuture;
    }

    public Future<Pair<AggregatedSamplesPerTraceCtx, CallTreeView>> getCallTreeView(AggregatedProfileNamingStrategy profileName, String traceName, Function<AggregatedProfileInfo, CallTreeView> viewCreator) {
        return getView(profileName, traceName, viewCreator, true);
    }

    public Future<Pair<AggregatedSamplesPerTraceCtx, CalleesTreeView>> getCalleesTreeView(AggregatedProfileNamingStrategy profileName, String traceName, Function<AggregatedProfileInfo, CalleesTreeView> viewCreator) {
        return getView(profileName, traceName, viewCreator, false);
    }

    private void doCleanUpOnEviction(RemovalNotification<String, Future<AggregatedProfileInfo>> onRemoval) {
        if(!RemovalCause.REPLACED.equals(onRemoval.getCause())) {
            // remove any dependent values from other cache objects
            workerExecutor.executeBlocking(f -> {
                AggregatedProfileNamingStrategy profileName = AggregatedProfileNamingStrategy.fromFileName(onRemoval.getKey());
                synchronized (this) {
                    List<String> dependentKeys = loadedViews.remove(toKey(profileName));
                    if (dependentKeys != null) {
                        cache.invalidateViews(dependentKeys);
                    }
                }

                try {
                    try (CloseableSharedLock csl = new CloseableSharedLock()) {
                        // double check whether the same profile has started loaded again by the time we got the lock
                        Future<AggregatedProfileInfo> _cachedProfileInfo = cache.get(toKey(profileName));
                        if (_cachedProfileInfo != null) {
                            f.complete();
                            return;
                        }
                        else {
                            cleanupProfileStatus(profileName, onRemoval.getValue());
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("Error while doing zookeeper cleanup for file: {}", profileName, e);
                    f.fail(e);
                }
                f.complete();
            }, ar -> {});
        }
    }

    private void updateProfileStatusToLoaded(AggregatedProfileNamingStrategy profileName, Future<AggregatedProfileInfo> profileLoadFuture) throws Exception {
        // profile not cached anywhere
        zookeeper.setData().forPath(zkPathForProfile(profileName), buildResidencyInfo(ProfileResidencyInfo.LoadStatus.Loaded).toByteArray());
        cache.put(toKey(profileName), profileLoadFuture);
    }

    private void updateProfileStatusToLoading(AggregatedProfileNamingStrategy profileName, Future<AggregatedProfileInfo> profileLoadFuture, boolean nodeExists) throws Exception {
        NodeLoadInfo nodeLoadInfo = readFrom(zkNodesInfoPath, NodeLoadInfo.parser());
        CuratorTransactionFinal transaction = zookeeper.inTransaction().setData().forPath(zkNodesInfoPath, buildNodeLoadInfo(nodeLoadInfo.getProfilesLoaded() + 1).toByteArray()).and();
        if(nodeExists) {
            transaction = transaction.setData().forPath(zkPathForProfile(profileName), buildResidencyInfo(ProfileResidencyInfo.LoadStatus.Loading).toByteArray()).and();
        }
        else {
            transaction = transaction.create().withMode(CreateMode.EPHEMERAL).forPath(zkPathForProfile(profileName), buildResidencyInfo(ProfileResidencyInfo.LoadStatus.Loading).toByteArray()).and();
        }
        transaction.commit();
        cache.put(toKey(profileName), profileLoadFuture);
    }

    private void cleanupProfileStatus(AggregatedProfileNamingStrategy profileName, Future<AggregatedProfileInfo> profileLoadFuture) throws Exception {
        int weight = profileLoadFuture.succeeded() ? profileLoadFuture.result().getUtilizationWeight() : 1;
        NodeLoadInfo nodeLoadInfo = readFrom(zkNodesInfoPath, NodeLoadInfo.parser());
        zookeeper.inTransaction().setData().forPath(zkNodesInfoPath, buildNodeLoadInfo(nodeLoadInfo.getProfilesLoaded() - weight).toByteArray())
            .and().delete().forPath(zkPathForProfile(profileName))
            .and().commit();
    }

    private void completeFuture(AggregatedProfileNamingStrategy profileName, Future<AggregatedProfileInfo> cachedProfileInfo, Future<AggregatedProfileInfo> profileFuture) {
        if (!cachedProfileInfo.isComplete()) {
            logger.debug("found profile is loading");
            profileFuture.fail(new ProfileLoadInProgressException(profileName));
        }
        else if (!cachedProfileInfo.succeeded()) {
            logger.debug("found profile could not be loaded because of an error");
            profileFuture.fail(new CachedProfileNotFoundException(cachedProfileInfo.cause()));
        }
        else {
            logger.debug("completing with profile");
            profileFuture.complete(cachedProfileInfo.result());
        }
    }

    private <ViewType extends Cacheable> Future<Pair<AggregatedSamplesPerTraceCtx, ViewType>> getView(AggregatedProfileNamingStrategy profileName, String traceName, Function<AggregatedProfileInfo, ViewType> viewCreator, boolean isCallersView) {
        Future<Pair<AggregatedSamplesPerTraceCtx, ViewType>> viewFuture = Future.future();
        // TODO succeed fast
        String profileKey = toKey(profileName);
        if (cache.get(profileKey) != null) {
            workerExecutor.executeBlocking(f -> getOrCreateView(profileName, traceName, viewCreator, f, isCallersView), viewFuture.completer());
        }
        else {
            // else check into zookeeper if this is cached in another node
            workerExecutor.executeBlocking(f -> {
                ProfileResidencyInfo residencyInfo = null;
                try {
                    try(CloseableSharedLock csl = new CloseableSharedLock()) {
                        residencyInfo = readFrom(zkPathForProfile(profileName), ProfileResidencyInfo.parser());
                    }
                }
                catch (Exception e) {
                    logger.error("Error while reading profile residency info. file: {}", profileName, e);
                    f.fail(e);
                }

                if(residencyInfo != null) {
                    // by the time we got the lock, it is possible a profile load has started
                    if (residencyInfo.getIp().equals(myIp) && residencyInfo.getPort() == port) {
                        getOrCreateView(profileName, traceName, viewCreator, f, isCallersView);
                    }
                    else {
                        // cached in another node
                        f.fail(new CachedProfileNotFoundException(residencyInfo.getIp(), residencyInfo.getPort()));
                    }
                }
                else {
                    f.fail(new CachedProfileNotFoundException());
                }
            }, viewFuture.completer());
        }

        return viewFuture;
    }

    private <ViewType extends Cacheable> void getOrCreateView(AggregatedProfileNamingStrategy profileName, String traceName, Function<AggregatedProfileInfo, ViewType> viewCreator, Future<Pair<AggregatedSamplesPerTraceCtx, ViewType>> f, boolean isCallersView) {
        synchronized (this) {
            Future<AggregatedProfileInfo> cachedProfileInfo = cache.get(toKey(profileName));
            if (cachedProfileInfo != null) {
                if (!cachedProfileInfo.isComplete()) {
                    f.fail(new ProfileLoadInProgressException(profileName));
                    return;
                }

                // unlikely case where profile load failed
                if (!cachedProfileInfo.succeeded()) {
                    f.fail(new CachedProfileNotFoundException(cachedProfileInfo.cause()));
                    return;
                }

                String viewKey = toKeyForViews(profileName, traceName, isCallersView);
                AggregatedSamplesPerTraceCtx samplesPerTraceCtx = cachedProfileInfo.result().getAggregatedSamples(traceName);

                ViewType ctView = cache.getView(viewKey);
                if (ctView != null) {
                    f.complete(new Pair<>(samplesPerTraceCtx, ctView));
                }
                else {
                    // no cached view, so create a new one
                    switch (profileName.workType) {
                        case cpu_sample_work:
                            ctView = viewCreator.apply(cachedProfileInfo.result());
                            addViewToCache(profileName, viewKey, ctView);
                            f.complete(new Pair<>(samplesPerTraceCtx, ctView));
                            break;
                        default:
                            f.fail(new IllegalArgumentException("Unsupported workType: " + profileName.workType));
                    }
                }
            }
            else {
                f.fail(new CachedProfileNotFoundException());
            }
        }
    }

    private void addViewToCache(AggregatedProfileNamingStrategy profileName, String key, Cacheable cacheable) {
        cache.putView(key, cacheable);
        loadedViews.compute(toKey(profileName), (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(key);
            return v;
        });
    }

    private NodeLoadInfo buildNodeLoadInfo(int profileCount) {
        return NodeLoadInfo.newBuilder().setProfilesLoaded(profileCount).build();
    }

    private ProfileResidencyInfo buildResidencyInfo(ProfileResidencyInfo.LoadStatus loadStatus) {
        return ProfileResidencyInfo.newBuilder().setIp(myIp).setPort(port).setStatus(loadStatus).build();
    }

    private String toKey(AggregatedProfileNamingStrategy profileName) {
        return profileName.toString();
    }

    private String toKeyForViews(AggregatedProfileNamingStrategy profileName, String traceName, boolean isCallersView) {
        if (isCallersView) {
            return profileName.toString() + "/" + traceName + "/callersView";
        }
        else {
            return profileName.toString() + "/" + traceName + "/calleesView";
        }
    }

    protected String zkPathForProfile(AggregatedProfileNamingStrategy profileName) {
        return "/profilesLoadStatus/" +  BaseEncoding.base32().encode(profileName.toString().getBytes(Charset.forName("utf-8")));
    }

    private <T extends AbstractMessage> T readFrom(String profileName, Parser<T> parser) throws Exception {
        byte[] bytes = zookeeper.getData().forPath(profileName);
        return parser.parseFrom(bytes);
    }

    private class CloseableSharedLock implements AutoCloseable {

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
        public void close() throws Exception {
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
}

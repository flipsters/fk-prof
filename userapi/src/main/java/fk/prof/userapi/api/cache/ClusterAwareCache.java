package fk.prof.userapi.api.cache;

import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.userapi.Cacheable;
import fk.prof.userapi.Configuration;
import fk.prof.userapi.Pair;
import fk.prof.userapi.api.AggregatedProfileLoader;
import fk.prof.userapi.model.AggregatedProfileInfo;
import fk.prof.userapi.model.AggregatedSamplesPerTraceCtx;
import fk.prof.userapi.model.tree.CallTreeView;
import fk.prof.userapi.model.tree.CalleesTreeView;
import fk.prof.userapi.proto.LoadInfoEntities.ProfileResidencyInfo;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Created by gaurav.ashok on 21/06/17.
 */
public class ClusterAwareCache {

    private static final Logger logger = LoggerFactory.getLogger(ClusterAwareCache.class);

    private final LocalProfileCache cache;
    private final Map<String, ArrayList<String>> loadedViews;

    private final WorkerExecutor workerExecutor;
    /*
    zookeeper node structure:
        /fk-prof-userapi                                                        (session namespace)
        |___/nodesInfo/{ip:port} -> NodeLoadInfo                                (ephemeral)
        |___/profilesLoadStatus/{profileName} -> ProfileResidencyInfo           (ephemeral)
     */
    private ZookeeperLoadInfoStore zkStore;
    private final String myIp;
    private final int port;

    public ClusterAwareCache(CuratorFramework zookeeper, WorkerExecutor workerExecutor, LocalProfileCache cache, Configuration config) {
        this.myIp = config.getIpAddress();
        this.port = config.getHttpConfig().getHttpPort();
        this.zkStore = new ZookeeperLoadInfoStore(zookeeper, myIp, port);
        this.workerExecutor = workerExecutor;

        this.cache = cache;
        this.cache.setRemovalListener(this::doCleanUpOnEviction);
        this.loadedViews = new ConcurrentHashMap<>();
    }

    public Future<Object> onClusterJoin() {
        return doAsync(f -> f.complete(zkStore.ensureBasePathExists()));
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
            completeFuture(profileName, cachedProfileInfo, profileFuture);
        }
        else {
            // check zookeeper if it is loaded somewhere else
            doAsync((Future<AggregatedProfileInfo> f) -> {
                try (AutoCloseable lock = zkStore.getLock()) {
                    Future<AggregatedProfileInfo> _cachedProfileInfo = cache.get(profileKey);
                    if (_cachedProfileInfo != null) {
                        completeFuture(profileName, _cachedProfileInfo, f);
                        return;
                    }

                    // still no cached profile. read zookeeper for remotely cached profile
                    ProfileResidencyInfo residencyInfo = zkStore.readProfileResidencyInfo(profileName);
                    // stale node exists. will update instead of create
                    boolean staleNodeExists = false;
                    if(residencyInfo != null) {
                        if(residencyInfo.getIp().equals(myIp) && residencyInfo.getPort() == port) {
                            staleNodeExists = true;
                        }
                        else {
                            f.fail(new CachedProfileNotFoundException(residencyInfo.getIp(), residencyInfo.getPort()));
                            return;
                        }
                    }

                    // profile not cached anywhere
                    // start the loading process
                    Future<AggregatedProfileInfo> loadProfileFuture = Future.future();
                    workerExecutor.executeBlocking(f2 -> profileLoader.load(f2, profileName), loadProfileFuture.completer());

                    // update LOADING status in zookeeper
                    zkStore.updateProfileStatusToLoading(profileName, staleNodeExists);
                    cache.put(profileKey, loadProfileFuture);

                    loadProfileFuture.setHandler(ar -> {
                        logger.info("Profile load complete. file: {}", profileName);
                        // load_profile might fail, regardless reinsert to take the new utilization into account.
                        cache.put(profileKey, loadProfileFuture);
                    });

                    if(loadProfileFuture.isComplete()) {
                        f.complete(loadProfileFuture.result());
                    }
                    f.fail(new ProfileLoadInProgressException(profileName));
                }
            }, "Error while interacting with zookeeper for file: {}", profileName).setHandler(profileFuture.completer());
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

            AggregatedProfileNamingStrategy profileName = AggregatedProfileNamingStrategy.fromFileName(onRemoval.getKey());

            doAsync(f -> {
                synchronized (this) {
                    List<String> dependentKeys = loadedViews.remove(toKey(profileName));
                    if (dependentKeys != null) {
                        cache.invalidateViews(dependentKeys);
                    }
                }

                try(AutoCloseable lock = zkStore.getLock()) {
                    ProfileResidencyInfo residencyInfo = zkStore.readProfileResidencyInfo(profileName);
                    boolean deleteProfileNode = false;
                    if(residencyInfo != null && (myIp.equals(residencyInfo.getIp()) && port == residencyInfo.getPort())) {
                        deleteProfileNode = true;
                    }
                    zkStore.removeProfile(profileName, deleteProfileNode);
                }

                f.complete();
            }, "Error while cleaning for file: {}", profileName.toString());
        }
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
            doAsync((Future<Pair<AggregatedSamplesPerTraceCtx, ViewType>> f) -> {
                ProfileResidencyInfo residencyInfo;
                try(AutoCloseable lock = zkStore.getLock()) {
                    residencyInfo = zkStore.readProfileResidencyInfo(profileName);
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
            }).setHandler(viewFuture.completer());
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

    interface BlockingTask<T> {
        void getResult(Future<T> future) throws Exception;
    }

    private <T> Future<T> doAsync(BlockingTask<T> s) {
        return doAsync(s, "");
    }

    private <T> Future<T> doAsync(BlockingTask<T> s, String failMsg, Object... objects) {
        Future<T> result = Future.future();
        workerExecutor.executeBlocking(f -> {
            try {
                s.getResult(f);
            }
            catch (Exception e) {
                logger.error(failMsg, e, objects);
                if(!f.isComplete()) {
                    f.fail(e);
                }
            }
        }, result.completer());
        return result;
    }
}
package fk.prof.userapi.api.cache;

import com.google.common.base.Ticker;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.userapi.Configuration;
import fk.prof.userapi.Pair;
import fk.prof.userapi.UserapiConfigManager;
import fk.prof.userapi.api.AggregatedProfileLoader;
import fk.prof.userapi.api.ProfileStoreAPIImpl;
import fk.prof.userapi.model.AggregatedProfileInfo;
import fk.prof.userapi.model.AggregatedSamplesPerTraceCtx;
import fk.prof.userapi.model.tree.CallTreeView;
import fk.prof.userapi.model.tree.CalleesTreeView;
import fk.prof.userapi.proto.LoadInfoEntities;
import fk.prof.userapi.proto.LoadInfoEntities.ProfileResidencyInfo.LoadStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.CreateMode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by gaurav.ashok on 14/07/17.
 */
@RunWith(VertxUnitRunner.class)
public class CacheTest {
    private static final int zkPort = 2191;

    static {
        UserapiConfigManager.setDefaultSystemProperties();
    }

    private Vertx vertx;
    private static Configuration config;
    private static TestingServer zookeeper;
    private static CuratorFramework curator;
    private WorkerExecutor executor;
    private ClusterAwareCache cache;
    private LocalProfileCache localProfileCache;

    private static ZonedDateTime beginning = ZonedDateTime.now(Clock.systemUTC());

    @BeforeClass
    public static void beforeClass() throws Exception {
        config = UserapiConfigManager.loadConfig(ProfileStoreAPIImpl.class.getClassLoader().getResource("userapi-conf.json").getFile());

        zookeeper = new TestingServer(2191, true);

        Configuration.CuratorConfig curatorConfig = config.getCuratorConfig();
        curator = CuratorFrameworkFactory.builder()
            .connectString("127.0.0.1:" + zkPort)
            .retryPolicy(new ExponentialBackoffRetry(1000, curatorConfig.getMaxRetries()))
            .connectionTimeoutMs(curatorConfig.getConnectionTimeoutMs())
            .sessionTimeoutMs(curatorConfig.getSessionTimeoutMs())
            .namespace(curatorConfig.getNamespace())
            .build();

        curator.start();
        curator.blockUntilConnected(config.getCuratorConfig().getConnectionTimeoutMs(), TimeUnit.MILLISECONDS);
    }


    @Before
    public void beforeTest() throws Exception {
        vertx = Vertx.vertx();
        executor = vertx.createSharedWorkerExecutor(config.getBlockingWorkerPool().getName(), 3);
        cleanUpZookeeper();
    }

    private void setUpDefaultCache(TestContext context) {
        localProfileCache = new LocalProfileCache(config);
        cache = new ClusterAwareCache(curator, executor, localProfileCache, config);
        Async async = context.async();
        cache.onClusterJoin().setHandler(ar -> {
            context.assertTrue(ar.succeeded());
            async.complete();
        });
    }

    private void setUpCache(TestContext context, LocalProfileCache localCache) {
        localProfileCache = localCache;
        cache = new ClusterAwareCache(curator, executor, localProfileCache, config);
        Async async = context.async();
        cache.onClusterJoin().setHandler(ar -> {
            context.assertTrue(ar.succeeded());
            async.complete();
        });
    }

    @Test(timeout = 1000)
    public void testNodesInfoExistWhenCacheIsCreated(TestContext context) throws Exception {
        setUpDefaultCache(context);
        byte[] bytes = curator.getData().forPath("/nodesInfo/127.0.0.1:" + config.getHttpConfig().getHttpPort());
        Assert.assertNotNull(bytes);
        Assert.assertNotNull(LoadInfoEntities.NodeLoadInfo.parseFrom(bytes));
    }

    @Test(timeout = 3500)
    public void testLoadProfile_shouldCallLoaderOnlyOnceOnMultipleInvocations(TestContext context) throws Exception {
        setUpDefaultCache(context);
        Async async = context.async(2);
        NameProfilePair npPair = npPair("proc1", dt(0));
        AggregatedProfileLoader loader = mockedProfileLoader(npPair);

        Future<AggregatedProfileInfo> profile1 = cache.getAggregatedProfile(npPair.name, loader);
        Future<AggregatedProfileInfo> profile2 = cache.getAggregatedProfile(npPair.name, loader);

        profile1.setHandler(ar -> {
            context.assertTrue(ar.failed());
            context.assertTrue(ar.cause() instanceof ProfileLoadInProgressException);
            async.countDown();
        });

        profile2.setHandler(ar -> {
            context.assertTrue(ar.failed());
            context.assertTrue(ar.cause() instanceof ProfileLoadInProgressException);
            async.countDown();
        });

        async.awaitSuccess(2000);
        verify(loader, times(1)).load(any(), eq(npPair.name));

        // fetch it again after waiting for some time
        Thread.sleep(1000);
        Async async2 = context.async(1);
        profile1 = cache.getAggregatedProfile(npPair.name, loader);

        profile1.setHandler(ar -> {
            context.assertTrue(ar.succeeded());
            context.assertEquals(ar.result(), npPair.profileInfo);
            async2.countDown();
        });
    }

    @Test(timeout =  2500)
    public void testLoadProfileAndView_shouldReturnWithTheExactCause(TestContext context) throws Exception {
        Async async = context.async(2);
        setUpDefaultCache(context);
        Exception e = new IOException();
        AggregatedProfileNamingStrategy profileName = profileName("proc1", dt(0));
        localProfileCache.put(profileName.toString(), Future.failedFuture(e));

        Future<AggregatedProfileInfo> profile1 = cache.getAggregatedProfile(profileName, mockedProfileLoader());

        profile1.setHandler(ar -> {
            context.assertTrue(ar.failed());
            context.assertTrue(ar.cause() instanceof CachedProfileNotFoundException);
            context.assertEquals(ar.cause().getCause(), e);
            async.countDown();
        });

        Future<Pair<AggregatedSamplesPerTraceCtx, CallTreeView>> view = cache.getCallTreeView(profileName, "", null);
        view.setHandler(ar -> {
            context.assertTrue(ar.failed());
            context.assertTrue(ar.cause() instanceof CachedProfileNotFoundException);
            context.assertEquals(ar.cause().getCause(), e);
            async.countDown();
        });
    }

    @Test(timeout = 2500)
    public void testLoadCallersView_shouldReturnCallersViewForAlreadyLoadedProfile(TestContext context) throws Exception {
        Async async = context.async(2);
        setUpDefaultCache(context);
        NameProfilePair npPair = npPair("proc1", dt(0));
        localProfileCache.put(npPair.name.toString(), Future.succeededFuture(npPair.profileInfo));

        Function<AggregatedProfileInfo, CallTreeView> viewCreator = mockedViewCreator(CallTreeView.class, npPair);

        Future<Pair<AggregatedSamplesPerTraceCtx, CallTreeView>> view1 = cache.getCallTreeView(npPair.name, "t1", viewCreator);
        Future<Pair<AggregatedSamplesPerTraceCtx, CallTreeView>> view2 = cache.getCallTreeView(npPair.name, "t1", viewCreator);

        view1.setHandler(ar -> {
            context.assertTrue(ar.succeeded());
            context.assertEquals(ar.result().second, npPair.calltreeView);
            async.countDown();
        });

        view2.setHandler(ar -> {
            context.assertTrue(ar.succeeded());
            context.assertEquals(ar.result().second, npPair.calltreeView);
            async.countDown();
        });

        async.awaitSuccess(2000);
        verify(viewCreator, times(1)).apply(same(npPair.profileInfo));
    }

    @Test(timeout = 2500)
    public void testLoadProfileAndViewWhenRemotelyCached_shouldThrowExceptionWithRemoteIp(TestContext context) throws Exception {
        Async async = context.async(1);
        setUpDefaultCache(context);
        AggregatedProfileNamingStrategy profileName = profileName("proc1", dt(0));
        // update zookeeper that tells that profile is loaded somewhere else
        createProfileNode(profileName, "127.0.0.1", 3456, LoadStatus.Loaded);

        Future<AggregatedProfileInfo> profile1 = cache.getAggregatedProfile(profileName, mockedProfileLoader());

        profile1.setHandler(ar -> {
            context.assertTrue(ar.failed());
            context.assertTrue(ar.cause() instanceof CachedProfileNotFoundException);
            CachedProfileNotFoundException ex = (CachedProfileNotFoundException) ar.cause();
            context.assertTrue(ex.isCachedRemotely());
            context.assertEquals(ex.getIp(), "127.0.0.1");
            context.assertEquals(ex.getPort(), 3456);
            async.countDown();
        });

        async.awaitSuccess(2000);

        Async async2 = context.async(1);
        Future<?> f = cache.getCalleesTreeView(profileName, "t1", null);
        f.setHandler(ar -> {
            context.assertTrue(ar.failed());
            context.assertTrue(ar.cause() instanceof CachedProfileNotFoundException);
            CachedProfileNotFoundException ex = (CachedProfileNotFoundException) ar.cause();
            context.assertTrue(ex.isCachedRemotely());
            context.assertEquals(ex.getIp(), "127.0.0.1");
            context.assertEquals(ex.getPort(), 3456);
            async2.countDown();
        });
    }

    @Test(timeout = 4000)
    public void testProfileExpiry_cacheShouldGetInvalidated_EntryShouldBeRemovedFromZookeeper(TestContext context) throws Exception {
        TestTicker ticker = new TestTicker();
        setUpCache(context, new LocalProfileCache(config, ticker));

        NameProfilePair npPair = npPair("proc2", dt(0));
        AggregatedProfileLoader loader = mockedProfileLoader(npPair);
        Function<AggregatedProfileInfo, CallTreeView> viewCreator = mockedViewCreator(CallTreeView.class, npPair);

        Async async1 = context.async();
        cache.getAggregatedProfile(npPair.name, loader);
        Thread.sleep(1200);

        Future<?> profile1 = cache.getAggregatedProfile(npPair.name, loader);
        profile1.setHandler(ar -> {
            context.assertTrue(ar.succeeded());
            async1.countDown();
        });
        async1.awaitSuccess(2000);

        Async async2 = context.async();
        Future<?> view1 = cache.getCallTreeView(npPair.name, "t1", viewCreator);
        view1.setHandler(ar -> {
            context.assertTrue(ar.succeeded());
            async2.countDown();
        });
        async2.awaitSuccess(500);

        // all objects are loaded. advance time
        ticker.advance(config.getProfileRetentionDurationMin() + 1, TimeUnit.MINUTES);

        // retry fetching
        Async async3 = context.async(1);
        view1 = cache.getCallTreeView(npPair.name, "t1", viewCreator);
        view1.setHandler(ar -> {
            context.assertTrue(ar.failed());
            context.assertTrue(ar.cause() instanceof CachedProfileNotFoundException);
            async3.countDown();
        });
        async3.awaitSuccess(1000);

        verify(viewCreator, times(1)).apply(same(npPair.profileInfo));
        verify(loader, times(1)).load(any(), same(npPair.name));
    }

    private AggregatedProfileLoader mockedProfileLoader(NameProfilePair... npPairs) {
        AggregatedProfileLoader loader = mock(AggregatedProfileLoader.class);
        for(NameProfilePair npPair : npPairs) {
            doAnswer(invocation -> {
                getDelayedFuture(invocation.getArgument(0), 500, npPair.profileInfo);
                return null;
            }).when(loader).load(any(), eq(npPair.name));
        }
        return loader;
    }

    private <T> Function<AggregatedProfileInfo, T> mockedViewCreator(Class<T> clazz, NameProfilePair... npPairs) {
        Function<AggregatedProfileInfo, T> foo = mock(Function.class);
        for(NameProfilePair npPair : npPairs) {
            if(CallTreeView.class.equals(clazz)) {
                doReturn(npPair.calltreeView).when(foo).apply(npPair.profileInfo);
            }
            else {
                doReturn(npPair.calleesTreeView).when(foo).apply(npPair.profileInfo);
            }
        }
        return foo;
    }

    private static AggregatedProfileNamingStrategy profileName(String procId, ZonedDateTime dt) {
        return new AggregatedProfileNamingStrategy(config.getProfilesBaseDir(), 1, "app1", "cluster1", procId, dt, 1200, AggregatedProfileModel.WorkType.cpu_sample_work);
    }

    private <T> void getDelayedFuture(Future<T> f, int ms, T obj) {
        vertx.setTimer(ms, h -> {
            if(obj instanceof Throwable) {
                f.fail((Throwable) obj);
            }
            else {
                f.complete(obj);
            }
        });
    }

    private ZonedDateTime dt(int offsetInSec) {
        return beginning.plusSeconds(offsetInSec);
    }

    private void createProfileNode(AggregatedProfileNamingStrategy profileName, String ip, int port, LoadStatus status) throws Exception {
        curator.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(cache.zkPathForProfile(profileName),
            LoadInfoEntities.ProfileResidencyInfo.newBuilder().setIp(ip).setPort(port).setStatus(status).build().toByteArray());
    }

    private static class NameProfilePair {
        final AggregatedProfileNamingStrategy name;
        final AggregatedProfileInfo profileInfo;
        final CallTreeView calltreeView;
        final CalleesTreeView calleesTreeView;

        NameProfilePair(String procId, ZonedDateTime dt) {
            this.name = profileName(procId, dt);
            this.profileInfo = mock(AggregatedProfileInfo.class);
            this.calltreeView = mock(CallTreeView.class);
            this.calleesTreeView = mock(CalleesTreeView.class);
        }
    }

    private static NameProfilePair npPair(String procId, ZonedDateTime dt) {
        return new NameProfilePair(procId, dt);
    }

    private void cleanUpZookeeper() throws Exception {
        List<String> profileNodes = new ArrayList<>();
        List<String> nodes = new ArrayList<>();
        if(curator.checkExists().forPath("/profilesLoadStatus") != null) {
            profileNodes.addAll(curator.getChildren().forPath("/profilesLoadStatus"));
        }
        if(curator.checkExists().forPath("/nodesInfo") != null) {
            nodes.addAll(curator.getChildren().forPath("/nodesInfo"));
        }

        for(String path : profileNodes) {
            curator.delete().forPath("/profilesLoadStatus/" + path);
        }
        for(String path : nodes) {
            curator.delete().forPath("/nodesInfo/" + path);
        }
    }

    private static class TestTicker extends Ticker {
        final long beginTime;
        final AtomicLong currTime;

        public TestTicker() {
            beginTime = System.nanoTime();
            currTime = new AtomicLong(beginTime);
        }

        void advance(long duration, TimeUnit unit) {
            currTime.addAndGet(unit.toNanos(duration));
        }

        @Override
        public long read() {
            return currTime.get();
        }
    }
}

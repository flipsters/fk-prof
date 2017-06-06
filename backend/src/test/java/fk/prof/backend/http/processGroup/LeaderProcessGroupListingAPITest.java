package fk.prof.backend.http.processGroup;

import fk.prof.backend.AssociationApiTest;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.Configuration;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderHttpVerticleDeployer;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.model.policy.PolicyStoreAPI;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.internal.util.collections.Sets;

import java.util.Random;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for ProcessGroupAPIs in LeaderHttpVerticle which
 * are getAppIds, getClusterIds, getProcNames
 * Created by rohit.patiyal on 30/05/17.
 */
@RunWith(VertxUnitRunner.class)
public class LeaderProcessGroupListingAPITest {
    public static final String DELIMITER = "/";
    private static final String P_APP_ID = "app1";
    private static final String NP_APP_ID = "foo";
    private static final String P_CLUSTER_ID = "cluster1";
    private static final String NP_CLUSTER_ID = "bar";
    private static final String P_PROC = "process1";
    private static final String NP_PROC = "main";

    private TestingServer testingServer;
    private PolicyStoreAPI policyStoreAPI;
    private Vertx vertx;
    private HttpClient client;
    private int leaderPort;

    @Before
    public void setUp() throws Exception {
        ConfigManager.setDefaultSystemProperties();
        testingServer = new TestingServer();

        Configuration config = ConfigManager.loadConfig(AssociationApiTest.class.getClassLoader().getResource("config.json").getFile());
        vertx = Vertx.vertx(new VertxOptions(config.vertxOptions));
        leaderPort = config.leaderHttpServerOpts.getPort();

        BackendAssociationStore backendAssociationStore = mock(BackendAssociationStore.class);
        policyStoreAPI = mock(PolicyStoreAPI.class);
        PolicyStore policyStore = mock(PolicyStore.class);
        client = vertx.createHttpClient();
        VerticleDeployer leaderHttpVerticleDeployer = new LeaderHttpVerticleDeployer(vertx, config, backendAssociationStore, policyStore, policyStoreAPI);
        leaderHttpVerticleDeployer.deploy();
        //Wait for some time for deployment to complete
        Thread.sleep(1000);
    }

    @After
    public void tearDown(TestContext context) throws Exception {
        final Async async = context.async();
        client.close();
        testingServer.close();
        vertx.close(result -> {
            if (result.succeeded()) {
                async.complete();
            } else {
                context.fail();
            }
        });
    }

    @Test
    public void TestGetAppIdsRoute(TestContext context) throws Exception {
        final Async async = context.async();
        String pPrefixSet = "(^$|a|ap|app|app1)";
        String npPrefixSet = "(f|fo|foo)";

        when(policyStoreAPI.getAppIds(ArgumentMatchers.matches(pPrefixSet))).then(invocation -> Sets.newSet(P_APP_ID));
        when(policyStoreAPI.getAppIds(ArgumentMatchers.matches(npPrefixSet))).then(invocation -> null);


        Future<Void> pCorrectPrefix = Future.future();
        Future<Void> pIncorrectPrefix = Future.future();
        Future<Void> pNoPrefix = Future.future();

        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + ApiPathConstants.APPIDS + "?prefix=" + P_APP_ID.substring(0, 1 + new Random().nextInt(P_APP_ID.length() - 1)), httpClientResponse -> {
            context.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                context.assertTrue(buffer.toString().contains(P_APP_ID));
                context.assertFalse(buffer.toString().contains(NP_APP_ID));
                pCorrectPrefix.complete();
            });
        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + ApiPathConstants.APPIDS + "?prefix=", httpClientResponse -> {
            context.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                context.assertTrue(buffer.toString().contains(P_APP_ID));
                context.assertFalse(buffer.toString().contains(NP_APP_ID));
                pIncorrectPrefix.complete();
            });
        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + ApiPathConstants.APPIDS + "?prefix=" + NP_APP_ID.substring(0, 1 + new Random().nextInt(NP_APP_ID.length() - 1)), httpClientResponse -> {
            context.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                context.assertFalse(buffer.toString().contains(P_APP_ID));
                context.assertFalse(buffer.toString().contains(NP_APP_ID));
                pNoPrefix.complete();
            });
        });

        CompositeFuture.all(pCorrectPrefix, pIncorrectPrefix, pNoPrefix).setHandler(compositeFutureAsyncResult -> async.complete());

    }


    @Test
    public void TestGetClusterIdsRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        String pPrefixSet = "(^$|c|cl|clu|clus|clust|cluste|cluster|cluster1)";
        String npPrefixSet = "(b|ba|bar)";

        when(policyStoreAPI.getClusterIds(eq(P_APP_ID), ArgumentMatchers.matches(pPrefixSet))).then(invocation -> Sets.newSet(P_CLUSTER_ID));
        when(policyStoreAPI.getClusterIds(eq(P_APP_ID), ArgumentMatchers.matches(npPrefixSet))).then(invocation -> null);
        when(policyStoreAPI.getClusterIds(eq(NP_APP_ID), ArgumentMatchers.matches(pPrefixSet))).then(invocation -> null);
        when(policyStoreAPI.getClusterIds(eq(NP_APP_ID), ArgumentMatchers.matches(npPrefixSet))).then(invocation -> null);

        Future<Void> pAndCorrectPrefix = Future.future();
        Future<Void> pAndIncorrectPrefix = Future.future();
        Future<Void> pAndNoPrefix = Future.future();

        Future<Void> npAndPPrefix = Future.future();
        Future<Void> npAndNpPrefix = Future.future();
        Future<Void> npAndNoPrefix = Future.future();


        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/clusterIds/" + P_APP_ID + "?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                pAndNoPrefix.complete();
            });
        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/clusterIds/" + P_APP_ID + "?prefix=" + P_CLUSTER_ID.substring(0, 1 + new Random().nextInt(P_CLUSTER_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                pAndCorrectPrefix.complete();
            });
        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/clusterIds/" + P_APP_ID + "?prefix=" + NP_CLUSTER_ID.substring(0, 1 + new Random().nextInt(NP_CLUSTER_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                pAndIncorrectPrefix.complete();
            });
        });

        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/clusterIds/" + NP_APP_ID + "?prefix=" + P_CLUSTER_ID.substring(0, 1 + new Random().nextInt(P_CLUSTER_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                npAndPPrefix.complete();
            });
        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/clusterIds/" + NP_APP_ID + "?prefix=" + NP_CLUSTER_ID.substring(0, 1 + new Random().nextInt(NP_CLUSTER_ID.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                npAndNpPrefix.complete();
            });
        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/clusterIds/" + NP_APP_ID + "?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_CLUSTER_ID));
                testContext.assertFalse(buffer.toString().contains(NP_CLUSTER_ID));
                npAndNoPrefix.complete();
            });
        });

        CompositeFuture.all(pAndCorrectPrefix, pAndIncorrectPrefix, pAndNoPrefix, npAndPPrefix, npAndNpPrefix, npAndNoPrefix).setHandler(compositeFutureAsyncResult -> async.complete());
    }

    @Test
    public void TestGetProcNameRoute(TestContext testContext) throws Exception {
        final Async async = testContext.async();
        String pPrefixSet = "(^$|p|pr|pro|proc|proce|proces|process|process1)";
        String npPrefixSet = "(m|ma|mai|main)";

        when(policyStoreAPI.getProcNames(eq(P_APP_ID), eq(P_CLUSTER_ID), ArgumentMatchers.matches(pPrefixSet))).then(invocation -> Sets.newSet(P_PROC));
        when(policyStoreAPI.getProcNames(eq(P_APP_ID), eq(P_CLUSTER_ID), ArgumentMatchers.matches(npPrefixSet))).then(invocation -> null);
        when(policyStoreAPI.getProcNames(eq(NP_APP_ID), eq(NP_CLUSTER_ID), ArgumentMatchers.matches(pPrefixSet))).then(invocation -> null);
        when(policyStoreAPI.getProcNames(eq(NP_APP_ID), eq(NP_CLUSTER_ID), ArgumentMatchers.matches(npPrefixSet))).then(invocation -> null);

        Future<Void> pAndCorrectPrefix = Future.future();
        Future<Void> pAndIncorrectPrefix = Future.future();
        Future<Void> pAndNoPrefix = Future.future();

        Future<Void> npAndPPrefix = Future.future();
        Future<Void> npAndNpPrefix = Future.future();
        Future<Void> npAndNoPrefix = Future.future();

        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/procNames/" + P_APP_ID + DELIMITER + P_CLUSTER_ID + "?prefix=" + P_PROC.substring(0, 1 + new Random().nextInt(P_PROC.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                pAndCorrectPrefix.complete();
            });

        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/procNames/" + P_APP_ID + DELIMITER + P_CLUSTER_ID + "?prefix=" + NP_PROC.substring(0, 1 + new Random().nextInt(NP_PROC.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                pAndIncorrectPrefix.complete();
            });

        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/procNames/" + P_APP_ID + DELIMITER + P_CLUSTER_ID + "?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertTrue(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                pAndNoPrefix.complete();
            });

        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/procNames/" + NP_APP_ID + DELIMITER + NP_CLUSTER_ID + "?prefix=" + P_PROC.substring(0, 1 + new Random().nextInt(P_PROC.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                npAndPPrefix.complete();
            });

        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/procNames/" + NP_APP_ID + DELIMITER + NP_CLUSTER_ID + "?prefix=" + NP_PROC.substring(0, 1 + new Random().nextInt(NP_PROC.length() - 1)), httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                npAndNpPrefix.complete();
            });

        });
        client.getNow(leaderPort, "localhost", ApiPathConstants.LEADER + "/procNames/" + NP_APP_ID + DELIMITER + NP_CLUSTER_ID + "?prefix=", httpClientResponse -> {
            testContext.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
            httpClientResponse.bodyHandler(buffer -> {
                testContext.assertFalse(buffer.toString().contains(P_PROC));
                testContext.assertFalse(buffer.toString().contains(NP_PROC));
                npAndNoPrefix.complete();
            });

        });

        CompositeFuture.all(pAndCorrectPrefix, pAndIncorrectPrefix, pAndNoPrefix, npAndPPrefix, npAndNpPrefix, npAndNoPrefix).setHandler(compositeFutureAsyncResult -> async.complete());

    }

}


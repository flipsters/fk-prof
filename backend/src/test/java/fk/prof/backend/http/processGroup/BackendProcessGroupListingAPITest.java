package fk.prof.backend.http.processGroup;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.Configuration;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.BackendHttpVerticleDeployer;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.http.HttpHelper;
import fk.prof.backend.http.ProfHttpClient;
import fk.prof.backend.http.policy.BackendPolicyAPITest;
import fk.prof.backend.model.aggregation.impl.ActiveAggregationWindowsImpl;
import fk.prof.backend.model.assignment.AssociatedProcessGroups;
import fk.prof.backend.model.assignment.impl.AssociatedProcessGroupsImpl;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;
import fk.prof.backend.proto.BackendDTO;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.collections.Sets;

import java.util.Random;

import static org.mockito.Mockito.*;

/**
 * Tests for ProcessGroupListingAPIs in LeaderHttpVerticle which
 * are getAppIds, getClusterIds, getProcNames
 * Created by rohit.patiyal on 30/05/17.
 */
@RunWith(VertxUnitRunner.class)
public class BackendProcessGroupListingAPITest {
    public static final String DELIMITER = "/";
    private static final String APP_ID = "app1";
    private static final String CLUSTER_ID = "cluster1";
    private static final String PROC = "process1";


    private HttpServer leaderServer;
    private ProfHttpClient profHttpClient;
    private HttpClient client;
    private static int backendPort;
    private static int leaderPort;
    private static String LEADER_IP = "localhost";
    private InMemoryLeaderStore inMemoryLeaderStore;
    private static Vertx vertx;

    @Before
    public void setUp() throws Exception {
        ConfigManager.setDefaultSystemProperties();
        Configuration config = ConfigManager.loadConfig(BackendPolicyAPITest.class.getClassLoader().getResource("config.json").getFile());
        profHttpClient = mock(ProfHttpClient.class);
        vertx = Vertx.vertx(new VertxOptions(config.vertxOptions));

        client = vertx.createHttpClient();
        leaderServer = vertx.createHttpServer();
        backendPort = config.backendHttpServerOpts.getPort();
        leaderPort = config.leaderHttpServerOpts.getPort();
        inMemoryLeaderStore = spy(new InMemoryLeaderStore(config.ipAddress, config.leaderHttpServerOpts.getPort()));
        AssociatedProcessGroups associatedProcessGroups = new AssociatedProcessGroupsImpl(config.recorderDefunctThresholdSecs);
        VerticleDeployer backendHttpVerticleDeployer = new BackendHttpVerticleDeployer(vertx, config, inMemoryLeaderStore, new ActiveAggregationWindowsImpl(), associatedProcessGroups);
        backendHttpVerticleDeployer.deploy();
    }

    @After
    public void tearDown(TestContext context) throws Exception {
        final Async async = context.async();
        client.close();
        leaderServer.close();
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

        Router router = Router.router(vertx);
        HttpHelper.attachHandlersToRoute(router, HttpMethod.GET,
                ApiPathConstants.LEADER + ApiPathConstants.APPIDS, req -> req.response().end(Json.encode(Sets.newSet(APP_ID))));
        leaderServer.requestHandler(router::accept);
        leaderServer.listen(leaderPort, result -> {
            if (result.succeeded()) {
                when(inMemoryLeaderStore.getLeader()).thenReturn(BackendDTO.LeaderDetail.newBuilder().setHost(LEADER_IP).setPort(leaderPort).build());
                client.getNow(backendPort, "localhost", ApiPathConstants.APPIDS + "?prefix=" + APP_ID.substring(0, 1 + new Random().nextInt(APP_ID.length() - 1)), httpClientResponse -> {
                    context.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
                    httpClientResponse.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toJsonArray().size(), 1);
                        context.assertTrue(buffer.toString().contains(APP_ID));
                        async.complete();
                    });
                });
            } else {
                context.fail();
            }
        });
    }

    @Test
    public void TestGetClusterIdsRoute(TestContext context) throws Exception {
        final Async async = context.async();

        Router router = Router.router(vertx);
        HttpHelper.attachHandlersToRoute(router, HttpMethod.GET,
                ApiPathConstants.LEADER + ApiPathConstants.CLUSTERIDS_GIVEN_APPID, req -> req.response().end(Json.encode(Sets.newSet(CLUSTER_ID))));
        leaderServer.requestHandler(router::accept);
        leaderServer.listen(leaderPort, result -> {
            if (result.succeeded()) {
                when(inMemoryLeaderStore.getLeader()).thenReturn(BackendDTO.LeaderDetail.newBuilder().setHost(LEADER_IP).setPort(leaderPort).build());
                client.getNow(backendPort, "localhost", "/clusterIds/" + APP_ID + "?prefix=" + CLUSTER_ID.substring(0, 1 + new Random().nextInt(CLUSTER_ID.length() - 1)), httpClientResponse -> {
                    context.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
                    httpClientResponse.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toJsonArray().size(), 1);
                        context.assertTrue(buffer.toString().contains(CLUSTER_ID));
                        async.complete();
                    });
                });
            } else {
                context.fail();
            }
        });
    }


    @Test
    public void TestGetProcNamesRoute(TestContext context) throws Exception {
        final Async async = context.async();

        Router router = Router.router(vertx);
        HttpHelper.attachHandlersToRoute(router, HttpMethod.GET,
                ApiPathConstants.LEADER + ApiPathConstants.PROCNAMES_GIVEN_APPID_CLUSTERID, req -> req.response().end(Json.encode(Sets.newSet(PROC))));
        leaderServer.requestHandler(router::accept);
        leaderServer.listen(leaderPort, result -> {
            if (result.succeeded()) {
                when(inMemoryLeaderStore.getLeader()).thenReturn(BackendDTO.LeaderDetail.newBuilder().setHost(LEADER_IP).setPort(leaderPort).build());
                client.getNow(backendPort, "localhost", "/procNames/" + APP_ID + DELIMITER + CLUSTER_ID + "?prefix=" + PROC.substring(0, 1 + new Random().nextInt(PROC.length() - 1)), httpClientResponse -> {
                    context.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
                    httpClientResponse.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toJsonArray().size(), 1);
                        context.assertTrue(buffer.toString().contains(PROC));
                        async.complete();
                    });
                });
            } else {
                context.fail();
            }
        });
    }
}
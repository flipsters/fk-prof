package fk.prof.backend.http.policy;

import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.Configuration;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.BackendHttpVerticleDeployer;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.http.HttpHelper;
import fk.prof.backend.http.ProfHttpClient;
import fk.prof.backend.mock.MockPolicyData;
import fk.prof.backend.model.aggregation.impl.ActiveAggregationWindowsImpl;
import fk.prof.backend.model.assignment.AssociatedProcessGroups;
import fk.prof.backend.model.assignment.impl.AssociatedProcessGroupsImpl;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.proto.PolicyDTO;
import fk.prof.backend.util.ProtoUtil;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static fk.prof.backend.util.ZookeeperUtil.DELIMITER;
import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class BackendPolicyAPITest {

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
    public void TestGetPolicyProxiedToLeader(TestContext context) {
        final Async async = context.async();
        Router router = Router.router(vertx);
        HttpHelper.attachHandlersToRoute(router, HttpMethod.GET,
                ApiPathConstants.LEADER + ApiPathConstants.POLICY_GIVEN_APPID_CLUSTERID_PROCNAME, req -> {
                    PolicyDTO.VersionedPolicyDetails versionedPolicyDetails = MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), 0);

                    req.response().end(versionedPolicyDetails.toString());
                });
        leaderServer.requestHandler(router::accept);
        leaderServer.listen(leaderPort, result -> {
            if (result.succeeded()) {
                when(inMemoryLeaderStore.getLeader()).thenReturn(BackendDTO.LeaderDetail.newBuilder().setHost(LEADER_IP).setPort(leaderPort).build());
                String backendPolicyPath = ApiPathConstants.POLICY + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getAppId() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getCluster() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getProcName();

                client.getNow(backendPort, "localhost", backendPolicyPath, res -> {
                    res.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toString(), MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), 0).toString());
                        async.complete();
                    });
                });
            } else {
                context.fail();
            }
        });
    }

    @Test
    public void TestPutPolicyProxiedToLeader(TestContext context) {
        final Async async = context.async();
        Router router = Router.router(vertx);
        Buffer payloadAsBuffer = null;
        try {
            payloadAsBuffer = ProtoUtil.buildBufferFromProto(MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), 0));
        } catch (IOException e) {
            context.fail(e);
        }
        Buffer finalPayloadAsBuffer = payloadAsBuffer;
        HttpHelper.attachHandlersToRoute(router, HttpMethod.PUT,
                ApiPathConstants.LEADER + ApiPathConstants.POLICY_GIVEN_APPID_CLUSTERID_PROCNAME,
                BodyHandler.create().setBodyLimit(1024 * 10), req -> {
                    try {
                        PolicyDTO.VersionedPolicyDetails expected = PolicyDTO.VersionedPolicyDetails.parseFrom(finalPayloadAsBuffer.getBytes());
                        PolicyDTO.VersionedPolicyDetails got = PolicyDTO.VersionedPolicyDetails.parseFrom(req.getBody().getBytes());
                        context.assertEquals(expected, got);
                        req.response().end(got.toBuilder().setVersion(got.getVersion() + 1).toString());
                    } catch (InvalidProtocolBufferException e) {
                        context.fail(e);
                    }
                });
        leaderServer.requestHandler(router::accept);
        leaderServer.listen(leaderPort, result -> {
            if (result.succeeded()) {
                when(inMemoryLeaderStore.getLeader()).thenReturn(BackendDTO.LeaderDetail.newBuilder().setHost(LEADER_IP).setPort(leaderPort).build());
                String backendPolicyPath = ApiPathConstants.POLICY + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getAppId() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getCluster() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getProcName();

                client.put(backendPort, "localhost", backendPolicyPath, res -> {
                    res.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toString(), MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), 1).toString());
                        async.complete();
                    });
                }).end(finalPayloadAsBuffer);
            } else {
                context.fail();
            }
        });
    }

    @Test
    public void TestPostPolicyProxiedToLeader(TestContext context) {
        final Async async = context.async();
        Router router = Router.router(vertx);
        Buffer payloadAsBuffer = null;
        try {
            payloadAsBuffer = ProtoUtil.buildBufferFromProto(MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), -1));
        } catch (IOException e) {
            context.fail(e);
        }
        Buffer finalPayloadAsBuffer = payloadAsBuffer;
        HttpHelper.attachHandlersToRoute(router, HttpMethod.POST,
                ApiPathConstants.LEADER + ApiPathConstants.POLICY_GIVEN_APPID_CLUSTERID_PROCNAME,
                BodyHandler.create().setBodyLimit(1024 * 10), req -> {
                    try {
                        PolicyDTO.VersionedPolicyDetails expected = PolicyDTO.VersionedPolicyDetails.parseFrom(finalPayloadAsBuffer.getBytes());
                        PolicyDTO.VersionedPolicyDetails got = PolicyDTO.VersionedPolicyDetails.parseFrom(req.getBody().getBytes());
                        context.assertEquals(expected, got);
                        req.response().end(got.toBuilder().setVersion(got.getVersion() + 1).toString());
                    } catch (InvalidProtocolBufferException e) {
                        context.fail(e);
                    }
                });
        leaderServer.requestHandler(router::accept);
        leaderServer.listen(leaderPort, result -> {
            if (result.succeeded()) {
                when(inMemoryLeaderStore.getLeader()).thenReturn(BackendDTO.LeaderDetail.newBuilder().setHost(LEADER_IP).setPort(leaderPort).build());
                String backendPolicyPath = ApiPathConstants.POLICY + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getAppId() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getCluster() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getProcName();

                client.post(backendPort, "localhost", backendPolicyPath, res -> {
                    res.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toString(), MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), 0).toString());
                        async.complete();
                    });
                }).end(finalPayloadAsBuffer);
            } else {
                context.fail();
            }
        });
    }
}

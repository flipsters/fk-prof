package fk.prof.backend.http.policy;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.Configuration;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.BackendHttpVerticleDeployer;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.http.ProfHttpClient;
import fk.prof.backend.mock.MockPolicyData;
import fk.prof.backend.model.aggregation.impl.ActiveAggregationWindowsImpl;
import fk.prof.backend.model.assignment.AssociatedProcessGroups;
import fk.prof.backend.model.assignment.impl.AssociatedProcessGroupsImpl;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static fk.prof.backend.util.ZookeeperUtil.DELIMITER;
import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class BackendPolicyAPITest {

    private HttpServer leader;
    private ProfHttpClient profHttpClient;
    private HttpClient client;
    private static int backendPort;
    private static int leaderPort;
    private static String LEADER_IP = "localhost";
    private InMemoryLeaderStore inMemoryLeaderStore;

    @Before
    public void setUp() throws Exception {
        ConfigManager.setDefaultSystemProperties();
        Configuration config = ConfigManager.loadConfig(BackendPolicyAPITest.class.getClassLoader().getResource("config.json").getFile());
        Vertx vertx = Vertx.vertx(new VertxOptions(config.vertxOptions));
        profHttpClient = mock(ProfHttpClient.class);
        client = vertx.createHttpClient();
        leader = vertx.createHttpServer();
        backendPort = config.backendHttpServerOpts.getPort();
        leaderPort = config.leaderHttpServerOpts.getPort();
        inMemoryLeaderStore = spy(new InMemoryLeaderStore(config.ipAddress, config.leaderHttpServerOpts.getPort()));
        AssociatedProcessGroups associatedProcessGroups = new AssociatedProcessGroupsImpl(config.recorderDefunctThresholdSecs);
        VerticleDeployer backendHttpVerticleDeployer = new BackendHttpVerticleDeployer(vertx, config, inMemoryLeaderStore, new ActiveAggregationWindowsImpl(), associatedProcessGroups);
        backendHttpVerticleDeployer.deploy();
    }

    @Test
    public void TestProxyToLeader(TestContext context) throws Exception {
        final Async async = context.async();

        Buffer payloadAsBuffer = ProtoUtil.buildBufferFromProto(MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), -1));
        ConcurrentLinkedQueue<HttpServerRequest> requestsQ = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Buffer> bufferQ = new ConcurrentLinkedQueue<>();

        String leaderPolicyPath = ApiPathConstants.LEADER + ApiPathConstants.POLICY + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getAppId() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getCluster() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getProcName();
        String backendPolicyPath = ApiPathConstants.POLICY + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getAppId() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getCluster() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getProcName();

        leader.requestHandler(req -> {
            requestsQ.add(req);
            req.bodyHandler(buff -> {
                bufferQ.add(buff);
            if (bufferQ.size() == 2) {
                HttpServerRequest r1 = requestsQ.poll();
                HttpServerRequest r2 = requestsQ.poll();
                Buffer b1 = bufferQ.poll();
                Buffer b2 = bufferQ.poll();
                for (Map.Entry h : r1.headers().entries()) {
                    System.out.println("R1 " + h.getKey() + " : " + h.getValue());
                }
                for (Map.Entry h : r2.headers().entries()) {
                    System.out.println("R2 " + h.getKey() + " : " + h.getValue());
                }
//                if(!(r1.params().isEmpty() && r2.params().isEmpty()))
//                context.assertEquals(r1.params(), r2.params());
                context.assertEquals(b1, b2);
                async.complete();
            }
            });
        });
        leader.listen(leaderPort, LEADER_IP);
        //Wait for some time for deployment to complete
        Thread.sleep(1000);
        when(inMemoryLeaderStore.getLeader()).thenReturn(BackendDTO.LeaderDetail.newBuilder().setHost(LEADER_IP).setPort(leaderPort).build());
        client.post(leaderPort, "localhost", leaderPolicyPath, res -> {
        }).end(payloadAsBuffer);
        client.post(backendPort, "localhost", backendPolicyPath, res -> {
        }).end(payloadAsBuffer);
    }
}

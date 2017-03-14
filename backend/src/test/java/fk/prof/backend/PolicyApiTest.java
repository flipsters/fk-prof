package fk.prof.backend;

import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderHttpVerticleDeployer;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ProcessGroupCountBasedBackendComparator;
import fk.prof.backend.model.association.impl.ZookeeperBasedBackendAssociationStore;
import fk.prof.backend.model.policy.PolicyStore;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import policy.PolicyDetails;
import recording.Recorder;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for policy API in LeaderHttpVerticle
 * Created by rohit.patiyal on 14/03/17.
 */
@RunWith(VertxUnitRunner.class)
public class PolicyApiTest {
  private final String backendAssociationPath = "/assoc";
  private final String policyPath = "/policy";
  private Vertx vertx;
  private Integer port;
  private int leaderPort;
  private TestingServer testingServer;
  private CuratorFramework curatorClient;
  private ConfigManager configManager;
  private PolicyStore policyStore;

  private static List<Recorder.ProcessGroup> mockProcessGroups;
  private static List<PolicyDetails> mockPolicies;
  private HttpClient client;
  private BackendAssociationStore backendAssociationStore;

  @BeforeClass
  public static void setBeforeClass()
      throws Exception {
    mockProcessGroups = Arrays.asList(
        Recorder.ProcessGroup.newBuilder().setAppId("a1").setCluster("c1").setProcName("p1").build(),
        Recorder.ProcessGroup.newBuilder().setAppId("a1").setCluster("c1").setProcName("p2").build(),
        Recorder.ProcessGroup.newBuilder().setAppId("a1").setCluster("c2").setProcName("p3").build(),
        Recorder.ProcessGroup.newBuilder().setAppId("a2").setCluster("c1").setProcName("p1").build()
    );
    mockPolicies = Arrays.asList(
        PolicyDetails.newBuilder().setAdministrator("admin1").setCreatedAt("3").setModifiedAt("3").setLastScheduled("3:20").build(),
        PolicyDetails.newBuilder().setAdministrator("admin1").setCreatedAt("4").setModifiedAt("4").setLastScheduled("4:30").build(),
        PolicyDetails.newBuilder().setAdministrator("admin2").setCreatedAt("5").setModifiedAt("5").setLastScheduled("5:40").build()
    );
  }


  @Before
  public void setUp() throws Exception {
    ConfigManager.setDefaultSystemProperties();

    testingServer = new TestingServer();
    Timing timing = new Timing();
    curatorClient = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
    curatorClient.start();
    curatorClient.blockUntilConnected(10, TimeUnit.SECONDS);
    curatorClient.create().forPath(policyPath);
    curatorClient.create().forPath(backendAssociationPath);

    configManager = new ConfigManager(AssociationApiTest.class.getClassLoader().getResource("config.json").getFile());
    vertx = Vertx.vertx(new VertxOptions(configManager.getVertxConfig()));
    port = configManager.getBackendHttpPort();
    leaderPort = configManager.getLeaderHttpPort();
    backendAssociationStore = new ZookeeperBasedBackendAssociationStore(vertx, curatorClient, "/assoc", 1, 1, configManager.getBackendHttpPort(), new ProcessGroupCountBasedBackendComparator());

    policyStore = mock(PolicyStore.class);
    when(policyStore.getAssociatedPolicies(mockProcessGroups.get(0).getAppId())).thenAnswer(invocation -> new HashMap<Object, Object>() {
      {
        put(mockProcessGroups.get(0).getAppId(), new HashMap<Object, Object>() {
          {
            put(mockProcessGroups.get(0).getCluster(), new HashMap<Object, Object>() {
              {
                put(mockProcessGroups.get(0).getProcName(), mockPolicies.get(0));
                put(mockProcessGroups.get(1).getProcName(), mockPolicies.get(1));
              }
            });
            put(mockProcessGroups.get(2).getCluster(), new HashMap<Object, Object>() {
              {
                put(mockProcessGroups.get(2).getProcName(), mockPolicies.get(2));
              }
            });
          }
        });
      }
    });

    client = vertx.createHttpClient();
    VerticleDeployer leaderHttpVerticleDeployer = new LeaderHttpVerticleDeployer(vertx, configManager, backendAssociationStore, policyStore);
    leaderHttpVerticleDeployer.deploy();
    //Wait for some time for deployment to complete
    Thread.sleep(1000);
  }

  @Test
  public void testGetPolicyGivenAppId(TestContext context) throws Exception {
    final Async async = context.async();
    client.getNow(leaderPort, "localhost", "/leader/policies/" + mockProcessGroups.get(0).getAppId(), httpClientResponse -> {
      context.assertEquals(httpClientResponse.statusCode(), HttpResponseStatus.OK.code());
      httpClientResponse.bodyHandler(buffer -> {
//        System.out.println(buffer.toString());
        context.assertFalse(buffer.toString().contains(mockProcessGroups.get(0).getAppId()));
        context.assertTrue(buffer.toJsonArray().size() == 3);
        async.complete();
      });
    });
  }

  @After
  public void tearDown(TestContext context) throws Exception {
    System.out.println("Tearing down");
    client.close();
    vertx.close(result -> {
      System.out.println("Vertx shutdown");
      curatorClient.close();
      try {
        testingServer.close();
      } catch (IOException ex) {
        System.err.println(ex.getMessage());
      }
      if (result.failed()) {
        context.fail(result.cause());
      }
    });

  }
}

package fk.prof.backend;

import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderHttpVerticleDeployer;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ProcessGroupCountBasedBackendComparator;
import fk.prof.backend.model.association.impl.ZookeeperBasedBackendAssociationStore;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import recording.Recorder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(VertxUnitRunner.class)
public class LeaderAPILoadAndAssociationTest {
  private Vertx vertx;
  private ConfigManager configManager;
  private Integer port;

  private TestingServer testingServer;
  private CuratorFramework curatorClient;
  private List<Recorder.ProcessGroup> mockProcessGroups;


  @Before
  public void setBefore(TestContext context) throws Exception {
    mockProcessGroups = Arrays.asList(
      Recorder.ProcessGroup.newBuilder().setAppId("a").setCluster("c").setProcName("p1").build(),
      Recorder.ProcessGroup.newBuilder().setAppId("a").setCluster("c").setProcName("p2").build(),
      Recorder.ProcessGroup.newBuilder().setAppId("a").setCluster("c").setProcName("p3").build()
    );
    ConfigManager.setDefaultSystemProperties();

    testingServer = new TestingServer();
    curatorClient = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), 500, 500, new RetryOneTime(1));
    curatorClient.start();
    curatorClient.blockUntilConnected(10, TimeUnit.SECONDS);

    configManager = new ConfigManager(LeaderAPILoadAndAssociationTest.class.getClassLoader().getResource("config.json").getFile());
    vertx = Vertx.vertx(new VertxOptions(configManager.getVertxConfig()));
    port = configManager.getLeaderHttpPort();

    JsonObject leaderHttpConfig = configManager.getLeaderHttpDeploymentConfig();
    String backendAssociationPath = leaderHttpConfig.getString("backend.association.path", "/assoc");
    curatorClient.create().forPath(backendAssociationPath);

    BackendAssociationStore backendAssociationStore = new ZookeeperBasedBackendAssociationStore(
        vertx, curatorClient, backendAssociationPath,
        configManager.getLoadReportIntervalInSeconds(),
        leaderHttpConfig.getInteger("load.miss.tolerance", 1), configManager.getBackendHttpPort(),
        new ProcessGroupCountBasedBackendComparator());

    VerticleDeployer leaderHttpDeployer = new LeaderHttpVerticleDeployer(vertx, configManager, backendAssociationStore);
    leaderHttpDeployer.deploy();
    //Wait for some time for deployment to complete
    Thread.sleep(1000);
  }

  @After
  public void tearDown(TestContext context) throws IOException {
    System.out.println("Tearing down");
    vertx.close(result -> {
      System.out.println("Vertx shutdown");
      curatorClient.close();
      try {
        testingServer.close();
      } catch (IOException ex) {
      }
      if (result.failed()) {
        context.fail(result.cause());
      }
    });
  }

  @Test(timeout = 5000)
  public void reportNewBackendLoad(TestContext context) throws IOException {
    makeRequestReportLoad(BackendDTO.LoadReportRequest.newBuilder().setIp("1").setLoad(0.5f).setCurrTick(1).build())
        .setHandler(ar -> {
          if(ar.succeeded()) {
            context.assertEquals(0, ar.result().getProcessGroupList().size());
            context.async().complete();
          } else {
            context.fail(ar.cause());
          }
        });
  }

  /**
   * Simulates following scenario:
   * => backend ip=1 reported
   * => association fetched for process group=p1, should be backend ip=1, since this is the only backend available
   * => association fetched for process group=p2, should again be backend ip=1, since this is the only backend available
   * => backend ip=2 reported
   * => association fetched for process group=p3, should be backend ip=2, since this has zero assigned and takes priority over backend ip=1
   * => wait for sometime so that backends get marked as defunct, then report load for backend ip=1, so that it gets marked available
   * => association fetched for process group=p3 again, should be backend ip=1 now since backend ip=2 has been marked defunct
   * @param context
   */
  @Test(timeout = 5000)
  public void getAssociationForProcessGroups(TestContext context) throws IOException {
    final Async async = context.async();
    BackendDTO.LoadReportRequest loadRequest1 = BackendDTO.LoadReportRequest.newBuilder().setIp("1").setLoad(0.5f).setCurrTick(1).build();
    BackendDTO.LoadReportRequest loadRequest2 = BackendDTO.LoadReportRequest.newBuilder().setIp("2").setLoad(0.5f).setCurrTick(1).build();

    makeRequestReportLoad(loadRequest1)
        .setHandler(ar1 -> {
          if(ar1.succeeded()) {
            try {
              makeRequestGetAssociation(mockProcessGroups.get(0))
                  .setHandler(ar2 -> {
                    if (ar2.succeeded()) {
                      try {
//                        Recorder.AssignedBackend ab2 = Recorder.AssignedBackend.parseFrom(ar2.result())
                        context.assertEquals("1", ar2.result().getHost());
                        makeRequestGetAssociation(mockProcessGroups.get(1))
                            .setHandler(ar3 -> {
                              if (ar3.succeeded()) {
                                context.assertEquals("1", ar3.result().getHost());
                                try {
                                  makeRequestReportLoad(loadRequest2)
                                      .setHandler(ar4 -> {
                                        if (ar4.succeeded()) {
                                          try {
                                            makeRequestGetAssociation(mockProcessGroups.get(2))
                                                .setHandler(ar5 -> {
                                                  if (ar5.succeeded()) {
                                                    context.assertEquals("2", ar5.result().getHost());
                                                    vertx.setTimer(3000, timerId -> {
                                                      try {
                                                        makeRequestReportLoad(loadRequest1)
                                                            .setHandler(ar6 -> {
                                                              if (ar6.succeeded()) {
                                                                try {
                                                                  makeRequestGetAssociation(mockProcessGroups.get(2))
                                                                      .setHandler(ar7 -> {
                                                                        if (ar7.succeeded()) {
                                                                          context.assertEquals("1", ar7.result().getHost());
                                                                          async.complete();
                                                                        } else {
                                                                          context.fail(ar7.cause());
                                                                        }
                                                                      });
                                                                } catch (IOException ex) {
                                                                  context.fail(ex);
                                                                }
                                                              } else {
                                                                context.fail(ar6.cause());
                                                              }
                                                            });
                                                      } catch (IOException ex) {
                                                        context.fail(ex);
                                                      }
                                                    });
                                                  } else {
                                                    context.fail(ar5.cause());
                                                  }
                                                });
                                          } catch (IOException ex) {
                                            context.fail(ex);
                                          }
                                        } else {
                                          context.fail(ar4.cause());
                                        }
                                      });
                                } catch (IOException ex) {
                                  context.fail(ex);
                                }
                              } else {
                                context.fail(ar3.cause());
                              }
                            });
                      } catch (IOException ex) {
                        context.fail(ex);
                      }
                    } else {
                      context.fail(ar2.cause());
                    }
                  });
            } catch (IOException ex) { context.fail(ex); }
          } else {
            context.fail(ar1.cause());
          }
        });
  }

  private Future<Recorder.ProcessGroups> makeRequestReportLoad(BackendDTO.LoadReportRequest payload)
      throws IOException {
    Future<Recorder.ProcessGroups> future = Future.future();
    HttpClientRequest request = vertx.createHttpClient()
        .post(port, "localhost", "/leader/load")
        .handler(response -> {
          response.bodyHandler(buffer -> {
            try {
              Recorder.ProcessGroups result = Recorder.ProcessGroups.parseFrom(buffer.getBytes());
              future.complete(result);
            } catch (Exception ex) {
              future.fail(ex);
            }
          });
        }).exceptionHandler(ex -> {
          future.fail(ex);
        });
    request.end(ProtoUtil.buildBufferFromProto(payload));
    return future;
  }

  private Future<Recorder.AssignedBackend> makeRequestGetAssociation(Recorder.ProcessGroup payload)
      throws IOException {
    Future<Recorder.AssignedBackend> future = Future.future();
    HttpClientRequest request = vertx.createHttpClient()
        .post(port, "localhost", "/leader/association")
        .handler(response -> {
          response.bodyHandler(buffer -> {
            try {
              Recorder.AssignedBackend assignedBackend = Recorder.AssignedBackend.parseFrom(buffer.getBytes());
              future.complete(assignedBackend);
            } catch (Exception ex) {
              future.fail(ex);
            }
          });
        }).exceptionHandler(ex -> future.fail(ex));
    request.end(ProtoUtil.buildBufferFromProto(payload));
    return future;
  }

}

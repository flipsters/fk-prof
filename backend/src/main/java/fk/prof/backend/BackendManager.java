package fk.prof.backend;

import com.google.common.base.Preconditions;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.BackendHttpVerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderElectionParticipatorVerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderElectionWatcherVerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderHttpVerticleDeployer;
import fk.prof.backend.leader.election.LeaderElectedTask;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ProcessGroupCountBasedBackendComparator;
import fk.prof.backend.model.association.impl.ZookeeperBasedBackendAssociationStore;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.model.policy.impl.ZookeeperBasedPolicyStore;
import fk.prof.backend.service.ProfileWorkService;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TODO: Deployment process is liable to changes later
 */
public class BackendManager {
  private static Logger logger = LoggerFactory.getLogger(BackendManager.class);

  private final Vertx vertx;
  private final ConfigManager configManager;
  private final CuratorFramework curatorClient;

  public BackendManager(String configFilePath) throws Exception {
    this(new ConfigManager(configFilePath));
  }

  public BackendManager(ConfigManager configManager) throws Exception {
    ConfigManager.setDefaultSystemProperties();
    this.configManager = Preconditions.checkNotNull(configManager);

    VertxOptions vertxOptions = new VertxOptions(configManager.getVertxConfig());
    vertxOptions.setMetricsOptions(new DropwizardMetricsOptions()
        .setEnabled(true)
        .setJmxEnabled(true)
        .setRegistryName(ConfigManager.METRIC_REGISTRY)
        .addMonitoredHttpServerUri(new Match().setValue("/.*").setType(MatchType.REGEX)));
    this.vertx = Vertx.vertx(vertxOptions);

    this.curatorClient = createCuratorClient();
    curatorClient.start();
    curatorClient.blockUntilConnected(configManager.getCuratorConfig().getInteger("connection.timeout.ms", 10000), TimeUnit.MILLISECONDS);
  }

  public Future<Void> close() {
    Future future = Future.future();
    vertx.close(closeResult -> {
      if (closeResult.succeeded()) {
        logger.info("Shutdown successful for vertx instance");
        curatorClient.close();
        future.complete();
      } else {
        logger.error("Error shutting down vertx instance");
        future.fail(closeResult.cause());
      }
    });

    return future;
  }

  public Future<Void> launch() {
    Future result = Future.future();
    InMemoryLeaderStore leaderStore = new InMemoryLeaderStore(configManager.getIPAddress());
    ProfileWorkService profileWorkService = new ProfileWorkService();
    PolicyStore policyStore = createPolicyStore(curatorClient);

    VerticleDeployer backendHttpVerticleDeployer = new BackendHttpVerticleDeployer(vertx, configManager, leaderStore, profileWorkService, policyStore);
    backendHttpVerticleDeployer.deploy().setHandler(backendDeployResult -> {
      if (backendDeployResult.succeeded()) {
        try {
          List<String> backendDeployments = backendDeployResult.result().list();
          BackendAssociationStore backendAssociationStore = createBackendAssociationStore(vertx, curatorClient);
          VerticleDeployer leaderHttpVerticleDeployer = new LeaderHttpVerticleDeployer(vertx, configManager, backendAssociationStore);
          Runnable leaderElectedTask = createLeaderElectedTask(vertx, leaderHttpVerticleDeployer, backendDeployments);

          VerticleDeployer leaderElectionParticipatorVerticleDeployer = new LeaderElectionParticipatorVerticleDeployer(
              vertx, configManager, curatorClient, leaderElectedTask);
          VerticleDeployer leaderElectionWatcherVerticleDeployer = new LeaderElectionWatcherVerticleDeployer(vertx, configManager, curatorClient, leaderStore);

          CompositeFuture leaderDeployFuture = CompositeFuture.all(
              leaderElectionParticipatorVerticleDeployer.deploy(), leaderElectionWatcherVerticleDeployer.deploy());
          leaderDeployFuture.setHandler(leaderDeployResult -> {
            if (leaderDeployResult.succeeded()) {
              result.complete();
            } else {
              result.fail(leaderDeployResult.cause());
            }
          });
        } catch (Exception ex) {
          result.fail(ex);
        }
      } else {
        result.fail(backendDeployResult.cause());
      }
    });

    return result;
  }

  private PolicyStore createPolicyStore(CuratorFramework curatorClient) {
    JsonObject policyConfig = configManager.getPolicyConfig();
    String policyPath = policyConfig.getString("policy.path", "/policy");
    return new ZookeeperBasedPolicyStore(curatorClient, Executors.newCachedThreadPool(), policyPath);
  }


  private CuratorFramework createCuratorClient() {
    JsonObject curatorConfig = configManager.getCuratorConfig();
    return CuratorFrameworkFactory.builder()
        .connectString(curatorConfig.getString("connection.url"))
        .retryPolicy(new ExponentialBackoffRetry(1000, curatorConfig.getInteger("max.retries", 3)))
        .connectionTimeoutMs(curatorConfig.getInteger("connection.timeout.ms", 10000))
        .sessionTimeoutMs(curatorConfig.getInteger("session.timeout.ms", 60000))
        .namespace(curatorConfig.getString("namespace", "fkprof"))
        .build();
  }

  private BackendAssociationStore createBackendAssociationStore(
      Vertx vertx, CuratorFramework curatorClient)
      throws Exception {
    int loadReportIntervalInSeconds = configManager.getLoadReportIntervalInSeconds();
    JsonObject leaderHttpDeploymentConfig = configManager.getLeaderHttpDeploymentConfig();
    String backendAssociationPath = leaderHttpDeploymentConfig.getString("backend.association.path", "/association");
    int loadMissTolerance = leaderHttpDeploymentConfig.getInteger("load.miss.tolerance", 2);
    return new ZookeeperBasedBackendAssociationStore(vertx, curatorClient, backendAssociationPath,
        loadReportIntervalInSeconds, loadMissTolerance, configManager.getBackendHttpPort(), new ProcessGroupCountBasedBackendComparator());
  }

  private Runnable createLeaderElectedTask(Vertx vertx, VerticleDeployer leaderHttpVerticleDeployer, List<String> backendDeployments) {
    LeaderElectedTask.Builder builder = LeaderElectedTask.newBuilder();
    builder.disableBackend(backendDeployments);
    return builder.build(vertx, leaderHttpVerticleDeployer);
  }
}

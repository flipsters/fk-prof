package fk.prof.userapi;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Preconditions;
import fk.prof.storage.AsyncStorage;
import fk.prof.storage.api.ProcessGroupAPI;
import fk.prof.storage.impl.S3AsyncStorage;
import fk.prof.storage.S3ClientFactory;
import fk.prof.userapi.api.ProfileAPI;
import fk.prof.userapi.api.impl.AsyncStorageBasedProcessGroupAPI;
import fk.prof.userapi.api.impl.AsyncStorageBasedProfileAPI;
import fk.prof.userapi.deployer.VerticleDeployer;
import fk.prof.userapi.deployer.impl.UserapiHttpVerticleDeployer;
import fk.prof.userapi.http.UserapiApiPathConstants;
import fk.prof.userapi.model.json.ProtoSerializers;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.nio.ByteBuffer;
import java.util.concurrent.*;

public class UserapiManager {
  private static Logger logger = LoggerFactory.getLogger(UserapiManager.class);

  private final Vertx vertx;
  private final UserapiConfigManager userapiConfigManager;
  private AsyncStorage storage;
  private GenericObjectPool<ByteBuffer> bufferPool;
  private MetricRegistry metricRegistry;

  public UserapiManager(String configFilePath) throws Exception {
    this(new UserapiConfigManager(configFilePath));
  }

  public UserapiManager(UserapiConfigManager userapiConfigManager) throws Exception {
    UserapiConfigManager.setDefaultSystemProperties();
    this.userapiConfigManager = Preconditions.checkNotNull(userapiConfigManager);

    VertxOptions vertxOptions = new VertxOptions(userapiConfigManager.getVertxConfig());
    vertxOptions.setMetricsOptions(buildMetricsOptions());
    this.vertx = Vertx.vertx(vertxOptions);
    this.metricRegistry = SharedMetricRegistries.getOrCreate(UserapiConfigManager.METRIC_REGISTRY);

    initStorage();
  }

  public Future<Void> close() {
    Future future = Future.future();
    vertx.close(closeResult -> {
      if (closeResult.succeeded()) {
        logger.info("Shutdown successful for vertx instance");
        future.complete();
      } else {
        logger.error("Error shutting down vertx instance");
        future.fail(closeResult.cause());
      }
    });

    return future;
  }

  public Future<Void> launch() {
    Future<Void> result = Future.future();
    // register serializers
    registerSerializers(Json.mapper);
    registerSerializers(Json.prettyMapper);

    ProfileAPI profileAPI = new AsyncStorageBasedProfileAPI(vertx, this.storage, userapiConfigManager.getProfileRetentionDuration());
    ProcessGroupAPI processGroupAPI = new AsyncStorageBasedProcessGroupAPI(this.storage);
    VerticleDeployer userapiHttpVerticleDeployer = new UserapiHttpVerticleDeployer(vertx, userapiConfigManager, profileAPI, processGroupAPI);

    userapiHttpVerticleDeployer.deploy().setHandler(verticleDeployCompositeResult -> {
      if (verticleDeployCompositeResult.succeeded()) {
        result.complete();
      } else {
        result.fail(verticleDeployCompositeResult.cause());
      }
    });

    return result;
  }

  private void registerSerializers(ObjectMapper mapper) {
    // protobuf
    ProtoSerializers.registerSerializers(mapper);

    // java 8, datetime
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
  }

  private void initStorage() {
    JsonObject threadPoolConfig = userapiConfigManager.getStorageThreadPoolConfig();
    Meter threadPoolRejectionsMtr = metricRegistry.meter(MetricRegistry.name(AsyncStorage.class, "threadpool.rejections"));

    // thread pool with bounded queue for s3 io.
    BlockingQueue ioTaskQueue = new LinkedBlockingQueue(threadPoolConfig.getInteger("queue.maxsize"));
    ExecutorService storageExecSvc = new InstrumentedExecutorService(
        new ThreadPoolExecutor(threadPoolConfig.getInteger("coresize"), threadPoolConfig.getInteger("maxsize"), threadPoolConfig.getInteger("idletime.secs"), TimeUnit.SECONDS, ioTaskQueue,
            new AbortPolicy("storageExectorSvc", threadPoolRejectionsMtr)),
        metricRegistry, "executors.fixed_thread_pool.storage");

    JsonObject s3Config = userapiConfigManager.getS3Config();
    this.storage = new S3AsyncStorage(S3ClientFactory.create(s3Config.getString("endpoint"), s3Config.getString("access.key"), s3Config.getString("secret.key")),
            storageExecSvc, s3Config.getLong("list.objects.timeout.ms"));
  }


  private MetricsOptions buildMetricsOptions() {
    return new DropwizardMetricsOptions()
        .setEnabled(true)
        .setJmxEnabled(true)
        .setRegistryName(UserapiConfigManager.METRIC_REGISTRY)
        .addMonitoredHttpServerUri(new Match().setValue(UserapiApiPathConstants.APPS + ".*").setAlias(UserapiApiPathConstants.APPS).setType(MatchType.REGEX))
        .addMonitoredHttpServerUri(new Match().setValue(UserapiApiPathConstants.CLUSTER_GIVEN_APPID + ".*").setAlias(UserapiApiPathConstants.CLUSTER_GIVEN_APPID).setType(MatchType.REGEX))
        .addMonitoredHttpServerUri(new Match().setValue(UserapiApiPathConstants.PROC_GIVEN_APPID_CLUSTERID + ".*").setAlias(UserapiApiPathConstants.PROC_GIVEN_APPID_CLUSTERID).setType(MatchType.REGEX))
        .addMonitoredHttpServerUri(new Match().setValue(UserapiApiPathConstants.PROFILES_GIVEN_APPID_CLUSTERID_PROCID).setAlias(UserapiApiPathConstants.PROFILE_GIVEN_APPID_CLUSTERID_PROCID_WORKTYPE_TRACENAME).setType(MatchType.REGEX))
        .addMonitoredHttpServerUri(new Match().setValue(UserapiApiPathConstants.PROFILE_GIVEN_APPID_CLUSTERID_PROCID_WORKTYPE_TRACENAME).setAlias(UserapiApiPathConstants.PROFILE_GIVEN_APPID_CLUSTERID_PROCID_WORKTYPE_TRACENAME).setType(MatchType.REGEX));
  }

  public static class AbortPolicy implements RejectedExecutionHandler {

    private String forExecutorSvc;
    private Meter meter;

    public AbortPolicy(String forExecutorSvc, Meter meter) {
      this.forExecutorSvc = forExecutorSvc;
      this.meter = meter;
    }

    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
      meter.mark();
      throw new RejectedExecutionException("Task rejected from " + forExecutorSvc);
    }
  }
}

package fk.prof.backend.http;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.http.handler.RecordedProfileRequestHandler;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.request.CompositeByteBufInputStream;
import fk.prof.backend.request.profile.RecordedProfileProcessor;
import fk.prof.backend.request.profile.impl.SharedMapBasedSingleProcessingOfProfileGate;
import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.util.ProtoUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import policy.PolicyDetails;
import recording.Recorder;

import java.io.IOException;

public class BackendHttpVerticle extends AbstractVerticle {
  private static Logger logger = LoggerFactory.getLogger(BackendHttpVerticle.class);

  private final ConfigManager configManager;
  private final LeaderReadContext leaderReadContext;
  private final IProfileWorkService profileWorkService;
  private final int leaderPort;


  private LocalMap<Long, Boolean> workIdsInPipeline;
  private ProfHttpClient httpClient;

  public BackendHttpVerticle(ConfigManager configManager,
                             LeaderReadContext leaderReadContext,
                             IProfileWorkService profileWorkService) {
    this.configManager = configManager;
    this.leaderPort = configManager.getLeaderHttpPort();

    this.leaderReadContext = leaderReadContext;
    this.profileWorkService = profileWorkService;
  }

  @Override
  public void start(Future<Void> fut) {
    JsonObject httpClientConfig = configManager.getHttpClientConfig();
    httpClient = ProfHttpClient.newBuilder()
        .keepAlive(httpClientConfig.getBoolean("keepalive", true))
        .useCompression(httpClientConfig.getBoolean("compression", true))
        .setConnectTimeoutInMs(httpClientConfig.getInteger("connect.timeout.ms", 5000))
        .setIdleTimeoutInSeconds(httpClientConfig.getInteger("idle.timeout.secs", 120))
        .setMaxAttempts(httpClientConfig.getInteger("max.attempts", 3))
        .build(vertx);

    Router router = setupRouting();
    workIdsInPipeline = vertx.sharedData().getLocalMap("WORK_ID_PIPELINE");
    vertx.createHttpServer(HttpHelper.getHttpServerOptions(configManager.getBackendHttpServerConfig()))
        .requestHandler(router::accept)
        .listen(configManager.getBackendHttpPort(), http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());

    router.post(ApiPathConstants.AGGREGATOR_POST_PROFILE).handler(this::handlePostProfile);

    router.put(ApiPathConstants.BACKEND_PUT_ASSOCIATION)
        .handler(BodyHandler.create().setBodyLimit(1024 * 10));
    router.put(ApiPathConstants.BACKEND_PUT_ASSOCIATION)
        .handler(this::handlePutAssociation);

    router.get(ApiPathConstants.BACKEND_GET_POLICIES_GIVEN_APPID).handler(this::handleGetPoliciesGivenAppId);
    router.get(ApiPathConstants.BACKEND_GET_POLICIES_GIVEN_APPID_CLUSTERID).handler(this::handleGetPoliciesGivenAppIdClusterId);
    router.get(ApiPathConstants.BACKEND_GET_POLICIES_GIVEN_APPID_CLUSTERID_PROCESS).handler(this::handleGetPoliciesGivenAppIdClusterIdProcess);

    router.put(ApiPathConstants.BACKEND_PUT_POLICY_GIVEN_APPID_CLUSTERID_PROCESS).handler(BodyHandler.create().setBodyLimit(1024 * 10));
    router.put(ApiPathConstants.BACKEND_PUT_POLICY_GIVEN_APPID_CLUSTERID_PROCESS).handler(this::handlePutPolicyGivenAppIdClusterIdProcess);

    router.delete(ApiPathConstants.BACKEND_DELETE_POLICY_GIVEN_APPID_CLUSTERID_PROCESS).handler(BodyHandler.create().setBodyLimit(1024 * 10));
    router.delete(ApiPathConstants.BACKEND_DELETE_POLICY_GIVEN_APPID_CLUSTERID_PROCESS).handler(this::handleDeletePolicyGivenAppIdClusterIdProcess);

    return router;
  }

  private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
    if (http.succeeded()) {
      fut.complete();
    } else {
      fut.fail(http.cause());
    }
  }

  private void handlePostProfile(RoutingContext context) {
    CompositeByteBufInputStream inputStream = new CompositeByteBufInputStream();
    RecordedProfileProcessor profileProcessor = new RecordedProfileProcessor(
        profileWorkService,
        new SharedMapBasedSingleProcessingOfProfileGate(workIdsInPipeline),
        config().getJsonObject("parser").getInteger("recordingheader.max.bytes", 1024),
        config().getJsonObject("parser").getInteger("parser.wse.max.bytes", 1024 * 1024));

    RecordedProfileRequestHandler requestHandler = new RecordedProfileRequestHandler(context, inputStream, profileProcessor);
    context.request()
        .handler(requestHandler)
        .endHandler(v -> {
          try {
            if (!context.response().ended()) {
              //Can safely attempt to close the profile processor here because endHandler is called once the entire body has been read
              //and example in vertx docs also indicates that this handler will execute once all chunk handlers have completed execution
              //http://vertx.io/docs/vertx-core/java/#_handling_requests
              inputStream.close();
              profileProcessor.close();
              context.response().end();
            }
          } catch (Exception ex) {
            HttpFailure httpFailure = HttpFailure.failure(ex);
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
  }

  private void handlePutAssociation(RoutingContext context) {
    String leaderIPAddress = verifyLeaderAvailabilityOrFail(context.response());
    if (leaderIPAddress != null) {
      try {
        //Deserialize to proto message to catch payload related errors early
        Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.parseFrom(context.getBody().getBytes());
        //TODO: Check if backend already knows about this process group and send self as the association, rather than proxying to leader
        makeRequestGetAssociation(leaderIPAddress, processGroup).setHandler(ar -> {
          if (ar.succeeded()) {
            context.response().setStatusCode(ar.result().getStatusCode());
            context.response().end(ar.result().getResponse());
          } else {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
      } catch (Exception ex) {
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

  private void handleGetPoliciesGivenAppId(RoutingContext context) {
    String leaderIPAddress = verifyLeaderAvailabilityOrFail(context.response());
    if (leaderIPAddress != null) {
      final String appId = context.request().getParam("appId");
      try {
        makeRequestGetPolicyGivenAppId(leaderIPAddress, appId).setHandler(ar -> {
          if (ar.succeeded()) {
            context.response().setStatusCode(ar.result().getStatusCode());
            context.response().end(ar.result().getResponse());
          } else {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
      } catch (Exception ex) {
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

  private void handleGetPoliciesGivenAppIdClusterId(RoutingContext context) {
    String leaderIPAddress = verifyLeaderAvailabilityOrFail(context.response());
    if (leaderIPAddress != null) {
      final String appId = context.request().getParam("appId");
      final String clusterId = context.request().getParam("clusterId");
      try {
        makeRequestGetPolicyGivenAppIdClusterId(leaderIPAddress, appId, clusterId).setHandler(ar -> {
          if (ar.succeeded()) {
            context.response().setStatusCode(ar.result().getStatusCode());
            context.response().end(ar.result().getResponse());
          } else {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
      } catch (Exception ex) {
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

  private void handleGetPoliciesGivenAppIdClusterIdProcess(RoutingContext context) {
    String leaderIPAddress = verifyLeaderAvailabilityOrFail(context.response());
    if (leaderIPAddress != null) {
      final String appId = context.request().getParam("appId");
      final String clusterId = context.request().getParam("clusterId");
      final String process = context.request().getParam("process");
      try {
        makeRequestGetPolicyGivenAppIdClusterIdProcess(leaderIPAddress, appId, clusterId, process).setHandler(ar -> {
          if (ar.succeeded()) {
            context.response().setStatusCode(ar.result().getStatusCode());
            context.response().end(ar.result().getResponse());
          } else {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
      } catch (Exception ex) {
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

  private void handlePutPolicyGivenAppIdClusterIdProcess(RoutingContext context) {
    String leaderIPAddress = verifyLeaderAvailabilityOrFail(context.response());
    if (leaderIPAddress != null) {
      try {
        final String appId = context.request().getParam("appId");
        final String clusterId = context.request().getParam("clusterId");
        final String process = context.request().getParam("process");

        //Deserialize to proto message to catch payload related errors early
        Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(appId).setCluster(clusterId).setProcName(process).build();
        PolicyDetails policyDetails = PolicyDetails.parseFrom(context.getBody().getBytes());
        makeRequestPutPolicy(leaderIPAddress, processGroup, policyDetails).setHandler(ar -> {
          if (ar.succeeded()) {
            context.response().setStatusCode(ar.result().getStatusCode());
            context.response().end(ar.result().getResponse());
          } else {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
      } catch (Exception ex) {
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

  private void handleDeletePolicyGivenAppIdClusterIdProcess(RoutingContext context) {
    String leaderIPAddress = verifyLeaderAvailabilityOrFail(context.response());
    if (leaderIPAddress != null) {
      try {
        final String appId = context.request().getParam("appId");
        final String clusterId = context.request().getParam("clusterId");
        final String process = context.request().getParam("process");

        //Deserialize to proto message to catch payload related errors early
        Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(appId).setCluster(clusterId).setProcName(process).build();
        JsonObject policyAdminJson = context.getBodyAsJson();
        makeRequestDeletePolicy(leaderIPAddress, processGroup, policyAdminJson).setHandler(ar -> {
          if (ar.succeeded()) {
            context.response().setStatusCode(ar.result().getStatusCode());
            context.response().end(ar.result().getResponse());
          } else {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
      } catch (Exception ex) {
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }


  private String verifyLeaderAvailabilityOrFail(HttpServerResponse response) {
    if (leaderReadContext.isLeader()) {
      response.setStatusCode(400).end("Leader refuses to respond to this request");
      return null;
    } else {
      String leaderIPAddress = leaderReadContext.getLeaderIPAddress();
      if (leaderIPAddress == null) {
        response.setStatusCode(503).putHeader("Retry-After", "10").end("Leader not elected yet");
        return null;
      } else {
        return leaderIPAddress;
      }
    }
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestGetAssociation(String leaderIPAddress, Recorder.ProcessGroup payload)
      throws IOException {
    Buffer payloadAsBuffer = ProtoUtil.buildBufferFromProto(payload);
    return httpClient.requestAsyncWithRetry(
        HttpMethod.POST,
        leaderIPAddress, leaderPort, ApiPathConstants.LEADER_PUT_ASSOCIATION,
        payloadAsBuffer);
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestGetPolicyGivenAppId(String leaderIPAddress, String appId)
      throws IOException {

    return httpClient.requestAsyncWithRetry(
        HttpMethod.GET,
        leaderIPAddress, leaderPort,
        ApiPathConstants.LEADER_POLICIES + ApiPathConstants.DELIMITER + appId, null);
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestGetPolicyGivenAppIdClusterId(String leaderIPAddress, String appId, String clusterId)
      throws IOException {
    return httpClient.requestAsyncWithRetry(
        HttpMethod.GET,
        leaderIPAddress, leaderPort,
        ApiPathConstants.LEADER_POLICIES + ApiPathConstants.DELIMITER + appId + ApiPathConstants.DELIMITER + clusterId, null);
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestGetPolicyGivenAppIdClusterIdProcess(String leaderIPAddress, String appId, String clusterId, String process)
      throws IOException {
    return httpClient.requestAsyncWithRetry(
        HttpMethod.GET,
        leaderIPAddress, leaderPort,
        ApiPathConstants.LEADER_POLICIES + ApiPathConstants.DELIMITER + appId + ApiPathConstants.DELIMITER + clusterId + ApiPathConstants.DELIMITER + process, null);
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestPutPolicy(String leaderIPAddress, Recorder.ProcessGroup processGroup, PolicyDetails policyDetails)
      throws IOException {
    Buffer payloadAsBuffer = ProtoUtil.buildBufferFromProto(policyDetails);
    return httpClient.requestAsyncWithRetry(
        HttpMethod.PUT,
        leaderIPAddress, leaderPort, ApiPathConstants.LEADER_POLICIES + ApiPathConstants.DELIMITER + processGroup.getAppId() + ApiPathConstants.DELIMITER + processGroup.getCluster() + ApiPathConstants.DELIMITER + processGroup.getProcName(),
        payloadAsBuffer);
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestDeletePolicy(String leaderIPAddress, Recorder.ProcessGroup processGroup, JsonObject policyAdminJson)
      throws IOException {
    Buffer payloadAsBuffer = Buffer.buffer(policyAdminJson.toString());
    return httpClient.requestAsyncWithRetry(
        HttpMethod.DELETE,
        leaderIPAddress, leaderPort, ApiPathConstants.LEADER_POLICIES + ApiPathConstants.DELIMITER + processGroup.getAppId() + ApiPathConstants.DELIMITER + processGroup.getCluster() + ApiPathConstants.DELIMITER + processGroup.getProcName(),
        payloadAsBuffer);
  }
}

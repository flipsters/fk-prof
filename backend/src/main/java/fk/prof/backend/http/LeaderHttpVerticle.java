package fk.prof.backend.http;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.Configuration;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.policy.PolicyStoreAPI;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.proto.PolicyDTO;
import fk.prof.backend.util.ProtoUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import fk.prof.metrics.BackendTag;
import fk.prof.metrics.MetricName;
import fk.prof.metrics.ProcessGroupTag;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import recording.Recorder;

import java.io.IOException;

public class LeaderHttpVerticle extends AbstractVerticle {
  private final Configuration config;
  private final BackendAssociationStore backendAssociationStore;
  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(ConfigManager.METRIC_REGISTRY);
  private final PolicyStoreAPI policyStoreAPI;

  public LeaderHttpVerticle(Configuration config,
                            BackendAssociationStore backendAssociationStore,
                            PolicyStoreAPI policyStoreAPI) {
    this.config = config;
    this.backendAssociationStore = backendAssociationStore;
    this.policyStoreAPI = policyStoreAPI;
  }

  @Override
  public void start(Future<Void> fut) {
    Router router = setupRouting();
    vertx.createHttpServer(config.getLeaderHttpServerOpts())
        .requestHandler(router::accept)
        .listen(config.getLeaderHttpServerOpts().getPort(),
            http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());

    HttpHelper.attachHandlersToRoute(router, HttpMethod.POST, ApiPathConstants.LEADER_POST_LOAD,
        BodyHandler.create().setBodyLimit(64), this::handlePostLoad);

    HttpHelper.attachHandlersToRoute(router, HttpMethod.POST, ApiPathConstants.LEADER_POST_ASSOCIATION,
        BodyHandler.create().setBodyLimit(1024 * 10), this::handlePostAssociation);

    HttpHelper.attachHandlersToRoute(router, HttpMethod.GET, ApiPathConstants.LEADER_GET_ASSOCIATIONS,
        this::handleGetAssociations);

    String apiPathForGetWork = ApiPathConstants.LEADER_GET_WORK + "/:appId/:clusterId/:procName";
    HttpHelper.attachHandlersToRoute(router, HttpMethod.GET, apiPathForGetWork,
        BodyHandler.create().setBodyLimit(1024 * 100), this::handleGetWork);

    HttpHelper.attachHandlersToRoute(router, HttpMethod.GET,
        ApiPathConstants.LEADER + ApiPathConstants.POLICY_GIVEN_APPID_CLUSTERID_PROCNAME, this::handleGetPolicy);
    HttpHelper.attachHandlersToRoute(router, HttpMethod.PUT,
        ApiPathConstants.LEADER + ApiPathConstants.POLICY_GIVEN_APPID_CLUSTERID_PROCNAME,
        BodyHandler.create().setBodyLimit(1024), this::handleUpdatePolicy);
    HttpHelper.attachHandlersToRoute(router, HttpMethod.POST,
        ApiPathConstants.LEADER + ApiPathConstants.POLICY_GIVEN_APPID_CLUSTERID_PROCNAME,
        BodyHandler.create().setBodyLimit(1024), this::handleCreatePolicy);

    HttpHelper.attachHandlersToRoute(router, HttpMethod.GET, ApiPathConstants.LEADER + ApiPathConstants.APPIDS, this::handleGetAppIds);
    HttpHelper.attachHandlersToRoute(router, HttpMethod.GET, ApiPathConstants.LEADER + ApiPathConstants.CLUSTERIDS_GIVEN_APPID, this::handleGetClusterIds);
    HttpHelper.attachHandlersToRoute(router, HttpMethod.GET, ApiPathConstants.LEADER + ApiPathConstants.PROCNAMES_GIVEN_APPID_CLUSTERID, this::handleGetProcNames);

    return router;
  }

  private void handleGetAppIds(RoutingContext context) {
    try {
      final String prefix = context.request().getParam("prefix");
      context.response().end(Json.encode(policyStoreAPI.getAppIds(prefix)));
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handleGetClusterIds(RoutingContext context) {
    try {
      final String appId = context.request().getParam("appId");
      String prefix = context.request().getParam("prefix");
      if (prefix == null) {
        prefix = "";
      }
      context.response().end(Json.encode(policyStoreAPI.getClusterIds(appId, prefix)));
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handleGetProcNames(RoutingContext context) {
    try {
      final String appId = context.request().getParam("appId");
      final String clusterId = context.request().getParam("clusterId");
      String prefix = context.request().getParam("prefix");
      if (prefix == null) {
        prefix = "";
      }
      context.response().end(Json.encode(policyStoreAPI.getProcNames(appId, clusterId, prefix)));
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
    if (http.succeeded()) {
      fut.complete();
    } else {
      fut.fail(http.cause());
    }
  }

  private void handlePostLoad(RoutingContext context) {
    try {
      BackendDTO.LoadReportRequest payload = ProtoUtil.buildProtoFromBuffer(BackendDTO.LoadReportRequest.parser(), context.getBody());
      String backendStr = new BackendTag(payload.getIp(), payload.getPort()).toString();
      Meter mtrFailure = metricRegistry.meter(MetricRegistry.name(MetricName.Leader_LoadReport_Failure.get(), backendStr));
      Meter mtrSuccess = metricRegistry.meter(MetricRegistry.name(MetricName.Leader_LoadReport_Success.get(), backendStr));

      backendAssociationStore.reportBackendLoad(payload).setHandler(ar -> {
        mtrSuccess.mark();
        if(ar.succeeded()) {
          try {
            Buffer responseBuffer = ProtoUtil.buildBufferFromProto(ar.result());
            context.response().end(responseBuffer);
          } catch (IOException ex) {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        } else {
          mtrFailure.mark();
          HttpFailure httpFailure = HttpFailure.failure(ar.cause());
          HttpHelper.handleFailure(context, httpFailure);
        }
      });
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handlePostAssociation(RoutingContext context) {
    try {
      Recorder.RecorderInfo recorderInfo = ProtoUtil.buildProtoFromBuffer(Recorder.RecorderInfo.parser(), context.getBody());
      Recorder.ProcessGroup processGroup = RecorderProtoUtil.mapRecorderInfoToProcessGroup(recorderInfo);

      String processGroupStr = new ProcessGroupTag(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName()).toString();
      Meter mtrFailure = metricRegistry.meter(MetricRegistry.name(MetricName.Leader_Assoc_Failure.get(), processGroupStr));
      Meter mtrSuccess = metricRegistry.meter(MetricRegistry.name(MetricName.Leader_Assoc_Success.get(), processGroupStr));

      backendAssociationStore.associateAndGetBackend(processGroup).setHandler(ar -> {
        //TODO: Evaluate if this lambda can be extracted out as a static variable/function if this is repetitive across the codebase
        if(ar.succeeded()) {
          mtrSuccess.mark();
          try {
            context.response().end(ProtoUtil.buildBufferFromProto(ar.result()));
          } catch (Exception ex) {
            HttpFailure httpFailure = HttpFailure.failure(ex);
            HttpHelper.handleFailure(context, httpFailure);
          }
        } else {
          mtrFailure.mark();
          HttpFailure httpFailure = HttpFailure.failure(ar.cause());
          HttpHelper.handleFailure(context, httpFailure);
        }
      });
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handleGetWork(RoutingContext context) {
    try {
      String appId = context.request().getParam("appId");
      String clusterId = context.request().getParam("clusterId");
      String procName = context.request().getParam("procName");
      Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(appId).setCluster(clusterId).setProcName(procName).build();

      String processGroupStr = new ProcessGroupTag(appId, clusterId, procName).toString();
      Meter mtrAssocMiss = metricRegistry.meter(MetricRegistry.name(MetricName.Leader_Work_Assoc_Miss.get(), processGroupStr));
      Meter mtrPolicyMiss = metricRegistry.meter(MetricRegistry.name(MetricName.Leader_Work_Policy_Miss.get(), processGroupStr));

      String backendIP = context.request().getParam("ip");
      int backendPort = Integer.valueOf(context.request().getParam("port"));
      Recorder.AssignedBackend callingBackend = Recorder.AssignedBackend.newBuilder().setHost(backendIP).setPort(backendPort).build();

      if(!callingBackend.equals(backendAssociationStore.getAssociatedBackend(processGroup))) {
        mtrAssocMiss.mark();
        context.response().setStatusCode(400);
        context.response().end("Calling backend=" + RecorderProtoUtil.assignedBackendCompactRepr(callingBackend) + " not assigned to process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
      } else {
        PolicyDTO.VersionedPolicyDetails recordingPolicy = this.policyStoreAPI.getVersionedPolicy(processGroup);
        if (recordingPolicy == null) {
          mtrPolicyMiss.mark();
          context.response().setStatusCode(400);
          context.response().end("Policy not found for process_group" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
        } else {
          context.response().end(ProtoUtil.buildBufferFromProto(recordingPolicy));
        }
      }
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handleGetPolicy(RoutingContext context) {
    try {
      Recorder.ProcessGroup pG = parseProcessGroup(context);
      PolicyDTO.VersionedPolicyDetails versionedPolicyDetails = policyStoreAPI.getVersionedPolicy(pG);
      if (versionedPolicyDetails == null) {
        context.response().setStatusCode(400).end("Policy not found for ProcessGroup " + RecorderProtoUtil.processGroupCompactRepr(pG));
      } else {
        context.response().end(versionedPolicyDetails.toString());
      }
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handleCreatePolicy(RoutingContext context) {
    try {
      Recorder.ProcessGroup pG = parseProcessGroup(context);
      PolicyDTO.VersionedPolicyDetails versionedPolicyDetails = parseVersionedPolicyFromPayload(context);
      policyStoreAPI.createVersionedPolicy(pG, versionedPolicyDetails).setHandler(result -> {
        if (result.succeeded()) {
          context.response().setStatusCode(201).end(result.result().toString());
        } else {
          HttpFailure httpFailure = HttpFailure.failure(result.cause());
          HttpHelper.handleFailure(context, httpFailure);
        }
      });
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handleUpdatePolicy(RoutingContext context) {
    try {
      Recorder.ProcessGroup pG = parseProcessGroup(context);
      PolicyDTO.VersionedPolicyDetails versionedPolicyDetails = parseVersionedPolicyFromPayload(context);
      policyStoreAPI.updateVersionedPolicy(pG, versionedPolicyDetails).setHandler(result -> {
        if (result.succeeded()) {
          context.response().setStatusCode(204).end(result.result().toString());
        } else {
          HttpFailure httpFailure = HttpFailure.failure(result.cause());
          HttpHelper.handleFailure(context, httpFailure);
        }
      });
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private PolicyDTO.VersionedPolicyDetails parseVersionedPolicyFromPayload(RoutingContext context) throws Exception {
    byte[] payload = context.getBody().getBytes();
    return PolicyDTO.VersionedPolicyDetails.parseFrom(payload);
  }

  private Recorder.ProcessGroup parseProcessGroup(RoutingContext context) throws Exception {
    String appId = context.request().getParam("appId");
    String clusterId = context.request().getParam("clusterId");
    String procName = context.request().getParam("procName");

    return Recorder.ProcessGroup.newBuilder().setAppId(appId).setCluster(clusterId).setProcName(procName).build();
  }

  private void handleGetAssociations(RoutingContext context) {
    try {
      context.response().end(ProtoUtil.buildBufferFromProto(backendAssociationStore.getAssociations()));
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }
}

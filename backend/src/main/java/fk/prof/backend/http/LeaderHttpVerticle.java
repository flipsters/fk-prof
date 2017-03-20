package fk.prof.backend.http;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.model.policy.impl.PolicyAPIResponse;
import fk.prof.backend.model.policy.json.PolicyProtobufSerializers;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import policy.PolicyDetails;
import recording.Recorder;

import java.io.IOException;

public class LeaderHttpVerticle extends AbstractVerticle {
  private final ConfigManager configManager;
  private final BackendAssociationStore backendAssociationStore;
  private final PolicyStore policyStore;

  public LeaderHttpVerticle(ConfigManager configManager,
                            BackendAssociationStore backendAssociationStore,
                            PolicyStore policyStore) {
    this.configManager = configManager;
    this.backendAssociationStore = backendAssociationStore;
    this.policyStore = policyStore;
  }

  @Override
  public void start(Future<Void> fut) {
    Router router = setupRouting();
    PolicyProtobufSerializers.registerSerializer(Json.mapper);
    PolicyProtobufSerializers.registerSerializer(Json.prettyMapper);
    vertx.createHttpServer(HttpHelper.getHttpServerOptions(configManager.getLeaderHttpServerConfig()))
        .requestHandler(router::accept)
        .listen(configManager.getLeaderHttpPort(),
            http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());

    router.post(ApiPathConstants.LEADER_POST_LOAD)
        .handler(BodyHandler.create().setBodyLimit(64));
    router.post(ApiPathConstants.LEADER_POST_LOAD)
        .handler(this::handlePostLoad);

    router.post(ApiPathConstants.LEADER_PUT_ASSOCIATION)
        .handler(BodyHandler.create().setBodyLimit(1024 * 10));
    router.post(ApiPathConstants.LEADER_PUT_ASSOCIATION)
        .handler(this::handlePutAssociation);

    router.get(ApiPathConstants.LEADER_GET_POLICIES_GIVEN_APPID).handler(this::handleGetPolicyGivenAppId);

    router.get(ApiPathConstants.LEADER_GET_POLICIES_GIVEN_APPID_CLUSTERID).handler(this::handleGetPolicyGivenAppIdClusterId);

    router.get(ApiPathConstants.LEADER_GET_POLICIES_GIVEN_APPID_CLUSTERID_PROCESS).handler(this::handleGetPolicyGivenAppIdClusterIdProcess);

    router.put(ApiPathConstants.LEADER_PUT_POLICY).handler(BodyHandler.create().setBodyLimit(1024));
    router.put(ApiPathConstants.LEADER_PUT_POLICY).handler(this::handlePutPolicy);

    router.delete(ApiPathConstants.LEADER_PUT_POLICY).handler(BodyHandler.create().setBodyLimit(1024));
    router.delete(ApiPathConstants.LEADER_DELETE_POLICY).handler(this::handleDeletePolicy);

    return router;
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
      BackendDTO.LoadReportRequest payload = BackendDTO.LoadReportRequest.parseFrom(context.getBody().getBytes());
      backendAssociationStore.reportBackendLoad(payload).setHandler(ar -> {
        if (ar.succeeded()) {
          try {
            Buffer responseBuffer = ProtoUtil.buildBufferFromProto(ar.result());
            context.response().end(responseBuffer);
          } catch (IOException ex) {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
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

  private void handlePutAssociation(RoutingContext context) {
    try {
      Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.parseFrom(context.getBody().getBytes());
      backendAssociationStore.getAssociatedBackend(processGroup).setHandler(ar -> {
        //TODO: Evaluate if this lambda can be extracted out as a static variable/function if this is repetitive across the codebase
        if (ar.succeeded()) {
          try {
            context.response().end(ProtoUtil.buildBufferFromProto(ar.result()));
          } catch (Exception ex) {
            HttpFailure httpFailure = HttpFailure.failure(ex);
            HttpHelper.handleFailure(context, httpFailure);
          }
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

  private void handleGetPolicyGivenAppId(RoutingContext context) {
    final String appId = context.request().getParam("appId");
    String response = Json.encode(PolicyAPIResponse.getNewInstance(policyStore.getAssociatedPolicies(appId)));
    context.response().putHeader("content-type", "application/json").end(response);
  }

  private void handleGetPolicyGivenAppIdClusterId(RoutingContext context) {
    final String appId = context.request().getParam("appId");
    final String clusterId = context.request().getParam("clusterId");
    String response = Json.encode(PolicyAPIResponse.getNewInstance(policyStore.getAssociatedPolicies(appId, clusterId)));
    context.response().putHeader("content-type", "application/json").end(response);
  }

  private void handleGetPolicyGivenAppIdClusterIdProcess(RoutingContext context) {
    final String appId = context.request().getParam("appId");
    final String clusterId = context.request().getParam("clusterId");
    final String process = context.request().getParam("process");
    String response = Json.encode(PolicyAPIResponse.getNewInstance(policyStore.getAssociatedPolicies(appId, clusterId, process)));
    context.response().putHeader("content-type", "application/json").end(response);
  }

  private void handlePutPolicy(RoutingContext context) {
    final String appId = context.request().getParam("appId");
    final String clusterId = context.request().getParam("clusterId");
    final String process = context.request().getParam("process");
    try {
      Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(appId).setCluster(clusterId).setProcName(process).build();
      PolicyDetails policyDetails = PolicyDetails.parseFrom(context.getBody().getBytes());
      policyStore.setPolicy(processGroup, policyDetails).whenComplete((aVoid, throwable) -> {
        if (throwable == null) {
          context.response().setStatusCode(200).end();
        } else {
          HttpFailure httpFailure = HttpFailure.failure(throwable);
          HttpHelper.handleFailure(context, httpFailure);
        }
      });
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handleDeletePolicy(RoutingContext context) {
    final String appId = context.request().getParam("appId");
    final String clusterId = context.request().getParam("clusterId");
    final String process = context.request().getParam("process");
    try {
      Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(appId).setCluster(clusterId).setProcName(process).build();
      String admin = context.getBodyAsJson().getString("administrator");
      policyStore.removePolicy(processGroup, admin).whenComplete((aVoid, throwable) -> {
        if (throwable == null) {
          context.response().setStatusCode(200).end();
        } else {
          HttpFailure httpFailure = HttpFailure.failure(throwable);
          HttpHelper.handleFailure(context, httpFailure);
        }
      });
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }


}

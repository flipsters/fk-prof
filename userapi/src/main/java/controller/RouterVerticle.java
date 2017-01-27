package controller;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import model.D42Model;
import model.IDataModel;
import model.Profile;

import java.util.Set;

/**
 * Routes requests to their respective handlers
 * Created by rohit.patiyal on 18/01/17.
 */
public class RouterVerticle extends AbstractVerticle {

    private IDataModel d42Store = null;

    private Router configureRouter() {
        Router router = Router.router(vertx);
        router.route("/").handler(routingContext -> routingContext.response()
                .putHeader("context-type", "text/html")
                .end("<h1>Welcome to UserAPI for FKProfiler"));
        router.get("/apps").blockingHandler(this::getAppIds, false);
        router.get("/cluster/:appId").blockingHandler(this::getClusterIds, false);
        router.get("/proc/:appId/:clusterId").blockingHandler(this::getProcs, false);
        router.get("/profiles/:appId/:clusterId/:proc").blockingHandler(this::getProfiles, false);
        // router.get("/traces/:appId/:clusterId/:proc/:workType").blockingHandler(this.getTraces, false);

        return router;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Router router = configureRouter();

        Future<HttpServer> serverFuture = Future.future();
        Future<String> dbFuture = Future.future();

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", 8080), serverFuture.completer());


        vertx.executeBlocking(future -> {
            d42Store = new D42Model();
            future.complete();
        }, dbFuture.completer());

        CompositeFuture.all(serverFuture, dbFuture).setHandler(compositeFutureAsyncResult -> {
            if (compositeFutureAsyncResult.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(compositeFutureAsyncResult.cause());
            }
        });
    }

    private void getAppIds(RoutingContext routingContext) {
        String prefix = routingContext.request().getParam("prefix");
        if (prefix == null) {
            prefix = "";
        }
        try {
            Set<String> appIds = d42Store.getAppIdsWithPrefix(prefix);
            routingContext.response().
                    putHeader("content-type", "application/json; charset=utf-8").
                    end(Json.encodePrettily(appIds));
        } catch (Exception e) {
            routingContext.response().setStatusCode(HttpResponseStatus.GATEWAY_TIMEOUT.code()).end();
            e.printStackTrace();
        }
    }

    private void getClusterIds(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        String prefix = routingContext.request().getParam("prefix");

        if (appId == null) {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        } else {
            if (prefix == null) {
                prefix = "";
            }
            try {
                Set<String> clusterIds = d42Store.getClusterIdsWithPrefix(appId, prefix);
                routingContext.response().
                        putHeader("content-type", "application/json; charset=utf-8").
                        end(Json.encodePrettily(clusterIds));
            } catch (Exception e) {
                routingContext.response().setStatusCode(HttpResponseStatus.GATEWAY_TIMEOUT.code()).end();
            }
        }
    }

    private void getProcs(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        final String clusterId = routingContext.request().getParam("clusterId");
        String prefix = routingContext.request().getParam("prefix");

        if (appId == null || clusterId == null) {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        } else {
            if (prefix == null) {
                prefix = "";
            }
            try {
                Set<String> procIds = d42Store.getProcsWithPrefix(appId, clusterId, prefix);
                routingContext.response().
                        putHeader("content-type", "application/json; charset=utf-8").
                        end(Json.encodePrettily(procIds));
            } catch (Exception e) {
                routingContext.response().setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code()).end();
            }
        }
    }

    private void getProfiles(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        final String clusterId = routingContext.request().getParam("clusterId");
        final String proc = routingContext.request().getParam("proc");
        String start = routingContext.request().getParam("start");
        String duration = routingContext.request().getParam("duration");

        if (appId == null || clusterId == null || proc == null) {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        } else {
            if (start == null) {
                start = "";
            }
            if (duration == null) {
                duration = "";
            }
            try {
                Set<Profile> profiles = d42Store.getProfilesInTimeWindow(appId, clusterId, proc, start, duration);
                routingContext.response().
                        putHeader("content-type", "application/json; charset=utf-8").
                        end(Json.encodePrettily(profiles));
            } catch (Exception e) {
                routingContext.response().setStatusCode(HttpResponseStatus.GATEWAY_TIMEOUT.code()).end();
            }
        }
    }
}

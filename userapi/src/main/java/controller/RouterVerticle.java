package controller;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Routes requests to their respective handlers
 * Created by rohit.patiyal on 18/01/17.
 */
public class RouterVerticle extends AbstractVerticle {

    private Router configureRouter() {
        Router router = Router.router(vertx);
        router.route("/").handler(routingContext -> routingContext.response()
                .putHeader("context-type", "text/html")
                .end("<h1>Welcome to UserAPI for FKProfiler"));
        router.get("/apps").handler(this::getAppIds);
        router.get("/cluster/:appId").handler(this::getClusterIds);
        return router;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Router router = configureRouter();

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", 8080), result -> {
                    if (result.succeeded()) {
                        startFuture.complete();
                    } else {
                        startFuture.fail(result.cause());
                    }
                });
    }

    private void getAppIds(RoutingContext routingContext) {
        final String prefix = routingContext.request().getParam("prefix");
        if (prefix == null) {
            routingContext.response().end("<h1>Will return all AppIds");
        } else {
            //TODO Query S3 to fetch all AppIds with prefix pre
            routingContext.response().end("<h1>Will return AppIds with prefix = " + prefix);
        }
    }

    private void getClusterIds(RoutingContext routingContext) {
        final String prefix = routingContext.request().getParam("prefix");
        final String appId = routingContext.request().getParam("appId");
        if (appId == null) {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        } else {
            if (prefix == null) {
                routingContext.response().end("<h1>Will return all clusterIds for appId = " + appId);
            } else {
                //TODO Query S3 to fetch all AppIds with prefix pre
                routingContext.response().end("<h1>Will return ApiIds with appId = " + appId + "; pre = " + prefix);
            }
        }
    }

}

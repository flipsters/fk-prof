package fk.prof.userapi.verticles;

import com.google.common.base.MoreObjects;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.aggregation.proto.AggregatedProfileModel.FrameNode;
import fk.prof.aggregation.proto.AggregatedProfileModel.WorkType;
import fk.prof.storage.StreamTransformer;
import fk.prof.userapi.Configuration;
import fk.prof.userapi.Pair;
import fk.prof.userapi.ServiceUnavailableException;
import fk.prof.userapi.api.ProfileStoreAPI;
import fk.prof.userapi.api.cache.CachedProfileNotFoundException;
import fk.prof.userapi.api.cache.ProfileLoadInProgressException;
import fk.prof.userapi.http.UserapiApiPathConstants;
import fk.prof.userapi.model.AggregatedSamplesPerTraceCtx;
import fk.prof.userapi.model.AggregationWindowSummary;
import fk.prof.userapi.model.tree.CalleesTreeView;
import fk.prof.userapi.model.tree.CallTreeView;
import fk.prof.userapi.model.tree.IndexedTreeNode;
import fk.prof.userapi.model.tree.TreeViewResponse.CpuSampleCalleesTreeViewResponse;
import fk.prof.userapi.model.tree.TreeViewResponse.CpuSampleCallersTreeViewResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.CompositeFutureImpl;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.TimeoutHandler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static fk.prof.userapi.util.RequestParam.getParam;

/**
 * Routes requests to their respective handlers
 * Created by rohit.patiyal on 18/01/17.
 */
public class HttpVerticle extends AbstractVerticle {

    private static Logger LOGGER = LoggerFactory.getLogger(HttpVerticle.class);
    private static int VERSION = 1;

    private String baseDir;
    private final int maxListProfilesDurationInSecs;
    private Configuration.HttpConfig httpConfig;
    private ProfileStoreAPI profileStoreAPI;
    private Integer maxDepthForTreeExpand;

    public HttpVerticle(Configuration config, ProfileStoreAPI profileStoreAPI) {
        this.httpConfig = config.getHttpConfig();
        this.profileStoreAPI = profileStoreAPI;
        this.baseDir = config.getProfilesBaseDir();
        this.maxListProfilesDurationInSecs = config.getMaxListProfilesDurationInDays()*24*60*60;
        this.maxDepthForTreeExpand = config.getMaxDepthExpansion();
    }

    private Router configureRouter() {
        Router router = Router.router(vertx);
        router.route().handler(TimeoutHandler.create(httpConfig.getRequestTimeout()));
        router.route().handler(LoggerHandler.create());

        router.get(UserapiApiPathConstants.APPS).handler(this::getAppIds);
        router.get(UserapiApiPathConstants.CLUSTER_GIVEN_APPID).handler(this::getClusterIds);
        router.get(UserapiApiPathConstants.PROC_GIVEN_APPID_CLUSTERID).handler(this::getProcId);
        router.get(UserapiApiPathConstants.PROFILES_GIVEN_APPID_CLUSTERID_PROCID).handler(this::getProfiles);

        router.post(UserapiApiPathConstants.VIEW_FOR_CPU_SAMPLING).handler(BodyHandler.create().setBodyLimit(1024 * 1024));
        router.post(UserapiApiPathConstants.VIEW_FOR_CPU_SAMPLING).handler(this::getViewForCpuSampling);

        router.get(UserapiApiPathConstants.HEALTHCHECK).handler(this::handleGetHealth);

        return router;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        httpConfig = config().mapTo(Configuration.HttpConfig.class);

        Router router = configureRouter();
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(httpConfig.getHttpPort(), event -> {
                    if (event.succeeded()) {
                        startFuture.complete();
                    } else {
                        startFuture.fail(event.cause());
                    }
                });
    }

    private void getAppIds(RoutingContext routingContext) {
        String prefix = routingContext.request().getParam("prefix");
        if (prefix == null) {
            prefix = "";
        }
        Future<Set<String>> future = Future.future();
        profileStoreAPI.getAppIdsWithPrefix(future.setHandler(result -> setResponse(result, routingContext)),
            baseDir, prefix);
    }

    private void getClusterIds(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        String prefix = routingContext.request().getParam("prefix");
        if (prefix == null) {
            prefix = "";
        }
        Future<Set<String>> future = Future.future();
        profileStoreAPI.getClusterIdsWithPrefix(future.setHandler(result -> setResponse(result, routingContext)),
            baseDir, appId, prefix);
    }

    private void getProcId(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        final String clusterId = routingContext.request().getParam("clusterId");
        String prefix = routingContext.request().getParam("prefix");
        if (prefix == null) {
            prefix = "";
        }
        Future<Set<String>> future = Future.future();
        profileStoreAPI.getProcsWithPrefix(future.setHandler(result -> setResponse(result, routingContext)),
            baseDir, appId, clusterId, prefix);
    }

    private void getProfiles(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        final String clusterId = routingContext.request().getParam("clusterId");
        final String proc = routingContext.request().getParam("procId");

        ZonedDateTime startTime;
        int duration;

        try {
            startTime = ZonedDateTime.parse(routingContext.request().getParam("start"), DateTimeFormatter.ISO_ZONED_DATE_TIME);
            duration = Integer.parseInt(routingContext.request().getParam("duration"));
        } catch (Exception e) {
            setResponse(Future.failedFuture(new IllegalArgumentException(e)), routingContext);
            return;
        }
        if (duration > maxListProfilesDurationInSecs) {
            setResponse(Future.failedFuture(new IllegalArgumentException("Max window size supported = " + maxListProfilesDurationInSecs + " seconds, requested window size = " + duration + " seconds")), routingContext);
            return;
        }

        Future<List<AggregatedProfileNamingStrategy>> foundProfiles = Future.future();
        foundProfiles.setHandler(result -> {
            if(result.failed()) {
                setResponse(result, routingContext);
                return;
            }

            List<Future> profileSummaries = new ArrayList<>();
            for (AggregatedProfileNamingStrategy filename: result.result()) {
                Future<AggregationWindowSummary> summary = Future.future();

                profileStoreAPI.loadSummary(summary, filename);
                profileSummaries.add(summary);
            }

            CompositeFuture.join(profileSummaries).setHandler(summaryResult -> {
                List<AggregationWindowSummary> succeeded = new ArrayList<>();
                List<ErroredGetSummaryResponse> failed = new ArrayList<>();

                // Can only get the underlying list of results of it is a CompositeFutureImpl
                if(summaryResult instanceof CompositeFutureImpl) {
                    CompositeFutureImpl compositeFuture = (CompositeFutureImpl) summaryResult;
                    for (int i = 0; i < compositeFuture.size(); ++i) {
                        if(compositeFuture.succeeded(i)) {
                            succeeded.add(compositeFuture.resultAt(i));
                        }
                        else {
                            AggregatedProfileNamingStrategy failedFilename = result.result().get(i);
                            failed.add(new ErroredGetSummaryResponse(failedFilename.startTime, failedFilename.duration, compositeFuture.cause(i).getMessage()));
                        }
                    }
                }
                else {
                    if(summaryResult.succeeded()) {
                        CompositeFuture compositeFuture = summaryResult.result();
                        for (int i = 0; i < compositeFuture.size(); ++i) {
                            succeeded.add(compositeFuture.resultAt(i));
                        }
                    }
                    else {
                        // composite future failed so set error in response.
                        setResponse(Future.failedFuture(summaryResult.cause()), routingContext);
                        return;
                    }
                }

                Map<String, Object> response = new HashMap<>();
                response.put("succeeded", succeeded);
                response.put("failed", failed);

                setResponse(Future.succeededFuture(response), routingContext, true);
            });
        });

        profileStoreAPI.getProfilesInTimeWindow(foundProfiles,
            baseDir, appId, clusterId, proc, startTime, duration);
    }

    private void getViewForCpuSampling(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();

        String appId, clusterId, procId, traceName;
        Boolean autoExpand;
        Integer maxDepth, duration;
        ZonedDateTime startTime;
        String viewType;
        List<Integer> nodeIds;
        try {
            viewType = getParam(req, "viewType");

            if(!("callers".equals(viewType) || "callees".equals(viewType))) {
                throw new IllegalArgumentException("viewType \"" + viewType + "\" not supported");
            }

            appId = getParam(req, "appId");
            clusterId = getParam(req, "clusterId");
            procId = getParam(req, "procId");
            traceName = getParam(req, "traceName");
            startTime = getParam(req, "start", ZonedDateTime.class);
            duration = getParam(req, "duration", Integer.class);
            autoExpand = MoreObjects.firstNonNull(getParam(req, "autoExpand", Boolean.class, false), false);
            maxDepth = Math.min(maxDepthForTreeExpand, MoreObjects.firstNonNull(getParam(req, "maxDepth", Integer.class, false), maxDepthForTreeExpand));
            nodeIds = routingContext.getBodyAsJsonArray().getList();
        }
        catch (IllegalArgumentException e) {
            setResponse(Future.failedFuture(e), routingContext);
            return;
        }
        catch (Exception e) {
            setResponse(Future.failedFuture(new IllegalArgumentException(e)), routingContext);
            return;
        }

        AggregatedProfileNamingStrategy profileName = new AggregatedProfileNamingStrategy(baseDir, VERSION, appId, clusterId, procId, startTime, duration, WorkType.cpu_sample_work);

        if("callers".equals(viewType)) {
            getCallersViewForCpuSampling(routingContext, profileName, traceName, nodeIds, autoExpand, maxDepth);
        }
        else {
            getCalleesViewForCpuSampling(routingContext, profileName, traceName, nodeIds, autoExpand, maxDepth);
        }
    }

    private void getCallersViewForCpuSampling(RoutingContext routingContext, AggregatedProfileNamingStrategy profileName, String traceName,
                                              List<Integer> nodeIds, boolean autoExpand, int maxDepth) {
        Future<Pair<AggregatedSamplesPerTraceCtx,CallTreeView>> callTreeView = profileStoreAPI.getCpuSamplingCallersTreeView(profileName, traceName);

        callTreeView.setHandler(ar -> {
            if(ar.failed()) {
                setResponse(ar, routingContext);
            }
            else {
                AggregatedSamplesPerTraceCtx samplesPerTraceCtx = ar.result().first;
                CallTreeView treeView = ar.result().second;

                List<Integer> originIds = nodeIds;
                if(originIds == null || originIds.isEmpty()) {
                    originIds = treeView.getRootNodes().stream().map(e -> e.getIdx()).collect(Collectors.toList());
                }

                List<IndexedTreeNode<FrameNode>> subTree = treeView.getSubTree(originIds, maxDepth, autoExpand);
                Map<Integer, String> methodLookup = new HashMap<>();

                subTree.forEach(e -> e.visit((i,data) -> methodLookup.put(data.getMethodId(), samplesPerTraceCtx.getMethodLookup().get(data.getMethodId()))));

                setResponse(Future.succeededFuture(new CpuSampleCallersTreeViewResponse(subTree, methodLookup)), routingContext);
            }
        });
    }

    private void getCalleesViewForCpuSampling(RoutingContext routingContext, AggregatedProfileNamingStrategy profileName, String traceName,
                                              List<Integer> nodeIds, boolean autoExpand, int maxDepth) {
        Future<Pair<AggregatedSamplesPerTraceCtx,CalleesTreeView>> calleesTreeView = profileStoreAPI.getCpuSamplingCalleesTreeView(profileName, traceName);

        calleesTreeView.setHandler(ar -> {
            if(ar.failed()) {
                setResponse(ar, routingContext);
            }
            else {
                AggregatedSamplesPerTraceCtx samplesPerTraceCtx = ar.result().first;
                CalleesTreeView treeView = ar.result().second;

                List<Integer> originIds = nodeIds;
                if(originIds == null || originIds.isEmpty()) {
                    originIds = treeView.getRootNodes().stream().map(e -> e.getIdx()).collect(Collectors.toList());
                }

                List<IndexedTreeNode<FrameNode>> subTree = treeView.getSubTree(originIds, maxDepth, autoExpand);
                Map<Integer, String> methodLookup = new HashMap<>();

                subTree.forEach(e -> e.visit((i,data) -> methodLookup.put(data.getMethodId(), samplesPerTraceCtx.getMethodLookup().get(data.getMethodId()))));

                setResponse(Future.succeededFuture(new CpuSampleCalleesTreeViewResponse(subTree, methodLookup)), routingContext);
            }
        });
    }

    private void handleGetHealth(RoutingContext routingContext) {
        routingContext.response().setStatusCode(200).end();
    }

    private <T> void setResponse(AsyncResult<T> result, RoutingContext routingContext) {
        setResponse(result, routingContext, false);
    }

    private <T> void setResponse(AsyncResult<T> result, RoutingContext routingContext, boolean gzipped) {
        if(routingContext.response().ended()) {
            return;
        }

        HttpServerResponse response = routingContext.response();

        if(result.failed()) {
            Throwable cause = result.cause();
            LOGGER.error(routingContext.request().uri(), cause);


            if(cause instanceof ProfileLoadInProgressException) {
                // 202 for notifying that profile loading is in progress, and the request can be tried again after some time.
                endResponseWithError(response, cause, 202);
            }
            else if(cause instanceof FileNotFoundException) {
                if(cause instanceof CachedProfileNotFoundException) {
                    CachedProfileNotFoundException ex = (CachedProfileNotFoundException) cause;
                    if(ex.isCachedRemotely()) {
                        response.putHeader("location", "http://" + ex.getIp() + ":" + ex.getPort() + "/");
                        endResponse(response, 307);
                        return;
                    }
                    else if(ex.getCause() != null) {
                        // something went wrong while loading it. send 500
                        endResponseWithError(response, ex.getCause(), 500);
                        return;
                    }
                }
                endResponseWithError(response, cause, 404);
            }
            else if(cause instanceof IllegalArgumentException) {
                endResponseWithError(response, cause, 400);
            }
            else if(cause instanceof ServiceUnavailableException) {
                endResponseWithError(response, cause, 503);
            }
            else {
                endResponseWithError(response, cause, 500);
            }
        }
        else {
            String encodedResponse = Json.encode(result.result());

            response.putHeader("content-type", "application/json");
            if(gzipped && safeContains(routingContext.request().getHeader("Accept-Encoding"), "gzip")) {
                Buffer compressedBuf;
                try {
                    compressedBuf = Buffer.buffer(StreamTransformer.compress(encodedResponse.getBytes(Charset.forName("utf-8"))));
                }
                catch(IOException e) {
                    setResponse(Future.failedFuture(e), routingContext, false);
                    return;
                }

                response.putHeader("Content-Encoding", "gzip");
                response.end(compressedBuf);
            }
            else {
                response.end(encodedResponse);
            }
        }
    }

    private boolean safeContains(String str, String subStr) {
        if(str == null || subStr == null) {
            return false;
        }
        return str.toLowerCase().contains(subStr.toLowerCase());
    }

    private void endResponseWithError(HttpServerResponse response, Throwable error, int statusCode) {
        response.setStatusCode(statusCode).end(buildHttpErrorObject(error.getMessage(), statusCode).encode());
    }

    private void endResponse(HttpServerResponse response, int statusCode) {
        response.setStatusCode(statusCode).end();
    }

    private JsonObject buildHttpErrorObject(String msg, int statusCode) {
        final JsonObject error = new JsonObject()
                .put("timestamp", System.currentTimeMillis())
                .put("status", statusCode);

        switch (statusCode) {
            case 400: error.put("error", "BAD_REQUEST");
                break;
            case 404: error.put("error", "NOT_FOUND");
                break;
            case 500: error.put("error", "INTERNAL_SERVER_ERROR");
                break;
            default:  error.put("error", "SOMETHING_WENT_WRONG");
        }

        if (msg != null) {
            error.put("message", msg);
        }
        return error;
    }

    public static class ErroredGetSummaryResponse {
        private final ZonedDateTime start;
        private final int duration;
        private final String error;

        ErroredGetSummaryResponse(ZonedDateTime start, int duration, String errorMsg) {
            this.start = start;
            this.duration = duration;
            this.error = errorMsg;
        }

        public ZonedDateTime getStart() {
            return start;
        }

        public int getDuration() {
            return duration;
        }

        public String getError() {
            return error;
        }
    }
}

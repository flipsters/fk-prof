package fk.prof.userapi.verticles;

import fk.prof.userapi.Configuration;
import fk.prof.userapi.UserapiConfigManager;
import fk.prof.userapi.http.UserapiApiPathConstants;
import fk.prof.userapi.http.UserapiHttpHelper;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests for Policy APIs of HttpVerticle
 * Created by rohitpatiyal on 20/6/17.
 */
@RunWith(VertxUnitRunner.class)
public class PolicyAPITest {

    private HttpServer backendServer;
    private HttpClient client;
    private static Vertx vertx;

    public void setup(TestContext context) throws IOException {
        final Async async = context.async();
        UserapiConfigManager.setDefaultSystemProperties();
        Configuration config = UserapiConfigManager.loadConfig(PolicyAPITest.class.getClassLoader().getResource("userapi-config.json").getFile());
        vertx = Vertx.vertx(new VertxOptions(config.getVertxOptions()));

        client = vertx.createHttpClient();
        backendServer = vertx.createHttpServer();
    }

    @Test
    public void TestGetPolicyProxiedToBackends(TestContext context) throws Exception {
        final Async async = context.async();
        Router router = Router.router(vertx);
        UserapiHttpHelper.attachHandlersToRoute(router, HttpMethod.GET,
                UserapiApiPathConstants.GET_POLICY_GIVEN_APPID_CLUSTERID_PROCNAME, req ->{
                    PolicyDTO.
                    req.response().end(versionedPolicyDetails.toString());
                });

    }
}

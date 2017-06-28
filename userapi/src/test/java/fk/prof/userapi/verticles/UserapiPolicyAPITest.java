package fk.prof.userapi.verticles;

import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.userapi.Configuration;
import fk.prof.userapi.UserapiConfigManager;
import fk.prof.userapi.api.ProfileStoreAPI;
import fk.prof.userapi.deployer.VerticleDeployer;
import fk.prof.userapi.deployer.impl.UserapiHttpVerticleDeployer;
import fk.prof.userapi.http.UserapiApiPathConstants;
import fk.prof.userapi.http.UserapiHttpHelper;
import fk.prof.userapi.model.json.ProtoSerializers;
import fk.prof.userapi.util.ProtoUtil;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import mock.MockPolicyData;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import proto.PolicyDTO;

import java.io.IOException;
import java.util.Arrays;

import static fk.prof.userapi.http.UserapiApiPathConstants.DELIMITER;

/**
 * Tests for Policy APIs of HttpVerticle
 * Created by rohitpatiyal on 20/6/17.
 */
@RunWith(VertxUnitRunner.class)
public class UserapiPolicyAPITest {

    private HttpServer backendServer;
    private HttpClient client;
    private static Vertx vertx;
    private static int backendPort;
    private static int userapiPort;

    @BeforeClass
    public static void setup() {
        ProtoSerializers.registerSerializers(Json.mapper);
    }

    @Before
    public void setup(TestContext context) throws IOException {
        final Async async = context.async();
        UserapiConfigManager.setDefaultSystemProperties();
        Configuration config = UserapiConfigManager.loadConfig(UserapiPolicyAPITest.class.getClassLoader().getResource("userapi-conf.json").getFile());
        backendPort = config.getBackendConfig().getPort();
        userapiPort = config.getHttpConfig().getHttpPort();
        vertx = Vertx.vertx(new VertxOptions(config.getVertxOptions()));
        client = vertx.createHttpClient();
        backendServer = vertx.createHttpServer();

        VerticleDeployer userapiHttpVerticleDeployer = new UserapiHttpVerticleDeployer(vertx, config, Mockito.mock(ProfileStoreAPI.class));
        userapiHttpVerticleDeployer.deploy().setHandler(aR -> {
           if(aR.succeeded()) async.complete();
           else context.fail();
        });
    }

    @After
    public void tearDown(TestContext context) throws Exception {
        final Async async = context.async();
        backendServer.close();
        client.close();
        vertx.close(result -> {
           if(result.succeeded()) {
               async.complete();
           } else {
               context.fail();
           }
        });
    }

    @Test
    public void TestGetPolicyProxiedToBackend(TestContext context) throws Exception {
        final Async async = context.async();
        Router router = Router.router(vertx);
        UserapiHttpHelper.attachHandlersToRoute(router, HttpMethod.GET,
                UserapiApiPathConstants.GET_POLICY_GIVEN_APPID_CLUSTERID_PROCNAME, req ->{
                    PolicyDTO.VersionedPolicyDetails versionedPolicyDetails = MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0),0);
                    req.response().end(versionedPolicyDetails.toString());
                });
        backendServer.requestHandler(router::accept);
        backendServer.listen(backendPort, result -> {
            if(result.succeeded()){
                String userapiPolicyPath = UserapiApiPathConstants.POLICY + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getAppId() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getCluster() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getProcName();
                client.getNow(userapiPort, "localhost", userapiPolicyPath, res -> {
                    res.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toString(), MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0),0).toString());
                        async.complete();
                   });
                });
            }else{
                context.fail();
            }
        });
    }

    @Test
    public void TestPutPolicyProxiedToLeader(TestContext context) {
        final Async async = context.async();
        Router router = Router.router(vertx);
        String payloadAsBuffer = null;
        payloadAsBuffer = Json.encode(MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), 0));
        System.err.println(payloadAsBuffer);
        String finalPayloadAsBuffer = payloadAsBuffer;
        UserapiHttpHelper.attachHandlersToRoute(router, HttpMethod.PUT,
                UserapiApiPathConstants.PUT_POLICY_GIVEN_APPID_CLUSTERID_PROCNAME,
                BodyHandler.create().setBodyLimit(1024 * 10), req -> {
                    try {
                        System.out.println("POLICY RECEIVED BY BACKEND : " + req.getBody());
                        PolicyDTO.VersionedPolicyDetails expected = PolicyDTO.VersionedPolicyDetails.parseFrom(finalPayloadAsBuffer.getBytes());
                        PolicyDTO.VersionedPolicyDetails got = PolicyDTO.VersionedPolicyDetails.parseFrom(req.getBody().getBytes());
                        context.assertEquals(expected, got);
                        req.response().end(got.toBuilder().setVersion(got.getVersion() + 1).toString());
                    } catch (InvalidProtocolBufferException e) {
                        context.fail(e);
                    }
                });
        backendServer.requestHandler(router::accept);
        backendServer.listen(backendPort, result -> {
            if (result.succeeded()) {
                String backendPolicyPath = UserapiApiPathConstants.POLICY + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getAppId() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getCluster() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getProcName();

                client.put(userapiPort, "localhost", backendPolicyPath, res -> {
                    res.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toString(), MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), 1).toString());
                        async.complete();
                    });
                }).end(finalPayloadAsBuffer);
            } else {
                context.fail();
            }
        });
    }

    @Test
    public void TestPostPolicyProxiedToLeader(TestContext context) {
        final Async async = context.async();
        Router router = Router.router(vertx);
        Buffer payloadAsBuffer = null;
        try {
            payloadAsBuffer = ProtoUtil.buildBufferFromProto(MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), -1));
        } catch (IOException e) {
            context.fail(e);
        }
        Buffer finalPayloadAsBuffer = payloadAsBuffer;
        UserapiHttpHelper.attachHandlersToRoute(router, HttpMethod.POST,
                UserapiApiPathConstants.POST_POLICY_GIVEN_APPID_CLUSTERID_PROCNAME,
                BodyHandler.create().setBodyLimit(1024 * 10), req -> {
                    try {
                        PolicyDTO.VersionedPolicyDetails expected = PolicyDTO.VersionedPolicyDetails.parseFrom(finalPayloadAsBuffer.getBytes());
                        PolicyDTO.VersionedPolicyDetails got = PolicyDTO.VersionedPolicyDetails.parseFrom(req.getBody().getBytes());
                        context.assertEquals(expected, got);
                        req.response().end(got.toBuilder().setVersion(got.getVersion() + 1).toString());
                    } catch (InvalidProtocolBufferException e) {
                        context.fail(e);
                    }
                });
        backendServer.requestHandler(router::accept);
        backendServer.listen(backendPort, result -> {
            if (result.succeeded()) {
                String userapiPolicyPath = UserapiApiPathConstants.POLICY + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getAppId() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getCluster() + DELIMITER + MockPolicyData.mockProcessGroups.get(0).getProcName();

                client.post(userapiPort, "localhost", userapiPolicyPath, res -> {
                    res.bodyHandler(buffer -> {
                        context.assertEquals(buffer.toString(), MockPolicyData.getMockVersionedPolicyDetails(MockPolicyData.mockPolicyDetails.get(0), 0).toString());
                        async.complete();
                    });
                }).end(finalPayloadAsBuffer);
            } else {
                context.fail();
            }
        });
    }
}

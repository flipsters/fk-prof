package fk.prof.backend;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Map configuration to POJO and encapsulate default settings inside it
 * Use this POJO to access config and remove jsonobject getters from rest of the code base
 */
public class ConfigManager {
    public static final String METRIC_REGISTRY = "vertx-registry";
  private static final String IP_ADDRESS_KEY = "ip.address";
  private static final String BACKEND_HTTP_PORT_KEY = "backend.http.port";
  private static final String LEADER_HTTP_PORT_KEY = "leader.http.port";
  private static final String BACKEND_HTTP_SERVER_OPTIONS_KEY = "backend.http.server";
  private static final String LEADER_HTTP_SERVER_OPTIONS_KEY = "leader.http.server";
  private static final String HTTP_CLIENT_OPTIONS_KEY = "http.client";
  private static final String LOAD_REPORT_INTERVAL_KEY = "load.report.interval.secs";
  private static final String VERTX_OPTIONS_KEY = "vertxOptions";
  private static final String BACKEND_HTTP_DEPLOYMENT_OPTIONS_KEY = "backendHttpOptions";
  private static final String CURATOR_OPTIONS_KEY = "curatorOptions";
  private static final String LEADER_ELECTION_DEPLOYMENT_OPTIONS_KEY = "leaderElectionOptions";
  private static final String LEADER_HTTP_DEPLOYMENT_OPTIONS_KEY = "leaderHttpOptions";
  private static final String LOGFACTORY_SYSTEM_PROPERTY_KEY = "vertx.logger-delegate-factory-class-name";
  private static final String LOGFACTORY_SYSTEM_PROPERTY_DEFAULT_VALUE = "io.vertx.core.logging.SLF4JLogDelegateFactory";
    private static final String POLICY_OPTIONS_KEY = "policyOptions";
  private final JsonObject config;


  public ConfigManager(String configFilePath) throws IOException {
    Preconditions.checkNotNull(configFilePath);
    this.config = new JsonObject(Files.toString(
        new File(configFilePath), StandardCharsets.UTF_8));
  }

  public ConfigManager(JsonObject config) {
    Preconditions.checkNotNull(config);
    this.config = config;
  }

    public static void setDefaultSystemProperties() {
        Properties properties = System.getProperties();
        properties.computeIfAbsent(ConfigManager.LOGFACTORY_SYSTEM_PROPERTY_KEY, k -> ConfigManager.LOGFACTORY_SYSTEM_PROPERTY_DEFAULT_VALUE);
        properties.computeIfAbsent("vertx.metrics.options.enabled", k -> true);
    }

  public String getIPAddress() {
    return config.getString(IP_ADDRESS_KEY, "127.0.0.1");
  }

  public int getBackendHttpPort() {
    return config.getInteger(BACKEND_HTTP_PORT_KEY, 2491);
  }

  public int getLeaderHttpPort() {
    return config.getInteger(LEADER_HTTP_PORT_KEY, 2496);
  }

  public int getLoadReportIntervalInSeconds() {
    return config.getInteger(LOAD_REPORT_INTERVAL_KEY, 60);
  }

  public JsonObject getBackendHttpServerConfig() {
    return config.getJsonObject(BACKEND_HTTP_SERVER_OPTIONS_KEY, new JsonObject());
  }

  public JsonObject getLeaderHttpServerConfig() {
    return config.getJsonObject(LEADER_HTTP_SERVER_OPTIONS_KEY, new JsonObject());
  }

  public JsonObject getHttpClientConfig() {
    return config.getJsonObject(HTTP_CLIENT_OPTIONS_KEY, new JsonObject());
  }

  public JsonObject getCuratorConfig() {
    return config.getJsonObject(CURATOR_OPTIONS_KEY, new JsonObject());
  }

  public JsonObject getVertxConfig() {
    return config.getJsonObject(VERTX_OPTIONS_KEY, new JsonObject());
  }

  public JsonObject getBackendHttpDeploymentConfig() {
    return enrichDeploymentConfig(config.getJsonObject(BACKEND_HTTP_DEPLOYMENT_OPTIONS_KEY, new JsonObject()));
  }

  public JsonObject getLeaderElectionDeploymentConfig() {
    return enrichDeploymentConfig(config.getJsonObject(LEADER_ELECTION_DEPLOYMENT_OPTIONS_KEY, new JsonObject()));
  }

  public JsonObject getLeaderHttpDeploymentConfig() {
    return enrichDeploymentConfig(config.getJsonObject(LEADER_HTTP_DEPLOYMENT_OPTIONS_KEY, new JsonObject()));
  }

  private JsonObject enrichDeploymentConfig(JsonObject deploymentConfig) {
    if(deploymentConfig.getJsonObject("config") == null) {
      deploymentConfig.put("config", new JsonObject());
    }
    return deploymentConfig;
  }

    public JsonObject getPolicyConfig() {
        return config.getJsonObject(POLICY_OPTIONS_KEY, new JsonObject());
  }
}

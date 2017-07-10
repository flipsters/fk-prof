package fk.prof.backend.http;

public final class ApiPathConstants {
  private ApiPathConstants() {
  }
  public static final String LEADER_API_PREFIX = "/leader";
  public static final String POLICY_API_PREFIX = "/policy";

  public static final String AGGREGATOR_POST_PROFILE = "/profile";

  public static final String BACKEND_POST_POLL = "/poll";
  public static final String BACKEND_HEALTHCHECK = "/health";
  public static final String BACKEND_POST_ASSOCIATION = "/association";
  public static final String BACKEND_GET_ASSOCIATIONS = "/associations";
  public static final String BACKEND_GET_APPS = "/appIds";
  public static final String BACKEND_GET_CLUSTERS_FOR_APP = "/clusterIds/:appId";
  public static final String BACKEND_GET_PROCS_FOR_APP_CLUSTER = "/procNames/:appId/:clusterId";
  public static final String BACKEND_GET_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
  public static final String BACKEND_PUT_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
  public static final String BACKEND_POST_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";

  public static final String LEADER_POST_LOAD = LEADER_API_PREFIX + "/load";
  public static final String LEADER_GET_WORK = LEADER_API_PREFIX + "/work";
  public static final String LEADER_POST_ASSOCIATION = LEADER_API_PREFIX + "/association";
  public static final String LEADER_GET_ASSOCIATIONS = LEADER_API_PREFIX + "/associations";
  public static final String LEADER_GET_APPS = LEADER_API_PREFIX + "/appIds";
  public static final String LEADER_GET_CLUSTERS_FOR_APP = LEADER_API_PREFIX + "/clusterIds/:appId";
  public static final String LEADER_GET_PROCS_FOR_APP_CLUSTER = LEADER_API_PREFIX + "/procNames/:appId/:clusterId";
  public static final String LEADER_GET_POLICY_FOR_APP_CLUSTER_PROC = LEADER_API_PREFIX + POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
  public static final String LEADER_PUT_POLICY_FOR_APP_CLUSTER_PROC = LEADER_API_PREFIX + POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
  public static final String LEADER_POST_POLICY_FOR_APP_CLUSTER_PROC = LEADER_API_PREFIX + POLICY_API_PREFIX + "/:appId/:clusterId/:procName";

}

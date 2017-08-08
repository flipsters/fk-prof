package fk.prof.backend.http;

public final class ApiPathConstants {
  private ApiPathConstants() {
  }
  public static final String LEADER_API_PREFIX = "/leader";
  public static final String POLICY_API_PREFIX = "/policy";
  public static final String APPS_PREFIX = "/apps";
  public static final String CLUSTERS_PREFIX = "/clusters";
  public static final String PROCS_PREFIX = "/procs";

  public static final String AGGREGATOR_POST_PROFILE = "/profile";

  public static final String BACKEND_POST_POLL = "/poll";
  public static final String BACKEND_HEALTHCHECK = "/health";
  public static final String BACKEND_POST_ASSOCIATION = "/association";
  public static final String BACKEND_GET_ASSOCIATIONS = "/associations";
  public static final String BACKEND_GET_APPS = APPS_PREFIX;
  public static final String BACKEND_GET_CLUSTERS_FOR_APP = CLUSTERS_PREFIX + "/:appId";
  public static final String BACKEND_GET_PROCS_FOR_APP_CLUSTER = PROCS_PREFIX + "/:appId/:clusterId";
  public static final String BACKEND_GET_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
  public static final String BACKEND_PUT_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
  public static final String BACKEND_POST_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";

  public static final String LEADER_POST_LOAD = LEADER_API_PREFIX + "/load";
  public static final String LEADER_GET_WORK = LEADER_API_PREFIX + "/work";
  public static final String LEADER_POST_ASSOCIATION = LEADER_API_PREFIX + "/association";
  public static final String LEADER_GET_ASSOCIATIONS = LEADER_API_PREFIX + "/associations";
  public static final String LEADER_GET_APPS = LEADER_API_PREFIX + APPS_PREFIX;
  public static final String LEADER_GET_CLUSTERS_FOR_APP = LEADER_API_PREFIX + CLUSTERS_PREFIX + "/:appId";
  public static final String LEADER_GET_PROCS_FOR_APP_CLUSTER = LEADER_API_PREFIX + PROCS_PREFIX + "/:appId/:clusterId";
  public static final String LEADER_GET_POLICY_FOR_APP_CLUSTER_PROC = LEADER_API_PREFIX + POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
  public static final String LEADER_PUT_POLICY_FOR_APP_CLUSTER_PROC = LEADER_API_PREFIX + POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
  public static final String LEADER_POST_POLICY_FOR_APP_CLUSTER_PROC = LEADER_API_PREFIX + POLICY_API_PREFIX + "/:appId/:clusterId/:procName";

}

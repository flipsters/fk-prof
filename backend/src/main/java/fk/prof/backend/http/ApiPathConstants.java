package fk.prof.backend.http;

public final class ApiPathConstants {
  private ApiPathConstants() {
  }

  public static final String AGGREGATOR_POST_PROFILE = "/profile";

  public static final String LEADER_POST_LOAD = "/leader/load";
  public static final String LEADER_GET_WORK = "/leader/work";

  public static final String POST_ASSOCIATION = "/association";
  public static final String GET_ASSOCIATIONS = "/associations";

  public static final String BACKEND_POST_POLL = "/poll";
  public static final String BACKEND_HEALTHCHECK = "/health";

  public static final String APPIDS = "/appIds";
  public static final String CLUSTERIDS_GIVEN_APPID = "/clusterIds/:appId";
  public static final String PROCNAMES_GIVEN_APPID_CLUSTERID = "/procNames/:appId/:clusterId";

  public static final String POLICY_GIVEN_APPID_CLUSTERID_PROCNAME = "/policy/:appId/:clusterId/:procName";

  public static final String LEADER = "/leader";
  public static final String POLICY = "/policy";

}

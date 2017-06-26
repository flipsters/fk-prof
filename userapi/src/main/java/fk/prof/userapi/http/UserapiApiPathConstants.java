package fk.prof.userapi.http;

public final class UserapiApiPathConstants {
  private UserapiApiPathConstants() { }

  public static final String APPS = "/apps";
  public static final String CLUSTER_GIVEN_APPID = "/cluster/:appId";
  public static final String PROC_GIVEN_APPID_CLUSTERID = "/proc/:appId/:clusterId";

  public static final String PROFILES_GIVEN_APPID_CLUSTERID_PROCNAME = "/profiles/:appId/:clusterId/:procName";
  public static final String PROFILE_GIVEN_APPID_CLUSTERID_PROCNAME_WORKTYPE_TRACENAME = "/profile/:appId/:clusterId/:procName/cpu-sampling/:traceName";

  public static final String GET_POLICY_GIVEN_APPID_CLUSTERID_PROCNAME = "/policy/:appId/:clusterId/:procName";
  public static final String PUT_POLICY_GIVEN_APPID_CLUSTERID_PROCNAME = "/policy/:appId/:clusterId/:procName";
  public static final String POST_POLICY_GIVEN_APPID_CLUSTERID_PROCNAME = "/policy/:appId/:clusterId/:procName";

  public static final String HEALTHCHECK = "/health";

  public static final String POLICY = "/policy";
  public static final String DELIMITER = "/";
}

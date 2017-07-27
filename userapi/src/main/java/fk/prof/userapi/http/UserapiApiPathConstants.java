package fk.prof.userapi.http;

public final class UserapiApiPathConstants {
    private UserapiApiPathConstants() {
    }

    public static final String POLICY_API_PREFIX = "/policy";
    public static final String LIST_POLICY_API_PREFIX = "/list/policy";
    public static final String DELIMITER = "/";

    public static final String APPS = "/appIds";
    public static final String CLUSTERS_FOR_APP = "/clusterIds/:appId";
    public static final String PROCS_FOR_APP_CLUSTER = "/procNames/:appId/:clusterId";
    public static final String PROFILES_FOR_APP_CLUSTER_PROC = "/profiles/:appId/:clusterId/:procName";
    public static final String PROFILE_FOR_APP_CLUSTER_PROC_WORK_TRACE = "/profile/:appId/:clusterId/:procName/cpu-sampling/:traceName";

    public static final String POLICY_APPS = LIST_POLICY_API_PREFIX + "/appIds";
    public static final String POLICY_CLUSTERS_FOR_APP = LIST_POLICY_API_PREFIX + "/clusterIds/:appId";
    public static final String POLICY_PROCS_FOR_APP_CLUSTER = LIST_POLICY_API_PREFIX + "/procNames/:appId/:clusterId";
    public static final String GET_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
    public static final String PUT_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";
    public static final String POST_POLICY_FOR_APP_CLUSTER_PROC = POLICY_API_PREFIX + "/:appId/:clusterId/:procName";

    public static final String HEALTH_CHECK = "/health";

}

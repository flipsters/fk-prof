package model;

import java.util.Set;

/**
 * Interface for DataStores containing aggregated profile data
 * Created by rohit.patiyal on 23/01/17.
 */
public interface IDataStore {
    void initDataStore();

    Set<String> getAppIdsWithPrefix(String appIdPrefix);

    Set<String> getClusterIdsWithPrefix(String appId, String clusterIdPrefix);

    Set<String> getProcIdsWithPrefix(String appId, String clusterId, String procIdPrefix);

    Set<Profile> getProfilesInTimeWindow(String appId, String clusterId, String proc, String startTime, String durationInSeconds);
}

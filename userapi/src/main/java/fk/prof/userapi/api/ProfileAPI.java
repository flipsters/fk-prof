package fk.prof.userapi.api;

import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.userapi.model.AggregatedProfileInfo;
import fk.prof.userapi.model.AggregationWindowSummary;
import io.vertx.core.Future;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Interface for DataStores containing aggregated profile data
 * Created by rohit.patiyal on 23/01/17.
 */
public interface ProfileAPI {
    /**
     * Returns set of profiles of specified appId, clusterId and process from the DataStore filtered by the specified time interval and duration
     *
     * @param appId             appId of which the profiles are required
     * @param clusterId         clusterId of which the profiles are required
     * @param proc              process of which the profiles are required
     * @param startTime         startTime to filter the profiles
     * @param durationInSeconds duration from startTime to filter the profiles
     * @return completable future which returns set containing profiles
     */
    void getProfilesInTimeWindow(Future<List<AggregatedProfileNamingStrategy>> profiles, String baseDir, String appId, String clusterId, String proc, ZonedDateTime startTime, int durationInSeconds);

    /**
     * Returns aggregated profile for the provided header
     * @param future
     * @param filename
     */
    void load(Future<AggregatedProfileInfo> future, AggregatedProfileNamingStrategy filename);

    /**
     * Returns aggregated profile for the provided header
     * @param future
     * @param filename
     */
    void loadSummary(Future<AggregationWindowSummary> future, AggregatedProfileNamingStrategy filename);
}

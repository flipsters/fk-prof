package fk.prof.storage.api;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Interface representing a store which is indexed by process group
 * i.e. which stores data uniquely identified by a process group
 * Created by rohit.patiyal on 16/05/17.
 */
public interface ProcessGroupAPI {

  /**
   * Returns completable future which returns set of appIds from the DataStore filtered by the specified prefix
   *
   * @param appIdPrefix prefix to filter the appIds
   * @return completable future which returns set containing app ids
   */
  void getAppIdsWithPrefix(CompletableFuture<Set<String>> appIds, String baseDir, String appIdPrefix);

  /**
   * Returns set of clusterIds of specified appId from the DataStore filtered by the specified prefix
   *
   * @param appId           appId of which the clusterIds are required
   * @param clusterIdPrefix prefix to filter the clusterIds
   * @return completable future which returns set containing cluster ids
   */
  void getClusterIdsWithPrefix(CompletableFuture<Set<String>> clusterIds, String baseDir, String appId, String clusterIdPrefix);

  /**
   * Returns set of processes of specified appId and clusterId from the DataStore filtered by the specified prefix
   *
   * @param appId      appId of which the processes are required
   * @param clusterId  clusterId of which the processes are required
   * @param procPrefix prefix to filter the processes
   * @return completable future which returns set containing process names
   */
  void getProcNamesWithPrefix(CompletableFuture<Set<String>> procIds, String baseDir, String appId, String clusterId, String procPrefix);

}

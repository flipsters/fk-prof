package fk.prof.userapi.api.impl;

import com.google.common.io.BaseEncoding;
import fk.prof.storage.AsyncStorage;
import fk.prof.storage.api.ProcessGroupAPI;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 *  Provides implementation to {@link ProcessGroupAPI} using
 * {@link AsyncStorage} as the data store
 * Created by rohit.patiyal on 18/05/17.
 */
public class AsyncStorageBasedProcessGroupAPI implements ProcessGroupAPI{

  private static final String VERSION = "v0001";
  private static final String DELIMITER = "/";

  private final AsyncStorage asyncStorage;

  public AsyncStorageBasedProcessGroupAPI(AsyncStorage asyncStorage) {
    this.asyncStorage = asyncStorage;

  }


  public void getAppIdsWithPrefix(CompletableFuture<Set<String>> appIds, String baseDir, String appIdPrefix) {
        /* TODO: move this prefix creation to {@link AggregatedProfileNamingStrategy} */
    getListingAtLevelWithPrefix(appIds, baseDir + DELIMITER + VERSION + DELIMITER, appIdPrefix, true);
  }

  private void getListingAtLevelWithPrefix(CompletableFuture<Set<String>> listings, String level, String objPrefix, boolean encoded) {
    CompletableFuture<Set<String>> commonPrefixesFuture = asyncStorage.listAsync(level, false);
    commonPrefixesFuture.thenApply(commonPrefixes -> {
      Set<String> objs = new HashSet<>();
      for (String commonPrefix : commonPrefixes) {
        String objName = getLastFromCommonPrefix(commonPrefix);
        objName = encoded ? decode(objName) : objName;

        if (objName.startsWith(objPrefix)) {
          objs.add(objName);
        }
      }
      return objs;
    }).whenComplete((result, error) -> completeFuture(result, error, listings));
  }

  public void getClusterIdsWithPrefix(CompletableFuture<Set<String>> clusterIds, String baseDir, String appId, String clusterIdPrefix) {
    getListingAtLevelWithPrefix(clusterIds, baseDir + DELIMITER + VERSION + DELIMITER + encode(appId) + DELIMITER,
        clusterIdPrefix, true);
  }

  public void getProcNamesWithPrefix(CompletableFuture<Set<String>> procIds, String baseDir, String appId, String clusterId, String procPrefix) {
    getListingAtLevelWithPrefix(procIds, baseDir + DELIMITER + VERSION + DELIMITER + encode(appId) + DELIMITER + encode(clusterId) + DELIMITER,
        procPrefix, true);
  }

  private <T> void completeFuture(T result, Throwable error, CompletableFuture<T> future) {
    if(error == null) {
      future.complete(result);
    }
    else {
      future.completeExceptionally(error);
    }
  }

  private String getLastFromCommonPrefix(String commonPrefix) {
    String[] splits = commonPrefix.split(DELIMITER);
    return splits[splits.length - 1];
  }

  private String decode(String str) {
    return new String(BaseEncoding.base32().decode(str), Charset.forName("utf-8"));
  }

  private String encode(String str) {
    return BaseEncoding.base32().encode(str.getBytes(Charset.forName("utf-8")));
  }
}

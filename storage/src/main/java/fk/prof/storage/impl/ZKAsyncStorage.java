package fk.prof.storage.impl;

import fk.prof.storage.AsyncStorage;
import fk.prof.storage.StorageException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * AsyncStorage impl backed by S3 Object store.
 * Created by rohit.patiyal on 16/05/17.
 */
public class ZKAsyncStorage implements AsyncStorage {
  private static Logger logger = LoggerFactory.getLogger(ZKAsyncStorage.class);
  private final CuratorFramework curatorClient;
  private final CreateMode currentCreateMode;
  private static final String DELIMITER = "/";

  public ZKAsyncStorage(CuratorFramework curatorClient, CreateMode createMode) {
    this.curatorClient = curatorClient;
    this.currentCreateMode = createMode;
  }

  @Override
  public CompletableFuture<Void> storeAsync(String path, InputStream content, long length) {
    return storeAsync(path, content.toString().getBytes());
  }

  @Override
  public CompletableFuture<Void> storeAsync(String path, byte[] content) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    try {
      curatorClient.create().creatingParentsIfNeeded().withMode(currentCreateMode).inBackground((client, event) -> {
        if (KeeperException.Code.OK.intValue() == event.getResultCode()) {
          future.complete(null);
        } else {
          logger.error("Error from ZK for while storing at node path = " + path + " with result =" + KeeperException.Code.get(event.getResultCode()).name());
          future.completeExceptionally(new StorageException(KeeperException.Code.get(event.getResultCode()).name()));
        }
      }).forPath(path, content);
    } catch (Exception e) {
      future.completeExceptionally(new StorageException("Error writing data to Zookeeper znode."));
    }
    return future;
  }

  @Override
  public CompletableFuture<InputStream> fetchAsync(String path) {
    CompletableFuture<InputStream> future = new CompletableFuture<>();
    try {
      if (currentCreateMode.equals(CreateMode.PERSISTENT_SEQUENTIAL)) {
        List<String> sequentialPolicies = ZKPaths.getSortedChildren(curatorClient.getZookeeperClient().getZooKeeper(), path);
        path = path + DELIMITER + sequentialPolicies.get(sequentialPolicies.size() - 1);
      }
      String finalPath = path;
      curatorClient.getData().inBackground((client, event) -> {
        if (KeeperException.Code.OK.intValue() == event.getResultCode()) {
          future.complete(new ByteArrayInputStream(event.getData()));
        } else {
          logger.error("Error from ZK for while fetching for node path = " + finalPath + " with result =" + KeeperException.Code.get(event.getResultCode()).name());
          future.completeExceptionally(new StorageException(KeeperException.Code.get(event.getResultCode()).name()));
        }
      }).forPath(finalPath);
    } catch (Exception e) {
      future.completeExceptionally(new StorageException("Error reading data from Zookeeper znode."));
    }
    return future;
  }

  @Override
  public CompletableFuture<Set<String>> listAsync(String prefixPath, boolean recursive) {
    if (recursive) {
      logger.error("Error listing nodes at path = {} with recursive set to true", prefixPath);
      throw new StorageException("Unsupported operation with recursive set to true");
    }
    return CompletableFuture.supplyAsync(() -> {
      try {
        return new HashSet<>(curatorClient.getChildren().forPath(prefixPath));
      } catch (Exception e) {
        logger.error("Error from ZK for while listing nodes at path = {} with error = {}", prefixPath, e);
        throw new StorageException(e.getMessage());
      }
    });
  }
}

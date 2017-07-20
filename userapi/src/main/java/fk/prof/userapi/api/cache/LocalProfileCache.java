package fk.prof.userapi.api.cache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import fk.prof.userapi.Cacheable;
import fk.prof.userapi.Configuration;
import fk.prof.userapi.model.AggregatedProfileInfo;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Created by gaurav.ashok on 17/07/17.
 */
public class LocalProfileCache {
    private static final Logger logger = LoggerFactory.getLogger(LocalProfileCache.class);

    private final Cache<String, CacheableFuture<AggregatedProfileInfo>> cache;
    private final Cache<String, Cacheable> viewCache;

    private RemovalListener<String, Future<AggregatedProfileInfo>> removalListener;

    public LocalProfileCache(Configuration config) {
        this(config, Ticker.systemTicker());
    }

    @VisibleForTesting
    protected LocalProfileCache(Configuration config, Ticker ticker) {
        this.viewCache = CacheBuilder.newBuilder()
            .ticker(ticker)
            .weigher((k, v) -> 1)        // default weight of 1, effectively counting the cached objects.
            .maximumWeight(200)
            .expireAfterAccess(config.getProfileViewRetentionDurationMin(), TimeUnit.MINUTES)
            .build();

        this.cache = CacheBuilder.newBuilder()
            .ticker(ticker)
            .expireAfterAccess(config.getProfileRetentionDurationMin(), TimeUnit.MINUTES)
            .weigher((k, v) -> 1)        // default weight of 1, effectively counting the cached objects.
            .maximumWeight(100)
            .removalListener(this::doCleanupOnEviction)
            .build();

        this.removalListener = null;
    }

    public void setRemovalListener(RemovalListener<String, Future<AggregatedProfileInfo>> removalListener) {
        this.removalListener = removalListener;
    }

    public Future<AggregatedProfileInfo> get(String key) {
        CacheableFuture cf = cache.getIfPresent(key);
        return cf != null ? cf.future : null;
    }

    public void put(String key, Future<AggregatedProfileInfo> profileFuture) {
        cache.put(key, new CacheableFuture<>(profileFuture));
    }

    public void invalidateProfile(String key) {
        cache.invalidate(key);
    }

    public <T extends Cacheable> T getView(String key) {
        return (T)viewCache.getIfPresent(key);
    }

    public <T extends Cacheable> void putView(String key, T view) {
        viewCache.put(key, view);
    }

    public void invalidateViews(Iterable<String> keys) {
        viewCache.invalidateAll(keys);
    }

    private void doCleanupOnEviction(RemovalNotification<String, CacheableFuture<AggregatedProfileInfo>> evt) {
        logger.info("Profile evicted. file: {}", evt.getKey());
        if(removalListener != null) {
            removalListener.onRemoval(RemovalNotification.create(evt.getKey(), evt.getValue().future, evt.getCause()));
        }
    }

    private static class CacheableFuture<T extends Cacheable> implements Cacheable {
        private final Future<T> future;

        CacheableFuture(Future<T> future) {
            this.future = future;
        }

        @Override
        public int getUtilizationWeight() {
            if (future.succeeded()) {
                return future.result().getUtilizationWeight();
            }
            return 1;                   // default weight for empty future / failed future
        }
    }
}

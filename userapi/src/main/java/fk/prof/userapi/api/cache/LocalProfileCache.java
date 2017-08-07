package fk.prof.userapi.api.cache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.userapi.Cacheable;
import fk.prof.userapi.Configuration;
import fk.prof.userapi.model.AggregatedProfileInfo;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by gaurav.ashok on 17/07/17.
 */
public class LocalProfileCache {
    private static final Logger logger = LoggerFactory.getLogger(LocalProfileCache.class);

    private final AtomicInteger uidGenerator;
    private final Cache<AggregatedProfileNamingStrategy, CacheableProfile> cache;
    private final Cache<String, Cacheable> viewCache;

    private RemovalListener<AggregatedProfileNamingStrategy, Future<AggregatedProfileInfo>> removalListener;

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
        this.uidGenerator = new AtomicInteger(0);
    }

    public void setRemovalListener(RemovalListener<AggregatedProfileNamingStrategy, Future<AggregatedProfileInfo>> removalListener) {
        this.removalListener = removalListener;
    }

    public Future<AggregatedProfileInfo> get(AggregatedProfileNamingStrategy key) {
        CacheableProfile cacheableProfile = cache.getIfPresent(key);
        return cacheableProfile != null ? cacheableProfile.profile : null;
    }

    public void put(AggregatedProfileNamingStrategy key, Future<AggregatedProfileInfo> profileFuture) {
        cache.put(key, new CacheableProfile(key, uidGenerator.incrementAndGet(), profileFuture));
    }

    public <T extends Cacheable> T getView(AggregatedProfileNamingStrategy profileName, String viewName) {
        String viewKey = getViewKey(profileName, viewName);
        return viewKey != null ? (T)viewCache.getIfPresent(viewKey) : null;
    }

    public <T extends Cacheable> void putView(AggregatedProfileNamingStrategy key, String viewName, T view) {
        // dont cache it if dependent profile is not there.
        CacheableProfile cacheableProfile = cache.getIfPresent(key);
        if(cacheableProfile == null) {
            return;
        }
        String viewKey = cacheableProfile.addAndGetViewKey(viewName);
        viewCache.put(viewKey, view);
    }

    public List<AggregatedProfileNamingStrategy> cachedProfiles() {
        return new ArrayList<>(cache.asMap().keySet());
    }

    private void doCleanupOnEviction(RemovalNotification<AggregatedProfileNamingStrategy, CacheableProfile> evt) {
        if(evt.wasEvicted()) {
            logger.info("Profile evicted. file: {}", evt.getKey());
        }

        viewCache.invalidateAll(evt.getValue().cachedViews);

        if(removalListener != null) {
            removalListener.onRemoval(RemovalNotification.create(evt.getKey(), evt.getValue().profile, evt.getCause()));
        }
    }

    private static class CacheableProfile implements Cacheable {
        int uid;
        AggregatedProfileNamingStrategy profileName;
        Future<AggregatedProfileInfo> profile;
        List<String> cachedViews;

        CacheableProfile(AggregatedProfileNamingStrategy profileName, int uid, Future<AggregatedProfileInfo> profile) {
            this.uid = uid;
            this.profileName = profileName;
            this.profile = profile;
            this.cachedViews = new ArrayList<>();
        }

        String addAndGetViewKey(String viewName) {
            String viewKey = toViewKey(profileName, viewName, uid);
            synchronized (this) {
                cachedViews.add(viewKey);
            }
            return viewKey;
        }

        @Override
        public int getUtilizationWeight() {
            if(profile.succeeded()) {
                return profile.result().getUtilizationWeight();
            }
            return 1;
        }
    }

    private String getViewKey(AggregatedProfileNamingStrategy profileName, String viewName) {
        CacheableProfile cacheableProfile = cache.getIfPresent(profileName);
        return cacheableProfile != null ? toViewKey(profileName, viewName, cacheableProfile.uid) : null;
    }

    private static String toViewKey(AggregatedProfileNamingStrategy profileName, String viewName, int uid) {
        return profileName.toString() + viewName + uid;
    }
}

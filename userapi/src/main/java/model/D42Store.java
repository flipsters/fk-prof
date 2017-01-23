package model;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.commons.codec.binary.Base32;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Connects with the D42 Simple Storage Service
 * Created by rohit.patiyal on 19/01/17.
 */
public class D42Store implements IDataStore {
    private static final String ACCESS_KEY = "66ZX9WC7ZRO6S5BSO8TG";
    private static final String SECRET_KEY = "fGEJrdiSWNJlsZTiIiTPpUntDm0wCRV4tYbwu2M+";
    private static final String S3_BACKUP_ELB_END_POINT = "http://10.47.2.3:80";
    private static final String BUCKET_NAME = "bck1";
    private static final String VERSION = "v001";
    private static final String DELIMITER = "_";
    private static AmazonS3 conn = null;
    private Base32 base32 = null;

    public D42Store() {
        initDataStore();
        base32 = new Base32();
    }

    private static String getWorkType(String key) {
        String[] splits = key.split("_");
        return splits[6];

    }

    private static Profile getProfile(String key) {
        String[] splits = key.split("_");
        String start = splits[4];
        return new Profile(start, ZonedDateTime.parse(start).plusSeconds(Long.parseLong(splits[5])).toString());
    }

    public void initDataStore() {
        conn = new AmazonS3Client(
                new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY),
                new ClientConfiguration().withProtocol(Protocol.HTTP));
        conn.setEndpoint(S3_BACKUP_ELB_END_POINT);
        conn.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
        conn.getS3AccountOwner().getId();
    }

    private String getLastFromCommonPrefix(String commonPrefix) {
        String[] splits = commonPrefix.split("_");
        return splits[splits.length - 1];
    }

    private List<String> getCommonPrefixes(String prefix) {
        List<String> commonPrefixes = new ArrayList<>();
        ObjectListing objects = conn.listObjects(new ListObjectsRequest()
                .withBucketName(BUCKET_NAME).withDelimiter(DELIMITER).withPrefix(prefix));
        do {
            for (String commonPrefix : objects.getCommonPrefixes()) {
                commonPrefixes.add(commonPrefix);
            }
        } while (objects.isTruncated());
        return commonPrefixes;
    }

    private Set<String> getAllWithPrefix(String prefix) {
        Set<String> allObjects = new HashSet<>();
        ObjectListing objects = conn.listObjects(new ListObjectsRequest()
                .withBucketName(BUCKET_NAME).withPrefix(prefix));
        do {
            for (S3ObjectSummary objSummary : objects.getObjectSummaries()) {
                allObjects.add(objSummary.getKey());
                System.out.println(objSummary.getKey());
            }
        } while (objects.isTruncated());
        return allObjects;
    }

    private Set<String> getListingAtLevelWithPrefix(String level, String objPrefix, boolean encoded) {
        List<String> commonPrefixes = getCommonPrefixes(level);
        Set<String> objs = new HashSet<>();
        for (String commonPrefix : commonPrefixes) {
            String objName = getLastFromCommonPrefix(commonPrefix);
            if (encoded) {
                objName = new String(base32.decode(objName.getBytes()));
            }
            if (objName.indexOf(objPrefix) == 0) {
                objs.add(objName);
            }
        }
        return objs;
    }

    @Override
    public Set<String> getAppIdsWithPrefix(String appIdPrefix) {
        return getListingAtLevelWithPrefix(VERSION + DELIMITER, appIdPrefix, true);

    }

    @Override
    public Set<String> getClusterIdsWithPrefix(String appId, String clusterIdPrefix) {
        return getListingAtLevelWithPrefix(
                VERSION + DELIMITER + new String(base32.encode(appId.getBytes())) + DELIMITER,
                clusterIdPrefix,
                true);
    }

    @Override
    public Set<String> getProcIdsWithPrefix(String appId, String clusterId, String procIdPrefix) {
        return getListingAtLevelWithPrefix(VERSION + DELIMITER + new String(base32.encode(appId.getBytes()))
                        + DELIMITER + new String(base32.encode(clusterId.getBytes())) + DELIMITER,
                procIdPrefix,
                false);
    }

    @Override
    public Set<Profile> getProfilesInTimeWindow(String appId, String clusterId, String proc, String startTime, String durationInSeconds) {
        String prefix = VERSION + DELIMITER + new String(base32.encode(appId.getBytes()))
                + DELIMITER + new String(base32.encode(clusterId.getBytes())) + DELIMITER + startTime;
        Map<Profile, Set<String>> profilesMap = getAllWithPrefix(prefix).stream().collect(Collectors.groupingBy(D42Store::getProfile, Collectors.mapping(D42Store::getWorkType, Collectors.toSet())));
        Set<Profile> profiles = profilesMap.entrySet().stream().map(profileSetEntry -> {
            profileSetEntry.getKey().setValues(profileSetEntry.getValue());
            return profileSetEntry.getKey();
        }).collect(Collectors.toSet());
        try {
            ZonedDateTime startTimeAsTime = ZonedDateTime.parse(startTime);
            Long durationInSecondsAsLong = Long.parseLong(durationInSeconds);
            profiles = profiles.stream().filter(k -> ZonedDateTime.parse(k.getEnd()).isAfter(startTimeAsTime.plusSeconds(durationInSecondsAsLong))).collect(Collectors.toSet());
        } catch (DateTimeParseException e) {
            System.err.println(e);
        }

        return profiles;
    }

}

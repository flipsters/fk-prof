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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Connects with the D42 Simple Storage Service
 * Created by rohit.patiyal on 19/01/17.
 */
public class D42Store {
    private static final String ACCESS_KEY = "66ZX9WC7ZRO6S5BSO8TG";
    private static final String SECRET_KEY = "fGEJrdiSWNJlsZTiIiTPpUntDm0wCRV4tYbwu2M+";
    private static final String S3_BACKUP_ELB_END_POINT = "http://10.47.2.3:80";
    private static final String BUCKET_NAME = "bck1";
    private static final String VERSION = "v001";
    private static final String DELIMITER = "_";
    private static AmazonS3 conn = null;
    private Base32 base32 = null;

    public D42Store() {
        conn = new AmazonS3Client(
                new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY),
                new ClientConfiguration().withProtocol(Protocol.HTTP));
        conn.setEndpoint(S3_BACKUP_ELB_END_POINT);
        conn.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
        conn.getS3AccountOwner().getId();
        base32 = new Base32();
    }

    private String getAppIdFromKey(String key) {
        return key.split("_")[1];
    }

    private String getLastFromCommonPrefix(String commonPrefix) {
        String[] splits = commonPrefix.split("_");
        return splits[splits.length - 1];
    }

    private String getClusterIdFromKey(String key) {
        return key.split("_")[2];
    }

    private List<String> getObjectNameWithPrefix(String prefix) {
        List<String> objectNames = new ArrayList<>();
        ObjectListing objects = conn.listObjects(new ListObjectsRequest()
                .withBucketName(BUCKET_NAME).withDelimiter(DELIMITER).withPrefix(prefix));
        do {
            for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
                System.out.println(objectSummary.getSize());
                objectNames.add(objectSummary.getKey());

            }
            objects = conn.listNextBatchOfObjects(objects);
        } while (objects.isTruncated());
        return objectNames;
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

    public Set<String> getAppIdsWithPrefix(String appIdPrefix) {
        String prefix = VERSION + DELIMITER;
        List<String> commonPrefixes = getCommonPrefixes(prefix);
        Set<String> appIds = new HashSet<>();
        for (String commonPrefix : commonPrefixes) {
            String decodedAppId = new String(base32.decode(getLastFromCommonPrefix(commonPrefix).getBytes()));
            if (decodedAppId.indexOf(appIdPrefix) == 0) {
                appIds.add(decodedAppId);
            }
        }
        return appIds;
    }


    public Set<String> getClusterIdsWithPrefix(String appId, String clusterIdPrefix) {
        String prefix = VERSION + DELIMITER + new String(base32.encode(appId.getBytes())) + DELIMITER;
        List<String> objectNames = getObjectNameWithPrefix(prefix);
        Set<String> clusterIds = new HashSet<>();
        System.err.println(objectNames.size() + "OBJ SIZE ");
        for (String objName : objectNames) {
            System.err.println(objName);
            String decodedClusterId = new String(base32.decode(getClusterIdFromKey(objName).getBytes()));
            if (decodedClusterId.indexOf(clusterIdPrefix) == 0) {
                clusterIds.add(decodedClusterId);
            }
        }
        return clusterIds;
    }

    public Set<String> getProcIdsWithPrefix(String appId, String clusterId, String procIdPrefix) {
        String prefix = VERSION + DELIMITER + new String(base32.encode(appId.getBytes()))
                + DELIMITER + new String(base32.encode(clusterId.getBytes()));
        List<String> objectNames = getObjectNameWithPrefix(prefix);
        Set<String> clusterIds = new HashSet<>();

        for (String objName : objectNames) {
//            String decodedProcId = new String(base32.decode(.getBytes()));
//            if(getProcIdFromKey(objName).indexOf(procIdPrefix) == 0){
//                clusterIds.add(decodedClusterId);
//            }
        }
        return clusterIds;
    }


}

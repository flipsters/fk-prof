package helper;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.*;
import model.Profile;
import org.apache.commons.codec.binary.Base32;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helps in generating, verifying and writing dummy data to D42Model
 * Created by rohit.patiyal on 19/01/17.
 */
public class D42StoreHelper {
    private static final String ACCESS_KEY = "66ZX9WC7ZRO6S5BSO8TG";
    private static final String SECRET_KEY = "fGEJrdiSWNJlsZTiIiTPpUntDm0wCRV4tYbwu2M+";
    private static final String S3_BACKUP_ELB_END_POINT = "http://10.47.2.3:80";
    private static final String DELIMITER = "_";
    private static final String BUCKET_NAME = "bck1";
    private static AmazonS3 conn = null;

    private static void createConn() {
        conn = new AmazonS3Client(
                new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY),
                new ClientConfiguration().withProtocol(Protocol.HTTP));
        conn.setEndpoint(S3_BACKUP_ELB_END_POINT);
        conn.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
        conn.getS3AccountOwner().getId();
    }

    private static void VerifyBase32() {
        Base32 base32 = new Base32();
        String FOOOOD = "fooood";
        String encoded = new String(base32.encode(FOOOOD.getBytes()));
        String decoded = new String(base32.decode(encoded));
        System.out.println("full : " + FOOOOD + "| encoded : " + encoded + "| decoded : " + decoded);
    }

    private static String generateS3Name(String appId, String cluster, String process, String workType,
                                         String startTime, int duration, int part) {
        Base32 base32 = new Base32();
        String encodedAppId = new String(base32.encode(appId.getBytes()));
        String encodedCluster = new String(base32.encode(cluster.getBytes()));
        return "v001" + DELIMITER +
                encodedAppId + DELIMITER +
                encodedCluster + DELIMITER +
                process + DELIMITER +
                startTime + DELIMITER +
                String.valueOf(duration) + DELIMITER +
                workType + DELIMITER +
                FourDigitZeroPaddedInt(part);
    }

    private static String FourDigitZeroPaddedInt(int part) {
        return String.format("%04d", part);

    }

    private static List<String> generateS3Names(String appId, String cluster, String process, String workType, int duration, int startpart, int endpart) {
        List<String> fileNames = new ArrayList<>();
        for (int i = startpart; i <= endpart; i++) {
            fileNames.add(generateS3Name(appId, cluster, process, workType, ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), duration, i));
        }
        return fileNames;
    }


    private static Bucket getWritableBucket(String bucketName) {
        List<Bucket> buckets = conn.listBuckets();
        for (Bucket b : buckets) {
            if (b.getName().equals(bucketName)) {
                System.out.println("Bucket exists already");
                return b;
            }
        }
        System.out.println("New Bucket will be created");
        return conn.createBucket(bucketName);
    }

    private static void writeObjInBucket(Bucket writableBucket, List<String> fileNames) {
        for (String fileName : fileNames) {
            String file = fileName;
            ObjectMetadata objMeta = new ObjectMetadata();
            objMeta.setContentLength(file.getBytes().length);
            conn.putObject(writableBucket.getName(), fileName, new ByteArrayInputStream(file.getBytes()), objMeta);
        }
    }

    private static void writeBigObjInBucket(Bucket writableBucket, List<String> fileNames) {
        for (String fileName : fileNames) {
            String file = getBigFile(fileNames.get(0));
            ObjectMetadata objMeta = new ObjectMetadata();
            objMeta.setContentLength(file.getBytes().length);
            conn.putObject(writableBucket.getName(), fileName, new ByteArrayInputStream(file.getBytes()), objMeta);
        }
    }

    private static void deleteObjInBucket(Bucket writableBucket, List<String> fileNames) {
        for (String fileName : fileNames) {
            conn.deleteObject(writableBucket.getName(), fileName);
        }
    }

    private static void readObjInBucket(Bucket writableBucket, List<String> fileNames) {
        for (String fileName : fileNames) {
            conn.getObject(new GetObjectRequest(writableBucket.getName(), fileName), new File("tmp/" + fileName));
            try (Stream<String> stream = Files.lines(Paths.get("tmp/fileName"))) {
                stream.forEach(System.out::println);
            } catch (IOException e) {
                System.err.println(e);
            }
        }

    }

    private static String getBigFile(String s) {
        String bigS = s;
        for (int i = 0; i < 20; i++) {
            bigS += bigS;
        }
        return bigS;
    }

    private static void showBucketsWithObjects(String prefix) {
        for (Bucket bucket : conn.listBuckets()) {
            System.out.println("Bucket: " + bucket.getName()
                    + " | CreationDate: " + bucket.getCreationDate()
                    + " | Owner: " + bucket.getOwner());
            //  ObjectListing objects = conn.listObjects(bucket.getName());
            ObjectListing objects = conn.listObjects(new ListObjectsRequest().withBucketName(bucket.getName()).withPrefix(prefix).withDelimiter(DELIMITER));
            System.err.println(objects.getMarker());
            System.err.println(objects.getDelimiter());
            System.err.println(objects.getMaxKeys());
            System.err.println(objects.getPrefix());
            System.err.println(objects.getObjectSummaries());
            System.err.println(objects.getNextMarker());
            System.err.println(objects.getCommonPrefixes());
            do {
                for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
                    System.out.println(" `-----Key: " + objectSummary.getKey()
                            + " | Size: " + objectSummary.getSize()
                            + " | LastModified: " + objectSummary.getLastModified());
                }
                objects = conn.listNextBatchOfObjects(objects);
            } while (objects.isTruncated());
        }
    }

    private static void deleteBucketWithObjects(Bucket writableBucket, String prefix) {
        ObjectListing objects = conn.listObjects(writableBucket.getName());
        do {
            for (S3ObjectSummary objectSummary : objects.getObjectSummaries()) {
                conn.deleteObject(writableBucket.getName(), objectSummary.getKey());
            }
            objects = conn.listNextBatchOfObjects(objects);
        } while (objects.isTruncated());
    }


    public static void main(String args[]) {
        createConn();
        if (conn != null) {
            // VerifyBase32();
            // VerifyObjReading();
            // VerifyObjCleanup();
            //VerifyBucketCleanup();
            showBucketsWithObjects("v001_MZXW6===_MJQXE===_");
            // VerifyObjWriting();
            //VerifyFileNameSplit();
            //  groupByTimeWindow();
            //       getProfiles();
        }
    }

    private static Profile getProfile(String key) {
        String[] splits = key.split("_");
        String start = splits[4];
//         new TimeInterval(start, ZonedDateTime.parse(start).plusSeconds(Long.parseLong(splits[5])).toString());
        return new Profile(start, ZonedDateTime.parse(start).plusSeconds(Long.parseLong(splits[5])).toString());
    }

    private static void getProfiles() {
        String prefix = "v001_MZXW6===_MJQXE";
        Set<String> allObjects = getAllWithPrefix(prefix);
        Map<Profile, Set<String>> profilesMap = allObjects.stream().collect(Collectors.groupingBy(D42StoreHelper::getProfile, Collectors.mapping(D42StoreHelper::getWorkType, Collectors.toSet())));
        Set<Profile> profiles = profilesMap.entrySet().stream().map(profileSetEntry -> {
            profileSetEntry.getKey().setValues(profileSetEntry.getValue());
            return profileSetEntry.getKey();
        }).collect(Collectors.toSet());
        for (Profile p : profiles) {
            System.err.println(p.getStart() + ":" + p.getEnd() + " --- " + p.getValues().size());
        }
    }

    private static void VerifyFileNameSplit() {
        String s3Name = generateS3Names("foo", "bar", "main", "cpusamples", 1800, 0, 1).get(0);
        String[] sArray = s3Name.split("_");
        System.out.println(sArray[0]);
    }

    private static void VerifyObjCleanup() {
        showBucketsWithObjects("prefix");
        List<String> objects = new ArrayList<>();
        objects.add("v001_MFYHAMI=_MNWHK43UMVZDC===_process1_2017-01-20T11:22:17.204+05:30_1200_worktype1_0000");
        deleteObjInBucket(getWritableBucket("bck1"), objects);
        showBucketsWithObjects("prefix");
    }

    private static void VerifyBucketCleanup() {
        showBucketsWithObjects("prefix");
        //conn.deleteObject(getWritableBucket("bck3").getName(),"*");
        //conn.deleteBucket(getWritableBucket("bck3").getName());
        //conn.createBucket("bck3");
        deleteBucketWithObjects(getWritableBucket("bck1"), "prefix");
        //deleteBucketWithObjects(getWritableBucket("bck4"), "prefix");

        conn.deleteBucket(getWritableBucket("bck1").getName());
        //conn.deleteBucket(getWritableBucket("bck4").getName());

        showBucketsWithObjects("prefix");
    }

    private static Set<String> getAllWithPrefix(String prefix) {
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

    private static void groupByTimeWindow() {
        Set<String> allObjects = getAllWithPrefix("v001_MZXW6===_MJQXE===_main_2017-01-20T12:37:20");
        Map<TimeInterval, Set<String>> mp = allObjects.stream().collect(Collectors.groupingBy(D42StoreHelper::getTimeWindow, Collectors.mapping(D42StoreHelper::getWorkType, Collectors.toSet())));
        for (Map.Entry<TimeInterval, Set<String>> e : mp.entrySet()) {
            System.out.println(e.getKey().getStart() + ":" + e.getKey().getEnd() + " :: " + e.getValue());
        }
    }

    private static TimeInterval getTimeWindow(String key) {
        String[] splits = key.split("_");
        String start = splits[4];
        return TimeInterval.getTimeInterval(start, ZonedDateTime.parse(start).plusSeconds(Long.parseLong(splits[5])).toString());
    }

    private static String getWorkType(String key) {
        String[] splits = key.split("_");
        return splits[6];

    }

    private static void VerifyObjReading() {
        List<String> fileNames = new ArrayList<>();
        fileNames.add("test1.txt");
        readObjInBucket(getWritableBucket("bck1"), fileNames);
    }

    private static void VerifyObjWriting() {
        //showBucketsWithObjects("prefix");
        List<String> objects = new ArrayList<>();
        //       objects.add("v001_MFYHAMI=_MNWHK43UMVZDC===_process1_2017-01-20T11:23:36.877+05:30_1200_worktype1_0000");
        objects.add("v001_MZXW6===_MJQXE===_main_2017-01-20T12:37:20.551+05:30_1500_iosamples_0001");
        //writeObjInBucket(getWritableBucket("bck1"),objects);
        //writeObjInBucket(getWritableBucket("bck1"), generateS3Names("foo", "bar", "main", "cpusamples", 1800, 0, 1));
        //writeObjInBucket(getWritableBucket("bck2"), generateS3Names("foo", "bar", "main", "cpusamples", 1800, 2, 4));
        //writeObjInBucket(getWritableBucket("bck4"),generateS3Names("app1","cluster1","process1","worktype1", 1200, 1));
        //writeObjInBucket(getWritableBucket("bck1"), generateS3Names("app1", "cluster1", "process1", "worktype1", 1200, 0, 1));
        writeObjInBucket(getWritableBucket("bck1"), objects);

        // showBucketsWithObjects("prefix");
    }

    private static class TimeInterval {
        private String start;
        private String end;

        TimeInterval(String start, String end) {
            this.start = start;
            this.end = end;

        }

        static TimeInterval getTimeInterval(String start, String end) {

            return new TimeInterval(start, end);
        }

        public String getStart() {
            return start;
        }

        public String getEnd() {
            return end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TimeInterval that = (TimeInterval) o;

            if (start != null ? !start.equals(that.start) : that.start != null) return false;
            return end != null ? end.equals(that.end) : that.end == null;
        }

        @Override
        public int hashCode() {
            int result = start != null ? start.hashCode() : 0;
            result = 31 * result + (end != null ? end.hashCode() : 0);
            return result;
        }
    }
}

package fk.prof.userapi.api;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.CodedOutputStream;
import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.aggregation.model.*;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.aggregation.state.AggregationState;
import io.vertx.core.Future;

import java.io.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/**
 * @author gaurav.ashok
 */
public class MockAggregatedProfile {

    int sampleCounts = 0;

    public void uploadFile() throws Exception {

        String startime = "2017-03-01T07:00:00";
        FinalizedAggregationWindow window = buildAggregationWindow(startime, "/Users/gaurav.ashok/Documents/fk-profiler/methodids_mock");
        ZonedDateTime startimeZ = ZonedDateTime.parse(startime + "Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);

        ByteArrayOutputStream boutWS = new ByteArrayOutputStream();
        GZIPOutputStream zoutWS = new GZIPOutputStream(boutWS);
        ByteArrayOutputStream boutSummary = new ByteArrayOutputStream();
        GZIPOutputStream zoutSummary = new GZIPOutputStream(boutSummary);

        AggregationWindowSerializer windowsSer = new AggregationWindowSerializer(window, AggregatedProfileModel.WorkType.cpu_sample_work);
        AggregationWindowSummarySerializer windowSummarySer = new AggregationWindowSummarySerializer(window);

        windowsSer.serialize(zoutWS);
        windowSummarySer.serialize(zoutSummary);

        //flush
        zoutWS.close();
        zoutSummary.close();

        System.out.println("cpusamaple size: " + boutWS.size());
        System.out.println("summary size: " + boutSummary.size());

        // check for validity
        AggregatedProfileLoader loader = new AggregatedProfileLoader(null);
        Future f1 =  Future.future();
        AggregatedProfileNamingStrategy file1 = new AggregatedProfileNamingStrategy("profiles", 1, "app1", "cluster1", "proc1", startimeZ, 1800, AggregatedProfileModel.WorkType.cpu_sample_work);
        loader.loadFromInputStream(f1, file1, new GZIPInputStream(new ByteArrayInputStream(boutWS.toByteArray())));
        assert f1.succeeded();

        Future f2 =  Future.future();
        AggregatedProfileNamingStrategy file2 = new AggregatedProfileNamingStrategy("profiles", 1, "app1", "cluster1", "proc1", startimeZ, 1800);
        loader.loadSummaryFromInputStream(f2, file2, new GZIPInputStream(new ByteArrayInputStream(boutSummary.toByteArray())));
        assert f2.succeeded();

        // write to s3
        // TODO remove the credentials from here.
        String accessKey = "66ZX9WC7ZRO6S5BSO8TG";
        String secretKey = "fGEJrdiSWNJlsZTiIiTPpUntDm0wCRV4tYbwu2M+";
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setProtocol(Protocol.HTTP);

        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        AmazonS3Client conn = new AmazonS3Client(credentials,clientConfig);
        conn.setEndpoint("http://10.47.2.3:80");
        conn.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));

        // make sure bucket exit
        List<Bucket> buckets = conn.listBuckets();
        assert buckets.size() > 0;
        Optional<Bucket> profilesBucket = buckets.stream().filter(b -> "profiles".equals(b.getName())).findFirst();
        if(!profilesBucket.isPresent()) {
            profilesBucket = Optional.of(conn.createBucket("profiles"));
        }

        ObjectListing listing = conn.listObjects(new ListObjectsRequest().withBucketName("profiles"));

        System.out.println("Existing files: ");
        listing.getObjectSummaries().stream().forEach(e -> System.out.println(e.getKey()));

        writeToS3(conn, file1, profilesBucket.get(), boutWS.toByteArray());
        writeToS3(conn, file2, profilesBucket.get(), boutSummary.toByteArray());

        conn.shutdown();
    }

    private void writeToS3(AmazonS3Client conn, AggregatedProfileNamingStrategy filename, Bucket profilesBucket, byte[] bytes) {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(bytes.length);
        String name = filename.getFileName(0);
        name = name.substring(name.indexOf('/') + 1);

        System.out.println("writing to : " + name);
        PutObjectResult putResult = conn.putObject(profilesBucket.getName(), name, new ByteArrayInputStream(bytes), meta);

        System.out.println("len: " + bytes.length + ", md5: " + putResult.getContentMd5());
    }

    private FinalizedAggregationWindow buildAggregationWindow(String time, String stacktracesPath) throws Exception {

        LocalDateTime lt = LocalDateTime.parse(time, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        FinalizedCpuSamplingAggregationBucket cpuSampleBucket = buildTree(stacktracesPath);

        int sampleCount1 = this.sampleCounts / 2;
        int sampleCount2 = this.sampleCounts - sampleCount1;

        FinalizedAggregationWindow window = new FinalizedAggregationWindow("app1", "cluster1", "proc1", lt, lt.plusMinutes(30),
                buildRecordersList().getRecordersList(), buildProfilesWorkInfo(lt ,sampleCount1, sampleCount2), cpuSampleBucket);

        return window;
    }

    private FinalizedCpuSamplingAggregationBucket buildTree(String methodIDpath) throws IOException {
        CpuSamplingTraceDetail traceDetail = new CpuSamplingTraceDetail();

        MethodIdLookup lookup = new MethodIdLookup();
        CpuSamplingFrameNode global = traceDetail.getGlobalRoot();

        FileInputStream fis = new FileInputStream(methodIDpath);

        List<List<String>> stackTraces = new ObjectMapper().readValue(fis, List.class);

        Random random = new Random();

        int frameCounts = 0;
        int stackTracesCount = 0;
        for(List<String> st : stackTraces) {
            stackTracesCount++;
            global.incrementOnStackSamples();
            traceDetail.incrementSamples();

            CpuSamplingFrameNode node = global;

            for(String method : st) {
                int methodid = lookup.getOrAdd(method);
                node = node.getOrAddChild(methodid, random.nextInt(5));
                node.incrementOnStackSamples();
                frameCounts++;
            }
            node.incrementOnCpuSamples();
        }

        // write methodid lookup
        String[] methodNames = lookup.generateReverseLookup();

        System.out.println("frame counts: " + frameCounts);
        System.out.println("stacktrace counts: " + stackTracesCount);

        String methodNamesJson = new ObjectMapper().writeValueAsString(Arrays.asList(methodNames));
        PrintWriter pw = new PrintWriter(methodIDpath + "_deduped");
        pw.write(methodNamesJson);
        pw.close();

        this.sampleCounts = stackTracesCount;

        return new FinalizedCpuSamplingAggregationBucket(lookup, buildMap("full-app-trace", traceDetail));
    }

    private List<AggregatedProfileModel.FrameNodeList> buildFrameNodes(String path) throws Exception {
        FileInputStream fis = new FileInputStream(path);
        List<AggregatedProfileModel.FrameNodeList> list = new ArrayList<>();

        int count = 0;

        while(fis.available() != -1) {
            AggregatedProfileModel.FrameNodeList nodelist = AggregatedProfileModel.FrameNodeList.parseDelimitedFrom(fis);
            if(nodelist == null) {
                break;
            }
            count +=  nodelist.getFrameNodesCount();
            list.add(nodelist);
        }

        fis.close();

        System.out.println(list.size());
        System.out.println(count);

        return list;
    }

    public static AggregatedProfileModel.RecorderList buildRecordersList() {
        return AggregatedProfileModel.RecorderList.newBuilder()
                .addRecorders(
                        AggregatedProfileModel.RecorderInfo.newBuilder()
                                .setIp("192.168.1.1")
                                .setHostname("some-box-1")
                                .setAppId("app1")
                                .setInstanceGroup("ig1")
                                .setCluster("cluster1")
                                .setInstanceId("instance1")
                                .setProcessName("svc1")
                                .setVmId("vm1")
                                .setZone("chennai-1")
                                .setInstanceType("c1.xlarge"))
                .addRecorders(
                        AggregatedProfileModel.RecorderInfo.newBuilder()
                                .setIp("192.168.1.2")
                                .setHostname("some-box-2")
                                .setAppId("app1")
                                .setInstanceGroup("ig1")
                                .setCluster("cluster1")
                                .setInstanceId("instance2")
                                .setProcessName("svc1")
                                .setVmId("vm2")
                                .setZone("chennai-1")
                                .setInstanceType("c1.xlarge")).build();
    }

    private Map<Long, FinalizedProfileWorkInfo> buildProfilesWorkInfo(LocalDateTime aggregationStart, int count1, int count2) {

        FinalizedProfileWorkInfo wi = new FinalizedProfileWorkInfo(1, 0, AggregationState.COMPLETED, aggregationStart.plusSeconds(10), aggregationStart.plusSeconds(10 + 60),
                buildMap("full-app-trace", 5),
                buildMap(AggregatedProfileModel.WorkType.cpu_sample_work, count1)
                );

        FinalizedProfileWorkInfo wi2 = new FinalizedProfileWorkInfo(1, 1, AggregationState.RETRIED, aggregationStart.plusSeconds(24), aggregationStart.plusSeconds(24 + 60),
                buildMap("full-app-trace", 10),
                buildMap(AggregatedProfileModel.WorkType.cpu_sample_work, count2)
        );

        return buildMap(101l, wi, 102l, wi2);
    }

    // A temp byteBuffer is reused everytime to write a abstractMessage. This avoids the creation of temp buffer everytime
    // you want to write the message but its content is copied to the outputStream.
    public void testCodedOutputStream_firstWriteToByteBufferThenCopyToOutputStream() throws Exception {
        ByteBuffer b = ByteBuffer.allocate(1000);

        ByteArrayOutputStream bos = new ByteArrayOutputStream(20_000_000);
        CodedOutputStream finalout = CodedOutputStream.newInstance(bos);

        long milli = System.currentTimeMillis();
        int count = 0;
        int limit = 1_000_000;

        for(count = 0; count < limit; count++) {
            AggregatedProfileModel.Header h = getHeader();

            CodedOutputStream cos = CodedOutputStream.newInstance(b);
            h.writeTo(cos);
            cos.flush();

            int size = b.position();
            b.flip();

            finalout.writeUInt32NoTag(size);
            finalout.write(b);
            finalout.flush(); //flush to array outstream

            b.clear();

            if(bos.size() > 10_000_000) {
                bos.reset();
            }
        }

        long milli2 = System.currentTimeMillis();

        System.out.println("Time took: " + (milli2 - milli));
    }

    // here message is directly written to outputStream but a temp buffer is always created in the process.
    public void testCodedOutputStream_directlyWriteToOutputStreamWUsingWriteDelimited() throws Exception {

        ByteArrayOutputStream bos = new ByteArrayOutputStream(20_000_000);

        long milli = System.currentTimeMillis();
        int count = 0;
        int limit = 1_000_000;

        for(count = 0; count < limit; count++) {
            AggregatedProfileModel.Header h = getHeader();

            h.writeDelimitedTo(bos);

            if(bos.size() > 10_000_000) {
                bos.reset();
            }
        }

        long milli2 = System.currentTimeMillis();

        System.out.println("Time took: " + (milli2 - milli));
    }

    private AggregatedProfileModel.Header getHeader() {
        ZonedDateTime now = ZonedDateTime.parse("2017-02-07T07:30:10Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
        return AggregatedProfileModel.Header.newBuilder().setAppId("app1")
                .setClusterId("cluster1")
                .setProcId("proc1")
                .setAggregationStartTime(now.toString())
                .setAggregationEndTime(now.plusMinutes(30).toString())
                .setFormatVersion(1)
                .setWorkType(AggregatedProfileModel.WorkType.cpu_sample_work)
                .build();
    }

    private <K,V> Map<K, V> buildMap(Object... objects) {
        assert objects.length % 2 == 0;

        Map<K, V> map = new HashMap<>();
        for(int i = 0;i < objects.length; i += 2) {
            map.put((K)objects[i], (V)objects[i+1]);
        }

        return map;
    }
}

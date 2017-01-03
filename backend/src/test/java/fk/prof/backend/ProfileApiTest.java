package fk.prof.backend;

import com.google.protobuf.CodedOutputStream;
import fk.prof.backend.aggregator.AggregationStatus;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.aggregator.CpuSamplingAggregationBucket;
import fk.prof.backend.aggregator.ProfileWorkInfo;
import fk.prof.backend.http.MainVerticle;
import fk.prof.backend.mock.MockProfileComponents;
import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.service.ProfileWorkService;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingTraceDetail;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingFrameNode;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import recording.Recorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

@RunWith(VertxUnitRunner.class)
public class ProfileApiTest {

  private Vertx vertx;
  private Integer port = 9300;
  private IProfileWorkService profileWorkService;
  private AtomicLong workIdCounter = new AtomicLong(0);

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    profileWorkService = new ProfileWorkService();
    //Deploying two verticles with shared profileworkservice instance
    Verticle mainVerticle1 = new MainVerticle(profileWorkService);
    Verticle mainVerticle2 = new MainVerticle(profileWorkService);
    DeploymentOptions deploymentOptions = new DeploymentOptions();
    vertx.deployVerticle(mainVerticle1, deploymentOptions, context.asyncAssertSuccess());
    vertx.deployVerticle(mainVerticle2, deploymentOptions, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testWithValidSingleProfile(TestContext context) {
    long workId = workIdCounter.incrementAndGet();
    profileWorkService.associateAggregationWindow(workId,
        new AggregationWindow("a", "c", "p", LocalDateTime.now(), 20, 60, new long[]{workId}));

    final Async async = context.async();
    Future<Buffer> future = makeProfileRequest(context, MockProfileComponents.getRecordingHeader(workId), getMockWseEntriesForSingleProfile());
    future.setHandler(ar -> {
      if (ar.failed()) {
        context.fail(ar.cause());
      } else {
        //Validate aggregation
        AggregationWindow aggregationWindow = profileWorkService.getAssociatedAggregationWindow(workId);
        validateAggregationBucketOfPredefinedSamples(context, aggregationWindow);

        ProfileWorkInfo wi = aggregationWindow.getWorkInfo(workId);
        validateWorkInfo(context, wi, 3);

        async.complete();
      }
    });
  }

  @Test
  public void testWithValidMultipleProfiles(TestContext context) {
    long workId1 = workIdCounter.incrementAndGet();
    long workId2 = workIdCounter.incrementAndGet();
    long workId3 = workIdCounter.incrementAndGet();
    AggregationWindow aw = new AggregationWindow("a", "c", "p", LocalDateTime.now(), 20, 60, new long[]{workId1, workId2, workId3});
    profileWorkService.associateAggregationWindow(workId1, aw);
    profileWorkService.associateAggregationWindow(workId2, aw);
    profileWorkService.associateAggregationWindow(workId3, aw);
    List<Recorder.Wse> wseList = getMockWseEntriesForMultipleProfiles();

    final Async async = context.async();
    Future<Buffer> f1 = makeProfileRequest(context, MockProfileComponents.getRecordingHeader(workId1), Arrays.asList(wseList.get(0)));
    Future<Buffer> f2 = makeProfileRequest(context, MockProfileComponents.getRecordingHeader(workId2), Arrays.asList(wseList.get(1)));
    Future<Buffer> f3 = makeProfileRequest(context, MockProfileComponents.getRecordingHeader(workId3), Arrays.asList(wseList.get(2)));
    CompositeFuture.all(Arrays.asList(f1, f2, f3)).setHandler(ar -> {
      if (ar.failed()) {
        context.fail(ar.cause());
      } else {
        AggregationWindow aggregationWindow = profileWorkService.getAssociatedAggregationWindow(workId1);
        validateAggregationBucketOfPredefinedSamples(context, aggregationWindow);

        ProfileWorkInfo wi1 = aggregationWindow.getWorkInfo(workId1);
        validateWorkInfo(context, wi1, 1);
        ProfileWorkInfo wi2 = aggregationWindow.getWorkInfo(workId2);
        validateWorkInfo(context, wi2, 1);
        ProfileWorkInfo wi3 = aggregationWindow.getWorkInfo(workId3);
        validateWorkInfo(context, wi3, 1);

        async.complete();
      }
    });

  }

  @Test
  public void testWithInvalidHeaderLength(TestContext context) {
    makeInvalidHeaderProfileRequest(context, HeaderPayloadStrategy.INVALID_HEADER_LENGTH, "allowed range for recording header length");
  }

  @Test
  public void testWithInvalidRecordingHeader(TestContext context) {
    makeInvalidHeaderProfileRequest(context, HeaderPayloadStrategy.INVALID_RECORDING_HEADER, "error while parsing recording header");
  }

  @Test
  public void testWithInvalidHeaderChecksum(TestContext context) {
    makeInvalidHeaderProfileRequest(context, HeaderPayloadStrategy.INVALID_CHECKSUM, "checksum of header does not match");
  }

  @Test
  public void testWithInvalidWorkId(TestContext context) {
    makeInvalidHeaderProfileRequest(context, HeaderPayloadStrategy.INVALID_WORK_ID, "not found, cannot continue receiving");
  }

  @Test
  public void testWithInvalidWseLength(TestContext context) {
    makeInvalidWseProfileRequest(context, WsePayloadStrategy.INVALID_WSE_LENGTH, "allowed range for work-specific entry log");
  }

  @Test
  public void testWithInvalidWse(TestContext context) {
    makeInvalidWseProfileRequest(context, WsePayloadStrategy.INVALID_WSE, "error while parsing work-specific entry log");
  }

  @Test
  public void testWithInvalidWseChecksum(TestContext context) {
    makeInvalidWseProfileRequest(context, WsePayloadStrategy.INVALID_CHECKSUM, "checksum of work-specific entry log does not match");
  }

  private Future<Buffer> makeProfileRequest(TestContext context, Recorder.RecordingHeader recordingHeader, List<Recorder.Wse> wseList) {
    Future<Buffer> future = Future.future();
    vertx.executeBlocking(blockingFuture -> {
      try {
        HttpClientRequest request = vertx.createHttpClient()
            .post(port, "localhost", "/profile")
            .handler(response -> {
              response.bodyHandler(buffer -> {
                //If any error happens, returned in formatted json, printing for debugging purposes
                System.out.println(buffer.toString());
                context.assertEquals(response.statusCode(), 200);
                blockingFuture.complete(buffer);
              });
            })
//            .exceptionHandler(throwable -> blockingFuture.fail(throwable))
            .setChunked(true);

        ByteArrayOutputStream requestStream = new ByteArrayOutputStream();
        writeMockHeaderToRequest(recordingHeader, requestStream);
        writeMockWseEntriesToRequest(wseList, requestStream);
        byte[] requestBytes = requestStream.toByteArray();
        chunkAndWriteToRequest(request, requestBytes, 32);
      } catch (IOException ex) {
        blockingFuture.fail(ex);
      }
    }, false, future.completer());

    return future;
  }

  private void makeInvalidHeaderProfileRequest(TestContext context, HeaderPayloadStrategy payloadStrategy, String errorToGrep) {
    long workId = workIdCounter.incrementAndGet();
    if(!payloadStrategy.equals(HeaderPayloadStrategy.INVALID_WORK_ID)) {
      profileWorkService.associateAggregationWindow(workId,
          new AggregationWindow("a", "c", "p", LocalDateTime.now(), 20, 60, new long[]{workId}));
    }

    final Async async = context.async();
    try {
      HttpClientRequest request = vertx.createHttpClient()
          .post(port, "localhost", "/profile")
          .handler(response -> {
            response.bodyHandler(buffer -> {
              //If any error happens, returned in formatted json, printing for debugging purposes
              System.out.println(buffer.toString());
              context.assertEquals(response.statusCode(), 400);
              context.assertTrue(buffer.toString().toLowerCase().contains(errorToGrep));
              async.complete();
            });
          })
          .exceptionHandler(throwable -> context.fail(throwable))
          .setChunked(true);

      ByteArrayOutputStream requestStream = new ByteArrayOutputStream();
      writeMockHeaderToRequest(MockProfileComponents.getRecordingHeader(workId), requestStream, payloadStrategy);
      byte[] requestBytes = requestStream.toByteArray();
      chunkAndWriteToRequest(request, requestBytes, 32);
    } catch (IOException ex) {
      context.fail(ex);
    }
  }

  private void makeInvalidWseProfileRequest(TestContext context, WsePayloadStrategy payloadStrategy, String errorToGrep) {
    long workId = workIdCounter.incrementAndGet();
    profileWorkService.associateAggregationWindow(workId,
        new AggregationWindow("a", "c", "p", LocalDateTime.now(), 20, 60, new long[]{workId}));

    final Async async = context.async();
    try {
      HttpClientRequest request = vertx.createHttpClient()
          .post(port, "localhost", "/profile")
          .handler(response -> {
            response.bodyHandler(buffer -> {
              //If any error happens, returned in formatted json, printing for debugging purposes
              System.out.println(buffer.toString());
              context.assertEquals(response.statusCode(), 400);
              context.assertTrue(buffer.toString().toLowerCase().contains(errorToGrep));
              async.complete();
            });
          })
          .exceptionHandler(throwable -> context.fail(throwable))
          .setChunked(true);

      ByteArrayOutputStream requestStream = new ByteArrayOutputStream();
      writeMockHeaderToRequest(MockProfileComponents.getRecordingHeader(workId), requestStream);
      writeMockWseEntriesToRequest(getMockWseEntriesForSingleProfile(), requestStream, payloadStrategy);
      byte[] requestBytes = requestStream.toByteArray();
      chunkAndWriteToRequest(request, requestBytes, 32);
    } catch (IOException ex) {
      context.fail(ex);
    }
  }

  private void validateAggregationBucketOfPredefinedSamples(TestContext context, AggregationWindow aggregationWindow) {
    context.assertNotNull(aggregationWindow);
    context.assertTrue(aggregationWindow.hasProfileData(Recorder.WorkType.cpu_sample_work));

    CpuSamplingAggregationBucket aggregationBucket = aggregationWindow.getCpuSamplingAggregationBucket();
    context.assertEquals(aggregationBucket.getAvailableTraces(), new HashSet<>(Arrays.asList("1")));
    Map<Long, String> methodNameLookup = aggregationBucket.getMethodNameLookup();
    //Subtracting method count by 2, because ROOT, UNCLASSIFIABLE are static names always present in the map
    context.assertEquals(methodNameLookup.size() - 2, 5);
    CpuSamplingTraceDetail traceDetail = aggregationBucket.getTraceDetail("1");
    context.assertTrue(traceDetail.getUnclassifiableRoot().getChildrenUnsafe().size() == 1);

    CpuSamplingFrameNode logicalRoot = traceDetail.getUnclassifiableRoot().getChildrenUnsafe().get(0);
    context.assertEquals(methodNameLookup.get(logicalRoot.getMethodId()), ".Y()");
    context.assertEquals(logicalRoot.getOnStackSamples(), 3);
    context.assertEquals(logicalRoot.getOnCpuSamples(), 0);

    List<CpuSamplingFrameNode> leaves = new ArrayList<>();
    Queue<CpuSamplingFrameNode> toVisit = new ArrayDeque<>();
    toVisit.add(logicalRoot);
    while (toVisit.size() > 0) {
      CpuSamplingFrameNode visiting = toVisit.poll();
      List<CpuSamplingFrameNode> children = visiting.getChildrenUnsafe();
      if (children.size() == 0) {
        leaves.add(visiting);
      } else {
        for (CpuSamplingFrameNode child : children) {
          toVisit.offer(child);
        }
      }
    }
    context.assertEquals(leaves.size(), 3);

    int dMethodLeaves = 0, cMethodLeaves = 0;
    for (CpuSamplingFrameNode leaf : leaves) {
      context.assertEquals(leaf.getOnStackSamples(), 1);
      context.assertEquals(leaf.getOnCpuSamples(), 1);
      if (methodNameLookup.get(leaf.getMethodId()).equals(".C()")) {
        cMethodLeaves++;
      } else if (methodNameLookup.get(leaf.getMethodId()).equals(".D()")) {
        dMethodLeaves++;
      }
    }
    context.assertEquals(cMethodLeaves, 1);
    context.assertEquals(dMethodLeaves, 2);
  }

  private void validateWorkInfo(TestContext context, ProfileWorkInfo workInfo, int expectedSamples) {
    context.assertEquals(workInfo.getStatus(), AggregationStatus.COMPLETED);
    context.assertNotNull(workInfo.getStartedAt());
    context.assertNotNull(workInfo.getEndedAt());
    context.assertEquals(workInfo.getTraceCoverages().size(), 1);
    context.assertEquals(workInfo.getTraceCoverages().get("1"), 5);
    context.assertEquals(workInfo.getSamples(), expectedSamples);
  }

  private static void chunkAndWriteToRequest(HttpClientRequest request, byte[] requestBytes, int chunkSizeInBytes) {
    int i = 0;
    for (; (i + chunkSizeInBytes) <= requestBytes.length; i += chunkSizeInBytes) {
      writeChunkToRequest(request, requestBytes, i, i + chunkSizeInBytes);
    }
    writeChunkToRequest(request, requestBytes, i, requestBytes.length);

    request.end();
  }

  private static void writeChunkToRequest(HttpClientRequest request, byte[] bytes, int start, int end) {
    request.write(Buffer.buffer(Arrays.copyOfRange(bytes, start, end)));
    try {
      Thread.sleep(10);
    } catch (Exception ex) {
    }
  }

  private static void writeMockHeaderToRequest(Recorder.RecordingHeader recordingHeader, ByteArrayOutputStream requestStream) throws IOException {
    writeMockHeaderToRequest(recordingHeader, requestStream, HeaderPayloadStrategy.VALID);
  }

  private static void writeMockHeaderToRequest(Recorder.RecordingHeader recordingHeader, ByteArrayOutputStream requestStream, HeaderPayloadStrategy payloadStrategy) throws IOException {
    int encodedVersion = 1;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);

    byte[] recordingHeaderBytes = recordingHeader.toByteArray();
    codedOutputStream.writeUInt32NoTag(encodedVersion);

    if(payloadStrategy.equals(HeaderPayloadStrategy.INVALID_HEADER_LENGTH)) {
      codedOutputStream.writeUInt32NoTag(Integer.MAX_VALUE);
    } else {
      codedOutputStream.writeUInt32NoTag(recordingHeaderBytes.length);
    }

    if(payloadStrategy.equals(HeaderPayloadStrategy.INVALID_RECORDING_HEADER)) {
      byte[] invalidArr = Arrays.copyOfRange(recordingHeaderBytes, 0, recordingHeaderBytes.length);
      invalidArr[0] = invalidArr[1] = invalidArr[3] = 0;
      invalidArr[invalidArr.length - 1] = invalidArr[invalidArr.length - 2] = invalidArr[invalidArr.length - 3] = 0;
      codedOutputStream.writeByteArrayNoTag(invalidArr);
    } else {
      recordingHeader.writeTo(codedOutputStream);
    }
    codedOutputStream.flush();
    byte[] bytesWritten = outputStream.toByteArray();

    Checksum recordingHeaderChecksum = new Adler32();
    recordingHeaderChecksum.update(bytesWritten, 0, bytesWritten.length);
    long checksumValue = payloadStrategy.equals(HeaderPayloadStrategy.INVALID_CHECKSUM) ? 0 : recordingHeaderChecksum.getValue();
    codedOutputStream.writeUInt32NoTag((int) checksumValue);
    codedOutputStream.flush();
    outputStream.writeTo(requestStream);
  }

  private static void writeMockWseEntriesToRequest(List<Recorder.Wse> wseList, ByteArrayOutputStream requestStream) throws IOException {
    writeMockWseEntriesToRequest(wseList, requestStream, WsePayloadStrategy.VALID);
  }

  private static void writeMockWseEntriesToRequest(List<Recorder.Wse> wseList, ByteArrayOutputStream requestStream, WsePayloadStrategy payloadStrategy) throws IOException {
    if(wseList != null) {
      for (Recorder.Wse wse : wseList) {
        writeWseToRequest(wse, requestStream, payloadStrategy);
      }
    }
  }

  private static void writeWseToRequest(Recorder.Wse wse, ByteArrayOutputStream requestStream, WsePayloadStrategy payloadStrategy) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
    byte[] wseBytes = wse.toByteArray();

    if(payloadStrategy.equals(WsePayloadStrategy.INVALID_WSE_LENGTH)) {
      codedOutputStream.writeUInt32NoTag(Integer.MAX_VALUE);
    } else {
      codedOutputStream.writeUInt32NoTag(wseBytes.length);
    }

    if(payloadStrategy.equals(WsePayloadStrategy.INVALID_WSE)) {
      byte[] invalidArr = Arrays.copyOfRange(wseBytes, 0, wseBytes.length);
      invalidArr[0] = invalidArr[1] = invalidArr[3] = 0;
      invalidArr[invalidArr.length - 1] = invalidArr[invalidArr.length - 2] = invalidArr[invalidArr.length - 3] = 0;
      codedOutputStream.writeByteArrayNoTag(invalidArr);
    } else {
      wse.writeTo(codedOutputStream);
    }
    codedOutputStream.flush();
    byte[] bytesWritten = outputStream.toByteArray();

    Checksum wseChecksum = new Adler32();
    wseChecksum.update(bytesWritten, 0, bytesWritten.length);
    long checksumValue = payloadStrategy.equals(WsePayloadStrategy.INVALID_CHECKSUM) ? 0 : wseChecksum.getValue();
    codedOutputStream.writeUInt32NoTag((int) checksumValue);
    codedOutputStream.flush();

    outputStream.writeTo(requestStream);
  }

  private static List<Recorder.Wse> getMockWseEntriesForSingleProfile() {
    List<Recorder.StackSample> samples = MockProfileComponents.getPredefinedStackSamples(1);
    Recorder.StackSampleWse ssw1 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(0))
        .addStackSample(samples.get(1))
        .build();
    Recorder.StackSampleWse ssw2 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(2))
        .build();

    Recorder.Wse wse1 = MockProfileComponents.getMockCpuWseWithStackSample(ssw1, null);
    Recorder.Wse wse2 = MockProfileComponents.getMockCpuWseWithStackSample(ssw2, ssw1);

    return Arrays.asList(wse1, wse2);
  }

  private static List<Recorder.Wse> getMockWseEntriesForMultipleProfiles() {
    List<Recorder.StackSample> samples = MockProfileComponents.getPredefinedStackSamples(1);
    Recorder.StackSampleWse ssw1 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(0))
        .build();
    Recorder.StackSampleWse ssw2 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(1))
        .build();
    Recorder.StackSampleWse ssw3 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(2))
        .build();

    Recorder.Wse wse1 = MockProfileComponents.getMockCpuWseWithStackSample(ssw1, null);
    Recorder.Wse wse2 = MockProfileComponents.getMockCpuWseWithStackSample(ssw2, null);
    Recorder.Wse wse3 = MockProfileComponents.getMockCpuWseWithStackSample(ssw3, null);

    return Arrays.asList(wse1, wse2, wse3);
  }

  public enum HeaderPayloadStrategy {
    VALID,
    INVALID_CHECKSUM,
    INVALID_RECORDING_HEADER,
    INVALID_HEADER_LENGTH,
    INVALID_WORK_ID
  }

  public enum WsePayloadStrategy {
    VALID,
    INVALID_CHECKSUM,
    INVALID_WSE,
    INVALID_WSE_LENGTH
  }

}
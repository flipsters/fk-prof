package fk.prof.storage.impl;

import com.amazonaws.util.StringInputStream;
import fk.prof.storage.util.DataConverterUtil;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.apache.zookeeper.CreateMode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Test for ZKStorage class
 * Created by rohit.patiyal on 17/05/17.
 */
public class ZKAsyncStorageTest {

  private TestingServer testingServer;
  private CuratorFramework curatorClient;
  private final String rootNode = "/root";
  private ZKAsyncStorage zkAsyncStorage;

  @Before
  public void setUp() throws Exception {
    testingServer = new TestingServer();
    Timing timing = new Timing();

    curatorClient = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
    curatorClient.start();
    curatorClient.blockUntilConnected(2, TimeUnit.SECONDS);
    curatorClient.create().forPath(rootNode);

    zkAsyncStorage = new ZKAsyncStorage(curatorClient, CreateMode.PERSISTENT_SEQUENTIAL);
  }

  @After
  public void tearDown() throws Exception {
    testingServer.close();
    curatorClient.close();
  }

  @Test
  public void testStoreAsyncBytes() throws Exception {
    assertStoreAsyncBytesCall("data1", rootNode + "/");
  }

  @Test
  public void testStoreAsyncInputStream() throws Exception {
    assertStoreAsyncStreamCall("data1", rootNode + "/");
  }

  @Test
  public void testFetchAsync() throws Exception {
    String data1 = "data1";

    //PRE TEST
    assertStoreAsyncBytesCall(data1, rootNode + "/");
    String expected = data1;
    //TEST
    assertFetchAsyncCall(expected, rootNode);

    String data2 = "data2";
    //PRE TEST
    assertStoreAsyncStreamCall(data2, rootNode + "/");
    expected = data2;
    //TEST
    assertFetchAsyncCall(expected, rootNode);

  }

  @Test
  public void testListAsync() throws Exception {
    Set<String> expected = new HashSet<>(Arrays.asList("key1", "key2"));
    for (String keys : expected)
      assertStoreAsyncStreamCall("data1", rootNode + "/" + keys + "/v");
    assertListAsyncStreamCall(expected, rootNode);
  }

  private void assertFetchAsyncCall(String expected, String path) throws Exception {
    zkAsyncStorage.fetchAsync(path)
        .whenComplete((inputStream, throwable) -> {
          byte[] got = null;
          try {
            got = DataConverterUtil.inputStreamToByteArray(inputStream);
            Assert.assertEquals(expected, new String(got));
          } catch (IOException e) {
            e.printStackTrace();
          }
        }).get(1, TimeUnit.SECONDS);
  }

  private void assertStoreAsyncStreamCall(String data, String path) throws Exception {
    byte[] byteData = data.getBytes();
    zkAsyncStorage.storeAsync(path, new StringInputStream(data), data.length())
        .whenComplete((aVoid, throwable) -> Assert.assertNull(throwable)).get(1, TimeUnit.SECONDS);
  }

  private void assertStoreAsyncBytesCall(String data, String path) throws Exception {
    byte[] byteData = data.getBytes();
    zkAsyncStorage.storeAsync(path, byteData)
        .whenComplete((aVoid, throwable) -> Assert.assertNull(throwable)).get(1, TimeUnit.SECONDS);
  }

  private void assertListAsyncStreamCall(Set<String> expected, String path) throws Exception {
    zkAsyncStorage.listAsync(path, false)
        .whenComplete((strings, throwable) -> Assert.assertEquals(expected, strings)).get(1, TimeUnit.SECONDS);
  }

}
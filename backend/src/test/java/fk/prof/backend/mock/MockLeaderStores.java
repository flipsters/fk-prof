package fk.prof.backend.mock;

import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.model.election.LeaderWriteContext;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;
import fk.prof.backend.proto.BackendDTO;

import java.util.concurrent.CountDownLatch;

public class MockLeaderStores {

  public static class TestLeaderStore implements LeaderReadContext, LeaderWriteContext {
    private final BackendDTO.LeaderDetail selfLeaderDetail;
    private BackendDTO.LeaderDetail currentLeaderDetail;
    private boolean self = false;
    private final CountDownLatch latch;

    public TestLeaderStore(String ipAddress, int leaderPort, CountDownLatch latch) {
      this.selfLeaderDetail = BackendDTO.LeaderDetail.newBuilder().setHost(ipAddress).setPort(leaderPort).build();
      this.latch = latch;
    }

    @Override
    public void setLeader(BackendDTO.LeaderDetail leader) {
      currentLeaderDetail = leader;
      self = currentLeaderDetail != null && currentLeaderDetail.equals(selfLeaderDetail);
      if (currentLeaderDetail != null) {
        latch.countDown();
      }
    }

    @Override
    public BackendDTO.LeaderDetail getLeader() {
      return currentLeaderDetail;
    }

    @Override
    public boolean isLeader() {
      return self;
    }
  }


  public static class WrappedLeaderStore implements LeaderReadContext, LeaderWriteContext {
    private final InMemoryLeaderStore toWrap;
    private final CountDownLatch latch;

    public WrappedLeaderStore(InMemoryLeaderStore toWrap, CountDownLatch latch) {
      this.toWrap = toWrap;
      this.latch = latch;
    }

    @Override
    public BackendDTO.LeaderDetail getLeader() {
      return toWrap.getLeader();
    }

    @Override
    public boolean isLeader() {
      return toWrap.isLeader();
    }

    @Override
    public void setLeader(BackendDTO.LeaderDetail leader) {
      toWrap.setLeader(leader);
      if(leader != null) {
        latch.countDown();
      }
    }
  }

}

package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.Configuration;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.leader.election.LeaderElectionParticipator;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import org.apache.curator.framework.CuratorFramework;

public class LeaderElectionParticipatorVerticleDeployer extends VerticleDeployer {

  private final CuratorFramework curatorClient;
  private final Runnable leaderElectedTask;

  public LeaderElectionParticipatorVerticleDeployer(Vertx vertx,
                                                    Configuration config,
                                                    CuratorFramework curatorClient,
                                                    Runnable leaderElectedTask) {
    super(vertx, config);
    this.curatorClient = Preconditions.checkNotNull(curatorClient);
    this.leaderElectedTask = Preconditions.checkNotNull(leaderElectedTask);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return getConfig().getLeaderElectionDeploymentOpts();
  }

  @Override
  protected Verticle buildVerticle() {
    return new LeaderElectionParticipator(getConfig(), curatorClient, leaderElectedTask);
  }
}

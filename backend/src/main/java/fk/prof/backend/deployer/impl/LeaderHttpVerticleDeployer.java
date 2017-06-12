package fk.prof.backend.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.backend.Configuration;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.http.LeaderHttpVerticle;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.policy.PolicyStoreAPI;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class LeaderHttpVerticleDeployer extends VerticleDeployer {

  private final BackendAssociationStore backendAssociationStore;
  private final PolicyStoreAPI policyStoreAPI;

  public LeaderHttpVerticleDeployer(Vertx vertx,
                                    Configuration config,
                                    BackendAssociationStore backendAssociationStore,
                                    PolicyStoreAPI policyStoreAPI) {
    super(vertx, config);
    this.backendAssociationStore = Preconditions.checkNotNull(backendAssociationStore);
    this.policyStoreAPI = Preconditions.checkNotNull(policyStoreAPI);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return getConfig().leaderDeploymentOpts;
  }

  @Override
  protected Verticle buildVerticle() {
    return new LeaderHttpVerticle(getConfig(), backendAssociationStore, policyStoreAPI);
  }
}

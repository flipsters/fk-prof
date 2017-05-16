package fk.prof.userapi.deployer.impl;

import com.google.common.base.Preconditions;
import fk.prof.userapi.UserapiConfigManager;
import fk.prof.userapi.api.ProfileAPI;
import fk.prof.userapi.deployer.VerticleDeployer;
import fk.prof.userapi.verticles.HttpVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class UserapiHttpVerticleDeployer extends VerticleDeployer {

  private final ProfileAPI profileAPI;

  public UserapiHttpVerticleDeployer(Vertx vertx, UserapiConfigManager userapiConfigManager, ProfileAPI profileAPI) {
    super(vertx, userapiConfigManager);
    this.profileAPI = Preconditions.checkNotNull(profileAPI);
  }

  @Override
  protected DeploymentOptions getDeploymentOptions() {
    return new DeploymentOptions(getConfigManager().getUserapiHttpDeploymentConfig());
  }

  @Override
  protected Verticle buildVerticle() {
    return new HttpVerticle(getConfigManager(), profileAPI);
  }

}

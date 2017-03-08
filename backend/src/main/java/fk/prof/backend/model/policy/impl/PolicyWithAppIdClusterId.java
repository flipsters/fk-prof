package fk.prof.backend.model.policy.impl;

import policy.PolicyDetails;

/**
 * Represents the model of an item to be returned in response for getPolicyGivenAppIdClusterId API
 * Created by rohit.patiyal on 08/03/17.
 */
public class PolicyWithAppIdClusterId {
  public String appId;
  public String clusterId;
  public PolicyDetails policyDetails;

  public PolicyWithAppIdClusterId(String appId, String clusterId, PolicyDetails policyDetails) {
    this.appId = appId;
    this.clusterId = clusterId;
    this.policyDetails = policyDetails;
  }
}

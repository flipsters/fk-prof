package fk.prof.backend.model.policy.impl;

import policy.PolicyDetails;

/**
 * Represents the model of an item to be returned in response for getPoliciesGivenAppId API
 * Created by rohit.patiyal on 07/03/17.
 */
public class PolicyWithAppId {
  public String appId;
  public PolicyDetails policyDetails;


  public PolicyWithAppId(String appId, PolicyDetails policyDetails) {
    this.appId = appId;
    this.policyDetails = policyDetails;
  }
}

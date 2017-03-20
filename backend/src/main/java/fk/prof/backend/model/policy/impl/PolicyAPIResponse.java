package fk.prof.backend.model.policy.impl;

import policy.PolicyDetails;

import java.util.Map;

/**
 * Response wrapper class for policy GET APIs used in custom serialization
 * of nested Map to json using jackson in {@link fk.prof.backend.model.policy.json.PolicyProtobufSerializers}
 * Created by rohit.patiyal on 15/03/17.
 */
public class PolicyAPIResponse {
  private Map<String, Map<String, Map<String, PolicyDetails>>> policiesMap;

  private PolicyAPIResponse(Map<String, Map<String, Map<String, PolicyDetails>>> policiesMap) {
    this.policiesMap = policiesMap;
  }

  public static PolicyAPIResponse getNewInstance(Map<String, Map<String, Map<String, PolicyDetails>>> policiesMap) {
    return new PolicyAPIResponse(policiesMap);
  }

  public Map<String, Map<String, Map<String, PolicyDetails>>> getPoliciesMap() {
    return policiesMap;
  }
}

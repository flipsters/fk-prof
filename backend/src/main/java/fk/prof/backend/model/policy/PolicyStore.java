package fk.prof.backend.model.policy;

import fk.prof.backend.proto.PolicyDTO;
import io.vertx.core.Future;
import recording.Recorder;

import java.util.Set;

/**
 * Interface for accessing the store containing policy information
 * Created by rohit.patiyal on 18/05/17.
 */
public interface PolicyStore {
    /**
     * Method to allow delayed initialization. Calling other methods before init may result in undefined behaviour.
     */
    void init() throws Exception;

    /**
     * Returns appIds of the processGroups corresponding to policies in the policyStore
     *
     * @param prefix string to filter the appIds
     * @return Set of appIds
     * @throws Exception
     */
    Set<String> getAppIds(String prefix) throws Exception;

    /**
     * /**
     * Returns clusterIds from the processGroups corresponding to policies in the policyStore
     *
     * @param appId  for which the clusterIds are to be found
     * @param prefix string to filter the clusterIds
     * @return Set of clusterIds
     * @throws Exception
     */
    Set<String> getClusterIds(String appId, String prefix) throws Exception;

    /**
     * Returns procNames from the processGroups corresponding to policies in the policyStore
     *
     * @param appId     for which the procNames are to be found
     * @param clusterId for which the procNames are to be found
     * @param prefix    string to filter the procNames
     * @return Set of procNames
     * @throws Exception
     */
    Set<String> getProcNames(String appId, String clusterId, String prefix) throws Exception;

    /**
     * Gets VersionedPolicyDetails currently stored for the processGroup
     *
     * @param processGroup of which the policy is to be retrieved
     * @return versionedPolicyDetail for the processGroup
     */
    PolicyDTO.VersionedPolicyDetails getVersionedPolicy(Recorder.ProcessGroup processGroup);

    /**
     * Creates a VersionedPolicyDetails for the processGroup supplied if there exists no policy for it previously
     * @param processGroup of which the policy mapping is to be created
     * @param versionedPolicyDetails to be set for the processGroup
     * @return a void future which contains the created versionedPolicyDetails
     */
    Future<PolicyDTO.VersionedPolicyDetails> createVersionedPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.VersionedPolicyDetails versionedPolicyDetails);

    /**
     * Updates a VersionedPolicyDetails for the processGroup supplied if there exists a policy for it previously
     * @param processGroup of which the policy mapping is to be updated
     * @param versionedPolicyDetails to be set for the processGroup
     * @return a void future which contains the created versionedPolicyDetails
     */
    Future<PolicyDTO.VersionedPolicyDetails> updateVersionedPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.VersionedPolicyDetails versionedPolicyDetails);
}

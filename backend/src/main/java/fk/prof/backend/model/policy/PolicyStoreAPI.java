package fk.prof.backend.model.policy;

import fk.prof.backend.proto.PolicyDTO;
import recording.Recorder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for accessing the store containing policy information
 * Created by rohit.patiyal on 07/03/17.
 */
public interface PolicyStoreAPI {
  void init();

  PolicyDTO.PolicyDetails getPolicy(Recorder.ProcessGroup processGroup);
  CompletableFuture<Void> setPolicy(Recorder.ProcessGroup processGroup, PolicyDTO.PolicyDetails policyDetails);
}
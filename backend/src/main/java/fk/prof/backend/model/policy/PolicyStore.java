package fk.prof.backend.model.policy;

import policy.PolicyDetails;
import recording.Recorder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for accessing the store containing policy information
 * Created by rohit.patiyal on 07/03/17.
 */
public interface PolicyStore {
  CompletableFuture<List<PolicyDetails>> getAssociatedPolicies(Recorder.ProcessGroup processGroup);
}

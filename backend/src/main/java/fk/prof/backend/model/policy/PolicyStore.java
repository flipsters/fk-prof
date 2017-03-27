package fk.prof.backend.model.policy;

import fk.prof.backend.proto.BackendDTO;
import recording.Recorder;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO: Liable for refactoring. Dummy impl for now
 */
public class PolicyStore {
  private final Map<Recorder.ProcessGroup, BackendDTO.RecordingPolicy> store = new HashMap<>();

  public void put(Recorder.ProcessGroup processGroup, BackendDTO.RecordingPolicy recordingPolicy) {
    this.store.put(processGroup, recordingPolicy);
  }

  public BackendDTO.RecordingPolicy get(Recorder.ProcessGroup processGroup) {
    // default policy
    BackendDTO.RecordingPolicy policy = BackendDTO.RecordingPolicy.newBuilder()
            .setCoveragePct(100)
            .setDescription("cpu sampling")
            .setDuration(30)
            .addWork(BackendDTO.Work.newBuilder().setWType(BackendDTO.WorkType.cpu_sample_work).setCpuSample(BackendDTO.CpuSampleWork.newBuilder().setFrequency(50).setMaxFrames(128)))
            .build();
    return policy;
  }
}

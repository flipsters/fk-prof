package fk.prof.backend.model.association;

import fk.prof.backend.proto.BackendDTO;
import io.vertx.core.Future;
import recording.Recorder;

public interface BackendAssociationStore {
  Future<Recorder.ProcessGroups> reportBackendLoad(BackendDTO.LoadReportRequest payload);
  Future<Recorder.AssignedBackend> getAssociatedBackend(Recorder.ProcessGroup processGroup);
}

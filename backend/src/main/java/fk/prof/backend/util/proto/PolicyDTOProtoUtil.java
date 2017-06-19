package fk.prof.backend.util.proto;

import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.proto.PolicyDTO;

import java.util.stream.Collectors;

/**
 * Utility methods for policy proto
 * Created by rohit.patiyal on 22/05/17.
 */
public class PolicyDTOProtoUtil {
    private static String policyDetailsCompactRepr(PolicyDTO.PolicyDetails policyDetails) {
        return String.format("modAt=%s,creatAt=%s,creatBy=%s", policyDetails.getModifiedAt(), policyDetails.getModifiedBy(), policyDetails.getCreatedAt());
    }

    public static String versionedPolicyDetailsCompactRepr(PolicyDTO.VersionedPolicyDetails versionedPolicyDetails) {
        return String.format("version=%d,policy=(%s)", versionedPolicyDetails.getVersion(), policyDetailsCompactRepr(versionedPolicyDetails.getPolicyDetails()));
    }

    public static BackendDTO.RecordingPolicy translateToBackendRecordingPolicy(PolicyDTO.VersionedPolicyDetails versionedPolicy) {
        PolicyDTO.Policy inputPolicy = versionedPolicy.getPolicyDetails().getPolicy();
        return  BackendDTO.RecordingPolicy.newBuilder().setCoveragePct(inputPolicy.getSchedule().getPgCovPct())
                .setDuration(inputPolicy.getSchedule().getDuration())
                .setDescription(inputPolicy.getDescription())
                .addAllWork(inputPolicy.getWorkList().stream().map(PolicyDTOProtoUtil::translateToBackendDTOWork).collect(Collectors.toList())).build();
    }

    private static BackendDTO.WorkType translateToBackendDTOWorkType(PolicyDTO.WorkType workType){
        switch (workType){
            case cpu_sample_work:
                return BackendDTO.WorkType.cpu_sample_work;
            case thread_sample_work:
                return BackendDTO.WorkType.thread_sample_work;
            case monitor_contention_work:
                return BackendDTO.WorkType.monitor_contention_work;
            case monitor_wait_work:
                return BackendDTO.WorkType.monitor_wait_work;
            default:
                return BackendDTO.WorkType.cpu_sample_work;
        }
    }

    private static BackendDTO.Work translateToBackendDTOWork(PolicyDTO.Work work) {
        BackendDTO.Work.Builder outputWorkBuilder = BackendDTO.Work.newBuilder().setWType(translateToBackendDTOWorkType(work.getWType()));

        if(work.hasCpuSample()){
            PolicyDTO.CpuSampleWork inputCPUSample = work.getCpuSample();
            outputWorkBuilder.setCpuSample(BackendDTO.CpuSampleWork.newBuilder().setFrequency(inputCPUSample.getFrequency())
                    .setMaxFrames(inputCPUSample.getMaxFrames()).build());
        }
        if(work.hasThdSample()){
            PolicyDTO.ThreadSampleWork inputThdSample = work.getThdSample();
            outputWorkBuilder.setThdSample(BackendDTO.ThreadSampleWork.newBuilder().setFrequency(inputThdSample.getFrequency())
                    .setMaxFrames(inputThdSample.getMaxFrames()).build());
        }
        if(work.hasMonitorBlock()){
            PolicyDTO.MonitorContentionWork inputMonitorBlock = work.getMonitorBlock();
            outputWorkBuilder.setMonitorBlock(BackendDTO.MonitorContentionWork.newBuilder().setMaxMonitors(inputMonitorBlock.getMaxMonitors())
                    .setMaxFrames(inputMonitorBlock.getMaxFrames()).build());
        }
        if(work.hasMonitorWait()){
            PolicyDTO.MonitorWaitWork inputMonitorWait = work.getMonitorWait();
            outputWorkBuilder.setMonitorWait(BackendDTO.MonitorWaitWork.newBuilder().setMaxMonitors(inputMonitorWait.getMaxMonitors())
                    .setMaxFrames(inputMonitorWait.getMaxFrames()).build());
        }
        return outputWorkBuilder.build();
    }
}

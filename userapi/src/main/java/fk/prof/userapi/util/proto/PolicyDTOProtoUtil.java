package fk.prof.userapi.util.proto;

import proto.PolicyDTO;

import java.util.List;

/**
 * Utility methods for policy proto
 * Created by rohit.patiyal on 22/05/17.
 */
public class PolicyDTOProtoUtil {
    private static String policyDetailsCompactRepr(PolicyDTO.PolicyDetails policyDetails) {
        return String.format("modAt=%s,creatAt=%s,creatBy=%s,policy=%s", policyDetails.getModifiedAt(), policyDetails.getModifiedBy(), policyDetails.getCreatedAt(), policyCompactRepr(policyDetails.getPolicy()));
    }

    private static String policyCompactRepr(PolicyDTO.Policy policy) {
        return String.format("desc:%s,sched:{%s},work:[%s]", policy.getDescription(),policyScheduleCompactRepr(policy.getSchedule()),policyWorkListCompactRepr(policy.getWorkList()));
    }

    private static String policyWorkListCompactRepr(List<PolicyDTO.Work> workList) {
        StringBuilder sb = new StringBuilder();
        for(PolicyDTO.Work work: workList){
            sb.append(policyWorkCompactRepr(work));
        }
        return sb.toString();
    }

    private static String policyWorkCompactRepr(PolicyDTO.Work work) {
        StringBuilder sb = new StringBuilder();
        if(work.hasCpuSample()){
            sb.append("cpuSample:");
            PolicyDTO.CpuSampleWork cpuSample = work.getCpuSample();
            sb.append(String.format("{freq=%d,maxFram=%d}",cpuSample.getFrequency(), cpuSample.getMaxFrames()));
        }
        if(work.hasThdSample()){
            sb.append("threadSample:");
            PolicyDTO.ThreadSampleWork threadSample = work.getThdSample();
            sb.append(String.format("{freq=%d,maxFram=%d}",threadSample.getFrequency(), threadSample.getMaxFrames()));
        }
        if(work.hasMonitorBlock()){
            sb.append("monitorBlock:");
            PolicyDTO.MonitorContentionWork monitorContention = work.getMonitorBlock();
            sb.append(String.format("{maxMon=%d,maxFram=%d}",monitorContention.getMaxMonitors(), monitorContention.getMaxFrames()));
        }
        if(work.hasMonitorWait()){
            sb.append("monitorWait:");
            PolicyDTO.MonitorWaitWork monitorWait = work.getMonitorWait();
            sb.append(String.format("{maxMon=%d,maxFram=%d}",monitorWait.getMaxMonitors(), monitorWait.getMaxFrames()));
        }
        return sb.toString();
    }


    private static String policyScheduleCompactRepr(PolicyDTO.Schedule schedule) {
        return String.format("aft:%s,dur:%d,cov:%d", schedule.getAfter(), schedule.getDuration(), schedule.getPgCovPct());
    }

    public static String versionedPolicyDetailsCompactRepr(PolicyDTO.VersionedPolicyDetails versionedPolicyDetails) {
        return String.format("version=%d,policy={%s}", versionedPolicyDetails.getVersion(), policyDetailsCompactRepr(versionedPolicyDetails.getPolicyDetails()));
    }

    public static void validatePolicyValues(PolicyDTO.VersionedPolicyDetails versionedPolicyDetails) throws Exception {
        PolicyDTO.Policy policy = versionedPolicyDetails.getPolicyDetails().getPolicy();
        validateField("duration", policy.getSchedule().getDuration(), 60, 960);
        validateField("pgCovPct", policy.getSchedule().getPgCovPct(), 0, 100);
        for (PolicyDTO.Work work : policy.getWorkList()) {
            int workDetailsCount = 0;
            if(work.hasCpuSample()){
                validateField("cpuSample: frequency", work.getCpuSample().getFrequency(), 50, 100);
                validateField("cpuSample: maxFrames", work.getCpuSample().getMaxFrames(), 1, 999);
                workDetailsCount++;
            }
            if(work.hasThdSample()){
                validateField("threadSample: frequency", work.getThdSample().getFrequency(), 50, 100);
                validateField("threadSample: maxFrames", work.getThdSample().getMaxFrames(), 1, 999);
                workDetailsCount++;
            }
            if(work.hasMonitorBlock()){
                validateField("monitorBlock: maxMonitors", work.getMonitorBlock().getMaxMonitors(), 50, 100);
                validateField("monitorBlock: maxFrames", work.getMonitorBlock().getMaxFrames(), 1, 999);
                workDetailsCount++;
            }
            if(work.hasMonitorWait()){
                validateField("monitorWait: maxMonitors", work.getMonitorWait().getMaxMonitors(), 50, 100);
                validateField("monitorWait: maxFrames", work.getMonitorWait().getMaxFrames(), 1, 999);
                workDetailsCount++;
            }
            if(workDetailsCount != 1)
                throw new IllegalArgumentException("Only one work details per work supported, given: " + workDetailsCount);
        }
    }

    private static <T extends Comparable<T>> void validateField(String name, T value, T min, T max) throws Exception {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException("Value of " + name + " should be between [" + min + "," + max + "], given: " + value );
        }
    }
}

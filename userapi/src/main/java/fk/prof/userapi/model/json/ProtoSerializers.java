package fk.prof.userapi.model.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import proto.PolicyDTO;

import java.io.IOException;

/**
 * @author gaurav.ashok
 */
public class ProtoSerializers {

    public static void registerSerializers(ObjectMapper om) {
        SimpleModule module = new SimpleModule("protobufSerializers", new Version(1, 0, 0, null, null, null));
        module.addSerializer(AggregatedProfileModel.FrameNode.class, new FrameNodeSerializer());
        module.addSerializer(AggregatedProfileModel.CPUSamplingNodeProps.class, new CpuSampleFrameNodePropsSerializer());
        module.addSerializer(AggregatedProfileModel.Header.class, new HeaderSerializer());
        module.addSerializer(AggregatedProfileModel.RecorderInfo.class, new RecorderInfoSerializer());
        module.addSerializer(AggregatedProfileModel.ProfileWorkInfo.class, new ProfileWorkInfoSerializer());
        module.addSerializer(AggregatedProfileModel.TraceCtxDetail.class, new TraceCtxDetailsSerializer());
        module.addSerializer(PolicyDTO.CpuSampleWork.class, new CpuSampleWorkSerializer());
        module.addSerializer(PolicyDTO.ThreadSampleWork.class, new ThreadSampleWorkSerializer());
        module.addSerializer(PolicyDTO.MonitorContentionWork.class, new MonitorContentionWorkSerializer());
        module.addSerializer(PolicyDTO.MonitorWaitWork.class, new MonitorWaitWorkSerializer());
        module.addSerializer(PolicyDTO.Schedule.class, new ScheduleSerializer());
        module.addSerializer(PolicyDTO.Work.class, new WorkSerializer());
        module.addSerializer(PolicyDTO.Policy.class, new PolicySerializer());
        module.addSerializer(PolicyDTO.PolicyDetails.class, new PolicyDetailsSerializer());
        module.addSerializer(PolicyDTO.VersionedPolicyDetails.class, new VersionedPolicyDetailsSerializer());
        om.registerModule(module);
    }

    static class FrameNodeSerializer extends StdSerializer<AggregatedProfileModel.FrameNode> {

        public FrameNodeSerializer() {
            super(AggregatedProfileModel.FrameNode.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.FrameNode value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeStartArray();
            gen.writeNumber(value.getMethodId());
            gen.writeNumber(value.getChildCount());
            gen.writeNumber(value.getLineNo());
            if(value.getCpuSamplingProps() != null) {
                JsonSerializer cpuSamplesPropsSerializer = serializers.findValueSerializer(AggregatedProfileModel.CPUSamplingNodeProps.class);
                cpuSamplesPropsSerializer.serialize(value.getCpuSamplingProps(), gen, serializers);
            }
            gen.writeEndArray();
        }
    }

    static class TraceCtxDetailsSerializer extends StdSerializer<AggregatedProfileModel.TraceCtxDetail> {

        public TraceCtxDetailsSerializer() {
            super(AggregatedProfileModel.TraceCtxDetail.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.TraceCtxDetail value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeStartObject();
            gen.writeNumberField("trace_idx", value.getTraceIdx());
            gen.writeFieldName("props");
            gen.writeStartObject();
            gen.writeNumberField("samples", value.getSampleCount());
            gen.writeEndObject();
            gen.writeEndObject();
        }
    }

    static class CpuSampleFrameNodePropsSerializer extends StdSerializer<AggregatedProfileModel.CPUSamplingNodeProps> {

        public CpuSampleFrameNodePropsSerializer() {
            super(AggregatedProfileModel.CPUSamplingNodeProps.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.CPUSamplingNodeProps value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartArray();
            gen.writeNumber(value.getOnStackSamples());
            gen.writeNumber(value.getOnCpuSamples());
            gen.writeEndArray();
        }
    }

    static class HeaderSerializer extends StdSerializer<AggregatedProfileModel.Header> {

        public HeaderSerializer() {
            super(AggregatedProfileModel.Header.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.Header header, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("app_id", header.getAppId());
            gen.writeStringField("cluster_id", header.getClusterId());
            gen.writeStringField("proc_id", header.getProcId());
            gen.writeStringField("aggregation_startTime", header.getAggregationStartTime());
            gen.writeStringField("aggregation_end_time", header.getAggregationEndTime());
            if(header.hasWorkType()) {
                gen.writeStringField("work_type", header.getWorkType().name());
            }
            gen.writeEndObject();
        }
    }

    static class RecorderInfoSerializer extends StdSerializer<AggregatedProfileModel.RecorderInfo> {
        public RecorderInfoSerializer() {
            super(AggregatedProfileModel.RecorderInfo.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.RecorderInfo value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("ip", value.getIp());
            gen.writeStringField("hostname", value.getHostname());
            gen.writeStringField("app_id", value.getAppId());
            gen.writeStringField("instance_group", value.getInstanceGroup());
            gen.writeStringField("cluster", value.getCluster());
            gen.writeStringField("instace_id", value.getInstanceId());
            gen.writeStringField("process_name", value.getProcessName());
            gen.writeStringField("vm_id", value.getVmId());
            gen.writeStringField("zone", value.getZone());
            gen.writeStringField("instance_type", value.getInstanceType());
            gen.writeEndObject();
        }
    }

    static class ProfileWorkInfoSerializer extends StdSerializer<AggregatedProfileModel.ProfileWorkInfo> {
        public ProfileWorkInfoSerializer() {
            super(AggregatedProfileModel.ProfileWorkInfo.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.ProfileWorkInfo value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("start_offset", value.getStartOffset());
            gen.writeNumberField("duration", value.getDuration());
            gen.writeNumberField("recorder_version", value.getRecorderVersion());

            if(value.hasRecorderInfo()) {
                gen.writeFieldName("recorder_info");
                JsonSerializer recorderInfoSerializer = serializers.findValueSerializer(AggregatedProfileModel.RecorderInfo.class);
                recorderInfoSerializer.serialize(value.getRecorderInfo(), gen, serializers);
            }

            gen.writeObjectFieldStart("sample_count");
            for(AggregatedProfileModel.ProfileWorkInfo.SampleCount sampleCount : value.getSampleCountList()) {
                gen.writeNumberField(sampleCount.getWorkType().name(), sampleCount.getSampleCount());
            }
            gen.writeEndObject();

            gen.writeStringField("status", value.getStatus().name());
            gen.writeArrayFieldStart("trace_coverage_map");
            for(AggregatedProfileModel.ProfileWorkInfo.TraceCtxToCoveragePctMap keyValue: value.getTraceCoverageMapList()) {
                gen.writeStartArray();
                gen.writeNumber(keyValue.getTraceCtxIdx());
                gen.writeNumber(keyValue.getCoveragePct());
                gen.writeEndArray();
            }
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }

    static class VersionedPolicyDetailsSerializer extends StdSerializer<PolicyDTO.VersionedPolicyDetails> {
        public VersionedPolicyDetailsSerializer() {
            super(PolicyDTO.VersionedPolicyDetails.class);
        }

        @Override
        public void serialize(PolicyDTO.VersionedPolicyDetails versionedPolicyDetails, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("versioned_policy_details", versionedPolicyDetails.getVersion());

            jsonGenerator.writeFieldName("policy_details");
            JsonSerializer<Object> policyDetailsSerializer = serializerProvider.findValueSerializer(PolicyDTO.PolicyDetails.class);
            policyDetailsSerializer.serialize(versionedPolicyDetails.getPolicyDetails(), jsonGenerator, serializerProvider);

            jsonGenerator.writeEndObject();
        }
    }

     static class PolicyDetailsSerializer extends StdSerializer<PolicyDTO.PolicyDetails> {
         public PolicyDetailsSerializer() {
             super(PolicyDTO.PolicyDetails.class);
         }

         @Override
         public void serialize(PolicyDTO.PolicyDetails policyDetails, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName("policy");
            JsonSerializer<Object> policySerializer = serializerProvider.findValueSerializer(PolicyDTO.Policy.class);
            policySerializer.serialize(policyDetails.getPolicy(), jsonGenerator, serializerProvider);

            jsonGenerator.writeStringField("modified_by", policyDetails.getModifiedBy());
            jsonGenerator.writeStringField("modified_at", policyDetails.getModifiedBy());
            jsonGenerator.writeStringField("created_at", policyDetails.getCreatedAt());
            jsonGenerator.writeEndObject();
         }
     }

    static class PolicySerializer extends StdSerializer<PolicyDTO.Policy> {
        public PolicySerializer() {
            super(PolicyDTO.Policy.class);
        }

        @Override
        public void serialize(PolicyDTO.Policy policy, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName("schedule");
            JsonSerializer<Object> scheduleSerializer = serializerProvider.findValueSerializer(PolicyDTO.Schedule.class);
            scheduleSerializer.serialize(policy.getSchedule(), jsonGenerator, serializerProvider);

            jsonGenerator.writeStringField("description", policy.getDescription());
            jsonGenerator.writeArrayFieldStart("work");
            jsonGenerator.writeStartArray();
            for (PolicyDTO.Work work: policy.getWorkList()){
                JsonSerializer<Object> workSerializer = serializerProvider.findValueSerializer(PolicyDTO.Work.class);
                workSerializer.serialize(work, jsonGenerator, serializerProvider);
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject();
        }
    }

    static class ScheduleSerializer extends StdSerializer<PolicyDTO.Schedule>{
        public ScheduleSerializer() {
            super(PolicyDTO.Schedule.class);
        }

        @Override
        public void serialize(PolicyDTO.Schedule schedule, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("duration", schedule.getDuration());
            jsonGenerator.writeNumberField("pg_cov_pct", schedule.getPgCovPct());
            jsonGenerator.writeStringField("after", schedule.getAfter());
            jsonGenerator.writeEndObject();
        }
    }

    static class WorkSerializer extends StdSerializer<PolicyDTO.Work>{
        public WorkSerializer() {
            super(PolicyDTO.Work.class);
        }

        @Override
        public void serialize(PolicyDTO.Work work, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("w_type", work.getWType().getNumber());
            if(work.hasCpuSample()){
                JsonSerializer<Object> cpuSampleSerializer = serializerProvider.findValueSerializer(PolicyDTO.CpuSampleWork.class);
                cpuSampleSerializer.serialize(work.getCpuSample(), jsonGenerator, serializerProvider);
            }
            if(work.hasThdSample()){
                JsonSerializer<Object> thdSampleSerializer = serializerProvider.findValueSerializer(PolicyDTO.ThreadSampleWork.class);
                thdSampleSerializer.serialize(work.getThdSample(), jsonGenerator, serializerProvider);
            }
            if(work.hasMonitorBlock()){
                JsonSerializer<Object> monitorBlockSerializer = serializerProvider.findValueSerializer(PolicyDTO.MonitorContentionWork.class);
                monitorBlockSerializer.serialize(work.getMonitorBlock(), jsonGenerator, serializerProvider);
            }
            if(work.hasMonitorWait()){
                JsonSerializer<Object> monitorWaitSerializer = serializerProvider.findValueSerializer(PolicyDTO.MonitorWaitWork.class);
                monitorWaitSerializer.serialize(work.getMonitorWait(), jsonGenerator, serializerProvider);
            }
        }
    }

    static class CpuSampleWorkSerializer extends StdSerializer<PolicyDTO.CpuSampleWork>{
        public CpuSampleWorkSerializer() {
            super(PolicyDTO.CpuSampleWork.class);
        }

        @Override
        public void serialize(PolicyDTO.CpuSampleWork cpuSampleWork, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("frequency", cpuSampleWork.getFrequency());
            jsonGenerator.writeNumberField("max_frames", cpuSampleWork.getMaxFrames());
            jsonGenerator.writeEndObject();
        }
    }

    static class ThreadSampleWorkSerializer extends StdSerializer<PolicyDTO.ThreadSampleWork>{
        public ThreadSampleWorkSerializer(){
            super(PolicyDTO.ThreadSampleWork.class);
        }

        @Override
        public void serialize(PolicyDTO.ThreadSampleWork threadSampleWork, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("frequency", threadSampleWork.getFrequency());
            jsonGenerator.writeNumberField("max_frames", threadSampleWork.getMaxFrames());
            jsonGenerator.writeEndObject();
        }
    }

    static class MonitorContentionWorkSerializer extends StdSerializer<PolicyDTO.MonitorContentionWork>{
        public MonitorContentionWorkSerializer(){
            super(PolicyDTO.MonitorContentionWork.class);
        }
        @Override
        public void serialize(PolicyDTO.MonitorContentionWork monitorContentionWork, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("max_monitors", monitorContentionWork.getMaxMonitors());
            jsonGenerator.writeNumberField("max_frames", monitorContentionWork.getMaxFrames());
            jsonGenerator.writeEndObject();
        }
    }

    static class MonitorWaitWorkSerializer extends StdSerializer<PolicyDTO.MonitorWaitWork>{
        public MonitorWaitWorkSerializer(){
            super(PolicyDTO.MonitorWaitWork.class);
        }

        @Override
        public void serialize(PolicyDTO.MonitorWaitWork monitorWaitWork, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("max_monitors", monitorWaitWork.getMaxMonitors());
            jsonGenerator.writeNumberField("max_frames", monitorWaitWork.getMaxFrames());
            jsonGenerator.writeEndObject();
        }
    }
}

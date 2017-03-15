package fk.prof.backend.model.policy.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.protobuf.Descriptors;
import policy.PolicyDetails;

import java.io.IOException;
import java.util.Map;

/**
 * Serializers (Proto Objects to json String) using jackson for Policy Proto classes
 * Created by rohit.patiyal on 14/03/17.
 */
public class PolicyProtobufSerializers {

  public static void registerSerializer(ObjectMapper om) {
    SimpleModule module = new SimpleModule("policyProtobufSerializer", new Version(1, 0, 0, null, null, null));
    module.addSerializer(PolicyDetails.class, new PolicyDetailsSerializer());
    module.addSerializer((Class<Map<String, Map<String, Map<String, PolicyDetails>>>>) (Class<?>) Map.class, new ProcessGroupPolicyMapSerializer());
    om.registerModule(module);
  }

  static class PolicyDetailsSerializer extends StdSerializer<PolicyDetails> {

    PolicyDetailsSerializer() {
      super(PolicyDetails.class);
    }

    @Override
    public void serialize(PolicyDetails policyDetails, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
      jsonGenerator.writeStartObject();
      for (Map.Entry<Descriptors.FieldDescriptor, Object> kv : policyDetails.getAllFields().entrySet()) {

        if (kv.getKey().getNumber() != 0)
          jsonGenerator.writeObjectField(kv.getKey().getJsonName(), kv.getValue());
      }
      jsonGenerator.writeEndObject();
    }
  }


  static class ProcessGroupPolicyMapSerializer extends StdSerializer<Map<String, Map<String, Map<String, PolicyDetails>>>> {


    ProcessGroupPolicyMapSerializer() {
      super((Class<Map<String, Map<String, Map<String, PolicyDetails>>>>) (Class<?>) Map.class);
    }

    @Override
    public void serialize(Map<String, Map<String, Map<String, PolicyDetails>>> stringMapMap, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
      if (stringMapMap.size() == 1) {
        Map<String, Map<String, PolicyDetails>> stringMap = stringMapMap.values().iterator().next();
        if (stringMap.size() == 1) {
          Map<String, PolicyDetails> stringPolicy = stringMap.values().iterator().next();
          if (stringPolicy.size() == 1) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeObject(stringPolicy.values().iterator().next());
            jsonGenerator.writeEndObject();
          } else {
            jsonGenerator.writeStartArray();
            for (Map.Entry<String, PolicyDetails> procPol : stringPolicy.entrySet()) {
              jsonGenerator.writeStartObject();
//              jsonGenerator.writeObjectField("appId", stringMapMap.keySet().iterator().next());
//              jsonGenerator.writeObjectField("cluster", stringMap.keySet().iterator().next());
              jsonGenerator.writeObjectField("process", procPol.getKey());
              jsonGenerator.writeObjectField("policy_details", procPol.getValue());
              jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
          }
        } else {
          jsonGenerator.writeStartArray();
          for (Map.Entry<String, Map<String, PolicyDetails>> clusMap : stringMap.entrySet()) {
            for (Map.Entry<String, PolicyDetails> procPol : clusMap.getValue().entrySet()) {
              jsonGenerator.writeStartObject();
//              jsonGenerator.writeObjectField("appId", stringMapMap.keySet().iterator().next());
              jsonGenerator.writeObjectField("cluster", clusMap.getKey());
              jsonGenerator.writeObjectField("process", procPol.getKey());
              jsonGenerator.writeObjectField("policy_details", procPol.getValue());
              jsonGenerator.writeEndObject();
            }
          }
          jsonGenerator.writeEndArray();
        }
      } else {
        jsonGenerator.writeStartArray();
        for (Map.Entry<String, Map<String, Map<String, PolicyDetails>>> appMap : stringMapMap.entrySet()) {
          for (Map.Entry<String, Map<String, PolicyDetails>> clusMap : appMap.getValue().entrySet()) {
            for (Map.Entry<String, PolicyDetails> procPol : clusMap.getValue().entrySet()) {
              jsonGenerator.writeStartObject();
              jsonGenerator.writeObjectField("appId", appMap.getKey());
              jsonGenerator.writeObjectField("cluster", clusMap.getKey());
              jsonGenerator.writeObjectField("process", procPol.getKey());
              jsonGenerator.writeObjectField("policy_details", procPol.getValue());
              jsonGenerator.writeEndObject();
            }
          }
        }
        jsonGenerator.writeEndArray();
      }
    }
  }


}
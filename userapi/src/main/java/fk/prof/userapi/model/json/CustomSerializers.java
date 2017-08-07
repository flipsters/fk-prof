package fk.prof.userapi.model.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import fk.prof.userapi.model.TreeViewResponse;
import fk.prof.userapi.model.tree.IndexedTreeNode;

import java.io.IOException;
import java.util.List;

/**
 * Created by gaurav.ashok on 07/08/17.
 */
public class CustomSerializers {

    public static void registerSerializers(ObjectMapper om) {
        SimpleModule module = new SimpleModule("customSerializers", new Version(1, 0, 0, null, null, null));
        module.addSerializer(IndexedTreeNode.class, new IndexedNodeSerializer());
        module.addSerializer(TreeViewResponse.class, new TreeViewResponseSerializer());
        om.registerModule(module);
    }

    static class IndexedNodeSerializer extends StdSerializer<IndexedTreeNode> {
        IndexedNodeSerializer() {
            super(IndexedTreeNode.class);
        }

        @Override
        public void serialize(IndexedTreeNode value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeStartObject();
            gen.writeFieldName("data");
            serializers.defaultSerializeValue(value.getData(), gen);
            List<IndexedTreeNode> children = value.getChildren();
            if(children != null) {
                gen.writeFieldName("chld");
                gen.writeStartObject();
                for(IndexedTreeNode chld : children) {
                    gen.writeFieldName(String.valueOf(chld.getIdx()));
                    serializers.defaultSerializeValue(chld, gen);
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        }
    }

    static class TreeViewResponseSerializer extends StdSerializer<TreeViewResponse> {
        TreeViewResponseSerializer() {
            super(TreeViewResponse.class);
        }

        @Override
        public void serialize(TreeViewResponse value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeFieldName("method_lookup");
            serializers.defaultSerializeValue(value.getMethodLookup(), gen);

            List<IndexedTreeNode> nodes = value.getTree();
            for(IndexedTreeNode node: nodes) {
                gen.writeFieldName(String.valueOf(node.getIdx()));
                serializers.defaultSerializeValue(node, gen);
            }
        }
    }
}
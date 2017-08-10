package fk.prof.userapi.model.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import fk.prof.aggregation.proto.AggregatedProfileModel.FrameNode;
import fk.prof.userapi.model.tree.TreeViewResponse;
import fk.prof.userapi.model.tree.IndexedTreeNode;
import fk.prof.userapi.model.tree.TreeViewResponse.CpuSampleCalleesTreeViewResponse;
import fk.prof.userapi.model.tree.TreeViewResponse.CpuSampleCallersTreeViewResponse;

import java.io.IOException;
import java.util.List;

/**
 * Created by gaurav.ashok on 07/08/17.
 */
public class CustomSerializers {

    public static void registerSerializers(ObjectMapper om) {
        SimpleModule module = new SimpleModule("customSerializers", new Version(1, 0, 0, null, null, null));
        module.addSerializer(CpuSampleCallersTreeViewResponse.class, new TreeViewResponseSerializer(new IndexedNodeSerializer(new ProtoSerializers.CpuSampleFrameNodeWithStackSampleSerializer())));
        module.addSerializer(CpuSampleCalleesTreeViewResponse.class, new TreeViewResponseSerializer(new IndexedNodeSerializer(new ProtoSerializers.CpuSampleFrameNodeWithCpuSampleSerializer())));
        om.registerModule(module);
    }

    static class IndexedNodeSerializer extends StdSerializer<IndexedTreeNode> {

        private StdSerializer dataSerializer;

        IndexedNodeSerializer(StdSerializer dataSerializer) {
            super(IndexedTreeNode.class);
            this.dataSerializer = dataSerializer;
        }

        @Override
        public void serialize(IndexedTreeNode value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeFieldName("data");
            dataSerializer.serialize(value.getData(), gen, serializers);
            List<IndexedTreeNode> children = value.getChildren();
            if(children != null) {
                gen.writeFieldName("chld");
                gen.writeStartObject();
                for(IndexedTreeNode chld : children) {
                    gen.writeFieldName(String.valueOf(chld.getIdx()));
                    this.serialize(chld, gen, serializers);
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        }
    }

    static class TreeViewResponseSerializer extends StdSerializer<TreeViewResponse> {

        private StdSerializer indexedNodeSerializer;

        TreeViewResponseSerializer(StdSerializer indexedNodeSerializer) {
            super(TreeViewResponse.class);
            this.indexedNodeSerializer = indexedNodeSerializer;
        }

        @Override
        public void serialize(TreeViewResponse value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeFieldName("method_lookup");
            serializers.defaultSerializeValue(value.getMethodLookup(), gen);

            List<IndexedTreeNode<FrameNode>> nodes = value.getTree();

            for (IndexedTreeNode node : nodes) {
                gen.writeFieldName(String.valueOf(node.getIdx()));
                indexedNodeSerializer.serialize(node, gen, serializers);
            }
        }
    }
}

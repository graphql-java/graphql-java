package graphql.schema;

import graphql.util.NodeAdapter;
import graphql.util.NodeLocation;

import java.util.List;
import java.util.Map;

public class GraphQLSchemaElementAdapter implements NodeAdapter<GraphQLSchemaElement> {

    public static final GraphQLSchemaElementAdapter SCHEMA_ELEMENT_ADAPTER = new GraphQLSchemaElementAdapter();

    private GraphQLSchemaElementAdapter() {

    }

    @Override
    public Map<String, List<GraphQLSchemaElement>> getNamedChildren(GraphQLSchemaElement node) {
//        Map<String, List<GraphQLSchemaElement>> result = new LinkedHashMap<>();
//        result.put(null, node.getChildrenWithTypeReferences());
//        return result;
        return null;
    }

    @Override
    public GraphQLSchemaElement withNewChildren(GraphQLSchemaElement node, Map<String, List<GraphQLSchemaElement>> newChildren) {
        return null;
    }

    @Override
    public GraphQLSchemaElement removeChild(GraphQLSchemaElement node, NodeLocation location) {
        return null;
    }
}

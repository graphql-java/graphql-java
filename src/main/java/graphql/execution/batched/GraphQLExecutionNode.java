package graphql.execution.batched;

import graphql.language.Field;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.Map;

class GraphQLExecutionNode {

    private final GraphQLObjectType parentType;
    private final Map<String, List<Field>> fields;
    private final List<GraphQLExecutionNodeDatum> data;

    public GraphQLExecutionNode(GraphQLObjectType parentType,
                                Map<String, List<Field>> fields,
                                List<GraphQLExecutionNodeDatum> data) {
        this.parentType = parentType;
        this.fields = fields;
        this.data = data;
    }

    public GraphQLObjectType getParentType() {
        return parentType;
    }

    public Map<String, List<Field>> getFields() {
        return fields;
    }

    public List<GraphQLExecutionNodeDatum> getData() {
        return data;
    }
}

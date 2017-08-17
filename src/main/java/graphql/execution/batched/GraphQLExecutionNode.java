package graphql.execution.batched;

import graphql.language.Field;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.Map;

class GraphQLExecutionNode {

    private final GraphQLObjectType type;
    private final Map<String, List<Field>> fields;
    private final List<ResultContainer> data;

    public GraphQLExecutionNode(GraphQLObjectType type,
                                Map<String, List<Field>> fields,
                                List<ResultContainer> data) {
        this.type = type;
        this.fields = fields;
        this.data = data;
    }

    public GraphQLObjectType getType() {
        return type;
    }

    public Map<String, List<Field>> getFields() {
        return fields;
    }

    public List<ResultContainer> getData() {
        return data;
    }
}

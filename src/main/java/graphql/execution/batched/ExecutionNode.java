package graphql.execution.batched;

import graphql.language.Field;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.Map;

class ExecutionNode {

    private final GraphQLObjectType type;
    private final Map<String, List<Field>> fields;
    private final List<MapOrList> data;
    private final List<Object> sources;

    public ExecutionNode(GraphQLObjectType type,
                         Map<String, List<Field>> fields,
                         List<MapOrList> data,
                         List<Object> sources) {
        this.type = type;
        this.fields = fields;
        this.data = data;
        this.sources = sources;
    }

    public GraphQLObjectType getType() {
        return type;
    }

    public Map<String, List<Field>> getFields() {
        return fields;
    }

    public List<MapOrList> getParentResults() {
        return data;
    }

    public List<Object> getSources() {
        return sources;
    }
}

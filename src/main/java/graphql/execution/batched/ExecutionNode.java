package graphql.execution.batched;

import graphql.language.Field;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.Map;

class ExecutionNode {

    private final GraphQLObjectType type;
    private final Map<String, List<Field>> fields;
    private final List<MapOrList> parentResults;
    private final List<Object> sources;

    public ExecutionNode(GraphQLObjectType type,
                         Map<String, List<Field>> fields,
                         List<MapOrList> parentResults,
                         List<Object> sources) {
        this.type = type;
        this.fields = fields;
        this.parentResults = parentResults;
        this.sources = sources;
    }

    public GraphQLObjectType getType() {
        return type;
    }

    public Map<String, List<Field>> getFields() {
        return fields;
    }

    public List<MapOrList> getParentResults() {
        return parentResults;
    }

    public List<Object> getSources() {
        return sources;
    }
}

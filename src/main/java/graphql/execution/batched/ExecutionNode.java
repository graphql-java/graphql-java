package graphql.execution.batched;

import graphql.execution.ExecutionInfo;
import graphql.language.Field;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.Map;

@Deprecated
class ExecutionNode {

    private final GraphQLObjectType type;
    private final ExecutionInfo executionInfo;
    private final Map<String, List<Field>> fields;
    private final List<MapOrList> parentResults;
    private final List<Object> sources;

    public ExecutionNode(GraphQLObjectType type,
                         ExecutionInfo executionInfo,
                         Map<String, List<Field>> fields,
                         List<MapOrList> parentResults,
                         List<Object> sources) {
        this.type = type;
        this.executionInfo = executionInfo;
        this.fields = fields;
        this.parentResults = parentResults;
        this.sources = sources;
    }

    public GraphQLObjectType getType() {
        return type;
    }

    public ExecutionInfo getExecutionInfo() {
        return executionInfo;
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

package graphql.execution.batched;

import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedFields;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.Map;

@Deprecated
class ExecutionNode {

    private final GraphQLObjectType type;
    private final ExecutionStepInfo executionStepInfo;
    private final Map<String, MergedFields> fields;
    private final List<MapOrList> parentResults;
    private final List<Object> sources;

    public ExecutionNode(GraphQLObjectType type,
                         ExecutionStepInfo executionStepInfo,
                         Map<String, MergedFields> fields,
                         List<MapOrList> parentResults,
                         List<Object> sources) {
        this.type = type;
        this.executionStepInfo = executionStepInfo;
        this.fields = fields;
        this.parentResults = parentResults;
        this.sources = sources;
    }

    public GraphQLObjectType getType() {
        return type;
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }

    public Map<String, MergedFields> getFields() {
        return fields;
    }

    public List<MapOrList> getParentResults() {
        return parentResults;
    }

    public List<Object> getSources() {
        return sources;
    }
}

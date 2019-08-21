package graphql.analysis;

import graphql.Internal;
import graphql.language.Node;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

import java.util.Map;

@Internal
public class QueryVisitorFieldArgumentValueEnvironmentImpl implements QueryVisitorFieldArgumentValueEnvironment {

    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLArgument graphQLArgument;
    private final QueryVisitorFieldArgumentInputValue argumentInputValue;
    private final TraverserContext<Node> traverserContext;
    private final GraphQLSchema schema;
    private final Map<String, Object> variables;

    public QueryVisitorFieldArgumentValueEnvironmentImpl(GraphQLSchema schema, GraphQLFieldDefinition fieldDefinition, GraphQLArgument graphQLArgument, QueryVisitorFieldArgumentInputValue argumentInputValue, TraverserContext<Node> traverserContext, Map<String, Object> variables) {
        this.fieldDefinition = fieldDefinition;
        this.graphQLArgument = graphQLArgument;
        this.argumentInputValue = argumentInputValue;
        this.traverserContext = traverserContext;
        this.schema = schema;
        this.variables = variables;
    }

    @Override
    public GraphQLSchema getSchema() {
        return schema;
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    @Override
    public GraphQLArgument getGraphQLArgument() {
        return graphQLArgument;
    }

    @Override
    public QueryVisitorFieldArgumentInputValue getArgumentInputValue() {
        return argumentInputValue;
    }

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    @Override
    public TraverserContext<Node> getTraverserContext() {
        return traverserContext;
    }

    @Override
    public String toString() {
        return "QueryVisitorFieldArgumentValueEnvironmentImpl{" +
                "fieldDefinition=" + fieldDefinition +
                ", graphQLArgument=" + graphQLArgument +
                ", argumentInputValue=" + argumentInputValue +
                ", traverserContext=" + traverserContext +
                ", schema=" + schema +
                '}';
    }
}

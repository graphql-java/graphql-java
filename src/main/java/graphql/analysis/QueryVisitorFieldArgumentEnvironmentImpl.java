package graphql.analysis;

import graphql.Internal;
import graphql.language.Argument;
import graphql.language.Node;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

import java.util.Map;

@Internal
public class QueryVisitorFieldArgumentEnvironmentImpl implements QueryVisitorFieldArgumentEnvironment {

    private final GraphQLFieldDefinition fieldDefinition;
    private final Argument argument;
    private final GraphQLArgument graphQLArgument;
    private final Map<String, Object> arguments;
    private final Map<String, Object> variables;
    private final QueryVisitorFieldEnvironment parentEnvironment;
    private final TraverserContext<Node> traverserContext;
    private final GraphQLSchema schema;

    public QueryVisitorFieldArgumentEnvironmentImpl(GraphQLFieldDefinition fieldDefinition, Argument argument, GraphQLArgument graphQLArgument, Map<String, Object> arguments, Map<String, Object> variables, QueryVisitorFieldEnvironment parentEnvironment, TraverserContext<Node> traverserContext, GraphQLSchema schema) {
        this.fieldDefinition = fieldDefinition;
        this.argument = argument;
        this.graphQLArgument = graphQLArgument;
        this.arguments = arguments;
        this.variables = variables;
        this.parentEnvironment = parentEnvironment;
        this.traverserContext = traverserContext;
        this.schema = schema;
    }

    @Override
    public GraphQLSchema getSchema() {
        return schema;
    }

    @Override
    public Argument getArgument() {
        return argument;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    @Override
    public GraphQLArgument getGraphQLArgument() {
        return graphQLArgument;
    }

    @Override
    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    @Override
    public QueryVisitorFieldEnvironment getParentEnvironment() {
        return parentEnvironment;
    }

    @Override
    public TraverserContext<Node> getTraverserContext() {
        return traverserContext;
    }

    @Override
    public String toString() {
        return "QueryVisitorFieldArgumentEnvironmentImpl{" +
                "argument=" + argument +
                ", graphQLArgument=" + graphQLArgument +
                ", arguments=" + arguments +
                ", variables=" + variables +
                ", parentEnvironment=" + parentEnvironment +
                ", traverserContext=" + traverserContext +
                ", schema=" + schema +
                '}';
    }
}

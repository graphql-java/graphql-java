package graphql.analysis;

import graphql.language.Argument;
import graphql.language.Node;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

import java.util.Map;

public interface QueryVisitorFieldArgumentEnvironment {

    GraphQLSchema getSchema();

    GraphQLFieldDefinition getFieldDefinition();

    GraphQLArgument getGraphQLArgument();

    Argument getArgument();

    Map<String, Object> getArguments();

    Map<String, Object> getVariables();

    QueryVisitorFieldEnvironment getParentEnvironment();

    TraverserContext<Node> getTraverserContext();
}

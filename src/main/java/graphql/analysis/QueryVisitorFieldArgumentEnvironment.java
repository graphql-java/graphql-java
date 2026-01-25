package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Argument;
import graphql.language.Node;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

@PublicApi
@NullMarked
public interface QueryVisitorFieldArgumentEnvironment {

    GraphQLSchema getSchema();

    GraphQLFieldDefinition getFieldDefinition();

    GraphQLArgument getGraphQLArgument();

    Argument getArgument();

    Object getArgumentValue();

    Map<String, Object> getVariables();

    QueryVisitorFieldEnvironment getParentEnvironment();

    TraverserContext<Node> getTraverserContext();
}

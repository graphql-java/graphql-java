package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Node;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

@PublicApi
@NullMarked
public interface QueryVisitorFieldArgumentValueEnvironment {

    GraphQLSchema getSchema();

    GraphQLFieldDefinition getFieldDefinition();

    GraphQLArgument getGraphQLArgument();

    QueryVisitorFieldArgumentInputValue getArgumentInputValue();

    Map<String, Object> getVariables();

    TraverserContext<Node> getTraverserContext();

}

package graphql.analysis;

import graphql.language.Value;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputType;

/**
 * This describes the tree structure that forms from a argument input type,
 * especially with `input ComplexType { ....}` types that might in turn contain other complex
 * types and hence form a tree of values.
 */
public interface QueryVisitorFieldArgumentInputValue {
    
    GraphQLFieldDefinition getGraphQLFieldDefinition();

    GraphQLArgument getGraphQLArgument();

    QueryVisitorFieldArgumentInputValue getParent();

    String getName();

    GraphQLInputType getInputType();

    Value getValue();
}

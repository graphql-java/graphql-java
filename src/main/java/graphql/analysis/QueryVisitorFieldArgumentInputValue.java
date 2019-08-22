package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Value;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInputValueDefinition;

/**
 * This describes the tree structure that forms from a argument input type,
 * especially with `input ComplexType { ....}` types that might in turn contain other complex
 * types and hence form a tree of values.
 */
@PublicApi
public interface QueryVisitorFieldArgumentInputValue {

    QueryVisitorFieldArgumentInputValue getParent();

    GraphQLInputValueDefinition getInputValueDefinition();

    String getName();

    GraphQLInputType getInputType();

    Value getValue();
}

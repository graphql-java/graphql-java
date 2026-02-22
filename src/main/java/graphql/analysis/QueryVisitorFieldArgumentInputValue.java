package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Value;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInputValueDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * This describes the tree structure that forms from a argument input type,
 * especially with `input ComplexType { ....}` types that might in turn contain other complex
 * types and hence form a tree of values.
 */
@PublicApi
@NullMarked
public interface QueryVisitorFieldArgumentInputValue {

    @Nullable QueryVisitorFieldArgumentInputValue getParent();

    GraphQLInputValueDefinition getInputValueDefinition();

    String getName();

    GraphQLInputType getInputType();

    @Nullable Value getValue();
}

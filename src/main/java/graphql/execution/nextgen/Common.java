package graphql.execution.nextgen;

import graphql.Internal;
import graphql.execution.MissingRootTypeException;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.Optional;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public class Common {

    public static GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
        OperationDefinition.Operation operation = operationDefinition.getOperation();
        if (operation == MUTATION) {
            GraphQLObjectType mutationType = graphQLSchema.getMutationType();
            return Optional.ofNullable(mutationType)
                    .orElseThrow(() -> new MissingRootTypeException("Schema is not configured for mutations.", operationDefinition.getSourceLocation()));
        } else if (operation == QUERY) {
            GraphQLObjectType queryType = graphQLSchema.getQueryType();
            return Optional.ofNullable(queryType)
                    .orElseThrow(() -> new MissingRootTypeException("Schema does not define the required query root type.", operationDefinition.getSourceLocation()));
        } else if (operation == SUBSCRIPTION) {
            GraphQLObjectType subscriptionType = graphQLSchema.getSubscriptionType();
            return Optional.ofNullable(subscriptionType)
                    .orElseThrow(() -> new MissingRootTypeException("Schema is not configured for subscriptions.", operationDefinition.getSourceLocation()));
        } else {
            return assertShouldNeverHappen("Unhandled case.  An extra operation enum has been added without code support");
        }
    }
}

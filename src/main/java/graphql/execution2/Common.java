package graphql.execution2;

import graphql.execution.MissingRootTypeException;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;

public class Common {

    public static GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
        OperationDefinition.Operation operation = operationDefinition.getOperation();
        if (operation == MUTATION) {
            GraphQLObjectType mutationType = graphQLSchema.getMutationType();
            if (mutationType == null) {
                throw new MissingRootTypeException("Schema is not configured for mutations.", operationDefinition.getSourceLocation());
            }
            return mutationType;
        } else if (operation == QUERY) {
            GraphQLObjectType queryType = graphQLSchema.getQueryType();
            if (queryType == null) {
                throw new MissingRootTypeException("Schema does not define the required query root type.", operationDefinition.getSourceLocation());
            }
            return queryType;
        } else if (operation == SUBSCRIPTION) {
            GraphQLObjectType subscriptionType = graphQLSchema.getSubscriptionType();
            if (subscriptionType == null) {
                throw new MissingRootTypeException("Schema is not configured for subscriptions.", operationDefinition.getSourceLocation());
            }
            return subscriptionType;
        } else {
            return assertShouldNeverHappen("Unhandled case.  An extra operation enum has been added without code support");
        }
    }


    public static <T> List<List<T>> transposeMatrix(List<? extends List<T>> matrix) {
        int rowCount = matrix.size();
        int colCount = matrix.get(0).size();
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < colCount; j++) {
                T val = matrix.get(i).get(j);
                if (result.size() <= j) {
                    result.add(j, new ArrayList());
                }
                result.get(j).add(i, val);
            }
        }
        return result;
    }

    public static <T> CompletableFuture<List<T>> flatList(CompletableFuture<List<List<T>>> cf) {
        return cf.thenApply(Common::flatList);
    }

    public static <T> List<T> flatList(List<List<T>> listLists) {
        return listLists.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

}

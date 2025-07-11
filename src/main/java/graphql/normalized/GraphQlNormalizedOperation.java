package graphql.normalized;

import graphql.PublicApi;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.schema.GraphQLFieldsContainer;

/**
 * A {@link GraphQlNormalizedOperation} represents a normalized operation in GraphQL.
 * It is used to handle the execution of GraphQL operations according to the specification,
 * including merging duplicate fields and handling type detection for fields that may correspond
 * to multiple object types.
 */
@PublicApi
public interface GraphQlNormalizedOperation {
    /**
     * This will find a {@link GraphQlNormalizedField} given a merged field and a result path.  If this does not find a field it will assert with an exception
     *
     * @param mergedField     the merged field
     * @param fieldsContainer the containing type of that field
     * @param resultPath      the result path in play
     *
     * @return the ExecutableNormalizedField
     */
    GraphQlNormalizedField getGraphQlNormalizedField(MergedField mergedField, GraphQLFieldsContainer fieldsContainer, ResultPath resultPath);
}

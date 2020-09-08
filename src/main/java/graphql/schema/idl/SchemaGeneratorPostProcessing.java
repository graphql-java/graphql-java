package graphql.schema.idl;

import graphql.PublicSpi;
import graphql.schema.GraphQLSchema;

/**
 * These are called by the {@link SchemaGenerator} after a valid schema has been built
 * and they can then adjust it accordingly with some sort of post processing.
 */
@PublicSpi
public interface SchemaGeneratorPostProcessing {

    /**
     * Called to transform the schema from its built state into something else
     *
     * @param originalSchema the original built schema
     *
     * @return a non null schema
     */
    GraphQLSchema process(GraphQLSchema originalSchema);
}

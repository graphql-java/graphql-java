package graphql.execution;

import graphql.GraphQLError;
import graphql.PublicSpi;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.function.Consumer;

/**
 * This is called when an exception is thrown during {@link graphql.schema.DataFetcher#get(DataFetchingEnvironment)} execution
 */
@PublicSpi
public interface DataFetcherExceptionHandler extends Consumer<DataFetcherExceptionHandlerParameters> {

    /**
     * When an exception during a call to a {@link DataFetcher} then this handler
     * is called back to shape the error that should be placed in the list of errors
     * via {@link ExecutionContext#addError(GraphQLError)}
     *
     * @param handlerParameters the parameters to this callback
     */
    @Override
    void accept(DataFetcherExceptionHandlerParameters handlerParameters);
}

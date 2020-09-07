package graphql.execution;

import graphql.PublicApi;

@PublicApi
public class DataFetcherResultUnboxer implements ValueUnboxer {
    @Override
    public Object unbox(Object object, ValueUnboxingContext context) {
        if (object instanceof DataFetcherResult) {
            DataFetcherResult<?> dataFetcherResult = (DataFetcherResult<?>) object;

            dataFetcherResult.getErrors().forEach(context::addError);

            if (dataFetcherResult.getLocalContext() != null) {
                context.setLocalContext(dataFetcherResult.getLocalContext());
            }

            return context.unbox(dataFetcherResult.getData());
        } return object;
    }
}

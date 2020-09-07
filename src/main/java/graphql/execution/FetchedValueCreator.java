package graphql.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import graphql.GraphQLError;
import graphql.execution.ValueUnboxer.ValueUnboxingContext;
import graphql.schema.DataFetchingEnvironment;

public class FetchedValueCreator {
    private FetchedValueCreator() {}

    public static FetchedValue unbox(
            ValueUnboxer unboxer,
            BiConsumer<Exception, ValueUnboxingContext> exceptionProcessor,
            DataFetchingEnvironment dataFetchingEnvironment,
            Object object
    ) {
        class ValueUnboxingContextImpl implements ValueUnboxingContext {
            private Object localContext = dataFetchingEnvironment.getLocalContext();
            private final List<GraphQLError> graphQLErrors = new ArrayList<>(1);

            @Override
            public Object unbox(Object object) {
                return unboxer.unbox(object, this);
            }

            @Override
            public void addError(GraphQLError error) {
                graphQLErrors.add(error);
            }

            @Override
            public void setLocalContext(Object localContext) {
                this.localContext = localContext;
            }

            @Override
            public DataFetchingEnvironment getDataFetchingEnvironment() {
                return dataFetchingEnvironment;
            }
        }
        ValueUnboxingContextImpl valueUnboxingContext = new ValueUnboxingContextImpl();

        Object fetchedValue = getFetchedValue(unboxer, object, exceptionProcessor, valueUnboxingContext);

        return FetchedValue.newFetchedValue()
                .errors(valueUnboxingContext.graphQLErrors)
                .rawFetchedValue(object)
                .fetchedValue(fetchedValue)
                .localContext(valueUnboxingContext.localContext)
                .dataFetchingEnvironment(dataFetchingEnvironment)
                .build();
    }

    private static Object getFetchedValue(
            ValueUnboxer unboxer,
            Object object,
            BiConsumer<Exception, ValueUnboxingContext> exceptionConsumer,
            ValueUnboxingContext valueUnboxingContext
    ) {
        try {
            return unboxer.unbox(object, valueUnboxingContext);
        } catch (Exception e) {
            exceptionConsumer.accept(e, valueUnboxingContext);
            return null;
        }
    }
}

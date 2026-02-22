package graphql.execution;

import graphql.PublicApi;
import graphql.language.SourceLocation;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * The parameters available to {@link DataFetcherExceptionHandler}s
 */
@PublicApi
@NullMarked
public class DataFetcherExceptionHandlerParameters {

    private final DataFetchingEnvironment dataFetchingEnvironment;
    private final Throwable exception;

    private DataFetcherExceptionHandlerParameters(Builder builder) {
        this.exception = builder.exception;
        this.dataFetchingEnvironment = builder.dataFetchingEnvironment;
    }

    public Throwable getException() {
        return exception;
    }

    public ResultPath getPath() {
        return dataFetchingEnvironment.getExecutionStepInfo().getPath();
    }

    public DataFetchingEnvironment getDataFetchingEnvironment() {
        return dataFetchingEnvironment;
    }

    public MergedField getField() {
        return dataFetchingEnvironment.getMergedField();
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return dataFetchingEnvironment.getFieldDefinition();
    }

    public Map<String, Object> getArgumentValues() {
        return dataFetchingEnvironment.getArguments();
    }

    public SourceLocation getSourceLocation() {
        return assertNotNull(getField().getSingleField().getSourceLocation(), "source location must be present");
    }

    public static Builder newExceptionParameters() {
        return new Builder();
    }

    @NullUnmarked
    public static class Builder {
        DataFetchingEnvironment dataFetchingEnvironment;
        Throwable exception;

        private Builder() {
        }

        public Builder dataFetchingEnvironment(DataFetchingEnvironment dataFetchingEnvironment) {
            this.dataFetchingEnvironment = dataFetchingEnvironment;
            return this;
        }

        public Builder exception(Throwable exception) {
            this.exception = exception;
            return this;
        }

        public DataFetcherExceptionHandlerParameters build() {
            return new DataFetcherExceptionHandlerParameters(this);
        }
    }
}

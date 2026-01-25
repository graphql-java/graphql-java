package graphql.analysis;

import graphql.PublicApi;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

/**
 * The query complexity info.
 */
@PublicApi
@NullMarked
public class QueryComplexityInfo {

    private final int complexity;
    private final InstrumentationValidationParameters instrumentationValidationParameters;
    private final InstrumentationExecuteOperationParameters instrumentationExecuteOperationParameters;

    private QueryComplexityInfo(Builder builder) {
        this.complexity = builder.complexity;
        this.instrumentationValidationParameters = builder.instrumentationValidationParameters;
        this.instrumentationExecuteOperationParameters = builder.instrumentationExecuteOperationParameters;
    }

    /**
     * This returns the query complexity.
     *
     * @return the query complexity
     */
    public int getComplexity() {
        return complexity;
    }

    /**
     * This returns the instrumentation validation parameters.
     *
     * @return the instrumentation validation parameters.
     */
    public InstrumentationValidationParameters getInstrumentationValidationParameters() {
        return instrumentationValidationParameters;
    }

    /**
     * This returns the instrumentation execute operation parameters.
     *
     * @return the instrumentation execute operation parameters.
     */
    public InstrumentationExecuteOperationParameters getInstrumentationExecuteOperationParameters() {
        return instrumentationExecuteOperationParameters;
    }

    @Override
    public String toString() {
        return "QueryComplexityInfo{" +
                "complexity=" + complexity +
                '}';
    }

    /**
     * @return a new {@link QueryComplexityInfo} builder
     */
    public static Builder newQueryComplexityInfo() {
        return new Builder();
    }

    @PublicApi
    @NullUnmarked
    public static class Builder {

        private int complexity;
        private InstrumentationValidationParameters instrumentationValidationParameters;
        private InstrumentationExecuteOperationParameters instrumentationExecuteOperationParameters;

        private Builder() {
        }

        /**
         * The query complexity.
         *
         * @param complexity the query complexity
         *
         * @return this builder
         */
        public Builder complexity(int complexity) {
            this.complexity = complexity;
            return this;
        }

        /**
         * The instrumentation validation parameters.
         *
         * @param parameters the instrumentation validation parameters.
         *
         * @return this builder
         */
        public Builder instrumentationValidationParameters(InstrumentationValidationParameters parameters) {
            this.instrumentationValidationParameters = parameters;
            return this;
        }

        public Builder instrumentationExecuteOperationParameters(InstrumentationExecuteOperationParameters instrumentationExecuteOperationParameters) {
            this.instrumentationExecuteOperationParameters = instrumentationExecuteOperationParameters;
            return this;
        }

        /**
         * @return a built {@link QueryComplexityInfo} object
         */
        public QueryComplexityInfo build() {
            return new QueryComplexityInfo(this);
        }
    }
}

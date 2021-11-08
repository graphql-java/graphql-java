package graphql.analysis;

import graphql.PublicApi;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;

/**
 * The query complexity info.
 */
@PublicApi
public class QueryComplexityInfo {

    private final int complexity;
    private InstrumentationValidationParameters instrumentationValidationParameters;

    private QueryComplexityInfo(int complexity, InstrumentationValidationParameters parameters) {
        this.complexity = complexity;
        this.instrumentationValidationParameters = parameters;
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
    public static class Builder {

        private int complexity;
        private InstrumentationValidationParameters instrumentationValidationParameters;

        private Builder() {
        }

        /**
         * The query complexity.
         *
         * @param complexity the query complexity
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
         * @return this builder
         */
        public Builder instrumentationValidationParameters(InstrumentationValidationParameters parameters) {
            this.instrumentationValidationParameters = parameters;
            return this;
        }

        /**
         * @return a built {@link QueryComplexityInfo} object
         */
        public QueryComplexityInfo build() {
            return new QueryComplexityInfo(complexity, instrumentationValidationParameters);
        }
    }
}

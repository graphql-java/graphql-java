package graphql.util.querygenerator;

public class QueryGeneratorOptions {
    private final int maxFieldCount;

    private static final int MAX_FIELD_COUNT_LIMIT = 10_000;

    public QueryGeneratorOptions(int maxFieldCount) {
        this.maxFieldCount = maxFieldCount;
    }

    public int getMaxFieldCount() {
        return maxFieldCount;
    }


    public static class QueryGeneratorOptionsBuilder {
        private int maxFieldCount;

        QueryGeneratorOptionsBuilder maxFieldCount(int maxFieldCount) {
            if (maxFieldCount < 0) {
                throw new IllegalArgumentException("Max field count cannot be negative");
            }
            if (maxFieldCount > MAX_FIELD_COUNT_LIMIT) {
                throw new IllegalArgumentException("Max field count cannot exceed " + MAX_FIELD_COUNT_LIMIT);
            }
            this.maxFieldCount = maxFieldCount;
            return this;
        }

        public QueryGeneratorOptions build() {
            return new QueryGeneratorOptions(
                    maxFieldCount
            );
        }
    }

    public static QueryGeneratorOptions.QueryGeneratorOptionsBuilder builder() {
        return new QueryGeneratorOptions.QueryGeneratorOptionsBuilder();
    }

    public static QueryGeneratorOptions.QueryGeneratorOptionsBuilder defaultOptions() {
        return new QueryGeneratorOptions.QueryGeneratorOptionsBuilder()
                .maxFieldCount(MAX_FIELD_COUNT_LIMIT);
    }
}

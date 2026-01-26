package graphql.execution;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.TypeResolutionEnvironment;
import graphql.collect.ImmutableMapWithNullValues;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullUnmarked;

import java.util.Map;
import java.util.function.Supplier;

/**
 * This class is a classic builder style one that SHOULD have been on have been on {@link TypeResolutionEnvironment}
 * but for legacy reasons was not.  So it acts as the builder of {@link TypeResolutionEnvironment} objects
 */
@Internal
@NullMarked
public class TypeResolutionParameters {

    private final MergedField field;
    private final GraphQLType fieldType;
    private final @Nullable Object value;
    private final Supplier<ImmutableMapWithNullValues<String, Object>> argumentValues;
    private final GraphQLSchema schema;
    private final @Nullable Object context;
    private final @Nullable Object localContext;
    private final GraphQLContext graphQLContext;
    private final DataFetchingFieldSelectionSet selectionSet;

    private TypeResolutionParameters(Builder builder) {
        this.field = builder.field;
        this.fieldType = builder.fieldType;
        this.value = builder.value;
        this.argumentValues = builder.argumentValues;
        this.schema = builder.schema;
        this.context = builder.context;
        this.graphQLContext = builder.graphQLContext;
        this.localContext = builder.localContext;
        this.selectionSet = builder.selectionSet;
    }

    public MergedField getField() {
        return field;
    }

    public GraphQLType getFieldType() {
        return fieldType;
    }

    public @Nullable Object getValue() {
        return value;
    }

    public Map<String, Object> getArgumentValues() {
        return argumentValues.get();
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public DataFetchingFieldSelectionSet getSelectionSet() {
        return selectionSet;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    /**
     * @return the legacy context object
     *
     * @deprecated use {@link #getGraphQLContext()} instead
     */
    @Deprecated(since = "2021-07-05")
    public @Nullable Object getContext() {
        return context;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    public @Nullable Object getLocalContext() {
        return localContext;
    }

    @NullUnmarked
    public static class Builder {

        private MergedField field;
        private GraphQLType fieldType;
        private Object value;
        private Supplier<ImmutableMapWithNullValues<String, Object>> argumentValues;
        private GraphQLSchema schema;
        private Object context;
        private GraphQLContext graphQLContext;
        private Object localContext;
        private DataFetchingFieldSelectionSet selectionSet;

        public Builder field(MergedField field) {
            this.field = field;
            return this;
        }

        public Builder fieldType(GraphQLType fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder argumentValues(Supplier<Map<String, Object>> argumentValues) {
            this.argumentValues = () -> ImmutableMapWithNullValues.copyOf(argumentValues.get());
            return this;
        }

        public Builder schema(GraphQLSchema schema) {
            this.schema = schema;
            return this;
        }

        @Deprecated(since = "2021-07-05")
        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        public Builder graphQLContext(GraphQLContext context) {
            this.graphQLContext = context;
            return this;
        }

        public Builder localContext(Object localContext) {
            this.localContext = localContext;
            return this;
        }

        public Builder selectionSet(DataFetchingFieldSelectionSet selectionSet) {
            this.selectionSet = selectionSet;
            return this;
        }

        public TypeResolutionEnvironment build() {
            // this build should have always been in TypeResolutionEnvironment but this little workaround improves it a smidge,
            // and we can fix it up later so this class is redundant
            return new TypeResolutionEnvironment(new TypeResolutionParameters(this));
        }
    }
}

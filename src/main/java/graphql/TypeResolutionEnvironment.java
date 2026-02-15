package graphql;

import graphql.collect.ImmutableMapWithNullValues;
import graphql.execution.DataFetcherResult;
import graphql.execution.MergedField;
import graphql.execution.TypeResolutionParameters;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * This is passed to a {@link graphql.schema.TypeResolver} to help with object type resolution.
 * <p>
 * See {@link graphql.schema.TypeResolver#getType} for how this is used
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
@PublicApi
@NullMarked
public class TypeResolutionEnvironment {

    private final @Nullable Object object;
    private final Supplier<ImmutableMapWithNullValues<String, Object>> arguments;
    private final MergedField field;
    private final GraphQLType fieldType;
    private final GraphQLSchema schema;
    private final @Nullable Object context;
    private final GraphQLContext graphQLContext;
    private final @Nullable Object localContext;
    private final DataFetchingFieldSelectionSet fieldSelectionSet;

    @Internal
    public TypeResolutionEnvironment(TypeResolutionParameters parameters) {
        this.object = parameters.getValue();
        this.arguments = () -> ImmutableMapWithNullValues.copyOf(parameters.getArgumentValues());
        this.field = parameters.getField();
        this.fieldType = parameters.getFieldType();
        this.schema = parameters.getSchema();
        this.context = parameters.getContext();
        this.graphQLContext = parameters.getGraphQLContext();
        this.localContext = parameters.getLocalContext();
        this.fieldSelectionSet = parameters.getSelectionSet();
    }


    /**
     * You will be passed the specific source object that needs to be resolved into a concrete graphql object type
     *
     * @param <T> you decide what type it is
     *
     * @return the object that needs to be resolved into a specific graphql object type
     */
    @SuppressWarnings("unchecked")
    public @Nullable <T> T getObject() {
        return (T) object;
    }

    /**
     * @return the runtime arguments to this the graphql field
     */
    public Map<String, Object> getArguments() {
        return arguments.get();
    }

    /**
     * @return the graphql field in question
     */
    public MergedField getField() {
        return field;
    }

    /**
     * @return the type of the graphql field, which still be either a {@link graphql.schema.GraphQLUnionType} or a
     * {@link graphql.schema.GraphQLInterfaceType}
     */
    public GraphQLType getFieldType() {
        return fieldType;
    }

    /**
     * @return the graphql schema in question
     */
    public GraphQLSchema getSchema() {
        return schema;
    }

    /**
     * Returns the context object set in via {@link ExecutionInput#getContext()}
     *
     * @param <T> the type to cast the result to
     *
     * @return the context object
     *
     * @deprecated use {@link #getGraphQLContext()} instead
     */
    @Deprecated(since = "2021-12-27")
    public @Nullable <T> T getContext() {
        //noinspection unchecked
        return (T) context;
    }

    /**
     * @return the {@link GraphQLContext} object set in via {@link ExecutionInput#getGraphQLContext()}
     */
    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    /**
     * Returns the local context object set in via {@link DataFetcherResult#getLocalContext()}
     *
     * @param <T> the type to cast the result to
     *
     * @return the local context object
     */
    public @Nullable <T> T getLocalContext() {
        //noinspection unchecked
        return (T) localContext;
    }

    /**
     * @return the {@link DataFetchingFieldSelectionSet} for the current field fetch that needs type resolution
     */
    public DataFetchingFieldSelectionSet getSelectionSet() {
        return fieldSelectionSet;
    }
}

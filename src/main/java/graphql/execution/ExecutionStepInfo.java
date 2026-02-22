package graphql.execution;

import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableMapWithNullValues;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullUnmarked;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.schema.GraphQLTypeUtil.isList;

/**
 * As the graphql query executes, it forms a hierarchy from parent fields (and their type) to their child fields (and their type)
 * until a scalar type is encountered; this class captures that execution type information.
 * <p>
 * The static graphql type system (rightly) does not contain a hierarchy of child to parent types nor the nonnull ness of
 * type instances, so this helper class adds this information during query execution.
 */
@PublicApi
@NullMarked
public class ExecutionStepInfo {

    /*
     * An ExecutionStepInfo represent either a field or a list element inside a list of objects/interfaces/unions.
     *
     * A StepInfo never represent a Scalar/Enum inside a list (e.g. [String]) because GraphQL execution doesn't descend down
     * scalar/enums lists.
     *
     */

    /**
     * If this StepInfo represent a field the type is equal to fieldDefinition.getType()
     * <p>
     * if this StepInfo is a list element this type is the actual current list element. For example:
     * Query.pets: [[Pet]] with Pet either a Dog or Cat and the actual result is [[Dog1],[[Cat1]]
     * Then the type is (for a query "{pets{name}}"):
     * [[Pet]] for /pets (representing the field Query.pets, not a list element)
     * [Pet] fot /pets[0]
     * [Pet] for /pets[1]
     * Dog for /pets[0][0]
     * Cat for /pets[1][0]
     * String for /pets[0][0]/name (representing the field Dog.name, not a list element)
     * String for /pets[1][0]/name (representing the field Cat.name, not a list element)
     */
    private final GraphQLOutputType type;

    /**
     * A list element is characterized by having a path ending with an index segment. (ResultPath.isListSegment())
     */
    private final ResultPath path;
    private final @Nullable ExecutionStepInfo parent;

    /**
     * field, fieldDefinition, fieldContainer and arguments differ per field StepInfo.
     * <p>
     * But for list StepInfos these properties are the same as the field returning the list.
     */
    private final MergedField field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLObjectType fieldContainer;
    private final Supplier<ImmutableMapWithNullValues<String, Object>> arguments;

    private ExecutionStepInfo(Builder builder) {
        this.fieldDefinition = builder.fieldDefinition;
        this.field = builder.field;
        this.path = builder.path;
        this.parent = builder.parentInfo;
        this.type = assertNotNull(builder.type, "you must provide a graphql type");
        this.arguments = builder.arguments;
        this.fieldContainer = builder.fieldContainer;
    }

    /*
     * This constructor allows for a slightly ( 1% ish) faster transformation without an intermediate Builder object
     */
    private ExecutionStepInfo(GraphQLOutputType type,
                              ResultPath path,
                              @Nullable ExecutionStepInfo parent,
                              MergedField field,
                              GraphQLFieldDefinition fieldDefinition,
                              GraphQLObjectType fieldContainer,
                              Supplier<ImmutableMapWithNullValues<String, Object>> arguments) {
        this.type = assertNotNull(type, "you must provide a graphql type");
        this.path = path;
        this.parent = parent;
        this.field = field;
        this.fieldDefinition = fieldDefinition;
        this.fieldContainer = fieldContainer;
        this.arguments = arguments;
    }

    /**
     * The GraphQLObjectType where fieldDefinition is defined.
     * Note:
     * For the Introspection field __typename the returned object type doesn't actually contain the fieldDefinition.
     *
     * @return the GraphQLObjectType defining the {@link #getFieldDefinition()}
     */
    public GraphQLObjectType getObjectType() {
        return fieldContainer;
    }

    /**
     * This returns the type for the current step.
     *
     * @return the graphql type in question
     */
    public GraphQLOutputType getType() {
        return type;
    }

    /**
     * This returns the type which is unwrapped if it was {@link GraphQLNonNull} wrapped
     *
     * @return the graphql type in question
     */
    public GraphQLOutputType getUnwrappedNonNullType() {
        return (GraphQLOutputType) GraphQLTypeUtil.unwrapNonNull(this.type);
    }

    /**
     * This returns the type which is unwrapped if it was {@link GraphQLNonNull} wrapped
     * and then cast to the target type.
     *
     * @param <T> for two
     *
     * @return the graphql type in question
     */
    public <T extends GraphQLOutputType> T getUnwrappedNonNullTypeAs() {
        return GraphQLTypeUtil.unwrapNonNullAs(this.type);
    }

    /**
     * This returns the field definition that is in play when this type info was created or null
     * if the type is a root query type
     *
     * @return the field definition or null if there is not one
     */
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    /**
     * This returns the AST fields that matches the {@link #getFieldDefinition()} during execution
     *
     * @return the  merged fields
     */
    public MergedField getField() {
        return field;
    }

    /**
     * @return the {@link ResultPath} to this info
     */
    public ResultPath getPath() {
        return path;
    }

    /**
     * @return true if the type must be nonnull
     */
    public boolean isNonNullType() {
        return GraphQLTypeUtil.isNonNull(this.type);
    }

    /**
     * @return true if the type is a list
     */
    public boolean isListType() {
        return isList(type);
    }

    /**
     * @return the resolved arguments that have been passed to this field
     */
    public Map<String, Object> getArguments() {
        return arguments.get();
    }

    /**
     * Returns the named argument
     *
     * @param name the name of the argument
     * @param <T>  you decide what type it is
     *
     * @return the named argument or null if it's not present
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getArgument(String name) {
        return (T) getArguments().get(name);
    }

    /**
     * @return the parent type information
     */
    public @Nullable ExecutionStepInfo getParent() {
        return parent;
    }

    /**
     * @return true if the type has a parent (most do)
     */
    public boolean hasParent() {
        return parent != null;
    }

    /**
     * This allows you to morph a type into a more specialized form yet return the same
     * parent and non-null ness, for example taking a {@link GraphQLInterfaceType}
     * and turning it into a specific {@link graphql.schema.GraphQLObjectType}
     * after type resolution has occurred
     *
     * @param newType the new type to be
     *
     * @return a new type info with the same
     */
    public ExecutionStepInfo changeTypeWithPreservedNonNull(GraphQLOutputType newType) {
        assertTrue(!GraphQLTypeUtil.isNonNull(newType), "newType can't be non null");
        if (isNonNullType()) {
            return transform(GraphQLNonNull.nonNull(newType));
        } else {
            return transform(newType);
        }
    }

    /**
     * @return the type in graphql SDL format, eg [typeName!]!
     */
    public String simplePrint() {
        return GraphQLTypeUtil.simplePrint(type);
    }

    @Override
    public String toString() {
        return "ExecutionStepInfo{" +
                " path=" + path +
                ", type=" + type +
                ", fieldDefinition=" + fieldDefinition +
                '}';
    }

    @Internal
    ExecutionStepInfo transform(GraphQLOutputType type) {
        return new ExecutionStepInfo(type, path, parent, field, fieldDefinition, fieldContainer, arguments);
    }

    @Internal
    ExecutionStepInfo transform(GraphQLOutputType type, @Nullable ExecutionStepInfo parent, ResultPath path) {
        return new ExecutionStepInfo(type, path, parent, field, fieldDefinition, fieldContainer, arguments);
    }

    public ExecutionStepInfo transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public String getResultKey() {
        return field.getResultKey();
    }

    /**
     * @return a builder of type info
     */
    public static ExecutionStepInfo.Builder newExecutionStepInfo() {
        return new Builder();
    }

    public static ExecutionStepInfo.Builder newExecutionStepInfo(ExecutionStepInfo existing) {
        return new Builder(existing);
    }

    @NullUnmarked
    public static class Builder {
        GraphQLOutputType type;
        ExecutionStepInfo parentInfo;
        GraphQLFieldDefinition fieldDefinition;
        GraphQLObjectType fieldContainer;
        MergedField field;
        ResultPath path;
        Supplier<ImmutableMapWithNullValues<String, Object>> arguments;

        /**
         * @see ExecutionStepInfo#newExecutionStepInfo()
         */
        private Builder() {
            arguments = ImmutableMapWithNullValues::emptyMap;
        }

        private Builder(ExecutionStepInfo existing) {
            this.type = existing.type;
            this.parentInfo = existing.parent;
            this.fieldDefinition = existing.fieldDefinition;
            this.fieldContainer = existing.fieldContainer;
            this.field = existing.field;
            this.path = existing.path;
            this.arguments = existing.arguments;
        }

        public Builder type(GraphQLOutputType type) {
            this.type = type;
            return this;
        }

        public Builder parentInfo(ExecutionStepInfo executionStepInfo) {
            this.parentInfo = executionStepInfo;
            return this;
        }

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder field(MergedField field) {
            this.field = field;
            return this;
        }

        public Builder path(ResultPath resultPath) {
            this.path = resultPath;
            return this;
        }

        public Builder arguments(Supplier<ImmutableMapWithNullValues<String, Object>> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder fieldContainer(GraphQLObjectType fieldContainer) {
            this.fieldContainer = fieldContainer;
            return this;
        }

        public ExecutionStepInfo build() {
            return new ExecutionStepInfo(this);
        }
    }
}

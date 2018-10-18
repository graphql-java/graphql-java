package graphql.execution;

import graphql.PublicApi;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
public class ExecutionStepInfo {

    private final GraphQLType type;
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final ExecutionPath path;
    private final Map<String, Object> arguments;
    private final ExecutionStepInfo parent;

    private ExecutionStepInfo(GraphQLType type, GraphQLFieldDefinition fieldDefinition, Field field, ExecutionPath path, ExecutionStepInfo parent, Map<String, Object> arguments) {
        this.fieldDefinition = fieldDefinition;
        this.field = field;
        this.path = path;
        this.parent = parent;
        this.type = assertNotNull(type, "you must provide a graphql type");
        this.arguments = arguments;

    }

    /**
     * This returns the type for the current step.
     *
     * @return the graphql type in question
     */
    public GraphQLType getType() {
        return type;
    }

    /**
     * This returns the type which is unwrapped if it was {@link GraphQLNonNull} wrapped
     *
     * @return the graphql type in question
     */
    public GraphQLType getUnwrappedNonNullType() {
        return GraphQLTypeUtil.unwrapNonNull(this.type);
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
     * This returns the AST field that matches the {@link #getFieldDefinition()} during execution
     *
     * @return the field
     */
    public Field getField() {
        return field;
    }

    /**
     * @return the {@link ExecutionPath} to this info
     */
    public ExecutionPath getPath() {
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
        return new LinkedHashMap<>(arguments);
    }

    /**
     * Returns the named argument
     *
     * @param name the name of the argument
     * @param <T>  you decide what type it is
     *
     * @return the named argument or null if its not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getArgument(String name) {
        return (T) arguments.get(name);
    }

    /**
     * @return the parent type information
     */
    public ExecutionStepInfo getParent() {
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
    public ExecutionStepInfo changeTypeWithPreservedNonNull(GraphQLType newType) {
        assertTrue(!GraphQLTypeUtil.isNonNull(newType), "newType can't be non null");
        if (isNonNullType()) {
            return new ExecutionStepInfo(GraphQLNonNull.nonNull(newType), fieldDefinition, field, path, this.parent, arguments);
        } else {
            return new ExecutionStepInfo(newType, fieldDefinition, field, path, this.parent, arguments);
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
                ", parent=" + parent +
                ", fieldDefinition=" + fieldDefinition +
                '}';
    }


    /**
     * @return a builder of type info
     */
    public static ExecutionStepInfo.Builder newExecutionStepInfo() {
        return new Builder();
    }

    public static class Builder {
        GraphQLType type;
        ExecutionStepInfo parentInfo;
        GraphQLFieldDefinition fieldDefinition;
        Field field;
        ExecutionPath executionPath;
        Map<String, Object> arguments = new LinkedHashMap<>();

        /**
         * @see ExecutionStepInfo#newExecutionStepInfo()
         */
        private Builder() {
        }

        public Builder type(GraphQLType type) {
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

        public Builder field(Field field) {
            this.field = field;
            return this;
        }

        public Builder path(ExecutionPath executionPath) {
            this.executionPath = executionPath;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = new LinkedHashMap<>(arguments == null ? Collections.emptyMap() : arguments);
            return this;
        }

        public ExecutionStepInfo build() {
            return new ExecutionStepInfo(type, fieldDefinition, field, executionPath, parentInfo, arguments);
        }
    }
}

package graphql.execution;

import graphql.PublicApi;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

import static graphql.Assert.assertNotNull;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.isNotWrapped;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

/**
 * As the graphql query executes, it forms a hierarchy from parent fields (and their type) to their child fields (and their type)
 * until a scalar type is encountered; this class captures that execution type information.
 * <p>
 * The static graphql type system (rightly) does not contain a hierarchy of child to parent types nor the nonnull ness of
 * type instances, so this helper class adds this information during query execution.
 */
@PublicApi
public class ExecutionInfo {

    private final GraphQLType type;
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final ExecutionPath path;
    private final boolean typeIsNonNull;
    private final Map<String, Object> arguments;
    private final ExecutionInfo parentType;

    private ExecutionInfo(GraphQLType type, GraphQLFieldDefinition fieldDefinition, Field field, ExecutionPath path, ExecutionInfo parentType, boolean nonNull, Map<String, Object> arguments) {
        this.fieldDefinition = fieldDefinition;
        this.field = field;
        this.path = path;
        this.parentType = parentType;
        this.type = type;
        this.typeIsNonNull = nonNull;
        this.arguments = arguments;
        assertNotNull(this.type, "you must provide a graphql type");
    }

    /**
     * This returns the type which is unwrapped if it was {@link GraphQLNonNull} wrapped
     *
     * @return the graphql type in question
     */
    public GraphQLType getType() {
        return type;
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
     * This will cast the type to a specific graphql type such
     * as {@link graphql.schema.GraphQLObjectType} say
     *
     * @param clazz the class to cast to
     * @param <T>   the type you want to become
     *
     * @return this type cast as that
     */
    @SuppressWarnings("unchecked")
    public <T extends GraphQLType> T castType(Class<T> clazz) {
        return clazz.cast(type);
    }

    /**
     * @return true if the type must be nonnull
     */
    public boolean isNonNullType() {
        return typeIsNonNull;
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
    public ExecutionInfo getParent() {
        return parentType;
    }

    /**
     * @return true if the type has a parent (most do)
     */
    public boolean hasParentType() {
        return parentType != null;
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
    public ExecutionInfo treatAs(GraphQLType newType) {
        return new ExecutionInfo(unwrapNonNull(newType), fieldDefinition, field, path, this.parentType, this.typeIsNonNull, arguments);
    }


    /**
     * graphql types can be wrapped in {@link GraphQLNonNull} and {@link GraphQLList} type wrappers
     * so this method will unwrap the type down to the raw unwrapped type and return that wrapping
     * as a stack, with the top of the stack being the raw underling type.
     *
     * @param type the type to unwrap
     *
     * @return a stack of the type wrapping which will be at least 1 later deep
     */
    public static Stack<GraphQLType> unwrapType(GraphQLType type) {
        type = assertNotNull(type);
        Stack<GraphQLType> decoration = new Stack<>();
        while (true) {
            decoration.push(type);
            if (isNotWrapped(type)) {
                break;
            }
            type = unwrapOne(type);
        }
        return decoration;
    }

    /**
     * graphql types can be wrapped in {@link GraphQLNonNull} and {@link GraphQLList} type wrappers
     * so this method will unwrap the type down to the raw underlying type.
     *
     * @param type the type to unwrap
     *
     * @return the underlying raw type with {@link GraphQLNonNull} and {@link GraphQLList} type wrappers removed
     */
    public static GraphQLType unwrapBaseType(GraphQLType type) {
        return unwrapAll(type);
    }


    /**
     * @return the type in graphql AST format, eg [typeName!]!
     */
    public String toAst() {
        // type info unwraps non nulls - we need it back here
        GraphQLType type = this.getType();
        if (isNonNullType()) {
            type = nonNull(type);
        }
        return GraphQLTypeUtil.getUnwrappedTypeName(type);

    }

    @Override
    public String toString() {
        return "ExecutionInfo{" +
                " path=" + path +
                ", type=" + type +
                ", parentInfo=" + parentType +
                ", typeIsNonNull=" + typeIsNonNull +
                ", fieldDefinition=" + fieldDefinition +
                '}';
    }

    private static GraphQLType unwrapNonNull(GraphQLType type) {
        // its possible to have non nulls wrapping non nulls of things but it must end at some point
        while (isNonNull(type)) {
            type = unwrapOne(type);
        }
        return type;
    }

    /**
     * @return a builder of type info
     */
    public static ExecutionInfo.Builder newExecutionInfo() {
        return new Builder();
    }

    public static class Builder {
        GraphQLType type;
        ExecutionInfo parentInfo;
        GraphQLFieldDefinition fieldDefinition;
        Field field;
        ExecutionPath executionPath;
        Map<String, Object> arguments = new LinkedHashMap<>();

        /**
         * @see ExecutionInfo#newExecutionInfo()
         */
        private Builder() {
        }

        public Builder type(GraphQLType type) {
            this.type = type;
            return this;
        }

        public Builder parentInfo(ExecutionInfo executionInfo) {
            this.parentInfo = executionInfo;
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

        public ExecutionInfo build() {
            if (isNonNull(type)) {
                return new ExecutionInfo(unwrapNonNull(type), fieldDefinition, field, executionPath, parentInfo, true, arguments);
            }
            return new ExecutionInfo(type, fieldDefinition, field, executionPath, parentInfo, false, arguments);
        }
    }
}

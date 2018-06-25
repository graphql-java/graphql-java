package graphql.execution;

import graphql.PublicApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

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
public class ExecutionTypeInfo {

    private final GraphQLType type;
    private final GraphQLFieldDefinition fieldDefinition;
    private final ExecutionPath path;
    private final boolean typeIsNonNull;
    private final ExecutionTypeInfo parentType;

    private ExecutionTypeInfo(GraphQLType type, GraphQLFieldDefinition fieldDefinition, ExecutionPath path, ExecutionTypeInfo parentType, boolean nonNull) {
        this.fieldDefinition = fieldDefinition;
        this.path = path;
        this.parentType = parentType;
        this.type = type;
        this.typeIsNonNull = nonNull;
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
     * @return the parent type information
     */
    public ExecutionTypeInfo getParentTypeInfo() {
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
    public ExecutionTypeInfo treatAs(GraphQLType newType) {
        return new ExecutionTypeInfo(unwrapNonNull(newType), fieldDefinition, path, this.parentType, this.typeIsNonNull);
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
        return "ExecutionTypeInfo{" +
                " path=" + path +
                ", type=" + type +
                ", parentType=" + parentType +
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
    public static ExecutionTypeInfo.Builder newTypeInfo() {
        return new Builder();
    }

    public static class Builder {
        GraphQLType type;
        ExecutionTypeInfo parentType;
        GraphQLFieldDefinition fieldDefinition;
        ExecutionPath executionPath;

        /**
         * @see ExecutionTypeInfo#newTypeInfo()
         */
        private Builder() {
        }

        public Builder type(GraphQLType type) {
            this.type = type;
            return this;
        }

        public Builder parentInfo(ExecutionTypeInfo typeInfo) {
            this.parentType = typeInfo;
            return this;
        }

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public Builder path(ExecutionPath executionPath) {
            this.executionPath = executionPath;
            return this;
        }


        public ExecutionTypeInfo build() {
            if (isNonNull(type)) {
                return new ExecutionTypeInfo(unwrapNonNull(type), fieldDefinition, executionPath, parentType, true);
            }
            return new ExecutionTypeInfo(type, fieldDefinition, executionPath, parentType, false);
        }
    }
}

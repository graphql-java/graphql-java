package graphql.execution;

import graphql.PublicApi;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;

import static graphql.Assert.assertNotNull;

/**
 * The raw graphql type system (rightly) does not contain a hierarchy of child to parent types nor the non null-ness of
 * type instances, however this is resolved during query execution and represented by this class.
 */
@PublicApi
public class TypeInfo {

    private final GraphQLType type;
    private final boolean typeIsNonNull;
    private final TypeInfo parentType;

    private TypeInfo(GraphQLType type, TypeInfo parentType, boolean nonNull) {
        this.parentType = parentType;
        this.type = type;
        this.typeIsNonNull = nonNull;
        assertNotNull(this.type, "you must provide a graphql type");
    }

    /**
     * @return the type in play, which will have been unwrapped if it was originally a {@link GraphQLNonNull} type
     */
    public GraphQLType type() {
        return type;
    }

    /**
     * Allows you to cast this type as a more specific graphql type
     *
     * @param clazz the class to cast it to
     * @param <T>   the type in play
     * @return the type as that specific graphql type
     */
    @SuppressWarnings("unchecked")
    public <T extends GraphQLType> T castType(Class<T> clazz) {
        return clazz.cast(type);
    }


    /**
     * @return true if the type was defined as a non null type
     */
    public boolean typeIsNonNull() {
        return typeIsNonNull;
    }

    public TypeInfo parentTypeInfo() {
        return parentType;
    }

    /**
     * @return true if this type has a parent type
     */
    public boolean hasParentType() {
        return parentType != null;
    }

    /**
     * This allows you to morph a type into a more specialized form yet return the same
     * parent and non-null ness
     *
     * @param type the new type to be
     * @return a new type info with the same
     */
    public TypeInfo asType(GraphQLType type) {
        return new TypeInfo(unwrap(type), this.parentType, this.typeIsNonNull);
    }

    private static GraphQLType unwrap(GraphQLType type) {
        // its possible to have non nulls wrapping non nulls of things but it must end at some point
        while (type instanceof GraphQLNonNull) {
            type = ((GraphQLNonNull) type).getWrappedType();
        }
        return type;
    }

    @Override
    public String toString() {
        return String.format("TypeInfo { nonnull=%s, type=%s, parentType=%s }",
                typeIsNonNull, type, parentType);
    }

    /**
     * @return a buidler of TypeInfo
     */
    public static TypeInfo.Builder newTypeInfo() {
        return new Builder();
    }

    public static class Builder {
        GraphQLType type;
        TypeInfo parentType;

        /**
         * @see TypeInfo#newTypeInfo()
         */
        private Builder() {
        }

        public Builder type(GraphQLType type) {
            this.type = type;
            return this;
        }

        public Builder parentInfo(TypeInfo typeInfo) {
            this.parentType = typeInfo;
            return this;
        }

        public TypeInfo build() {
            if (type instanceof GraphQLNonNull) {
                return new TypeInfo(unwrap(type), parentType, true);
            }
            return new TypeInfo(type, parentType, false);
        }
    }


}

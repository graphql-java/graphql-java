package graphql.execution;

import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;

import static graphql.Assert.assertNotNull;

/**
 * The raw graphql type system (rightly) does not contain a hierarchy of child to parent types nor the non null ness of
 * type instances.  This add this during query execution.
 */
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

    public GraphQLType type() {
        return type;
    }

    @SuppressWarnings("unchecked")
    public <T extends GraphQLType> T castType(Class<T> clazz) {
        return clazz.cast(type);
    }

    public boolean typeIsNonNull() {
        return typeIsNonNull;
    }

    public TypeInfo parentTypeInfo() {
        return parentType;
    }

    public boolean hasParentType() {
        return parentType != null;
    }

    /**
     * This allows you to morph a type into a more specialized form yet return the same
     * parent and non-null ness
     *
     * @param type the new type to be
     *
     * @return a new type info with the same
     */
    public TypeInfo asType(GraphQLType type) {
        return new TypeInfo(unwrap(type), this.parentType, this.typeIsNonNull);
    }

    public static Builder newTypeInfo() {
        return new Builder();
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

    static class Builder {
        GraphQLType type;
        TypeInfo parentType;

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

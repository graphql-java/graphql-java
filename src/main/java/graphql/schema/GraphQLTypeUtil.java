package graphql.schema;

import graphql.Internal;

@Internal
public class GraphQLTypeUtil {

    /**
     * This will get the unwrapped type name that includes the non null and list wrappers
     * so it might be '[typeName!]'
     *
     * @param type the type in play
     *
     * @return the unwrapped type name
     */
    public static String getUnwrappedTypeName(GraphQLType type) {
        StringBuilder sb = new StringBuilder();
        if (isNonNull(type)) {
            sb.append(getUnwrappedTypeName(unwrapOne(type)));
            sb.append("!");
        } else if (isList(type)) {
            sb.append("[");
            sb.append(getUnwrappedTypeName(unwrapOne(type)));
            sb.append("]");
        } else {
            sb.append(type.getName());
        }
        return sb.toString();
    }

    /**
     * Returns true if the given type is a non null type
     *
     * @param type the type to check
     *
     * @return true if the given type is a non null type
     */
    public static boolean isNonNull(GraphQLType type) {
        return type instanceof GraphQLNonNull;
    }

    /**
     * Returns true if the given type is a nullable type
     *
     * @param type the type to check
     *
     * @return true if the given type is a nullable type
     */
    public static boolean isNullable(GraphQLType type) {
        return !isNonNull(type);
    }

    /**
     * Returns true if the given type is a list type
     *
     * @param type the type to check
     *
     * @return true if the given type is a list type
     */
    public static boolean isList(GraphQLType type) {
        return type instanceof GraphQLList;
    }

    /**
     * Returns true if the given type is a non null or list type, that is a wrapped type
     *
     * @param type the type to check
     *
     * @return true if the given type is a non null or list type
     */
    public static boolean isWrapped(GraphQLType type) {
        return isList(type) || isNonNull(type);
    }

    /**
     * Returns true if the given type is NOT a non null or list type
     *
     * @param type the type to check
     *
     * @return true if the given type is NOT a non null or list type
     */
    public static boolean isNotWrapped(GraphQLType type) {
        return !isWrapped(type);
    }

    /**
     * Returns true if the given type is a scalar type
     *
     * @param type the type to check
     *
     * @return true if the given type is a scalar type
     */
    public static boolean isScalar(GraphQLType type) {
        return type instanceof GraphQLScalarType;
    }

    /**
     * Returns true if the given type is an enum type
     *
     * @param type the type to check
     *
     * @return true if the given type is an enum type
     */
    public static boolean isEnum(GraphQLType type) {
        return type instanceof GraphQLEnumType;
    }

    /**
     * Returns true if the given type is a leaf type, that it cant contain any more fields
     *
     * @param type the type to check
     *
     * @return true if the given type is a leaf type
     */
    public static boolean isLeaf(GraphQLType type) {
        GraphQLUnmodifiedType unmodifiedType = unwrapAll(type);
        return
                unmodifiedType instanceof GraphQLScalarType
                        || unmodifiedType instanceof GraphQLEnumType;
    }

    /**
     * Returns true if the given type is an input type
     *
     * @param type the type to check
     *
     * @return true if the given type is an input type
     */
    public static boolean isInput(GraphQLType type) {
        GraphQLUnmodifiedType unmodifiedType = unwrapAll(type);
        return
                unmodifiedType instanceof GraphQLScalarType
                        || unmodifiedType instanceof GraphQLEnumType
                        || unmodifiedType instanceof GraphQLInputObjectType;
    }

    /**
     * Unwraps one layer of the type or just returns the type again if its not a wrapped type
     *
     * @param type the type to unwrapOne
     *
     * @return the unwrapped type or the same type again if its not wrapped
     */
    public static GraphQLType unwrapOne(GraphQLType type) {
        if (isNonNull(type)) {
            return ((GraphQLNonNull) type).getWrappedType();
        } else if (isList(type)) {
            return ((GraphQLList) type).getWrappedType();
        }
        return type;
    }

    /**
     * Unwraps all layers of the type or just returns the type again if its not a wrapped type
     *
     * @param type the type to unwrapOne
     *
     * @return the underlying type
     */
    public static GraphQLUnmodifiedType unwrapAll(GraphQLType type) {
        while (true) {
            if (isNotWrapped(type)) {
                return (GraphQLUnmodifiedType) type;
            }
            type = unwrapOne(type);
        }
    }

}

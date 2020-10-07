package graphql.schema.idl;

import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.ListType;
import graphql.language.NonNullType;

/**
 * This class consists of {@code static} utility methods for operating on {@link graphql.language.Type}.
 */
public class TypeUtil {

    /**
     * This will return the type in graphql SDL format, eg [typeName!]!
     *
     * @param type the type in play
     * @return the type in graphql SDL format, eg [typeName!]!
     */
    public static String simplePrint(Type type) {
        StringBuilder sb = new StringBuilder();
        if (isNonNull(type)) {
            sb.append(simplePrint(unwrapOne(type)));
            sb.append("!");
        } else if (isList(type)) {
            sb.append("[");
            sb.append(simplePrint(unwrapOne(type)));
            sb.append("]");
        } else {
            sb.append(((TypeName) type).getName());
        }
        return sb.toString();
    }

    /**
     * Unwraps all layers of the type or just returns the type again if its not a wrapped type
     *
     * @param type the type to be unwrapped
     *
     * @return the unwrapped type or the same type again if its not wrapped
     */
    public static TypeName unwrapAll(Type type) {
        if (isList(type)) {
            return unwrapAll(((ListType) type).getType());
        } else if (type instanceof NonNullType) {
            return unwrapAll(((NonNullType) type).getType());
        }
        return (TypeName) type;
    }

    /**
     * Unwraps one layer of the type or just returns the type again if its not a wrapped type
     *
     * @param type the type to be unwrapped
     *
     * @return the unwrapped type or the same type again if its not wrapped
     */
    public static Type unwrapOne(Type type) {
        if (isNonNull(type)) {
            return ((NonNullType) type).getType();
        } else if (isList(type)) {
            return ((ListType) type).getType();
        }
        return type;
    }

    /**
     * Returns {@code true} if the provided type is a non null type,
     * otherwise returns {@code false}.
     *
     * @param type the type to check
     *
     * @return {@code true} if the provided type is a non null type
     * otherwise {@code false}
     */
    public static boolean isNonNull(Type type) {
        return type instanceof NonNullType;
    }

    /**
     * Returns {@code true} if the provided type is a list type,
     * otherwise returns {@code false}.
     *
     * @param type the type to check
     *
     * @return {@code true} if the provided type is a list typ,
     * otherwise {@code false}
     */
    public static boolean isList(Type type) {
        return type instanceof ListType;
    }

    /**
     * Returns {@code true} if the given type is a non null or list type,
     * that is a wrapped type, otherwise returns {@code false}.
     *
     * @param type the type to check
     *
     * @return {@code true} if the given type is a non null or list type,
     * otherwise {@code false}
     */
    public static boolean isWrapped(Type type) {
        return isList(type) || isNonNull(type);
    }

}

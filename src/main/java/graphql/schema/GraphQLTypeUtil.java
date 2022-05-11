package graphql.schema;

import graphql.Assert;
import graphql.PublicApi;
import graphql.introspection.Introspection;
import graphql.schema.idl.DirectiveInfo;
import graphql.schema.idl.ScalarInfo;

import java.util.Stack;
import java.util.function.Predicate;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;

/**
 * A utility class that helps work with {@link graphql.schema.GraphQLType}s
 */
@PublicApi
public class GraphQLTypeUtil {

    /**
     * This will return the type in graphql SDL format, eg [typeName!]!
     *
     * @param type the type in play
     *
     * @return the type in graphql SDL format, eg [typeName!]!
     */
    public static String simplePrint(GraphQLType type) {
        Assert.assertNotNull(type, () -> "type can't be null");
        StringBuilder sb = new StringBuilder();
        if (isNonNull(type)) {
            sb.append(simplePrint(unwrapOne(type)));
            sb.append("!");
        } else if (isList(type)) {
            sb.append("[");
            sb.append(simplePrint(unwrapOne(type)));
            sb.append("]");
        } else {
            sb.append(((GraphQLNamedType) type).getName());
        }
        return sb.toString();
    }

    public static String simplePrint(GraphQLSchemaElement schemaElement) {
        if (schemaElement instanceof GraphQLType) {
            return simplePrint((GraphQLType) schemaElement);
        }
        if (schemaElement instanceof GraphQLNamedSchemaElement) {
            return ((GraphQLNamedSchemaElement) schemaElement).getName();
        }
        // a schema element is either a GraphQLType or a GraphQLNamedSchemaElement
        return assertShouldNeverHappen("unexpected schema element: " + schemaElement);
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
     * Unwraps one layer of the type or just returns the type again if its not a wrapped type
     * and then cast to the target type.
     *
     * @param type the type to unwrapOne
     * @param <T>  for two
     *
     * @return the unwrapped type or the same type again if its not wrapped
     */
    public static <T extends GraphQLType> T unwrapOneAs(GraphQLType type) {
        //noinspection unchecked
        return (T) unwrapOne(type);
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

    /**
     * Unwraps all layers of the type or just returns the type again if its not a wrapped type
     * and then cast to the target type.
     *
     * @param type the type to unwrapOne
     * @param <T>  for two
     *
     * @return the underlying type
     */
    public static <T extends GraphQLType> T unwrapAllAs(GraphQLType type) {
        //noinspection unchecked
        return (T) unwrapAll(type);
    }


    /**
     * Unwraps all non nullable layers of the type until it reaches a type that is not {@link GraphQLNonNull}
     *
     * @param type the type to unwrap
     *
     * @return the underlying type that is not {@link GraphQLNonNull}
     */
    public static GraphQLType unwrapNonNull(GraphQLType type) {
        while (isNonNull(type)) {
            type = unwrapOne(type);
        }
        return type;
    }

    /**
     * Unwraps all non nullable layers of the type until it reaches a type that is not {@link GraphQLNonNull}
     * and then cast to the target type.
     *
     * @param type the type to unwrap
     * @param <T>  for two
     *
     * @return the underlying type that is not {@link GraphQLNonNull}
     */
    public static <T extends GraphQLType> T unwrapNonNullAs(GraphQLType type) {
        //noinspection unchecked
        return (T) unwrapNonNull(type);
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
        assertNotNull(type);
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

    public static boolean isInterfaceOrUnion(GraphQLType type) {
        return type instanceof GraphQLInterfaceType || type instanceof GraphQLUnionType;
    }

    public static boolean isObjectType(GraphQLType type) {
        return type instanceof GraphQLObjectType;
    }


    /**
     * This predicate returns true if the schema element is an inbuilt schema element
     * such as the system scalars and directives or introspection types
     *
     * @return true if its a system schema element
     */
    public static Predicate<GraphQLNamedSchemaElement> isSystemElement() {
        return schemaElement -> {
            if (schemaElement instanceof GraphQLScalarType) {
                return ScalarInfo.isGraphqlSpecifiedScalar((GraphQLScalarType) schemaElement);
            }
            if (schemaElement instanceof GraphQLDirective) {
                return DirectiveInfo.isGraphqlSpecifiedDirective((GraphQLDirective) schemaElement);
            }
            if (schemaElement instanceof GraphQLNamedType) {
                return Introspection.isIntrospectionTypes((GraphQLNamedType) schemaElement);
            }
            return false;
        };
    }
}

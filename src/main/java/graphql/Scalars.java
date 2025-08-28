package graphql;


import graphql.scalar.GraphqlBooleanCoercing;
import graphql.scalar.GraphqlFloatCoercing;
import graphql.scalar.GraphqlIDCoercing;
import graphql.scalar.GraphqlIntCoercing;
import graphql.scalar.GraphqlStringCoercing;
import graphql.schema.GraphQLScalarType;
import org.jspecify.annotations.NullMarked;

/**
 * This contains the implementations of the Scalar types that ship with graphql-java.  Some are proscribed
 * by the graphql specification (Int, Float, String, Boolean and ID) while others are offer because they are common on
 * Java platforms.
 * <p>
 * For more info see <a href="https://graphql.org/learn/schema/#scalar-types">https://graphql.org/learn/schema/#scalar-types</a> and
 * more specifically <a href="https://spec.graphql.org/draft/#sec-Scalars">https://spec.graphql.org/draft/#sec-Scalars</a>
 */
@PublicApi
@NullMarked
public class Scalars {

    /**
     * This represents the "Int" type as defined in the graphql specification : <a href="https://spec.graphql.org/October2021/#sec-Int">...</a>
     * <p>
     * The Int scalar type represents a signed 32‐bit numeric non‐fractional value.
     */
    public static final GraphQLScalarType GraphQLInt = GraphQLScalarType.newScalar()
            .name("Int").description("Built-in Int").coercing(new GraphqlIntCoercing()).build();

    /**
     * This represents the "Float" type as defined in the graphql specification : <a href="https://spec.graphql.org/October2021/#sec-Float">...</a>
     * <p>
     * Note: The Float type in GraphQL is equivalent to Double in Java. (double precision IEEE 754)
     */
    public static final GraphQLScalarType GraphQLFloat = GraphQLScalarType.newScalar()
            .name("Float").description("Built-in Float").coercing(new GraphqlFloatCoercing()).build();

    /**
     * This represents the "String" type as defined in the graphql specification : <a href="https://spec.graphql.org/October2021/#sec-String">...</a>
     */
    public static final GraphQLScalarType GraphQLString = GraphQLScalarType.newScalar()
            .name("String").description("Built-in String").coercing(new GraphqlStringCoercing()).build();

    /**
     * This represents the "Boolean" type as defined in the graphql specification : <a href="https://spec.graphql.org/October2021/#sec-Boolean">...</a>
     */
    public static final GraphQLScalarType GraphQLBoolean = GraphQLScalarType.newScalar()
            .name("Boolean").description("Built-in Boolean").coercing(new GraphqlBooleanCoercing()).build();

    /**
     * This represents the "ID" type as defined in the graphql specification : <a href="https://spec.graphql.org/October2021/#sec-ID">...</a>
     * <p>
     * The ID scalar type represents a unique identifier, often used to re-fetch an object or as the key for a cache. The
     * ID type is serialized in the same way as a String; however, it is not intended to be human‐readable. While it is
     * often numeric, it should always serialize as a String.
     */
    public static final GraphQLScalarType GraphQLID = GraphQLScalarType.newScalar()
            .name("ID").description("Built-in ID").coercing(new GraphqlIDCoercing()).build();
}

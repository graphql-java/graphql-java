package graphql.schema;

import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import graphql.language.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A GraphQL enum type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaEnum
        extends SchemaNamedType, SchemaInputType, SchemaOutputType {

    Object serialize(
            Object input,
            GraphQLContext graphQLContext,
            Locale locale);

    Object parseValue(
            Object input,
            GraphQLContext graphQLContext,
            Locale locale);

    Object parseLiteral(
            Value<?> input,
            GraphQLContext graphQLContext,
            Locale locale);

    Value<?> valueToLiteral(
            Object input,
            GraphQLContext graphQLContext,
            Locale locale);

    List<? extends SchemaEnumValue> getValues();

    @Nullable SchemaEnumValue getValue(String name);
}

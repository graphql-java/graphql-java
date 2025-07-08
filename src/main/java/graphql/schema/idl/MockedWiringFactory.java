package graphql.schema.idl;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.SingletonPropertyDataFetcher;
import graphql.schema.TypeResolver;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This is a {@link WiringFactory} which provides mocked types resolver
 * and scalars. It is useful for testing only, for example for creating schemas
 * that can be inspected but not executed on.
 * <p>
 * See {@link RuntimeWiring#MOCKED_WIRING} for example usage
 */
@PublicApi
@NullMarked
@SuppressWarnings("rawtypes")
public class MockedWiringFactory implements WiringFactory {

    @Override
    public boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
        return true;
    }

    @Override
    public TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
        return env -> {
            throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
        };
    }

    @Override
    public boolean providesTypeResolver(UnionWiringEnvironment environment) {
        return true;
    }

    @Override
    public TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
        return env -> {
            throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
        };
    }

    @Override
    public boolean providesDataFetcher(FieldWiringEnvironment environment) {
        return true;
    }

    @Override
    public DataFetcher<?> getDataFetcher(FieldWiringEnvironment environment) {
        return SingletonPropertyDataFetcher.singleton();
    }

    @Override
    public boolean providesScalar(ScalarWiringEnvironment environment) {
        return !ScalarInfo.isGraphqlSpecifiedScalar(environment.getScalarTypeDefinition().getName());
    }

    public GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
        return GraphQLScalarType.newScalar().name(environment.getScalarTypeDefinition().getName()).coercing(new Coercing<>() {
            @Nullable
            @Override
            public Object serialize(@NonNull Object dataFetcherResult, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingSerializeException {
                throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
            }

            @Nullable
            @Override
            public Object parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException {
                throw new UnsupportedOperationException("Not implemented...this is only a mocked wiring");
            }

            @Nullable
            @Override
            public Object parseLiteral(@NonNull Value input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException {
                return parseLiteralImpl(input, variables, graphQLContext, locale);
            }

            @Nullable
            private Object parseLiteralImpl(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
                if (input instanceof NullValue) {
                    return null;
                }
                if (input instanceof FloatValue) {
                    return ((FloatValue) input).getValue();
                }
                if (input instanceof StringValue) {
                    return ((StringValue) input).getValue();
                }
                if (input instanceof IntValue) {
                    return ((IntValue) input).getValue();
                }
                if (input instanceof BooleanValue) {
                    return ((BooleanValue) input).isValue();
                }
                if (input instanceof EnumValue) {
                    return ((EnumValue) input).getName();
                }
                if (input instanceof VariableReference) {
                    String varName = ((VariableReference) input).getName();
                    return variables.get(varName);
                }
                if (input instanceof ArrayValue) {
                    List<Value> values = ((ArrayValue) input).getValues();
                    return values.stream()
                            .map(v -> parseLiteral(v, variables, graphQLContext, locale))
                            .collect(Collectors.toList());
                }
                if (input instanceof ObjectValue) {
                    List<ObjectField> values = ((ObjectValue) input).getObjectFields();
                    Map<String, Object> parsedValues = new LinkedHashMap<>();
                    values.forEach(fld -> {
                        Object parsedValue = parseLiteral(fld.getValue(), variables, graphQLContext, locale);
                        if (parsedValue != null) {
                            parsedValues.put(fld.getName(), parsedValue);
                        }
                    });
                    return parsedValues;
                }
                return Assert.assertShouldNeverHappen("We have covered all Value types");
            }

        }).build();
    }
}

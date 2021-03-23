package graphql

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.language.SourceLocation
import graphql.language.StringValue
import graphql.schema.*
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class ScalarCoercionTest extends Specification {

    def "scalar coercion errors have source locations"() {
        when:

        GraphQLScalarType customValueScalar = GraphQLScalarType.newScalar()
                                                               .name("CustomValue")
                                                               .description("A custom scalar that handles values")
                                                               .coercing(new CustomValueScalar())
                                                               .build()

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
          .type(newTypeWiring("Query")
                  .dataFetcher("bar",
                               { env ->
                                 Map<String, Object> map = new HashMap<>()
                                 map.put("id", "def")
                                 return map
                               })
        )
          .scalar(customValueScalar)
          .build()

        def graphQL = TestUtil.graphQL("""
                schema {
                  query: Query
                }

                type Query {
                  bar(input: BarInput!): String
                }

                input BarInput {
                  baz: CustomValue
                }

                scalar CustomValue
            """, runtimeWiring).build()

        def variables = ["input": ["baz": "x"]]

        ExecutionInput varInput = ExecutionInput.newExecutionInput()
            .query('query Bar($input: BarInput!) {bar(input: $input)}')
            .variables(variables)
            .build()

        ExecutionResult varResult = graphQL
            .executeAsync(varInput)
            .join()

        then:

        varResult.data == null
        varResult.errors.size() == 1
        varResult.errors[0].errorType == ErrorType.ValidationError
        varResult.errors[0].message == "Variable 'baz' has an invalid value : Unable to parse variable value x as a custom value"
        varResult.errors[0].locations == [new SourceLocation(1, 11)]
    }

    def "scalar coercion errors with MaxQueryComplexityInstrumentation return an error"() {
        when:
        
        GraphQLScalarType customValueScalar = GraphQLScalarType.newScalar()
                                                               .name("CustomValue")
                                                               .description("A custom scalar that handles values")
                                                               .coercing(new CustomValueScalar())
                                                               .build()

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
          .type(newTypeWiring("Query")
                  .dataFetcher("bar",
                               { env ->
                                 Map<String, Object> map = new HashMap<>()
                                 map.put("id", "def")
                                 return map
                               })
        )
          .scalar(customValueScalar)
          .build()

        def graphQL = TestUtil.graphQL("""
                schema {
                  query: Query
                }

                type Query {
                  bar(input: BarInput!): String
                }

                input BarInput {
                  baz: CustomValue
                }

                scalar CustomValue
            """, runtimeWiring)
          .instrumentation(new MaxQueryComplexityInstrumentation(100))
          .build()

        def variables = ["input": ["baz": "x"]]

        ExecutionInput varInput = ExecutionInput.newExecutionInput()
            .query('query Bar($input: BarInput!) {bar(input: $input)}')
            .variables(variables)
            .build()

        ExecutionResult varResult = graphQL
            .executeAsync(varInput)
            .join()

        then:

        varResult.data == null
        varResult.errors.size() == 1
        varResult.errors[0].errorType == ErrorType.ValidationError
        varResult.errors[0].message == "Variable 'baz' has an invalid value : Unable to parse variable value x as a custom value"
        varResult.errors[0].locations == [new SourceLocation(1, 11)]
    }

    private static class CustomValueScalar implements Coercing<String, String> {

        @Override
        String serialize(Object dataFetcherResult) {
            String value = String.valueOf(dataFetcherResult)
            if (isValid(value)) {
              return value
            } else {
              throw new CoercingSerializeException("Unable to serialize " + value + " as a custom value")
            }
        }

        @Override
        String parseValue(Object input) {
            if (input instanceof String) {
              String value = input.toString()
              if (isValid(value)) {
                return value
              }
            }
            throw new CoercingParseValueException("Unable to parse variable value " + input + " as a custom value")
        }

        @Override
        String parseLiteral(Object input) {
            if (input instanceof StringValue) {
              String value = ((StringValue) input).getValue()
              if (isValid(value)) {
                return value
              }
            }
            throw new CoercingParseLiteralException(
              "Value is not a custom value: '" + String.valueOf(input) + "'"
            )
        }

        private static boolean isValid(String value) {
            return value.startsWith("custom_")
        }
    }
}


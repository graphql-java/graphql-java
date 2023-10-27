package graphql.execution.values

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.Scalars
import graphql.TestUtil
import graphql.execution.RawVariables
import graphql.execution.ValuesResolver
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLInputType
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import spock.lang.Specification

import static graphql.language.TypeName.newTypeName
import static graphql.language.VariableDefinition.newVariableDefinition

class InputInterceptorTest extends Specification {

    def sdl = """
        type Query {
            f(inputArg : InputArg, intArg : Int, stringArg : String,booleanArg : Boolean) : String
        }
            
        input InputArg {
            intArg : Int
            stringArg : String 
            booleanArg : Boolean
        }
    """

    def schema = TestUtil.schema(sdl)

    InputInterceptor interceptor = new InputInterceptor() {
        @Override
        Object intercept(@Nullable Object value, @NotNull GraphQLInputType graphQLType, @NotNull GraphQLContext graphqlContext, @NotNull Locale locale) {
            if (graphQLType == Scalars.GraphQLBoolean) {
                return "truthy" == value ? false : value
            }
            if (graphQLType == Scalars.GraphQLString) {
                return String.valueOf(value).reverse()
            }
            return value
        }
    }


    def "the input interceptor can be called"() {
        def inputArgDef = newVariableDefinition("inputArg",
                newTypeName("InputArg").build()).build()
        def booleanArgDef = newVariableDefinition("booleanArg",
                newTypeName("Boolean").build()).build()
        def stringArgDef = newVariableDefinition("stringArg",
                newTypeName("String").build()).build()

        def graphQLContext = GraphQLContext.newContext()
                .put(InputInterceptor.class, interceptor).build()

        when:
        def coercedVariables = ValuesResolver.coerceVariableValues(
                this.schema,
                [inputArgDef, booleanArgDef, stringArgDef],
                RawVariables.of([
                        "booleanArg": "truthy",
                        "stringArg" : "sdrawkcab",
                        "inputArg"  : [
                                "stringArg": "sdrawkcab osla"
                        ]
                ]),
                graphQLContext,
                Locale.CANADA
        )

        then:
        coercedVariables.toMap() == [
                "booleanArg": false,
                "stringArg" : "backwards",
                "inputArg"  : [
                        "stringArg": "also backwards"
                ]
        ]
    }

    def "integration test of interceptor being called"() {
        DataFetcher df = { DataFetchingEnvironment env ->
            return env.getArguments().entrySet()
                    .collect({ String.valueOf(it.key) + ":" + String.valueOf(it.value) })
                    .join(" ")
        }
        def schema = TestUtil.schema(sdl, ["Query": ["f": df]])
        def graphQL = GraphQL.newGraphQL(schema).build()
        def ei = ExecutionInput.newExecutionInput().query('''
            query q($booleanArg : Boolean, $stringArg : String) { 
                f(booleanArg : $booleanArg, stringArg : $stringArg) 
            }
            ''')
                .graphQLContext({ it.put(InputInterceptor.class, interceptor) })
                .variables(
                        "booleanArg": "truthy",
                        "stringArg": "sdrawkcab"

                )
                .build()

        when:
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [f: "stringArg:backwards booleanArg:false"]
    }


    def "integration test showing the presence of an interceptor wont stop scalar coercing"() {
        def schema = TestUtil.schema(sdl)
        def graphQL = GraphQL.newGraphQL(schema).build()
        def ei = ExecutionInput.newExecutionInput().query('''
            query q($booleanArg : Boolean, $stringArg : String) { 
                f(booleanArg : $booleanArg, stringArg : $stringArg) 
            }
            ''')
                .graphQLContext({ it.put(InputInterceptor.class, interceptor) })
                .variables(
                        "booleanArg": [not: "a boolean"],
                        "stringArg": "sdrawkcab"

                )
                .build()

        when:
        def er = graphQL.execute(ei)

        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Variable 'booleanArg' has an invalid value: Expected a value that can be converted to type 'Boolean' but it was a 'LinkedHashMap'"
    }
}

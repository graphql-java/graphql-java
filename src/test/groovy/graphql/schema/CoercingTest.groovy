package graphql.schema

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.VariableReference
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import spock.lang.Specification

class CoercingTest extends Specification {

    GraphQLScalarType mapLikeScalar = new GraphQLScalarType("MapLike", "MapLike", new Coercing() {
        @Override
        Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return dataFetcherResult
        }

        @Override
        Object parseValue(Object input) throws CoercingParseValueException {
            return input
        }

        @Override
        Object parseLiteral(Object input) throws CoercingParseLiteralException {
            return parseLiteral(input, [:])
        }

        @Override
        Object parseLiteral(Object input, Map variables) throws CoercingParseLiteralException {
            if (input instanceof StringValue) {
                return ((StringValue) input).getValue()
            }
            if (input instanceof IntValue) {
                return ((IntValue) input).getValue()
            }
            if (input instanceof FloatValue) {
                return ((FloatValue) input).getValue()
            }
            if (input instanceof BooleanValue) {
                return ((BooleanValue) input).isValue()
            }
            if (input instanceof ObjectValue) {
                Map<String, Object> obj = new LinkedHashMap<>()
                ((ObjectValue) input).getObjectFields().forEach({
                    fld ->
                        def value = parseLiteral(fld.getValue(), variables)
                        obj.put(fld.getName(), value)
                })
                return obj
            }
            if (input instanceof VariableReference) {
                def name = ((VariableReference) input).getName()
                return variables.get(name)
            }
            if (input instanceof ArrayValue) {
                return ((ArrayValue) input).getValues().collect({ v -> parseLiteral(v, variables) })
            }
            if (input instanceof NullValue) {
                return null
            }
            throw new CoercingParseLiteralException()
        }
    })


    def "end to end test of coercing with variables references"() {
        def spec = '''
        
            scalar MapLike
            
            type Query {
                field(argument : MapLike) : MapLike
            }    
        '''
        DataFetcher df = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                def argument = environment.getArgument("argument")
                return argument
            }
        }

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Query")
                .dataFetcher("field", df))
                .scalar(mapLikeScalar)
                .build()


        def graphQL = TestUtil.graphQL(spec,runtimeWiring).build()
        def executionInput = ExecutionInput.newExecutionInput()
                .variables([
                strVar: "strVar",
                intVar: 999
        ])
                .query('''
        query Q($strVar : String) {
            field(argument : { s : $strVar, i : 666 })
        }                    
        ''')
                .build()

        when:
        def er = graphQL.execute(executionInput)
        then:
        er.errors.isEmpty()
        er.data == [field: [s: "strVar", i: 666]]
    }
}

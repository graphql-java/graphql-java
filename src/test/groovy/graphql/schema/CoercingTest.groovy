package graphql.schema

import graphql.ExecutionInput
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

import static graphql.schema.GraphQLScalarType.newScalar

class CoercingTest extends Specification {

    GraphQLScalarType mapLikeScalar = newScalar().name("MapLike").description("MapLike").coercing(new Coercing() {
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
    .build()


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


        def graphQL = TestUtil.graphQL(spec, runtimeWiring).build()
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

    GraphQLScalarType customScalar = newScalar().name("CustomScalar").description("CustomScalar").coercing(new Coercing() {
        @Override
        Object serialize(Object input) throws CoercingSerializeException {
            if ("bang" == String.valueOf(input)) {
                throw CoercingSerializeException.newCoercingSerializeException()
                        .message("serialize message").extensions([serialize: true]).build()
            }
            return input
        }

        @Override
        Object parseValue(Object input) throws CoercingParseValueException {
            if ("bang" == String.valueOf(input)) {
                throw CoercingParseValueException.newCoercingParseValueException()
                        .message("parseValue message").extensions([parseValue: true]).build()
            }
            return input
        }

        @Override
        Object parseLiteral(Object input) throws CoercingParseLiteralException {
            StringValue sv = (StringValue) input
            if ("bang" == String.valueOf(sv.getValue())) {
                throw CoercingParseLiteralException.newCoercingParseLiteralException()
                        .message("parseLiteral message").extensions([parseLiteral: true]).build()
            }
            return new StringValue(String.valueOf("input"))
        }
    })
    .build()

    def customScalarSDL = '''
            scalar CustomScalar
            
            type Query {
                field(arg1 : CustomScalar, arg2 : CustomScalar) : CustomScalar
            }
        '''
    DataFetcher customScalarDF = { env -> return "bang" }

    def customScalarRuntimeWiring = RuntimeWiring.newRuntimeWiring()
            .type(TypeRuntimeWiring.newTypeWiring("Query").dataFetcher("field", customScalarDF))
            .scalar(customScalar)
            .build()

    def customScalarSchema = TestUtil.graphQL(customScalarSDL, customScalarRuntimeWiring).build()

    def "custom coercing parseValue messages become graphql errors"() {

        when:
        def ei = ExecutionInput.newExecutionInput().query('''
                    query($v1 : CustomScalar) {
                        field(arg1:$v1, arg2:"ok")
                    }
                    ''')
                .variables([v1: "bang"])
                .build()
        def er = customScalarSchema.execute(ei)
        then:
        er.errors.size() == 1
        er.errors[0].message.contains("parseValue message")
        er.errors[0].extensions == [parseValue: true]
    }

    def "custom coercing parseLiteral messages become graphql errors"() {

        when:
        def ei = ExecutionInput.newExecutionInput().query('''
                    query($v1 : CustomScalar) {
                        field(arg1:$v1, arg2:"bang")
                    }
                    ''')
                .variables([v1: "ok"])
                .build()
        def er = customScalarSchema.execute(ei)
        then:
        er.errors.size() == 1
        er.errors[0].message.contains("parseLiteral message")
        er.errors[0].extensions == [parseLiteral: true]
    }

    def "custom coercing serialize messages become graphql errors"() {

        when:
        def ei = ExecutionInput.newExecutionInput().query('''
                    query {
                        field
                    }
                    ''')
                .root([field: "bang"])
                .build()
        def er = customScalarSchema.execute(ei)
        then:
        er.errors.size() == 1
        er.errors[0].message.contains("serialize message")
        er.errors[0].extensions == [serialize: true]
    }
}

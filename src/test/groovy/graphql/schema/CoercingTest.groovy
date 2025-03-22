package graphql.schema

import graphql.ExecutionInput
import graphql.GraphQLContext
import graphql.TestUtil
import graphql.analysis.MaxQueryDepthInstrumentation
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
import org.jetbrains.annotations.NotNull
import spock.lang.Specification

import java.time.ZonedDateTime

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
        er.errors[0].message == "Validation error (WrongType@[field]) : argument 'arg2' with value 'StringValue{value='bang'}' is not a valid 'CustomScalar' - parseLiteral message"
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
        er.errors[0].message.contains("Can't serialize value (/field) : serialize message")
        er.errors[0].extensions == [serialize: true]
    }

    static def parseDateTimeValue(Object input) {
        try {
            if (input instanceof StringValue) {
                return ZonedDateTime.parse(((StringValue) input).getValue())
            } else if (input instanceof String) {
                return ZonedDateTime.parse((String) input)
            } else throw new IllegalArgumentException("Unexpected input type: ${input.getClass()}")
        } catch (Exception e) {
            throw new CoercingParseValueException("Failed to parse input value $input as ZonedDateTime", e)
        }
    }

    GraphQLScalarType zonedDateTimeLikeScalar = newScalar().name("ZonedDateTimeLike").description("ZonedDateTimeLike").coercing(new Coercing() {
        @Override
        Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return dataFetcherResult
        }

        @Override
        Object parseValue(Object input) throws CoercingParseValueException {
            return parseDateTimeValue(input)
        }

        @Override
        Object parseLiteral(Object input) throws CoercingParseLiteralException {
            return input
        }

        @Override
        StringValue valueToLiteral(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
            return new StringValue(input.toString())
        }
    })
    .build()

    def "custom scalars are only coerced once - end to end test with execution and instrumentation"() {
        def spec = '''
            scalar ZonedDateTimeLike
            
            type Query {
                field(argument : ZonedDateTimeLike) : ZonedDateTimeLike
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
                .scalar(zonedDateTimeLikeScalar)
                .build()

        def executionInput = ExecutionInput.newExecutionInput()
                .variables([zonedDateTime: "2022-05-16T19:52:37Z"])
                .query('''
                    query myZonedDateTimeQuery($zonedDateTime : ZonedDateTimeLike) {
                        field(argument : $zonedDateTime)
                    }''')
                .build()

        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(7)

        def graphQL = TestUtil.graphQL(spec, runtimeWiring).instrumentation(maximumQueryDepthInstrumentation).build()

        when:
        def er = graphQL.execute(executionInput)

        then:
        // If variables were coerced twice, would expect an IllegalArgumentException to be thrown,
        // as the ZonedDateTime custom scalar parser can only parse strings, not instances of ZonedDateTime
        notThrown(Exception)
        er.errors.isEmpty()
        er.data == [field: ZonedDateTime.parse("2022-05-16T19:52:37Z")]
    }
}

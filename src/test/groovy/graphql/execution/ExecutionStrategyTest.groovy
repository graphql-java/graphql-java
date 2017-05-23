package graphql.execution

import graphql.ExecutionResult
import graphql.GraphQLException
import graphql.Scalars
import graphql.language.Field
import graphql.schema.Coercing
import graphql.schema.GraphQLList
import graphql.schema.GraphQLScalarType
import spock.lang.Specification

import static ExecutionStrategyParameters.newParameters
import static graphql.schema.GraphQLNonNull.nonNull

@SuppressWarnings("GroovyPointlessBoolean")
class ExecutionStrategyTest extends Specification {

    ExecutionStrategy executionStrategy

    def setup() {
        executionStrategy = new ExecutionStrategy() {

            @Override
            ExecutionResult execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
                return null
            }
        }
    }

    def buildContext() {
        new ExecutionContext(null, null, null, executionStrategy, executionStrategy, executionStrategy, null, null, null, null, null)
    }


    def "completes value for a java.util.List"() {
        given:
        ExecutionContext executionContext = buildContext()
        Field field = new Field()
        def fieldType = new GraphQLList(Scalars.GraphQLString)
        def result = Arrays.asList("test")
        def parameters = newParameters()
                .typeInfo(TypeInfo.newTypeInfo().type(fieldType))
                .source(result)
                .fields(["fld": []])
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters, [field])

        then:
        executionResult.data == ["test"]
    }

    def "completes value for an array"() {
        given:
        ExecutionContext executionContext = buildContext()
        Field field = new Field()
        def fieldType = new GraphQLList(Scalars.GraphQLString)
        String[] result = ["test"]
        def parameters = newParameters()
                .typeInfo(TypeInfo.newTypeInfo().type(fieldType))
                .source(result)
                .fields(["fld": []])
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters, [field])

        then:
        executionResult.data == ["test"]
    }

    // this is wrong: we should return null with an error
    def "completing value with serializing throwing exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = Scalars.GraphQLInt
        String result = "not a number"
        def parameters = newParameters()
                .typeInfo(TypeInfo.newTypeInfo().type(fieldType))
                .source(result)
                .fields(["dummy": []])
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters, [new Field()])

        then:
        thrown(GraphQLException)

    }

    def "completing a scalar null value for a non null type throws an exception"() {

        GraphQLScalarType NullProducingScalar = new GraphQLScalarType("Custom", "It Can Produce Nulls", new Coercing<Double, Double>() {
            @Override
            Double serialize(Object input) {
                if (input == 0xCAFED00Dd) {
                    return null
                }
                return 0xCAFEBABEd
            }

            @Override
            Double parseValue(Object input) {
                throw new UnsupportedOperationException("Not implemented")
            }

            @Override
            Double parseLiteral(Object input) {
                throw new UnsupportedOperationException("Not implemented")
            }
        })


        ExecutionContext executionContext = buildContext()
        def fieldType = NullProducingScalar
        def typeInfo = TypeInfo.newTypeInfo().type(nonNull(fieldType)).build()
        NonNullableFieldValidator nullableFieldValidator = new NonNullableFieldValidator(executionContext,typeInfo)

        when:
        def parameters = newParameters()
                .typeInfo(TypeInfo.newTypeInfo().type(fieldType))
                .source(result)
                .fields(["dummy": []])
                .nonNullFieldValidator(nullableFieldValidator)
                .build()

        Exception actualException = null
        try {
            executionStrategy.completeValue(executionContext, parameters, [new Field()])
        } catch (Exception e) {
            actualException = e
        }

        then:
        if (errorExpected) {
            actualException instanceof NonNullableFieldWasNullException
            executionContext.errors.size() == 1
        } else {
            actualException != null
            executionContext.errors.size() == 0
        }


        where:
        result      || errorExpected
        1.0d        || false
        0xCAFED00Dd || true
        null        || true
    }

}

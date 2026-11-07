package graphql.execution.instrumentation

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeUtil
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

class InstrumentationFieldFetchParametersTest extends Specification {

    def "getField does not invoke the environment supplier when the field definition is supplied"() {
        given:
        def fieldDef = newFieldDefinition().name("name").type(GraphQLString).build()
        Supplier<DataFetchingEnvironment> throwingSupplier = { throw new IllegalStateException("environment should not be built") }

        def parameters = new InstrumentationFieldFetchParameters(null, fieldDef, throwingSupplier, null, true)

        when:
        def field = parameters.getField()

        then:
        field.is(fieldDef)
        GraphQLTypeUtil.isLeaf(field.getType())
        parameters.isTrivialDataFetcher()
    }

    def "getField falls back to the supplier when no field definition is supplied"() {
        given:
        def fieldDef = newFieldDefinition().name("name").type(GraphQLString).build()
        def callCount = new AtomicInteger()
        def stepInfo = Mock(graphql.execution.ExecutionStepInfo) {
            getFieldDefinition() >> fieldDef
        }
        def environment = Mock(DataFetchingEnvironment) {
            getExecutionStepInfo() >> stepInfo
        }
        Supplier<DataFetchingEnvironment> countingSupplier = { callCount.incrementAndGet(); environment }

        def parameters = new InstrumentationFieldFetchParameters(null, countingSupplier, null, false)

        when:
        def field = parameters.getField()

        then:
        field.is(fieldDef)
        callCount.get() == 1
    }

    def "InstrumentationFieldParameters getField does not invoke the step info supplier when the field definition is supplied"() {
        given:
        def fieldDef = newFieldDefinition().name("name").type(GraphQLString).build()
        Supplier<graphql.execution.ExecutionStepInfo> throwingSupplier = { throw new IllegalStateException("step info should not be built") }

        def parameters = new InstrumentationFieldParameters(null, fieldDef, throwingSupplier)

        when:
        def field = parameters.getField()

        then:
        field.is(fieldDef)
    }

    def "the engine supplies the field definition so an instrumentation can inspect the field type in beginFieldFetching"() {
        given:
        def sdl = '''
            type Query {
                hero: Hero
            }
            type Hero {
                name: String
                friends: [Hero]
            }
        '''
        def root = [hero: [name: "Luke", friends: [[name: "Han", friends: []]]]]
        def graphQL = TestUtil.graphQL(sdl).build()

        Map<String, Boolean> leafByField = [:]
        def instrumentation = new SimplePerformantInstrumentation() {
            @Override
            FieldFetchingInstrumentationContext beginFieldFetching(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
                GraphQLFieldDefinition fieldDef = parameters.getField()
                leafByField.put(fieldDef.getName(), GraphQLTypeUtil.isLeaf(fieldDef.getType()))
                return FieldFetchingInstrumentationContext.NOOP
            }
        }

        when:
        def result = graphQL.transform({ it.instrumentation(instrumentation) })
                .execute(ExecutionInput.newExecutionInput('{ hero { name friends { name } } }').root(root).build())

        then:
        result.errors.isEmpty()
        leafByField == [hero: false, name: true, friends: false]
    }
}

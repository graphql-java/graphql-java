package graphql.execution

import graphql.schema.GraphQLInputObjectType
import spock.lang.Specification

class OneOfInputCoercionBenchmarkTest extends Specification {

    def "benchmark fixtures coerce the intended input graph - #oneOfCase, depth #depth"() {
        given:
        // JMH classes are available only on the test runtime classpath.
        def benchmark = Class.forName("benchmark.OneOfInputCoercionBenchmark").getDeclaredConstructor().newInstance()
        benchmark.oneOfCase = oneOfCase
        benchmark.depth = depth
        benchmark.setup()

        when:
        def literalResult = benchmark.executeLiteralArgument()
        def variableResult = benchmark.executeVariableArgument()
        def variables = benchmark.coerceVariables().toMap()
        def schema = benchmark.buildSchema()

        then:
        literalResult.errors.isEmpty()
        variableResult.errors.isEmpty()
        literalResult.getData() == [result: 8 * depth]
        variableResult.getData() == literalResult.getData()
        schema.inputTypeContainsOneOf(schema.getType('Filter0')) == (oneOfCase == 'REACHABLE')
        ((GraphQLInputObjectType) schema.getType('Leaf')).isOneOf() == (oneOfCase == 'REACHABLE')
        ((GraphQLInputObjectType) schema.getType('Other')).isOneOf() == (oneOfCase == 'UNRELATED')

        and:
        def input = variables.input
        depth.times { level ->
            assert input['values'] == Collections.nCopies(8, [value: 1])
            assert input.containsKey('next') == (level < depth - 1)
            input = input.next
        }
        input == null

        where:
        [oneOfCase, depth] << [['NONE', 'UNRELATED', 'REACHABLE'], [1, 8]].combinations()
    }
}

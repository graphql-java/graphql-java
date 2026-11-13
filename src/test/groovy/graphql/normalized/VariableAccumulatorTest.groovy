package graphql.normalized

import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.StringValue
import graphql.language.TypeName
import spock.lang.Specification

class VariableAccumulatorTest extends Specification {

    def alwaysTruePredicate = new VariablePredicate() {
        @Override
        boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
            return true
        }
    }

    def "can build itself handing null values"() {
        when:
        VariableAccumulator accumulator = new VariableAccumulator(alwaysTruePredicate)
        accumulateData(accumulator)

        def variablesMap = accumulator.getVariablesMap()
        then:
        variablesMap == [v0: "hello", v1: 666, v2: null, v3: null]
    }

    def "can build variable definitions"() {
        when:
        VariableAccumulator accumulator = new VariableAccumulator(alwaysTruePredicate)
        accumulateData(accumulator)
        def names = accumulator.getVariableDefinitions().collect { vd -> vd.name }
        def typeNames = accumulator.getVariableDefinitions().collect { vd -> (vd.type as TypeName).name }

        then:
        names == ["v0", "v1", "v2", "v3"]
        typeNames == ["String", "Int", "Int", "String"]
    }

    private void accumulateData(VariableAccumulator accumulator) {
        accumulator.accumulateVariable(new NormalizedInputValue("String", StringValue.of("hello")))
        accumulator.accumulateVariable(new NormalizedInputValue("Int", IntValue.of(666)))
        accumulator.accumulateVariable(new NormalizedInputValue("Int", NullValue.of()))
        accumulator.accumulateVariable(new NormalizedInputValue("String", null))
    }
}

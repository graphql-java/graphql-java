package graphql.execution.directives

import graphql.GraphQLContext
import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.execution.MergedField
import graphql.execution.NormalizedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.normalized.NormalizedInputValue
import spock.lang.Specification

import java.util.function.Supplier

import static graphql.language.AstPrinter.printAst

class QueryDirectivesImplTest extends Specification {

    def sdl = '''
        directive @timeout(afterMillis : Int) on FIELD
        
        directive @cached(forMillis : Int = 99) on FIELD | QUERY
        
        directive @upper(place : String) on FIELD
       
        directive @rep(place : String) repeatable on FIELD
 
        type Query {
            f : String
        }
    '''

    def schema = TestUtil.schema(sdl)

    def "can get immediate directives"() {
        def f1 = TestUtil.parseField("f1 @cached @upper")
        def f2 = TestUtil.parseField("f2 @cached(forMillis : \$var) @timeout")

        def mergedField = MergedField.newMergedField([f1, f2]).build()

        def impl = new QueryDirectivesImpl(mergedField, schema,
                CoercedVariables.of([var: 10]),
                { -> NormalizedVariables.of([var: new NormalizedInputValue("type", IntValue.of(10))]) },
                GraphQLContext.getDefault(), Locale.getDefault())

        when:
        def directives = impl.getImmediateDirectivesByName()
        then:
        directives.keySet().sort() == ["cached", "timeout", "upper"]
        impl.getImmediateAppliedDirectivesByName().keySet().sort() == ["cached", "timeout", "upper"]

        when:
        def result = impl.getImmediateDirective("cached")
        def appliedResult = impl.getImmediateAppliedDirective("cached")

        then:
        result.size() == 2
        result[0].getName() == "cached"
        result[1].getName() == "cached"

        result[0].getArgument("forMillis").getArgumentValue().value == 99 // defaults. Retain deprecated method to test getImmediateDirective
        printAst(result[0].getArgument("forMillis").getArgumentDefaultValue().getValue()) == "99"

        result[1].getArgument("forMillis").getArgumentValue().value == 10 // Retain deprecated method to test getImmediateDirective
        printAst(result[1].getArgument("forMillis").getArgumentDefaultValue().getValue()) == "99"

        // the prototypical other properties are copied ok
        result[0].validLocations().collect({ it.name() }).sort() == ["FIELD", "QUERY"]
        result[1].validLocations().collect({ it.name() }).sort() == ["FIELD", "QUERY"]

        appliedResult.size() == 2
        appliedResult[0].getName() == "cached"
        appliedResult[1].getName() == "cached"

        appliedResult[0].getArgument("forMillis").getValue() == 99
        appliedResult[1].getArgument("forMillis").getValue() == 10
    }

    def "ordinary directive lookup does not request normalized variables: #getter"() {
        def normalizedVariables = Mock(Supplier)
        def queryDirectives = QueryDirectives.newQueryDirectives()
                .field(TestUtil.parseField('f @cached(forMillis: $var)'))
                .schema(schema)
                .coercedVariables(CoercedVariables.of([var: 10]))
                .normalizedVariables(normalizedVariables)
                .build()

        when:
        def result = queryDirectives."$getter"(*arguments)

        then:
        !result.isEmpty()
        0 * normalizedVariables.get()

        where:
        getter                                | arguments
        "getImmediateDirectivesByName"        | []
        "getImmediateDirectivesByField"       | []
        "getImmediateDirective"               | ["cached"]
        "getImmediateAppliedDirectivesByName"  | []
        "getImmediateAppliedDirectivesByField" | []
        "getImmediateAppliedDirective"        | ["cached"]
    }

    def "normalized directives reuse resolved instances and are cached with normalized first: #normalizedFirst"() {
        def firstField = TestUtil.parseField('f @rep(place: $var) @cached @rep(place: "HELLO")')
        def secondField = TestUtil.parseField('f @rep(place: "second")')
        int normalizations = 0
        def queryDirectives = QueryDirectives.newQueryDirectives()
                .mergedField(MergedField.newMergedField([firstField, secondField]).build())
                .schema(schema)
                .coercedVariables(CoercedVariables.of([var: "ABC"]))
                .normalizedVariables({
                    normalizations++
                    NormalizedVariables.of([var: new NormalizedInputValue("String", StringValue.of("ABC"))])
                })
                .build()

        when:
        def firstResult = normalizedFirst ? queryDirectives.getNormalizedInputValueByImmediateAppliedDirectives() :
                queryDirectives.getImmediateAppliedDirectivesByName()

        then:
        normalizations == (normalizedFirst ? 1 : 0)

        when:
        def normalized = queryDirectives.getNormalizedInputValueByImmediateAppliedDirectives()
        def applied = queryDirectives.getImmediateAppliedDirectivesByName()

        then:
        normalizations == 1
        normalized.keySet().toList() == applied.values().flatten()
        applied.rep.collect { normalized[it].place.typeName } == ["String", "String", "String"]
        applied.rep.collect { printAst(normalized[it].place.value) } == ['"ABC"', '"HELLO"', '"second"']
        normalized[applied.cached[0]] == [:]
        applied.cached[0].getArgument("forMillis").getValue() == 99
        queryDirectives.getImmediateAppliedDirectivesByField()[firstField] == [applied.rep[0], applied.cached[0], applied.rep[1]]
        queryDirectives.getImmediateAppliedDirectivesByField()[secondField] == [applied.rep[2]]
        queryDirectives.getNormalizedInputValueByImmediateAppliedDirectives().is(normalized)
        firstResult.is(normalizedFirst ? normalized : applied)
        normalizations == 1

        where:
        normalizedFirst << [true, false]
    }

    def "fields without directives do not request normalized variables even for normalized lookup"() {
        def normalizedVariables = Mock(Supplier)
        def queryDirectives = QueryDirectives.newQueryDirectives()
                .field(TestUtil.parseField("f"))
                .schema(schema)
                .normalizedVariables(normalizedVariables)
                .build()

        when:
        def normalized = queryDirectives.getNormalizedInputValueByImmediateAppliedDirectives()
        def applied = queryDirectives.getImmediateAppliedDirectivesByName()

        then:
        normalized.isEmpty()
        applied.isEmpty()
        0 * normalizedVariables.get()
    }

    def "a failed normalized lookup leaves ordinary directive values available"() {
        def queryDirectives = QueryDirectives.newQueryDirectives()
                .field(TestUtil.parseField('f @cached(forMillis: $var)'))
                .schema(schema)
                .coercedVariables(CoercedVariables.of([var: 10]))
                .normalizedVariables({ throw new UnsupportedOperationException("valueToLiteral is not supported") })
                .build()

        when:
        queryDirectives.getNormalizedInputValueByImmediateAppliedDirectives()

        then:
        thrown(UnsupportedOperationException)

        when:
        def applied = queryDirectives.getImmediateAppliedDirective("cached")

        then:
        applied[0].getArgument("forMillis").getValue() == 10
        queryDirectives.getImmediateDirective("cached")[0].getArgument("forMillis").getArgumentValue().value == 10
    }

    def "builder works as expected"() {
        def f1 = TestUtil.parseField("f1 @cached @upper")
        def f2 = TestUtil.parseField("f2 @cached(forMillis : \$var) @timeout")

        def mergedField = MergedField.newMergedField([f1, f2]).build()

        def queryDirectives = QueryDirectives.newQueryDirectives()
                .mergedField(mergedField)
                .schema(schema)
                .coercedVariables(CoercedVariables.of([var: 10]))
                .normalizedVariables({ NormalizedVariables.of([var: new NormalizedInputValue("type", IntValue.of(10))]) })
                .graphQLContext(GraphQLContext.getDefault())
                .locale(Locale.getDefault())
                .build()

        when:
        def appliedDirectivesByName = queryDirectives.getImmediateAppliedDirectivesByName()

        then:
        appliedDirectivesByName.keySet().sort() == ["cached", "timeout", "upper"]
    }

    def "gets repeated definitions"() {
        def f1 = TestUtil.parseField("f1 @rep(place: \$var) @rep(place: \"HELLO\")")

        def mergedField = MergedField.newMergedField([f1]).build()

        def queryDirectives = QueryDirectives.newQueryDirectives()
                .mergedField(mergedField)
                .schema(schema)
                .coercedVariables(CoercedVariables.of([var: "ABC"]))
                .graphQLContext(GraphQLContext.getDefault())
                .locale(Locale.getDefault())
                .build()

        when:
        def appliedDirectivesByName = queryDirectives.getImmediateAppliedDirectivesByName()

        then:
        appliedDirectivesByName.keySet() == ["rep"] as Set
        appliedDirectivesByName["rep"].size() == 2
        // Groovy is a pathway to many abilities some consider to be unnatural
        appliedDirectivesByName["rep"].arguments.value.flatten().sort() == ["ABC", "HELLO"]
    }
}

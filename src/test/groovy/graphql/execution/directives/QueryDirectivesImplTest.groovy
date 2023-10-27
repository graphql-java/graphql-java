package graphql.execution.directives

import graphql.GraphQLContext
import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.execution.MergedField
import spock.lang.Specification

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

        def impl = new QueryDirectivesImpl(mergedField, schema, [var: 10], GraphQLContext.getDefault(), Locale.getDefault())

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

    def "builder works as expected"() {
        def f1 = TestUtil.parseField("f1 @cached @upper")
        def f2 = TestUtil.parseField("f2 @cached(forMillis : \$var) @timeout")

        def mergedField = MergedField.newMergedField([f1, f2]).build()

        def queryDirectives = QueryDirectives.newQueryDirectives()
                .mergedField(mergedField)
                .schema(schema)
                .coercedVariables(CoercedVariables.of([var: 10]))
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

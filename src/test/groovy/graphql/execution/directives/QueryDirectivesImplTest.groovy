package graphql.execution.directives

import graphql.TestUtil
import graphql.execution.MergedField
import spock.lang.Specification

import static graphql.language.AstPrinter.printAst

class QueryDirectivesImplTest extends Specification {

    def sdl = '''
        directive @timeout(afterMillis : Int) on FIELD
        
        directive @cached(forMillis : Int = 99) on FIELD | QUERY
        
        directive @upper(place : String) on FIELD
 
        type Query {
            f : String
        }
    '''

    def schema = TestUtil.schema(sdl)


    def "can get immediate directives"() {

        def f1 = TestUtil.parseField("f1 @cached @upper")
        def f2 = TestUtil.parseField("f2 @cached(forMillis : \$var) @timeout")

        def mergedField = MergedField.newMergedField([f1, f2]).build()

        def impl = new QueryDirectivesImpl(mergedField, schema, [var: 10])

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

        result[0].getArgument("forMillis").getArgumentValue().value == 99 // defaults
        printAst(result[0].getArgument("forMillis").getArgumentDefaultValue().getValue()) == "99"

        result[1].getArgument("forMillis").getArgumentValue().value == 10
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

}

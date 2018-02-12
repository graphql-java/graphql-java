package graphql.analysis

import graphql.TestUtil
import spock.lang.Specification

import static graphql.StarWarsSchema.starWarsSchema

class OperationDependencyCheckerTest extends Specification {

    def "can discover variables"() {
        given:
        def query = '''
            query A {
                hero 
                {
                    id @export(as:"droidId")
                    name 
                    friends  @export(as:"r2d2Friends") 
                    {
                        name @export(as:"friendNames")
                    }
                }
            }
            
            query B($droidId : String!) {
                droid (id : $droidId ) {
                    name
                }
            }
        '''

        def doc = TestUtil.parseQuery(query)

        when:
        def checker = new OperationDependencyChecker(starWarsSchema, doc, [:])

        then:
        checker.exportedVariables == [A: ['droidId', 'friendNames', 'r2d2Friends'] as Set]
        checker.consumedVariables == [B: ['droidId'] as Set]

        checker.currentIsDependentOnPrevious("B", "A")
        !checker.currentIsDependentOnPrevious("A", "B")
    }

    def "can handle implicit single query"() {
        given:
        def query = '''
            query {
                hero 
                {
                    id @export(as:"droidId")
                    name 
                    friends  @export(as:"r2d2Friends") 
                    {
                        name @export(as:"friendNames")
                    }
                }
            }
        '''

        def doc = TestUtil.parseQuery(query)

        when:
        def checker = new OperationDependencyChecker(starWarsSchema, doc, [:])

        then:
        checker.exportedVariables == [implicit: ['droidId', 'friendNames', 'r2d2Friends'] as Set]
    }
}

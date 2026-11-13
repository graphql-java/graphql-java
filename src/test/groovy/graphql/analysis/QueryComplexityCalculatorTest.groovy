package graphql.analysis


import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.language.Document
import graphql.parser.Parser
import spock.lang.Specification

class QueryComplexityCalculatorTest extends Specification {

    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    def "can calculator complexity"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                scalar: String  
                foo: Foo
            }
        """)
        def query = createQuery("""
            query q {
                f2: foo {scalar foo{scalar}} 
                f1: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)


        when:
        FieldComplexityCalculator fieldComplexityCalculator = new FieldComplexityCalculator() {
            @Override
            int calculate(FieldComplexityEnvironment environment, int childComplexity) {
                return environment.getField().name.startsWith("foo") ? 10 : 1
            }
        }
        QueryComplexityCalculator calculator = QueryComplexityCalculator.newCalculator()
                .fieldComplexityCalculator(fieldComplexityCalculator).schema(schema).document(query).variables(CoercedVariables.emptyVariables())
                .build()
        def complexityScore = calculator.calculate()
        then:
        complexityScore == 20


    }
}

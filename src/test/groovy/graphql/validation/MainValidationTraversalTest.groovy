package graphql.validation


import spock.lang.Specification

import static graphql.TestUtil.parseQuery
import static graphql.TestUtil.schema

class MainValidationTraversalTest extends Specification {


    def "just runs"() {
        given:
        def sdl = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        """
        def query = "{foo{bar}}"
        def schema = schema(sdl)
        def document = parseQuery(query)
        ValidationErrorCollector validationErrorCollector = new ValidationErrorCollector();
        def traversal = newMainValidationTraversal(schema, document, validationErrorCollector)

        when:
        traversal.checkDocument()

        then:
        validationErrorCollector.getErrors().size() == 0

    }

    MainValidationTraversal newMainValidationTraversal(
            schema,
            document,
            ValidationErrorCollector validationErrorCollector) {
        ValidationContext validationContext = new ValidationContext(schema, document);
        List<AbstractRule> rules = []
        def rulesVisitor = new RulesVisitor(validationContext, rules)

        MainValidationTraversal mainValidationTraversal = new MainValidationTraversal(schema,
                document,
                null,
                rulesVisitor,
                validationContext,
                validationErrorCollector)
        mainValidationTraversal
    }
}

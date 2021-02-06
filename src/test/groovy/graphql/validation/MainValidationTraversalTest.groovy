package graphql.validation

import graphql.normalized.NormalizedField
import graphql.normalized.NormalizedQueryTree
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
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

    def "creates Normalized tree"() {
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
        traversal.checkDocument()

        when:
        def tree = traversal.getNormalizedQueryTree();

        then:
        printTree(tree) == ['Query.foo: Foo (conditional: false)',
                            'Foo.bar: String (conditional: false)']


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

    List<String> printTree(NormalizedQueryTree queryExecutionTree) {
        def result = []
        Traverser<NormalizedField> traverser = Traverser.depthFirst({ it.getChildren() });
        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<NormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<NormalizedField> context) {
                NormalizedField queryExecutionField = context.thisNode();
                result << queryExecutionField.printDetails()
                return TraversalControl.CONTINUE;
            }
        });
        result
    }


}

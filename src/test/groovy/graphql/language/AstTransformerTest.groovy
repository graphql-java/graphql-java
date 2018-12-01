package graphql.language

import graphql.TestUtil
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

class AstTransformerTest extends Specification {

    def "test"() {
        def document = TestUtil.parseQuery("{root{foo {midA {leafA} midB {leafB}} bar{midC{leafC} midD{leafD}}}}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                AstMultiZipper multiZipper = context.getCurrentAccumulate();
                multiZipper.withNewZipper()
                return super.visitField(node, context)
            }
        }
        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        newDocument != document
    }
}

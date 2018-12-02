package graphql.language

import graphql.TestUtil
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

class AstTransformerTest extends Specification {

    def "test"() {
        def document = TestUtil.parseQuery("{ root { foo { midA { leafA } midB { leafB } } bar { midC { leafC } midD { leafD } } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                AstMultiZipper multiZipper = context.getCurrentAccumulate();
                if (node.name.startsWith("mid")) {
                    String newName = node.name + "-modified"
                    def zipper = context
                            .getVar(AstZipper.class)
                            .withNewNode(node.transform({ builder -> builder.name(newName) }))
                    multiZipper = multiZipper.withNewZipper(zipper)
                }
                context.setAccumulate(multiZipper)
                return super.visitField(node, context)
            }
        }
        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        AstPrinter.printAstCompact(newDocument) == "query { root { foo { midA-modified { leafA } midB-modified { leafB } } bar { midC-modified { leafC } midD-modified { leafD } } } }"

    }
}

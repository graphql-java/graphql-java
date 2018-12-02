package graphql.language

import graphql.TestUtil
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

class AstTransformerTest extends Specification {

    def "modify multiple nodes"() {
        def document = TestUtil.parseQuery("{ root { foo { midA { leafA } midB { leafB } } bar { midC { leafC } midD { leafD } } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                if (!node.name.startsWith("mid")) {
                    return TraversalControl.CONTINUE
                }
                String newName = node.name + "-modified"

                Field changedField = node.transform({ builder -> builder.name(newName) })
                def zipperWithChangedNode = context.getVar(AstZipper.class).withNewNode(changedField)

                AstMultiZipper multiZipper = context.getCurrentAccumulate();
                context.setAccumulate(multiZipper.withNewZipper(zipperWithChangedNode))

                return TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        AstPrinter.printAstCompact(newDocument) ==
                "query { root { foo { midA-modified { leafA } midB-modified { leafB } } bar { midC-modified { leafC } midD-modified { leafD } } } }"

    }

}

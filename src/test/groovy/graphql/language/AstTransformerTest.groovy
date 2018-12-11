package graphql.language

import graphql.TestUtil
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.language.AstTransformerUtil.changeNode

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
                changeNode(context, changedField)
                return TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        AstPrinter.printAstCompact(newDocument) ==
                "query { root { foo { midA-modified { leafA } midB-modified { leafB } } bar { midC-modified { leafC } midD-modified { leafD } } } }"
    }

    def "no change at all"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()


        when:
        def newDocument = astTransformer.transform(document, new NodeVisitorStub())

        then:
        newDocument == document

    }

    def "one node changed"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                Field changedField = node.transform({ builder -> builder.name("foo2") })
                changeNode(context, changedField)
                return TraversalControl.CONTINUE
            }
        }


        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        AstPrinter.printAstCompact(newDocument) == "query { foo2 }"

    }

}

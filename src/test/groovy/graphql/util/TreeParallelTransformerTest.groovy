package graphql.util

import graphql.TestUtil
import graphql.language.Field
import graphql.language.Node
import spock.lang.Specification

import static graphql.language.AstNodeAdapter.AST_NODE_ADAPTER
import static graphql.language.AstPrinter.printAstCompact
import static graphql.util.TreeTransformerUtil.changeNode

class TreeParallelTransformerTest extends Specification {

    def "one node changed"() {
        def document = TestUtil.parseQuery("{foo}")

        TreeParallelTransformer<Node> parallelTransformer = TreeParallelTransformer.parallelTransformer(AST_NODE_ADAPTER)
        def visitor = new TraverserVisitorStub<Node>() {

            @Override
            TraversalControl enter(TraverserContext<Node> context) {
                Node node = context.thisNode()
                if (!(node instanceof Field)) return TraversalControl.CONTINUE;
                Field changedField = node.transform({ builder -> builder.name("foo2") })
                return changeNode(context, changedField)
            }
        }


        when:
        def newDocument = parallelTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo2}"

    }

    def "abort traversing"() {
        def document = TestUtil.parseQuery("{foo}")

        TreeParallelTransformer<Node> parallelTransformer = TreeParallelTransformer.parallelTransformer(AST_NODE_ADAPTER)
        def visitor = new TraverserVisitorStub<Node>() {

            @Override
            TraversalControl enter(TraverserContext<Node> context) {
                Node node = context.thisNode()
                if (!(node instanceof Field)) return TraversalControl.CONTINUE;
                Field changedField = node.transform({ builder -> builder.name("foo2") })
                changeNode(context, changedField)
                return TraversalControl.ABORT
            }
        }


        when:
        def newDocument = parallelTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo2}"

    }
}


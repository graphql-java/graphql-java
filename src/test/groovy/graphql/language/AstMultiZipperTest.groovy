package graphql.language

import graphql.TestUtil
import graphql.util.Breadcrumb
import graphql.util.NodeLocation
import graphql.util.NodeMultiZipper
import graphql.util.NodeZipper
import spock.lang.Specification

import static graphql.language.AstNodeAdapter.AST_NODE_ADAPTER


class AstMultiZipperTest extends Specification {


    def "toRootNode with one zipper"() {
        def rootNode = TestUtil.parseQuery("{root{midA{leafA leafB} midB{leafC leafD}}}")
        def operationDefinition = rootNode.children[0] as OperationDefinition

        def rootField = operationDefinition.selectionSet.children[0] as Field
        def midA = rootField.selectionSet.children[0] as Field

        def midB = rootField.selectionSet.children[1] as Field


        def b1 = new Breadcrumb(rootNode, new NodeLocation(Document.CHILD_DEFINITIONS, 0))
        def b2 = new Breadcrumb(operationDefinition, new NodeLocation(OperationDefinition.CHILD_SELECTION_SET, 0))
        def b3 = new Breadcrumb(operationDefinition.selectionSet, new NodeLocation(SelectionSet.CHILD_SELECTIONS, 0))
        def b4 = new Breadcrumb(rootField, new NodeLocation(Field.CHILD_SELECTION_SET, 0))
        def b5 = new Breadcrumb(rootField.selectionSet, new NodeLocation(SelectionSet.CHILD_SELECTIONS, 0))

        def breadCrumbsFromMidA = [b5, b4, b3, b2, b1]

        def midAZipper = new NodeZipper(midA, breadCrumbsFromMidA, AST_NODE_ADAPTER)
        def midAZipperChanged = midAZipper.withNewNode(midA.transform({ builder -> builder.name("midA-changed") }))
        def multiZipper = new NodeMultiZipper(rootNode, [midAZipperChanged], AST_NODE_ADAPTER)

        when:
        def newRoot = multiZipper.toRootNode()
        def newMidA = getMidA(newRoot) as Field

        then:
        newMidA.name == "midA-changed"
    }

    Node rootField(Document document) {
        def operationDefinition = document.children[0] as OperationDefinition
        operationDefinition.selectionSet.children[0] as Field
    }

    Node getMidA(Document document) {
        rootField(document).selectionSet.children[0] as Field
    }

    Node getMidB(Document document) {
        rootField(document).selectionSet.children[1] as Field
    }

    def "toRootNode with two zippers"() {
        def rootNode = TestUtil.parseQuery("{root{midA{leafA leafB} midB{leafC leafD}}}")
        def operationDefinition = rootNode.children[0] as OperationDefinition

        def rootField = operationDefinition.selectionSet.children[0] as Field
        def midA = rootField.selectionSet.children[0] as Field

        def midB = rootField.selectionSet.children[1] as Field


        def b1 = new Breadcrumb(rootNode, new NodeLocation(Document.CHILD_DEFINITIONS, 0))
        def b2 = new Breadcrumb(operationDefinition, new NodeLocation(OperationDefinition.CHILD_SELECTION_SET, 0))
        def b3 = new Breadcrumb(operationDefinition.selectionSet, new NodeLocation(SelectionSet.CHILD_SELECTIONS, 0))
        def b4 = new Breadcrumb(rootField, new NodeLocation(Field.CHILD_SELECTION_SET, 0))
        def bMidA = new Breadcrumb(rootField.selectionSet, new NodeLocation(SelectionSet.CHILD_SELECTIONS, 0))
        def bMidB = new Breadcrumb(rootField.selectionSet, new NodeLocation(SelectionSet.CHILD_SELECTIONS, 1))

        def breadCrumbsFromMidA = [bMidA, b4, b3, b2, b1]
        def breadCrumbsFromMidB = [bMidB, b4, b3, b2, b1]

        def midAChanged = midA.transform({ builder -> builder.name("midA-changed") })
        def midBChanged = midB.transform({ builder -> builder.name("midB-changed") })

        def midAZipperChanged = new NodeZipper(midAChanged, breadCrumbsFromMidA, AST_NODE_ADAPTER)
        def midBZipperChanged = new NodeZipper(midBChanged, breadCrumbsFromMidB, AST_NODE_ADAPTER)
        def multiZipper = new NodeMultiZipper(rootNode, [midAZipperChanged, midBZipperChanged], AST_NODE_ADAPTER)

        when:
        def newRoot = multiZipper.toRootNode()
        def newMidA = getMidA(newRoot) as Field
        def newMidB = getMidB(newRoot) as Field

        then:
        newMidA.name == "midA-changed"
        newMidB.name == "midB-changed"

    }
}

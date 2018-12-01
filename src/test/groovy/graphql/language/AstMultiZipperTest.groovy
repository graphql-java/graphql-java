package graphql.language

import graphql.TestUtil
import spock.lang.Specification

import static graphql.language.AstBreadcrumb.Location

class AstMultiZipperTest extends Specification {


    def "toRootNode"() {
        def rootNode = TestUtil.parseQuery("{root{midA{leafA leafB} midB{leafC leafD}}}")
        def operationDefinition = rootNode.children[0] as OperationDefinition

        def rootField = operationDefinition.selectionSet.children[0] as Field
        def midA = rootField.selectionSet.children[0] as Field
        def leafA = midA.selectionSet.children[0] as Field
        def leafB = midA.selectionSet.children[0] as Field

        def midB = rootField.selectionSet.children[1] as Field
        def leafC = midB.selectionSet.children[0] as Field
        def leafD = midB.selectionSet.children[0] as Field


        def b1 = new AstBreadcrumb(rootNode, new Location(Document.CHILD_DEFINITIONS, 0))
        def b2 = new AstBreadcrumb(operationDefinition, new Location(OperationDefinition.CHILD_SELECTION_SET, 0))
        def b3 = new AstBreadcrumb(operationDefinition.selectionSet, new Location(SelectionSet.CHILD_SELECTIONS, 0))
        def b4 = new AstBreadcrumb(rootField, new Location(Field.CHILD_SELECTION_SET, 0))
        def b5 = new AstBreadcrumb(rootField.selectionSet, new Location(SelectionSet.CHILD_SELECTIONS, 0))

        def breadCrumbsFromMidA = [b5, b4, b3, b2, b1]

        def midAZipper = new AstZipper(midA, breadCrumbsFromMidA)
        def midAZipperChanged = midAZipper.withNewNode(midA.transform({ builder -> builder.name("midA-changed") }))
        def multiZipper = new AstMultiZipper(rootNode, [midAZipperChanged])

        when:
        def newRoot = multiZipper.toRootNode()
        def newMidA = getMidANode(newRoot) as Field

        then:
        newMidA.name == "midA-changed"
    }

    Node getMidANode(Node rootNode) {
        def operationDefinition = rootNode.children[0] as OperationDefinition
        def rootField = operationDefinition.selectionSet.children[0] as Field
        def midA = rootField.selectionSet.children[0] as Field
        return midA
    }
}

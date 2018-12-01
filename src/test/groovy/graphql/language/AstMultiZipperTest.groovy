package graphql.language

import graphql.TestUtil
import spock.lang.Specification

import static graphql.language.AstBreadcrumb.Location

class AstMultiZipperTest extends Specification {


    def "toRootNode with one zipper"() {
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
        def leafA = midA.selectionSet.children[0] as Field
        def leafB = midA.selectionSet.children[0] as Field

        def midB = rootField.selectionSet.children[1] as Field
        def leafC = midB.selectionSet.children[0] as Field
        def leafD = midB.selectionSet.children[0] as Field


        def b1 = new AstBreadcrumb(rootNode, new Location(Document.CHILD_DEFINITIONS, 0))
        def b2 = new AstBreadcrumb(operationDefinition, new Location(OperationDefinition.CHILD_SELECTION_SET, 0))
        def b3 = new AstBreadcrumb(operationDefinition.selectionSet, new Location(SelectionSet.CHILD_SELECTIONS, 0))
        def b4 = new AstBreadcrumb(rootField, new Location(Field.CHILD_SELECTION_SET, 0))
        def bMidA = new AstBreadcrumb(rootField.selectionSet, new Location(SelectionSet.CHILD_SELECTIONS, 0))
        def bMidB = new AstBreadcrumb(rootField.selectionSet, new Location(SelectionSet.CHILD_SELECTIONS, 1))

        def breadCrumbsFromMidA = [bMidA, b4, b3, b2, b1]
        def breadCrumbsFromMidB = [bMidB, b4, b3, b2, b1]

        def midAChanged = midA.transform({ builder -> builder.name("midA-changed") })
        def midBChanged = midB.transform({ builder -> builder.name("midB-changed") })

        def midAZipperChanged = new AstZipper(midAChanged, breadCrumbsFromMidA)
        def midBZipperChanged = new AstZipper(midBChanged, breadCrumbsFromMidB)
        def multiZipper = new AstMultiZipper(rootNode, [midAZipperChanged, midBZipperChanged])

        when:
        def newRoot = multiZipper.toRootNode()
        def newMidA = getMidA(newRoot) as Field
        def newMidB = getMidB(newRoot) as Field

        then:
        newMidA.name == "midA-changed"
        newMidB.name == "midB-changed"

    }
}

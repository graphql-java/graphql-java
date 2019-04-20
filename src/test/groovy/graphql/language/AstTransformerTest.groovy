package graphql.language

import graphql.TestUtil
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.language.AstPrinter.printAstCompact
import static graphql.util.TreeTransformerUtil.changeNode
import static graphql.util.TreeTransformerUtil.changeParentNode
import static graphql.util.TreeTransformerUtil.deleteNode

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
                return changeNode(context, changedField)
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) ==
                "query {root {foo {midA-modified {leafA} midB-modified {leafB}} bar {midC-modified {leafC} midD-modified {leafD}}}}"
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
                return changeNode(context, changedField)
            }
        }


        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo2}"

    }

    def "add new children"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {


            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                if (node.name != "foo") return TraversalControl.CONTINUE;
                def newSelectionSet = SelectionSet.newSelectionSet([new Field("a"), new Field("b")]).build()
                Field changedField = node.transform({ builder -> builder.name("foo2").selectionSet(newSelectionSet) })
                return changeNode(context, changedField)
            }
        }


        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo2 {a b}}"

    }


    def "reorder children and sub children"() {
        def document = TestUtil.parseQuery("{root { b(b_arg: 1) { y x } a(a_arg:2) { w v } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> context) {
                if (node.getChildren().isEmpty()) return TraversalControl.CONTINUE;
                def selections = node.getSelections()
                Collections.sort(selections, { o1, o2 -> (o1.name <=> o2.name) })
                Node changed = node.transform({ builder -> builder.selections(selections) })
                return changeNode(context, changed)
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {root {a(a_arg:2) {v w} b(b_arg:1) {x y}}}"

    }


    def "remove a subtree "() {
        def document = TestUtil.parseQuery("{root { a(arg: 1) { x y } toDelete { x y } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> context) {
                if (context.getParentContext().thisNode().name == "root") {
                    def newNode = node.transform({ builder -> builder.selections([node.selections[0]]) })
                    return changeNode(context, newNode)
                }
                return TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {root {a(arg:1) {x y}}}"

    }

    def "delete node"() {
        def document = TestUtil.parseQuery("{root { a(arg: 1) { x y } toDelete { x y } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field field, TraverserContext<Node> context) {
                if (field.name == "toDelete") {
                    return deleteNode(context);
                } else {
                    return TraversalControl.CONTINUE;
                }
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {root {a(arg:1) {x y}}}"

    }

    def "delete multiple nodes and change others"() {
        def document = TestUtil.parseQuery("{root { a(arg: 1) { x1 y1 } b { x2 y2 } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field field, TraverserContext<Node> context) {
                if (field.name == "x1" || field.name == "x2") {
                    return deleteNode(context);
                } else if (field.name == "a") {
                    return changeNode(context, field.transform({ builder -> builder.name("aChanged") }))

                } else if (field.name == "root") {
                    Field addField = new Field("new")
                    def newSelectionSet = field.getSelectionSet().transform({ builder -> builder.selection(addField) })
                    changeNode(context, field.transform({ builder -> builder.selectionSet(newSelectionSet) }))
                } else {
                    return TraversalControl.CONTINUE;
                }
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:

        printAstCompact(newDocument) == "query {root {aChanged(arg:1) {y1} b {y2} new}}"

    }

    def "add sibling"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                return changeParentNode(context, { selectionSet ->
                    selectionSet.transform({ builder -> builder.selection(new Field("foo2")) })
                })
            }
        }


        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo foo2}"

    }

    def "delete node and add sibling"() {
        def document = TestUtil.parseQuery("{root { a(arg: 1) { x y } toDelete { x y } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field field, TraverserContext<Node> context) {
                if (field.name == "toDelete") {
                    return deleteNode(context);
                } else if (field.name == "a") {
                    return changeParentNode(context, { selectionSet ->
                        selectionSet.transform({ builder -> builder.selection(new Field("newOne")) })
                    })
                } else {
                    return TraversalControl.CONTINUE
                }
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {root {a(arg:1) {x y} newOne}}"

    }

    def "change root node"() {
        def document = TestUtil.parseQuery("query A{ field } query B{ fieldB }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitDocument(Document node, TraverserContext<Node> context) {
                def children = node.getChildren()
                children.remove(0)
                def newNode = node.transform({ builder -> builder.definitions(children) })
                changeNode(context, newNode)
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query B {fieldB}"

    }


}

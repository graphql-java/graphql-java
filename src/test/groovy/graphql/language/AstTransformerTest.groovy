package graphql.language

import graphql.TestUtil
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.language.AstPrinter.printAst
import static graphql.language.AstPrinter.printAstCompact
import static graphql.util.TreeTransformerUtil.changeNode
import static graphql.util.TreeTransformerUtil.deleteNode
import static graphql.util.TreeTransformerUtil.insertAfter
import static graphql.util.TreeTransformerUtil.insertBefore

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

    def "modify multiple nodes parallel"() {
        def document = TestUtil.parseQuery("{ root { foo { midA { leafA } midB { leafB } } bar { midC { leafC } midD { leafD } } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                Thread.sleep(50);
                if (!node.name.startsWith("mid")) {
                    return TraversalControl.CONTINUE
                }
                String newName = node.name + "-modified"

                Field changedField = node.transform({ builder -> builder.name(newName) })
                return changeNode(context, changedField)
            }
        }

        when:
        def newDocument = astTransformer.transformParallel(document, visitor)

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

    def "no change at all parallel"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()


        when:
        def newDocument = astTransformer.transformParallel(document, new NodeVisitorStub())

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

    def "one node changed parallel"() {
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
        def newDocument = astTransformer.transformParallel(document, visitor)

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

    def "add new children parallel"() {
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
        def newDocument = astTransformer.transformParallel(document, visitor)

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
                def selections = new ArrayList<>(node.getSelections())
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

    def "reorder children and sub children parallel"() {
        def document = TestUtil.parseQuery("{root { b(b_arg: 1) { y x } a(a_arg:2) { w v } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> context) {
                if (node.getChildren().isEmpty()) return TraversalControl.CONTINUE;
                def selections = new ArrayList<>(node.getSelections())
                Collections.sort(selections, { o1, o2 -> (o1.name <=> o2.name) })
                Node changed = node.transform({ builder -> builder.selections(selections) })
                return changeNode(context, changed)
            }
        }

        when:
        def newDocument = astTransformer.transformParallel(document, visitor)

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

    def "remove a subtree parallel"() {
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
        def newDocument = astTransformer.transformParallel(document, visitor)

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

    def "delete node through context"() {
        def document = TestUtil.parseQuery("{root { a(arg: 1) { x y } toDelete { x y } } }")

        final Map<Class<?>, Object> rootVars = new LinkedHashMap<>();
        rootVars.put(String.class, "toDelete");

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field field, TraverserContext<Node> context) {
                final String fieldToDelete = context.getVarFromParents(String.class);
                if (field.name == fieldToDelete) {
                    return deleteNode(context);
                } else {
                    return TraversalControl.CONTINUE;
                }
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor, rootVars)

        then:
        printAstCompact(newDocument) == "query {root {a(arg:1) {x y}}}"

    }

    def "delete node parallel"() {
        def document = TestUtil.parseQuery("{root { a(arg: 1) { x y } toDelete { x y } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field field, TraverserContext<Node> context) {
                if (field.name == "toDelete") {
                    Thread.sleep(250)
                    return deleteNode(context);
                } else {
                    return TraversalControl.CONTINUE;
                }
            }
        }

        when:
        def newDocument = astTransformer.transformParallel(document, visitor)

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
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:

        printAstCompact(newDocument) == "query {root {aChanged(arg:1) {y1} b {y2} new}}"
        printAstCompact(newDocumentParallel) == "query {root {aChanged(arg:1) {y1} b {y2} new}}"

    }

    def "add sibling after"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                return insertAfter(context, new Field("foo2"))
            }
        }


        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo foo2}"
        printAstCompact(newDocumentParallel) == "query {foo foo2}"

    }

    def "add sibling before"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                return insertBefore(context, new Field("foo2"))
            }
        }


        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo2 foo}"
        printAstCompact(newDocumentParallel) == "query {foo2 foo}"

    }

    def "add sibling before and after"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                insertBefore(context, new Field("foo2"))
                insertAfter(context, new Field("foo3"))
                TraversalControl.CONTINUE
            }
        }


        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo2 foo foo3}"

    }

    def "add sibling before and after parallel"() {
        def document = TestUtil.parseQuery("{foo}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                insertBefore(context, new Field("foo2"))
                insertAfter(context, new Field("foo3"))
                TraversalControl.CONTINUE
            }
        }


        when:
        def newDocument = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {foo2 foo foo3}"

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
                    return insertAfter(context, new Field("newOne"))
                } else {
                    return TraversalControl.CONTINUE
                }
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {root {a(arg:1) {x y} newOne}}"
        printAstCompact(newDocumentParallel) == "query {root {a(arg:1) {x y} newOne}}"

    }

    def "delete node and change sibling"() {
        def document = TestUtil.parseQuery("{root { a(arg: 1) { x y } toDelete { x y } } }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field field, TraverserContext<Node> context) {
                if (field.name == "toDelete") {
                    return deleteNode(context);
                } else if (field.name == "a") {
                    def newNode = field.transform({ builder -> builder.name("a-changed") })
                    return changeNode(context, newNode)
                } else {
                    return TraversalControl.CONTINUE
                }
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {root {a-changed(arg:1) {x y}}}"
        printAstCompact(newDocumentParallel) == "query {root {a-changed(arg:1) {x y}}}"


    }

    def "change root node"() {
        def document = TestUtil.parseQuery("query A{ field } query B{ fieldB }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitDocument(Document node, TraverserContext<Node> context) {
                def children = new ArrayList<>(node.getChildren())
                children.remove(0)
                def newNode = node.transform({ builder -> builder.definitions(children) })
                changeNode(context, newNode)
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query B {fieldB}"
        printAstCompact(newDocumentParallel) == "query B {fieldB}"

    }

    def "change different kind of children"() {
        def document = TestUtil.parseQuery("{ field(arg1:1, arg2:2) @directive1 @directive2}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitDirective(Directive node, TraverserContext<Node> context) {
                if (node.name == "directive1") {
                    insertAfter(context, new Directive("after1Directive"))
                } else {
                    deleteNode(context)
                    insertAfter(context, new Directive("newDirective2"))
                }
                TraversalControl.CONTINUE
            }

            @Override
            TraversalControl visitArgument(Argument node, TraverserContext<Node> context) {
                if (node.name == "arg1") {
                    deleteNode(context)
                    insertAfter(context, new Argument("newArg1", new IntValue(BigInteger.TEN)))
                } else {
                    insertAfter(context, new Argument("arg3", new IntValue(BigInteger.TEN)))
                }
                TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {field(newArg1:10,arg2:2,arg3:10) @directive1 @after1Directive @newDirective2}"
        printAstCompact(newDocumentParallel) == "query {field(newArg1:10,arg2:2,arg3:10) @directive1 @after1Directive @newDirective2}"

    }

    def "insertAfter and then insertBefore"() {
        def document = TestUtil.parseQuery("{ field @directive1 @directive2}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitDirective(Directive node, TraverserContext<Node> context) {
                if (node.name == "directive1") {
                    insertAfter(context, new Directive("after"))
                    insertBefore(context, new Directive("before"))
                }
                TraversalControl.CONTINUE
            }

            @Override
            TraversalControl visitArgument(Argument node, TraverserContext<Node> context) {
                TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {field @before @directive1 @after @directive2}"
        printAstCompact(newDocumentParallel) == "query {field @before @directive1 @after @directive2}"

    }


    def "mix of all modifications at once"() {
        def document = TestUtil.parseQuery("{ field(arg1:1, arg2:2) @directive1 @directive2}")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitDirective(Directive node, TraverserContext<Node> context) {

                if (node.name == "directive1") {
                    insertAfter(context, new Directive("d4"))
                    insertBefore(context, new Directive("d5"))
                } else {
                    insertBefore(context, new Directive("d1"))
                    insertBefore(context, new Directive("d2"))
                    deleteNode(context)
                    insertAfter(context, new Directive("d3"))
                }
                TraversalControl.CONTINUE
            }

            @Override
            TraversalControl visitArgument(Argument node, TraverserContext<Node> context) {
                if (node.name == "arg1") {
                    insertAfter(context, new Argument("a1", new IntValue(BigInteger.TEN)))
                } else {
                    deleteNode(context)
                    insertAfter(context, new Argument("a2", new IntValue(BigInteger.TEN)))
                    insertAfter(context, new Argument("a3", new IntValue(BigInteger.TEN)))
                    insertBefore(context, new Argument("a4", new IntValue(BigInteger.TEN)))
                }
                TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {field(arg1:1,a1:10,a4:10,a2:10,a3:10) @d5 @directive1 @d4 @d1 @d2 @d3}"
        printAstCompact(newDocumentParallel) == "query {field(arg1:1,a1:10,a4:10,a2:10,a3:10) @d5 @directive1 @d4 @d1 @d2 @d3}"

    }

    def "replace first , insert After twice, replace second"() {
        def document = TestUtil.parseQuery("{ first second }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field field, TraverserContext<Node> context) {
                if (field.name == "first") {
                    changeNode(context, new Field("first-changed"))
                    return TraversalControl.CONTINUE
                } else {
                    insertAfter(context, new Field("after-second-1"))
                    insertAfter(context, new Field("after-second-2"))
                    changeNode(context, new Field("second-changed"))
                    return TraversalControl.CONTINUE
                }

            }

        }

        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {first-changed second-changed after-second-1 after-second-2}"
        printAstCompact(newDocumentParallel) == "query {first-changed second-changed after-second-1 after-second-2}"

    }

    def "replaced and inserted in random order"() {
        def document = TestUtil.parseQuery("{ field }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field field, TraverserContext<Node> context) {
                insertBefore(context, new Field("before-1"))
                changeNode(context, new Field("changed"))
                insertAfter(context, new Field("after-1"))
                insertAfter(context, new Field("after-2"))
                changeNode(context, new Field("changed"))
                insertBefore(context, new Field("before-2"))
                insertAfter(context, new Field("after-3"))
                return TraversalControl.CONTINUE

            }

        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        printAstCompact(newDocument) == "query {before-1 before-2 changed after-1 after-2 after-3}"

    }

    def "changeNode can be called multiple times"() {
        def document = TestUtil.parseQuery("{ field }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitField(Field node, TraverserContext<Node> context) {
                changeNode(context, new Field("change1"))
                changeNode(context, new Field("change2"))
                changeNode(context, new Field("change3"))
                TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)
        def newDocumentParallel = astTransformer.transformParallel(document, visitor)

        then:
        printAstCompact(newDocument) == "query {change3}"
        printAstCompact(newDocumentParallel) == "query {change3}"

    }


    def "delete root node"() {
        def document = TestUtil.parseQuery("{ field }")

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitDocument(Document node, TraverserContext<Node> context) {
                deleteNode(context)
                TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        newDocument == null

    }

    def "regression: changing a property of an extension should not change the extension to a base definition"() {
        def document = TestUtil.toDocument("""
            extend schema { query: Query }
            extend type MyObjectType { login: String }
            extend input MyInputObjectType { login: String }
            extend interface MyInterface { login: String }
            extend enum MyEnum { MONDAY }
            extend scalar MyScalar @myDirective
            extend union MyUnion = MyObjectType
         """)

        AstTransformer astTransformer = new AstTransformer()

        def visitor = new NodeVisitorStub() {

            @Override
            TraversalControl visitFieldDefinition(FieldDefinition fieldDefinition, TraverserContext<Node> context) {
                if (fieldDefinition.name == "login") {
                    // change name
                    FieldDefinition signin = fieldDefinition.transform({ builder -> builder.name("signin") })
                    return changeNode(context, signin)
                }
                return super.visitFieldDefinition(fieldDefinition, context)
            }

            @Override
            TraversalControl visitInputValueDefinition(InputValueDefinition fieldDefinition, TraverserContext<Node> context) {
                if (fieldDefinition.name == "login") {
                    // change name
                    InputValueDefinition signin = fieldDefinition.transform({ builder -> builder.name("signin") })
                    return changeNode(context, signin)
                }
                return super.visitInputValueDefinition(fieldDefinition, context)
            }

            @Override
            TraversalControl visitEnumValueDefinition(EnumValueDefinition node, TraverserContext<Node> context) {
                if (node.name == "MONDAY") {
                    // change name
                    return changeNode(context, node.transform({ builder -> builder.name("TUESDAY") }))
                }
                return super.visitEnumValueDefinition(node, context)
            }

            @Override
            TraversalControl visitSchemaDefinition(SchemaDefinition node, TraverserContext<Node> context) {
                if (node instanceof SchemaExtensionDefinition) {
                    return changeNode(context, node.transformExtension({
                        it.operationTypeDefinitions([OperationTypeDefinition.newOperationTypeDefinition()
                                .name("myQuery")
                                .typeName(TypeName.newTypeName("MyQuery").build())
                                .build()])
                    }))
                }
                return super.visitSchemaDefinition(node, context)
            }
        }

        when:
        def newDocument = astTransformer.transform(document, visitor)

        then:
        newDocument instanceof Document

        and:
        def transformedDoc = newDocument as Document
        transformedDoc.definitions.find {it instanceof NamedNode && it.name == "MyObjectType"} instanceof ObjectTypeExtensionDefinition
        transformedDoc.definitions.find {it instanceof NamedNode && it.name == "MyInputObjectType"} instanceof InputObjectTypeExtensionDefinition
        transformedDoc.definitions.find {it instanceof NamedNode && it.name == "MyInterface"} instanceof InterfaceTypeExtensionDefinition
        transformedDoc.definitions.find {it instanceof NamedNode && it.name == "MyEnum"} instanceof EnumTypeExtensionDefinition
        transformedDoc.definitions.find {it instanceof NamedNode && it.name == "MyScalar"} instanceof ScalarTypeExtensionDefinition
        transformedDoc.definitions.find {it instanceof NamedNode && it.name == "MyUnion"} instanceof UnionTypeExtensionDefinition
        transformedDoc.definitions.find {it instanceof SchemaDefinition } instanceof SchemaExtensionDefinition

        and:
        printAst(newDocument).trim() == """
        |extend schema {
        |  myQuery: MyQuery
        |}
        |
        |extend type MyObjectType {
        |  signin: String
        |}
        |
        |extend input MyInputObjectType {
        |  signin: String
        |}
        |
        |extend interface MyInterface {
        |  signin: String
        |}
        |
        |extend enum MyEnum {
        |  TUESDAY
        |}
        |
        |extend scalar MyScalar @myDirective
        |
        |extend union MyUnion = MyObjectType
        """.trim().stripMargin()
    }
}

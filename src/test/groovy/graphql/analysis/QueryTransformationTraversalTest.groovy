package graphql.analysis

import graphql.TestUtil
import graphql.language.Document
import graphql.language.Field
import graphql.language.SelectionSet
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.language.AstPrinter.printAstCompact
import static graphql.language.AstTransformerUtil.*
import static graphql.language.Field.newField

class QueryTransformationTraversalTest extends Specification {
    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    QueryTraversal createQueryTraversal(Document document, GraphQLSchema schema, Map variables = [:]) {
        QueryTraversal queryTraversal = QueryTraversal.newQueryTraversal()
                .schema(schema)
                .document(document)
                .variables(variables)
                .build()
        return queryTraversal
    }

    def transfSchema = TestUtil.schema("""
            type Query {
                root: Root
            }
            type Root {
                fooA: Foo
                fooB: Foo  
            }
            type Foo {
                midA: MidA
                midB: MidB
            }
            
            type MidA {
                leafA: String
            }
            type MidB {
                leafB: String
            }
        """)

    def "transform query rename query fields based on type information "() {
        def query = TestUtil.parseQuery("{ root { fooA { midA { leafA } midB { leafB } } fooB { midA { leafA } midB { leafB } } } }")

        QueryTraversal queryTraversal = createQueryTraversal(query, transfSchema)

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.fieldDefinition.type.name == "MidA") {
                    String newName = env.field.name + "-modified"

                    Field changedField = env.field.transform({ builder -> builder.name(newName) })
                    changeNode(env.getTraverserContext(), changedField)
                }
            }
        }

        when:
        def newDocument = queryTraversal.transform(visitor)

        then:
        printAstCompact(newDocument) ==
                "query {root {fooA {midA-modified {leafA} midB {leafB}} fooB {midA-modified {leafA} midB {leafB}}}}"
    }

    def "transform query delete midA nodes"() {
        def query = TestUtil.parseQuery("{ root { fooA { midA { leafA } midB { leafB } } fooB { midA { leafA } midB { leafB } } } }")

        QueryTraversal queryTraversal = createQueryTraversal(query, transfSchema)

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.fieldDefinition.type.name == "MidA") {
                    deleteNode(env.getTraverserContext())
                }
            }
        }

        when:
        def newDocument = queryTraversal.transform(visitor)

        then:
        printAstCompact(newDocument) ==
                "query {root {fooA {midB {leafB}} fooB {midB {leafB}}}}"
    }

    def "transform query add midA sibling"() {
        def query = TestUtil.parseQuery("{ root { fooA { midA { leafA } } } }")

        QueryTraversal queryTraversal = createQueryTraversal(query, transfSchema)

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.fieldDefinition.type.name == "MidA") {
                    changeParentNode(env.getTraverserContext(), { node ->
                        def newChild = newField("addedField").build()
                        def newChildren = node.getNamedChildren()
                                .transform({ it.child(SelectionSet.CHILD_SELECTIONS, newChild) })
                        node.withNewChildren(newChildren)
                    })
                }
            }
        }

        when:
        def newDocument = queryTraversal.transform(visitor)

        then:
        printAstCompact(newDocument) ==
                "query {root {fooA {midA {leafA} addedField}}}"
    }

//    def "transform query fragment and inline fragment"() {
////        def query = TestUtil.parseQuery('''
////            {
////                root {
////                    fooA {
////                          midA { ...frag }
////                          midB { ... on MidB {
////                                    lb: leafB
////                                   }
////                               }
////                        }
////
////                    }
////
////
////            }
////            fragment frag on MidA {
////                a: leafA
////            }
////            ''')
//        def query = TestUtil.parseQuery('''
//            {
//                root {
//                    ... frag
//                }
//            }
//            fragment frag on FooA {
//                fooA {
//                          midA { leafA }
//                          }
//            }
//            ''')
//
//        QueryTraversal queryTraversal = createQueryTraversal(query, transfSchema)
//
//        def visitor = new QueryVisitorStub() {
//            @Override
//            void visitInlineFragment(QueryVisitorInlineFragmentEnvironment env) {
////                deleteNode(env.getTraverserContext())
//            }
//
//            @Override
//            void visitField(QueryVisitorFieldEnvironment env) {
//                changeNode(env.traverserContext, env.traverserContext.thisNode())
//            }
//        }
//
//        when:
//        def newDocument = queryTraversal.transform(visitor)
//
//        then:
//        printAstCompact(newDocument) ==
//                "query {root {fooA {midA {leafA} addedField}}}"
//    }
}

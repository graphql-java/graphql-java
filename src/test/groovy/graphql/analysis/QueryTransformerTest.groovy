package graphql.analysis

import graphql.TestUtil
import graphql.language.Document
import graphql.language.Field
import graphql.language.NodeUtil
import graphql.language.OperationDefinition
import graphql.language.TypeName
import graphql.parser.Parser
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType
import graphql.util.TraversalControl
import spock.lang.Specification

import static graphql.language.AstPrinter.printAstCompact
import static graphql.language.Field.newField
import static graphql.util.TreeTransformerUtil.changeNode
import static graphql.util.TreeTransformerUtil.deleteNode
import static graphql.util.TreeTransformerUtil.insertAfter

class QueryTransformerTest extends Specification {
    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    QueryTransformer createQueryTransformer(Document document, GraphQLSchema schema, Map variables = [:]) {
        def fragments = NodeUtil.getFragmentsByName(document)
        QueryTransformer queryTransformer = QueryTransformer.newQueryTransformer()
                .schema(schema)
                .fragmentsByName(fragments)
                .root(document)
                .variables(variables)
                .rootParentType(schema.getQueryType())
                .build()
        return queryTransformer
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

        QueryTransformer queryTransformer = createQueryTransformer(query, transfSchema)

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
        def newDocument = queryTransformer.transform(visitor)

        then:
        printAstCompact(newDocument) ==
                "query {root {fooA {midA-modified {leafA} midB {leafB}} fooB {midA-modified {leafA} midB {leafB}}}}"
    }

    def "transform query delete midA nodes"() {
        def query = TestUtil.parseQuery("{ root { fooA { midA { leafA } midB { leafB } } fooB { midA { leafA } midB { leafB } } } }")

        QueryTransformer queryTransformer = createQueryTransformer(query, transfSchema)

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.fieldDefinition.type.name == "MidA") {
                    deleteNode(env.getTraverserContext())
                }
            }
        }

        when:
        def newDocument = queryTransformer.transform(visitor)

        then:
        printAstCompact(newDocument) ==
                "query {root {fooA {midB {leafB}} fooB {midB {leafB}}}}"
    }

    def "transform query add midA sibling"() {
        def query = TestUtil.parseQuery("{ root { fooA { midA { leafA } } } }")

        QueryTransformer queryTransformer = createQueryTransformer(query, transfSchema)

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.fieldDefinition.type.name == "MidA") {
                    insertAfter(env.getTraverserContext(), newField("addedField").build())
                }
            }
        }

        when:
        def newDocument = queryTransformer.transform(visitor)

        then:
        printAstCompact(newDocument) ==
                "query {root {fooA {midA {leafA} addedField}}}"
    }

    def "transform query delete fragment spread and inline fragment"() {
        def query = TestUtil.parseQuery('''
            {
                root {
                    fooA {
                    ...frag
                    midB { leafB }
                    }
                    
                    fooB {
                    ...{
                      midA { leafA }
                    }
                    midB { leafB }
                    }
                }
            }
            fragment frag on Foo {
                 midA {leafA}
            }
            ''')

        QueryTransformer queryTransformer = createQueryTransformer(query, transfSchema)

        def visitor = new QueryVisitorStub() {
            @Override
            void visitInlineFragment(QueryVisitorInlineFragmentEnvironment env) {
                deleteNode(env.getTraverserContext())
            }

            @Override
            void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment env) {
                if (env.getFragmentDefinition().getName() == "frag") {
                    deleteNode(env.getTraverserContext())
                }
            }
        }

        when:
        def newDocument = queryTransformer.transform(visitor)
        then:

        printAstCompact(newDocument) ==
                "query {root {fooA {midB {leafB}} fooB {midB {leafB}}}} fragment frag on Foo {midA {leafA}}"
    }

    def "transform query does not traverse named fragments when started from query"() {
        def query = TestUtil.parseQuery('''
            {
                root {
                    ...frag
                }
            }
            fragment frag on Root {
                fooA{ midA {leafA}}
            }
            ''')

        def operationDefinition = NodeUtil.getOperation(query, null).operationDefinition
        def fragments = NodeUtil.getFragmentsByName(query)
        QueryTransformer queryTransformer = QueryTransformer.newQueryTransformer()
                .schema(transfSchema)
                .fragmentsByName(fragments)
                .root(operationDefinition)
                .variables([:])
                .rootParentType(transfSchema.getQueryType())
                .build()

        def visitor = Mock(QueryVisitor)


        when:
        queryTransformer.transform(visitor)
        then:
        1 * visitor.visitFieldWithControl({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "root" && it.fieldDefinition.type.name == "Root" && it.parentType.name == "Query" }) >> TraversalControl.CONTINUE
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironment it -> it.fragmentSpread.name == "frag" })
        0 * _
    }

    def "fragment definition is traversed if it is a root and can be transformed"() {
        def query = TestUtil.parseQuery('''
            {
                root {
                    ...frag
                }
            }
            fragment frag on Root {
                fooA{ midA {leafA}}
            }
            ''')
        def fragments = NodeUtil.getFragmentsByName(query)
        QueryTransformer queryTransformer = QueryTransformer.newQueryTransformer()
                .schema(transfSchema)
                .root(fragments["frag"])
                .rootParentType(transfSchema.getQueryType())
                .fragmentsByName(fragments)
                .variables([:])
                .build()

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.field.name == "leafA") {
                    deleteNode(env.traverserContext)
                }
                if (env.fieldDefinition.type.name == "String") {
                    insertAfter(env.traverserContext, newField("newChild1").build())
                    insertAfter(env.traverserContext, newField("newChild2").build())
                }
            }

            @Override
            void visitFragmentDefinition(QueryVisitorFragmentDefinitionEnvironment env) {
                def changed = env.fragmentDefinition.transform({ builder ->
                    builder.typeCondition(TypeName.newTypeName("newTypeName").build())
                            .name("newFragName")
                })
                changeNode(env.traverserContext, changed)
            }
        }


        when:
        def newFragment = queryTransformer.transform(visitor)
        then:
        printAstCompact(newFragment) ==
                "fragment newFragName on newTypeName {fooA {midA {newChild1 newChild2}}}"
    }

    def "transform starting in a interface field"() {
        def schema = TestUtil.schema("""
            type Query {
                root: SomeInterface
            }
            interface SomeInterface {
                field1: String
                field2: String
            }
        """)
        def query = TestUtil.parseQuery('''
            {
                root {
                   field1
                   field2 
                }
            }
            ''')
        def rootField = (query.children[0] as OperationDefinition).selectionSet.selections[0] as Field
        def field1 = rootField.selectionSet.selections[0] as Field
        QueryTransformer queryTransformer = QueryTransformer.newQueryTransformer()
                .schema(schema)
                .root(field1)
                .rootParentType(schema.getType("SomeInterface") as GraphQLFieldsContainer)
                .fragmentsByName([:])
                .variables([:])
                .build()

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.field.name == "field1") {
                    changeNode(env.traverserContext, env.field.transform({ builder -> builder.name("field1X") }))
                }
            }
        }


        when:
        def newNode = queryTransformer.transform(visitor)
        then:
        printAstCompact(newNode) == "field1X"

    }

    def "transform starting in a union field"() {
        def schema = TestUtil.schema("""
            type Query {
                root: SomeUnion
            }
            union SomeUnion = A | B
            type A  {
                a: String
            }
            type B  {
                b: String
            }
        """)
        def query = TestUtil.parseQuery('''
            {
                root {
                  __typename
                  ... on A {
                    a
                  }
                  ... on B {
                   b 
                  }
                }
            }
            ''')
        def rootField = (query.children[0] as OperationDefinition).selectionSet.selections[0] as Field
        def typeNameField = rootField.selectionSet.selections[0] as Field
        QueryTransformer queryTransformer = QueryTransformer.newQueryTransformer()
                .schema(schema)
                .root(typeNameField)
                .rootParentType(schema.getType("SomeUnion") as GraphQLUnionType)
                .fragmentsByName([:])
                .variables([:])
                .build()

        boolean visitedTypeNameField
        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                visitedTypeNameField = env.isTypeNameIntrospectionField()
            }
        }


        when:
        queryTransformer.transform(visitor)
        then:
        visitedTypeNameField

    }

    def "transform starting in a selectionSet node belonging to an interface"() {
        def schema = TestUtil.schema("""
            type Query {
                root: SomeInterface
            }
            interface SomeInterface {
                field1: String
                field2: String
            }
        """)
        def query = TestUtil.parseQuery('''
            {
                root {
                   field1
                   field2 
                }
            }
            ''')
        def rootField = (query.children[0] as OperationDefinition).selectionSet.selections[0] as Field
        QueryTransformer queryTransformer = QueryTransformer.newQueryTransformer()
                .schema(schema)
                .root(rootField.getSelectionSet())
                .rootParentType(schema.getType("SomeInterface") as GraphQLFieldsContainer)
                .fragmentsByName([:])
                .variables([:])
                .build()

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.field.name == "field1") {
                    changeNode(env.traverserContext, env.field.transform({ builder -> builder.name("field1X") }))
                }
            }
        }


        when:
        def newNode = queryTransformer.transform(visitor)
        then:
        printAstCompact(newNode) == "{field1X field2}"

    }

    def "transform starting in a selectionSet node belonging to an union"() {
        def schema = TestUtil.schema("""
        type Query {
            root: SomeUnion
        }
        union SomeUnion = A | B
        type A  {
            a: String
        }
        type B  {
            b: String
        }
        """)
        def query = TestUtil.parseQuery('''
            {
                root {
                  __typename
                  ... on A {
                    a
                  }
                  ... on B {
                    b
                  }
                }
            }
            ''')
        def rootField = (query.children[0] as OperationDefinition).selectionSet.selections[0] as Field
        QueryTransformer queryTransformer = QueryTransformer.newQueryTransformer()
                .schema(schema)
                .root(rootField.getSelectionSet())
                .rootParentType(schema.getType("SomeUnion") as GraphQLFieldsContainer)
                .fragmentsByName([:])
                .variables([:])
                .build()

        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment env) {
                if (env.field.name == "a") {
                    changeNode(env.traverserContext, env.field.transform({ builder -> builder.name("aX") }))
                }
            }
        }


        when:
        def newNode = queryTransformer.transform(visitor)
        then:
        printAstCompact(newNode) == "{__typename ... on A {aX} ... on B {b}}"

    }
}

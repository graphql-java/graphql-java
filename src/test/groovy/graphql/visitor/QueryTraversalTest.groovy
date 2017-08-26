package graphql.visitor

import graphql.TestUtil
import graphql.language.Document
import graphql.language.NodeUtil
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class QueryTraversalTest extends Specification {


    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    QueryTraversal createQueryTraversal(Document document, GraphQLSchema schema, QueryVisitor visitor, Map variables = [:]) {
        def operation = NodeUtil.getOperation(document, null)
        QueryTraversal queryTraversal = new QueryTraversal(
                document,
                operation.operationDefinition,
                schema,
                operation.fragmentsByName,
                variables,
                visitor
        )
        return queryTraversal
    }


    def "simple query"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {bar foo { subFoo} }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        when:
        queryTraversal.traverse()

        then:
        1 * visitor.visitField({ it.name == "foo" }, { it.type.name == "Foo" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "bar" }, { it.type.name == "String" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "subFoo" }, { it.type.name == "String" }, {
            it.name == "Foo"
        }, { VisitPath path ->
            path.field.name == "foo" && path.fieldDefinition.type.name == "Foo"
        })
    }

    def "query with inline fragment"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
                bar 
                ... on Query {
                    foo 
                    { subFoo
                    } 
                }
            }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        when:
        queryTraversal.traverse()

        then:
        1 * visitor.visitField({ it.name == "foo" }, { it.type.name == "Foo" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "bar" }, { it.type.name == "String" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "subFoo" }, { it.type.name == "String" }, {
            it.name == "Foo"
        }, { VisitPath path ->
            path.field.name == "foo" && path?.fieldDefinition.type.name == "Foo"
        })

    }

    def "query with inline fragment without condition"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
                bar 
                ... {
                    foo 
                    { subFoo
                    } 
                }
            }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        when:
        queryTraversal.traverse()

        then:
        1 * visitor.visitField({ it.name == "foo" }, { it.type.name == "Foo" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "bar" }, { it.type.name == "String" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "subFoo" }, { it.type.name == "String" }, {
            it.name == "Foo"
        }, { VisitPath path ->
            path.field.name == "foo" && path?.fieldDefinition.type.name == "Foo"
        })

    }


    def "query with fragment"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
                bar 
                ...Test
            }
            fragment Test on Query {
                foo 
                { subFoo
                } 
            }
            
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        when:
        queryTraversal.traverse()

        then:
        1 * visitor.visitField({ it.name == "foo" }, { it.type.name == "Foo" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "bar" }, { it.type.name == "String" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "subFoo" }, { it.type.name == "String" }, {
            it.name == "Foo"
        }, { VisitPath path ->
            path.field.name == "foo" && path?.fieldDefinition.type.name == "Foo"
        })

    }

    def "query with skipped fields"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo 
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
                bar 
                ...Test @skip(if: true)
            }
            fragment Test on Query {
                foo 
                { subFoo
                } 
            }
            
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        when:
        queryTraversal.traverse()

        then:
        1 * visitor.visitField({ it.name == "bar" }, { it.type.name == "String" }, { it.name == "Query" }, null)
        0 * visitor.visitField(*_)

    }

    def "query with skipped fields and variables"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo 
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            query MyQuery(\$variableFoo: Boolean) {
                bar 
                ...Test @skip(if: \$variableFoo)
            }
            fragment Test on Query {
                foo 
                { subFoo
                } 
            }
            
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor, [variableFoo: true])
        when:
        queryTraversal.traverse()

        then:
        1 * visitor.visitField({ it.name == "bar" }, { it.type.name == "String" }, { it.name == "Query" }, null)
        0 * visitor.visitField(*_)

    }

    def "nested fragments"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo1 
                bar: String
            }
            type Foo1 {
                string: String  
                subFoo: Foo2 
            }
            type Foo2 {
                otherString: String
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            query MyQuery(\$variableFoo: Boolean) {
                bar 
                ...Test @include(if: \$variableFoo)
            }
            fragment Test on Query {
                bar
                foo {
                    ...OnFoo1
                }
            }
            
            fragment OnFoo1 on Foo1 {
                string
                subFoo {
                    ... on Foo2 {
                       otherString 
                    }
                }
            }
            
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor, [variableFoo: true])
        when:
        queryTraversal.traverse()

        then:
        2 * visitor.visitField({ it.name == "bar" }, { it.type.name == "String" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "foo" }, { it.type.name == "Foo1" }, { it.name == "Query" }, null)
        1 * visitor.visitField({ it.name == "string" }, { it.type.name == "String" }, { it.name == "Foo1" }, _)
        1 * visitor.visitField({ it.name == "subFoo" }, { it.type.name == "Foo2" }, { it.name == "Foo1" }, _)
        1 * visitor.visitField({ it.name == "otherString" }, { it.type.name == "String" }, { it.name == "Foo2" }, {
            VisitPath path ->
                VisitPath parentPath = path.parentPath
                path.field.name == "subFoo" && path.fieldDefinition.type.name == "Foo2" && path.parentType.name == "Foo1" &&
                        parentPath.field.name == "foo" && parentPath.fieldDefinition.type.name == "Foo1" && parentPath.parentType.name == "Query"

        })

    }

}

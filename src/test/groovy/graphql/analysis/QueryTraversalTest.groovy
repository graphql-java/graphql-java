package graphql.analysis

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
                operation.operationDefinition,
                schema,
                operation.fragmentsByName,
                variables
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
        queryTraversal.visit(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
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
        queryTraversal.visit(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
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
        queryTraversal.visit(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
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
        queryTraversal.visit(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
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
        queryTraversal.visit(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
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
        queryTraversal.visit(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
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
        queryTraversal.visit(visitor)

        then:
        2 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo1" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "string" && it.fieldDefinition.type.name == "String" && it.parent.name == "Foo1" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "subFoo" && it.fieldDefinition.type.name == "Foo2" && it.parent.name == "Foo1" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            VisitPath parentPath = it.path.parentPath
            it.field.name == "otherString" && it.fieldDefinition.type.name == "String" && it.parent.name == "Foo2" &&
                    it.path.field.name == "subFoo" && it.path.fieldDefinition.type.name == "Foo2" && it.path.parentType.name == "Foo1" &&
                    parentPath.field.name == "foo" && parentPath.fieldDefinition.type.name == "Foo1" && parentPath.parentType.name == "Query"
        })

    }

}

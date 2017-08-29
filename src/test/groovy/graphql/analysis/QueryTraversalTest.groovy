package graphql.analysis

import graphql.TestUtil
import graphql.language.Document
import graphql.language.NodeUtil
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification
import spock.lang.Unroll

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

    def "test preOrder order"() {
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
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
        })
        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })

    }

    def "test postOrder order"() {
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
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        when:
        queryTraversal.visitPostOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
        })
        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })

    }


    @Unroll
    def "simple query: (#order)"() {
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
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "query with inline fragment (#order)"() {
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
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    @Unroll
    def "query with inline fragment without condition (#order)"() {
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
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }


    @Unroll
    def "query with fragment (#order)"() {
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
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parent.name == "Foo" &&
                    it.path.field.name == "foo" && it.path.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    @Unroll
    def "query with skipped fields (#order)"() {
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
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        0 * visitor.visitField(*_)

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "query with skipped fields and variables (#order)"() {
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
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parent.name == "Query" })
        0 * visitor.visitField(*_)

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "nested fragments (#order)"() {
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
        queryTraversal."$visitFn"(visitor)

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

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }


    def "reduce preOrder"() {
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
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        QueryReducer reducer = Mock(QueryReducer)
        when:
        def result = queryTraversal.reducePreOrder(reducer, 1)

        then:
        1 * reducer.reduceField({ it.field.name == "foo" }, 1) >> 2
        then:
        1 * reducer.reduceField({ it.field.name == "subFoo" }, 2) >> 3
        then:
        1 * reducer.reduceField({ it.field.name == "bar" }, 3) >> 4
        result == 4

    }


    def "reduce postOrder"() {
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
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        QueryReducer reducer = Mock(QueryReducer)
        when:
        def result = queryTraversal.reducePostOrder(reducer, 1)

        then:
        1 * reducer.reduceField({ it.field.name == "subFoo" }, 1) >> 2
        then:
        1 * reducer.reduceField({ it.field.name == "foo" }, 2) >> 3
        then:
        1 * reducer.reduceField({ it.field.name == "bar" }, 3) >> 4
        result == 4

    }

}

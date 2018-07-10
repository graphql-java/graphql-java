package graphql.analysis

import graphql.TestUtil
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.parser.Parser
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.Collections.emptyMap

class QueryTraversalTest extends Specification {


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

    def "test preOrder order for visitField"() {
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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" &&
                    it.selectionSetContainer == null
        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo" &&
                    it.selectionSetContainer == it.parentEnvironment.field

        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })

    }

    def "test postOrder order for visitField"() {
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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPostOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })

    }


    def "test preOrder order for inline fragments"() {
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
                    ... on Query {
                        ... on Query {
                            foo {subFoo}
                        }
                        ... on Query {
                            foo {subFoo}
                         }
                    }
                }
                """)
        def inlineFragmentRoot = query.children[0].children[0].children[0]
        assert inlineFragmentRoot instanceof InlineFragment
        def inlineFragmentLeft = inlineFragmentRoot.selectionSet.children[0]
        assert inlineFragmentLeft instanceof InlineFragment
        def inlineFragmentRight = inlineFragmentRoot.selectionSet.children[1]
        assert inlineFragmentRight instanceof InlineFragment
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitInlineFragment({ QueryVisitorInlineFragmentEnvironmentImpl env -> env.inlineFragment == inlineFragmentRoot })
        then:
        1 * visitor.visitInlineFragment({ QueryVisitorInlineFragmentEnvironmentImpl env -> env.inlineFragment == inlineFragmentLeft })
        then:
        1 * visitor.visitInlineFragment({ QueryVisitorInlineFragmentEnvironmentImpl env -> env.inlineFragment == inlineFragmentRight })

    }


    def "test postOrder order for inline fragments"() {
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
                    ... on Query @root {
                        ... on Query @left {
                            foo {subFoo}
                        }
                        ... on Query @right {
                            foo {subFoo}
                         }
                    }
                }
                """)
        def inlineFragmentRoot = query.children[0].children[0].children[0]
        assert inlineFragmentRoot instanceof InlineFragment
        def inlineFragmentLeft = inlineFragmentRoot.selectionSet.children[0]
        assert inlineFragmentLeft instanceof InlineFragment
        def inlineFragmentRight = inlineFragmentRoot.selectionSet.children[1]
        assert inlineFragmentRight instanceof InlineFragment
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPostOrder(visitor)

        then:
        1 * visitor.visitInlineFragment({ QueryVisitorInlineFragmentEnvironmentImpl env -> env.inlineFragment == inlineFragmentLeft })
        then:
        1 * visitor.visitInlineFragment({ QueryVisitorInlineFragmentEnvironmentImpl env -> env.inlineFragment == inlineFragmentRight })
        then:
        1 * visitor.visitInlineFragment({ QueryVisitorInlineFragmentEnvironmentImpl env -> env.inlineFragment == inlineFragmentRoot })

    }

    def "test preOrder order for fragment spreads"() {
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
                    ...F1
                }
                fragment F1 on Query {
                    ...F2
                    ...F3
                }
                fragment F2 on Query {
                    bar 
                }
                fragment F3 on Query {
                    bar 
                }
                """)

        def fragmentF1 = query.definitions[1]
        assert fragmentF1 instanceof FragmentDefinition
        def fragmentF2 = query.definitions[2]
        assert fragmentF2 instanceof FragmentDefinition
        def fragmentF3 = query.definitions[3]
        assert fragmentF3 instanceof FragmentDefinition

        def fragmentSpreadRoot = query.definitions[0].children[0].children[0]
        assert fragmentSpreadRoot instanceof FragmentSpread
        def fragmentSpreadLeft = fragmentF1.selectionSet.children[0]
        assert fragmentSpreadLeft instanceof FragmentSpread
        def fragmentSpreadRight = fragmentF1.selectionSet.children[1]
        assert fragmentSpreadRight instanceof FragmentSpread
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadRoot && env.fragmentDefinition == fragmentF1 })
        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadLeft && env.fragmentDefinition == fragmentF2 })
        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadRight && env.fragmentDefinition == fragmentF3 })

    }

    def "test postOrder order for fragment spreads"() {
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
                    ...F1
                }
                fragment F1 on Query {
                    ...F2
                    ...F3
                }
                fragment F2 on Query {
                    bar 
                }
                fragment F3 on Query {
                    bar 
                }
                """)

        def fragmentF1 = query.definitions[1]
        assert fragmentF1 instanceof FragmentDefinition
        def fragmentF2 = query.definitions[2]
        assert fragmentF2 instanceof FragmentDefinition
        def fragmentF3 = query.definitions[3]
        assert fragmentF3 instanceof FragmentDefinition

        def fragmentSpreadRoot = query.definitions[0].children[0].children[0]
        assert fragmentSpreadRoot instanceof FragmentSpread
        def fragmentSpreadLeft = fragmentF1.selectionSet.children[0]
        assert fragmentSpreadLeft instanceof FragmentSpread
        def fragmentSpreadRight = fragmentF1.selectionSet.children[1]
        assert fragmentSpreadRight instanceof FragmentSpread
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPostOrder(visitor)

        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadLeft && env.fragmentDefinition == fragmentF2 })
        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadRight && env.fragmentDefinition == fragmentF3 })
        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadRoot && env.fragmentDefinition == fragmentF1 })

    }

    def "works for mutations()"() {
        given:
        def schema = TestUtil.schema("""
            type Query {
              a: String
            }
            type Mutation{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
            schema {mutation: Mutation, query: Query}
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            mutation M{bar foo { subFoo} }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Mutation" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Mutation" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    def "works for subscriptions()"() {
        given:
        def schema = TestUtil.schema("""
            type Query {
              a: String
            }
            type Subscription{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
            schema {subscription: Subscription, query: Query}
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            subscription S{bar foo { subFoo} }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Subscription" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Subscription" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    @Unroll
    def "field with arguments: (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo(arg1: String, arg2: Boolean): String
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            query myQuery(\$myVar: String){foo(arg1: \$myVar, arg2: true)} 
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, ['myVar': 'hello'])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "foo" &&
                    it.arguments == ['arg1': 'hello', 'arg2': true]
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "simple query (#order)"() {
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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "query with non null and lists (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo!
                foo2: [Foo]
                foo3: [Foo!]
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {bar foo { subFoo} foo2 { subFoo} foo3 { subFoo}}
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.wrappedType.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.wrappedType.name == "Foo"
        })
        2 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "subFoo" })

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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        def inlineFragment = query.children[0].children[0].children[1]
        assert inlineFragment instanceof InlineFragment
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" && it.selectionSetContainer == inlineFragment })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        def fragmentDefinition = query.children[1]
        assert fragmentDefinition instanceof FragmentDefinition
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" && it.selectionSetContainer == fragmentDefinition })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, [variableFoo: true])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
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
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, [variableFoo: true])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        2 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo1" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "string" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Foo1" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "subFoo" && it.fieldDefinition.type.name == "Foo2" && it.parentType.name == "Foo1" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            QueryVisitorFieldEnvironmentImpl secondParent = it.parentEnvironment.parentEnvironment
            it.field.name == "otherString" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Foo2" &&
                    it.parentEnvironment.field.name == "subFoo" && it.parentEnvironment.fieldDefinition.type.name == "Foo2" && it.parentEnvironment.parentType.name == "Foo1" &&
                    secondParent.field.name == "foo" && secondParent.fieldDefinition.type.name == "Foo1" && secondParent.parentType.name == "Query"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    @Unroll
    def "skipped Fragment (#order)"() {
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
            }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, [variableFoo: false])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        0 * visitor.visitField(_)

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    @Unroll
    def "skipped inline Fragment (#order)"() {
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
                ...@include(if: \$variableFoo) {
                    foo
                }
            }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, [variableFoo: false])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        0 * visitor.visitField(_)

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    @Unroll
    def "skipped Field (#order)"() {
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
                foo @include(if: \$variableFoo)
            }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, [variableFoo: false])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        0 * visitor.visitField(_)

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
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
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
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
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

    def "works for interfaces()"() {
        given:
        def schema = TestUtil.schema("""
            type Query {
              a: Node
            }
            
            interface Node {
              id: ID!
            }
            
            type Person implements Node {
              id: ID!
              name: String
            }
            
            schema {query: Query}
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {a {id... on Person {name}}}
        """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "a" && it.fieldDefinition.type.name == "Node" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "name" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Person" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "id" && it.fieldDefinition.type.wrappedType.name == "ID" && it.parentType.name == "Node" })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    def "works for unions()"() {
        given:
        def schema = TestUtil.schema("""
            type Query {
              foo: CatOrDog
            }
            
            type Cat {
                catName: String
            }
            
            type Dog {
                dogName: String
            }
            union CatOrDog = Cat | Dog
            
            schema {query: Query}
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {foo {... on Cat {catName} ... on Dog {dogName}} }
        """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "CatOrDog" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "catName" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Cat" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "dogName" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Dog" })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    def "works with introspection fields"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {foo {__typename subFoo} 
            __schema{  types { name } }
            __type(name: "Foo") { name } 
            }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "__schema" && it.fieldDefinition.type.wrappedType.name == "__Schema" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "__type" && it.fieldDefinition.type.name == "__Type" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "types" })
        2 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "name" })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    def "#763 handles union types"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                someObject: SomeObject
            }
            type SomeObject {
                someUnionType: SomeUnionType  
            }
            
            union SomeUnionType = TypeX | TypeY
            
            type TypeX {
                field1 : String
            }

            type TypeY {
                field2 : String
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
            someObject {
                someUnionType {
                    __typename
                    ... on TypeX {
                        field1
                    }
                    ... on TypeY {
                        field2
                    }
                }
            }
        }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "someObject" && it.fieldDefinition.type.name == "SomeObject" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "someUnionType" && it.fieldDefinition.type.name == "SomeUnionType" && it.parentType.name == "SomeObject" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "__typename" && it.fieldDefinition.type.wrappedType.name == "String" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "field1" && it.fieldDefinition.type.name == "String" && it.parentType.name == "TypeX" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "field2" && it.fieldDefinition.type.name == "String" && it.parentType.name == "TypeY" })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }


    def "can select an arbitrary root node"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: SubFoo
            }
            type SubFoo {
               id: String 
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {foo { subFoo {id}} }
            """)
        def root = query.children[0].children[0].children[0].children[0].children[0]
        assert root instanceof Field
        ((Field) root).name == "subFoo"
        def rootParentType = schema.getType("Foo")
        QueryTraversal queryTraversal = QueryTraversal.newQueryTraversal()
                .schema(schema)
                .root(root)
                .rootParentType(rootParentType)
                .variables(emptyMap())
                .fragmentsByName(emptyMap())
                .build()
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "SubFoo"
        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "id" && it.fieldDefinition.type.name == "String" && it.parentType.name == "SubFoo" })

    }

    @Unroll
    def "builder doesn't allow ambiguous arguments"() {
        when:
        QueryTraversal.newQueryTraversal()
                .document(document)
                .operationName(operationName)
                .root(root)
                .rootParentType(rootParentType)
                .fragmentsByName(fragmentsByName)
                .build()

        then:
        thrown(IllegalStateException)

        where:
        document             | operationName | root                     | rootParentType          | fragmentsByName
        createQuery("{foo}") | null          | Field.newField().build() | null                    | null
        createQuery("{foo}") | "foo"         | Field.newField().build() | null                    | null
        createQuery("{foo}") | "foo"         | Field.newField().build() | Mock(GraphQLObjectType) | null
        createQuery("{foo}") | "foo"         | Field.newField().build() | null                    | emptyMap()
        null                 | "foo"         | Field.newField().build() | Mock(GraphQLObjectType) | null
        null                 | "foo"         | Field.newField().build() | Mock(GraphQLObjectType) | emptyMap()
        null                 | "foo"         | Field.newField().build() | Mock(GraphQLObjectType) | emptyMap()
        null                 | "foo"         | Field.newField().build() | null                    | emptyMap()
        null                 | "foo"         | null                     | Mock(GraphQLObjectType) | emptyMap()
        null                 | "foo"         | null                     | Mock(GraphQLObjectType) | null
        null                 | "foo"         | null                     | null                    | emptyMap()


    }


}

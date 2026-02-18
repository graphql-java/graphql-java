package graphql.analysis

import graphql.AssertException
import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.language.ArrayValue
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.IntValue
import graphql.language.NodeUtil
import graphql.language.ObjectValue
import graphql.language.OperationDefinition
import graphql.language.StringValue
import graphql.parser.Parser
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType
import graphql.util.TraversalControl
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.util.TraverserContext.Phase.ENTER
import static graphql.util.TraverserContext.Phase.LEAVE
import static java.util.Collections.emptyMap

class QueryTraverserTest extends Specification {


    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    QueryTraverser createQueryTraversal(Document document, GraphQLSchema schema, Map variables = [:]) {
        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .document(document)
                .variables(variables)
                .build()
        return queryTraversal
    }

    QueryVisitor mockQueryVisitor() {
        def mock = Mock(QueryVisitor)

        mock.visitFieldWithControl(_) >> { params ->
            mock.visitField(params)
            TraversalControl.CONTINUE
        }
        mock.visitArgument(_) >> { params ->
            TraversalControl.CONTINUE
        }
        mock.visitArgumentValue(_) >> { params ->
            TraversalControl.CONTINUE
        }
        return mock
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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

    def "test for visitArgs and visitArgsValue"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo (complexArg : Complex, simpleArg : String) : Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
            
            input Complex {
                name : String
                moreComplex : [MoreComplex]
            }
            
            input MoreComplex {
                height : Int
                weight : Int
            }
        """)
        def visitor = mockQueryVisitor()
        def query = createQuery('''{
            foo( complexArg : { name : "Ted", moreComplex : [{height : 100, weight : 200}] } , simpleArg : "Hi" ) { 
                subFoo
            } 
            bar 
        }''')
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPreOrder(visitor)

        then:

        1 * visitor.visitArgument({ QueryVisitorFieldArgumentEnvironment env ->
            env.argument.name == "complexArg" && env.graphQLArgument.type.name == "Complex"
        }) >> TraversalControl.CONTINUE

        1 * visitor.visitArgument({ QueryVisitorFieldArgumentEnvironment env ->
            env.argument.name == "simpleArg" && env.graphQLArgument.type.name == "String"
        }) >> TraversalControl.CONTINUE

        1 * visitor.visitArgumentValue({ QueryVisitorFieldArgumentValueEnvironment env ->
            env.graphQLArgument.name == "complexArg" &&
                    env.argumentInputValue.name == "complexArg" &&
                    env.argumentInputValue.value instanceof ObjectValue
        }) >> TraversalControl.CONTINUE

        1 * visitor.visitArgumentValue({ QueryVisitorFieldArgumentValueEnvironment env ->
            env.graphQLArgument.name == "complexArg" &&
                    env.argumentInputValue.name == "name" &&
                    env.argumentInputValue.value instanceof StringValue
        }) >> TraversalControl.CONTINUE

        1 * visitor.visitArgumentValue({ QueryVisitorFieldArgumentValueEnvironment env ->
            env.graphQLArgument.name == "complexArg" &&
                    env.argumentInputValue.name == "moreComplex" &&
                    env.argumentInputValue.value instanceof ArrayValue
        }) >> TraversalControl.CONTINUE

        1 * visitor.visitArgumentValue({ QueryVisitorFieldArgumentValueEnvironment env ->
            env.graphQLArgument.name == "complexArg" &&
                    env.argumentInputValue.parent.name == "moreComplex" &&
                    env.argumentInputValue.name == "weight" &&
                    env.argumentInputValue.value instanceof IntValue
        }) >> TraversalControl.CONTINUE

        1 * visitor.visitArgumentValue({ QueryVisitorFieldArgumentValueEnvironment env ->
            env.graphQLArgument.name == "complexArg" &&
                    env.argumentInputValue.parent.name == "moreComplex" &&
                    env.argumentInputValue.name == "height" &&
                    env.argumentInputValue.value instanceof IntValue &&
                    env.argumentInputValue.inputValueDefinition instanceof GraphQLInputObjectField
        }) >> TraversalControl.CONTINUE

        1 * visitor.visitArgumentValue({ QueryVisitorFieldArgumentValueEnvironment env ->
            env.graphQLArgument.name == "simpleArg" &&
                    env.argumentInputValue.name == "simpleArg" &&
                    env.argumentInputValue.value instanceof StringValue &&
                    env.argumentInputValue.inputValueDefinition instanceof GraphQLArgument
        }) >> TraversalControl.CONTINUE


        when:
        queryTraversal.visitPostOrder(visitor)

        then:

        1 * visitor.visitArgument({ QueryVisitorFieldArgumentEnvironment env ->
            env.argument.name == "complexArg" && env.graphQLArgument.type.name == "Complex"
        }) >> TraversalControl.CONTINUE

        1 * visitor.visitArgument({ QueryVisitorFieldArgumentEnvironment env ->
            env.argument.name == "simpleArg" && env.graphQLArgument.type.name == "String"
        }) >> TraversalControl.CONTINUE

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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPostOrder(visitor)

        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadLeft && env.fragmentDefinition == fragmentF2 })
        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadRight && env.fragmentDefinition == fragmentF3 })
        then:
        1 * visitor.visitFragmentSpread({ QueryVisitorFragmentSpreadEnvironmentImpl env -> env.fragmentSpread == fragmentSpreadRoot && env.fragmentDefinition == fragmentF1 })

    }


    def "test preOrder and postOrder order for fragment definitions and raw variables"() {
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
                {
                    ...F1
                }
                
                fragment F1 on Query {
                    foo {
                        subFoo
                    }
                }
                """)

        def fragments = NodeUtil.getFragmentsByName(query)

        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .root(fragments["F1"])
                .rootParentType(schema.getQueryType())
                .fragmentsByName(fragments)
                .variables([:])
                .build()

        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitFragmentDefinition({ QueryVisitorFragmentDefinitionEnvironment env -> env.fragmentDefinition == fragments["F1"] })

        when:
        queryTraversal.visitPostOrder(visitor)

        then:
        1 * visitor.visitFragmentDefinition({ QueryVisitorFragmentDefinitionEnvironment env -> env.fragmentDefinition == fragments["F1"] })
    }

    def "test preOrder and postOrder order for fragment definitions and coerced variables"() {
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
                {
                    ...F1
                }
                
                fragment F1 on Query {
                    foo {
                        subFoo
                    }
                }
                """)

        def fragments = NodeUtil.getFragmentsByName(query)

        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .root(fragments["F1"])
                .rootParentType(schema.getQueryType())
                .fragmentsByName(fragments)
                .coercedVariables(CoercedVariables.emptyVariables())
                .build()

        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitFragmentDefinition({ QueryVisitorFragmentDefinitionEnvironment env -> env.fragmentDefinition == fragments["F1"] })

        when:
        queryTraversal.visitPostOrder(visitor)

        then:
        1 * visitor.visitFragmentDefinition({ QueryVisitorFragmentDefinitionEnvironment env -> env.fragmentDefinition == fragments["F1"] })
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            mutation M{bar foo { subFoo} }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            subscription S{bar foo { subFoo} }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            query myQuery(\$myVar: String){foo(arg1: \$myVar, arg2: true)} 
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema, ['myVar': 'hello'])
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
    def "traverse a query when a default variable is a list: (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo(arg1: [String]): String
            }
        """)
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            query myQuery(\$myVar: [String] = ["hello default"]) {foo(arg1: \$myVar)} 
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema, ['myVar': 'hello'])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "foo" &&
                    it.arguments == ['arg1': ['hello']]
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "traverse a query when a default variable is a list and query does not specify variables: (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo(arg1: [String]): String
            }
        """)
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            query myQuery(\$myVar: [String] = ["hello default"]) {foo(arg1: \$myVar)} 
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema, [:])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "foo" &&
                    it.arguments == ['arg1': ['hello default']]
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {bar foo { subFoo} }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {bar foo { subFoo} foo2 { subFoo} foo3 { subFoo}}
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.wrappedType.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.fieldsContainer.name == "Foo" &&
                    (it.parentType instanceof GraphQLNonNull) &&
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema, [variableFoo: true])
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema, [variableFoo: true])
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            query MyQuery(\$variableFoo: Boolean) {
                bar 
                ...Test @include(if: \$variableFoo)
            }
            fragment Test on Query {
                bar
            }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema, [variableFoo: false])
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            query MyQuery(\$variableFoo: Boolean) {
                bar 
                ...@include(if: \$variableFoo) {
                    foo
                }
            }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema, [variableFoo: false])
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            query MyQuery(\$variableFoo: Boolean) {
                bar 
                foo @include(if: \$variableFoo)
            }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema, [variableFoo: false])
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {a {id... on Person {name}}}
        """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo {... on Cat {catName} ... on Dog {dogName}} }
        """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && it.fieldDefinition.type.name == "CatOrDog" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "catName" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Cat" && it.fieldsContainer.name == "Cat" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "dogName" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Dog" && it.fieldsContainer.name == "Dog" })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    def "works for modified types (non null list elements)"() {
        given:
        def schema = TestUtil.schema("""
            type Query {
              foo: [CatOrDog!]
              bar: [Bar!]!
            }
            
            type Cat {
                catName: String
            }
            
            type Bar {
                id: String
            }
            
            type Dog {
                dogName: String
            }
            
            union CatOrDog = Cat | Dog
            
            schema {query: Query}
        """)
        def catOrDog = schema.getType("CatOrDog")
        def bar = schema.getType("Bar")
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo {... on Cat {catName} ... on Dog {dogName}} bar {id}}
        """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "foo" && list(nonNull(catOrDog)).isEqualTo(it.fieldDefinition.type) && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "catName" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Cat" && it.fieldsContainer.name == "Cat" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "dogName" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Dog" && it.fieldsContainer.name == "Dog" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "id" && it.fieldDefinition.type.name == "String" && nonNull(list(nonNull(bar))).isEqualTo(it.parentType) && it.fieldsContainer.name == "Bar" })

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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo {__typename subFoo} 
            __schema{  types { name } }
            __type(name: "Foo") { name } 
            }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
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

    def "#763 handles union types and introspection fields"() {
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
        def visitor = mockQueryVisitor()
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
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "someObject" && it.fieldDefinition.type.name == "SomeObject" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "someUnionType" && it.fieldDefinition.type.name == "SomeUnionType" && it.parentType.name == "SomeObject" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "__typename" && it.fieldDefinition.type.wrappedType.name == "String" && it.typeNameIntrospectionField })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "field1" && it.fieldDefinition.type.name == "String" && it.parentType.name == "TypeX" })
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it -> it.field.name == "field2" && it.fieldDefinition.type.name == "String" && it.parentType.name == "TypeY" })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }


    def "can select an arbitrary root node with coerced variables as plain map"() {
        // When using an arbitrary root node, there is no variable definition context available.
        // Thus the variables must have already been coerced, but may appear as a plain map rather than CoercedVariables
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo { subFoo {id}} }
            """)
        def subFooAsRoot = query.children[0].children[0].children[0].children[0].children[0]
        assert subFooAsRoot instanceof Field
        ((Field) subFooAsRoot).name == "subFoo"
        def rootParentType = schema.getType("Foo")
        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .root(subFooAsRoot)
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

    def "can select an arbitrary root node with coerced variables"() {
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo { subFoo {id}} }
            """)
        def subFooAsRoot = query.children[0].children[0].children[0].children[0].children[0]
        assert subFooAsRoot instanceof Field
        ((Field) subFooAsRoot).name == "subFoo"
        def rootParentType = schema.getType("Foo")
        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .root(subFooAsRoot)
                .rootParentType(rootParentType)
                .coercedVariables(CoercedVariables.emptyVariables())
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
    def "builder doesn't allow null arguments"() {
        when:
        QueryTraverser.newQueryTraverser()
                .document(document)
                .operationName(operationName)
                .root(root)
                .rootParentType(rootParentType)
                .fragmentsByName(fragmentsByName)
                .build()

        then:
        thrown(AssertException)

        where:
        document             | operationName | root                     | rootParentType          | fragmentsByName
        createQuery("{foo}") | null          | Field.newField().name("dummy").build() | null                    | null
        createQuery("{foo}") | "foo"         | Field.newField().name("dummy").build() | null                    | null
        createQuery("{foo}") | "foo"         | Field.newField().name("dummy").build() | Mock(GraphQLObjectType) | null
        createQuery("{foo}") | "foo"         | Field.newField().name("dummy").build() | null                    | emptyMap()
        null                 | "foo"         | Field.newField().name("dummy").build() | Mock(GraphQLObjectType) | null
        null                 | "foo"         | Field.newField().name("dummy").build() | Mock(GraphQLObjectType) | emptyMap()
        null                 | "foo"         | Field.newField().name("dummy").build() | Mock(GraphQLObjectType) | emptyMap()
        null                 | "foo"         | Field.newField().name("dummy").build() | null                    | emptyMap()
        null                 | "foo"         | null                     | Mock(GraphQLObjectType) | emptyMap()
        null                 | "foo"         | null                     | Mock(GraphQLObjectType) | null
        null                 | "foo"         | null                     | null                    | emptyMap()
    }

    @Unroll
    def "builder doesn't allow ambiguous arguments"() {
        when:
        QueryTraverser.newQueryTraverser()
                .document(createQuery("{foo}"))
                .operationName("foo")
                .root(Field.newField().name("dummy").build())
                .rootParentType(Mock(GraphQLObjectType))
                .fragmentsByName(emptyMap())
                .build()

        then:
        thrown(IllegalStateException)
    }

    def "typename special field doesn't have a fields container and throws exception"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                bar: String
            }
        """)
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            { __typename }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        QueryVisitorFieldEnvironment env
        1 * visitor.visitField(_) >> { args ->
            env = args[0]
        }
        when:
        queryTraversal.visitPreOrder(visitor)
        env.typeNameIntrospectionField
        env.getFieldsContainer()

        then:
        thrown(IllegalStateException)

    }

    def "traverserContext is passed along"() {
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "foo" && it.traverserContext.getParentNodes().size() == 2
        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.traverserContext.getParentNodes().size() == 4

        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "bar" && it.traverserContext.getParentNodes().size() == 2
        })


    }

    def "traverserContext parent nodes for fragment definitions"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                bar: String
            }
        """)
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            { ...F } fragment F on Query @myDirective {bar}
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "bar" && it.traverserContext.getParentNodes().size() == 5 &&
                    it.traverserContext.getParentContext().getParentContext().thisNode() instanceof FragmentDefinition &&
                    ((FragmentDefinition) it.traverserContext.getParentContext().getParentContext().thisNode()).hasDirective("myDirective")
        })


    }

    def "test depthFirst"() {
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitDepthFirst(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "foo" && it.traverserContext.phase == ENTER
        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.traverserContext.phase == ENTER

        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "subFoo" && it.traverserContext.phase == LEAVE

        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "foo" && it.traverserContext.phase == LEAVE
        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "bar" && it.traverserContext.phase == ENTER
        })
        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "bar" && it.traverserContext.phase == LEAVE
        })

    }

    def "test accumulate is returned"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                bar: String
            }
        """)
        def query = createQuery("""
            {bar}
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        def visitor = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
                queryVisitorFieldEnvironment.traverserContext.setAccumulate("RESULT")
            }

        }
        when:
        def result = queryTraversal.visitDepthFirst(visitor)

        then:
        result == "RESULT"

    }

    def "can select an interface field as root node"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                root: SomeInterface
            }
            interface SomeInterface {
                hello: String
            }
        """)
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {root { hello } }
            """)
        def rootField = (query.children[0] as OperationDefinition).selectionSet.selections[0] as Field
        def hello = rootField.selectionSet.selections[0] as Field
        hello.name == "hello"
        def rootParentType = schema.getType("SomeInterface") as GraphQLInterfaceType
        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .root(hello)
                .rootParentType(rootParentType)
                .variables(emptyMap())
                .fragmentsByName(emptyMap())
                .build()
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.field.name == "hello" && it.parentType.name == "SomeInterface"
        })

    }

    def "can select __typename field as root node"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
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
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {root { __typename } }
            """)
        def rootField = (query.children[0] as OperationDefinition).selectionSet.selections[0] as Field
        def typeNameField = rootField.selectionSet.selections[0] as Field
        def rootParentType = schema.getType("SomeUnion") as GraphQLUnionType
        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .root(typeNameField)
                .rootParentType(rootParentType)
                .variables(emptyMap())
                .fragmentsByName(emptyMap())
                .build()
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.isTypeNameIntrospectionField()
        })

    }

    def "respects visitorWithControl result"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                field: Foo 
            }
            type Foo {
                a: String
            }
        """)
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {field { a } }
            """)
        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .document(query)
                .variables(emptyMap())
                .build()
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitFieldWithControl(_) >> { TraversalControl.ABORT }

    }

    def "can copy with Scalar ObjectField visits"() {
        given:
        def schema = TestUtil.schema('''
            scalar JSON
            
            type Query{
                field(arg :  JSON): String 
            }
        ''', newRuntimeWiring().scalar(TestUtil.mockScalar("JSON")).build())
        def visitor = mockQueryVisitor()
        def query = createQuery('''
            {field(arg : {a : "x", b : "y"}) }
            ''')
        QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .document(query)
                .variables(emptyMap())
                .build()
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.fieldDefinition.name == "field"
        })

    }

    def "directive arguments are not visited"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }

            directive @cache(
                ttl: Int!
            ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT
        """)
        def visitor = mockQueryVisitor()
        def query = createQuery("""
            {foo { subFoo @cache(ttl:100) } bar @cache(ttl:200) }
            """)
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPreOrder(visitor)
        then:
        0 * visitor.visitArgument(_)
    }

    def "conditional nodes via variables are defaulted correctly and visited correctly"() {

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
        def visitor = mockQueryVisitor()
        def query = createQuery('''
            query test($var : Boolean = true)  {
                bar @include(if:$var)
            }
            ''')
        QueryTraverser queryTraversal = createQueryTraversal(query, schema)

        when: "we have an enabled variable conditional node"
        queryTraversal.visitPreOrder(visitor)

        then: "it should be visited"
        1 * visitor.visitField({ QueryVisitorFieldEnvironmentImpl it ->
            it.fieldDefinition.name == "bar"
        })

        when: "we have an enabled variable conditional node"
        query = createQuery('''
            query test($var : Boolean = false)  {
                bar @include(if:$var)
            }
            ''')
        queryTraversal = createQueryTraversal(query, schema)
        queryTraversal.visitPreOrder(visitor)
        then: "it should not be visited"
        0 * visitor.visitField(_)
    }

    def "can coerce field arguments or not"() {
        def sdl = """
            input Test{   x: String!} 
            type Query{   testInput(input: Test!): String}
            type Mutation{   testInput(input: Test!): String}
            """

        def schema = TestUtil.schema(sdl)

        def query = createQuery('''
        mutation a($test: Test!) {
            testInput(input: $test)
        }''')


        def fieldArgMap = [:]
        def queryVisitorStub = new QueryVisitorStub() {
            @Override
            void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
                super.visitField(queryVisitorFieldEnvironment)
                fieldArgMap = queryVisitorFieldEnvironment.getArguments()
            }
        }

        when:
        QueryTraverser.newQueryTraverser()
                .schema(schema)
                .document(query)
                .coercedVariables(CoercedVariables.of([test: [x: "X"]]))
                .build()
                .visitPreOrder(queryVisitorStub)

        then:

        fieldArgMap == [input: [x:"X"]]

        when:
        fieldArgMap = null


        def options = QueryTraversalOptions.defaultOptions()
                .coerceFieldArguments(false)
        QueryTraverser.newQueryTraverser()
                .schema(schema)
                .document(query)
                .coercedVariables(CoercedVariables.of([test: [x: "X"]]))
                .options(options)
                .build()
                .visitPreOrder(queryVisitorStub)


        then:
        fieldArgMap == [:] // empty map
    }
}

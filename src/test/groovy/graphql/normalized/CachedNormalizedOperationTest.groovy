package graphql.normalized

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.TestUtil
import graphql.language.Document
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import spock.lang.Specification

class CachedNormalizedOperationTest extends Specification {

    // --- Test helpers ---

    private static void assertValidQuery(GraphQLSchema schema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(schema).build()
        def ei = ExecutionInput.newExecutionInput(query).variables(variables).build()
        assert graphQL.execute(ei).errors.size() == 0
    }

    private static List<String> printTree(ExecutableNormalizedOperation tree) {
        def result = []
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
        traverser.traverse(tree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField field = context.thisNode()
                result << field.printDetails()
                return TraversalControl.CONTINUE
            }
        })
        return result
    }

    private static List<String> printCachedTree(CachedNormalizedOperation tree) {
        def result = []
        Traverser<CachedNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
        traverser.traverse(tree.getTopLevelFields(), new TraverserVisitorStub<CachedNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<CachedNormalizedField> context) {
                CachedNormalizedField field = context.thisNode()
                result << field.printDetails()
                return TraversalControl.CONTINUE
            }
        })
        return result
    }

    private static List<String> printCachedTreeWithConditions(CachedNormalizedOperation tree) {
        def result = []
        Traverser<CachedNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
        traverser.traverse(tree.getTopLevelFields(), new TraverserVisitorStub<CachedNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<CachedNormalizedField> context) {
                CachedNormalizedField field = context.thisNode()
                result << field.printDetails() + " [" + field.getInclusionCondition() + "]"
                return TraversalControl.CONTINUE
            }
        })
        return result
    }

    // --- Tests ---

    def "basic query without skip/include produces same tree"() {
        given:
        String schema = """
        type Query {
            animal: Animal
        }
        interface Animal {
            name: String
            friends: [Friend]
        }
        type Friend {
            name: String
        }
        type Bird implements Animal {
            name: String
            friends: [Friend]
        }
        type Cat implements Animal {
            name: String
            friends: [Friend]
        }
        type Dog implements Animal {
            name: String
            friends: [Friend]
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            animal {
                name
                friends {
                    name
                }
            }
        }
        """
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)
        def materialized = CachedOperationMaterializer.materialize(
                cached, [:], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        def printedCached = printCachedTree(cached)
        printedCached == ['Query.animal',
                          '[Bird, Cat, Dog].name',
                          '[Bird, Cat, Dog].friends',
                          'Friend.name']

        and:
        def printedMaterialized = printTree(materialized)
        printedMaterialized == ['Query.animal',
                                '[Bird, Cat, Dog].name',
                                '[Bird, Cat, Dog].friends',
                                'Friend.name']
    }

    def "skip/include with literal booleans are resolved at cache time"() {
        given:
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
            baz: String
            qux: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            foo {
                bar @skip(if: true)
                baz @include(if: false)
                qux
            }
        }
        """
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        then:
        // bar and baz should be eliminated at cache time (literal skip/include)
        def printedCached = printCachedTree(cached)
        printedCached == ['Query.foo',
                          'Foo.qux']
    }

    def "skip/include with variables are preserved as conditions in cached tree"() {
        given:
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
            baz: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($skipBar: Boolean!, $showBaz: Boolean!) {
            foo {
                bar @skip(if: $skipBar)
                baz @include(if: $showBaz)
            }
        }
        '''
        assertValidQuery(graphQLSchema, query, [skipBar: false, showBaz: true])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        then:
        def printed = printCachedTreeWithConditions(cached)
        printed == ['Query.foo [ALWAYS]',
                    'Foo.bar [NOT($skipBar)]',
                    'Foo.baz [IF($showBaz)]']
    }

    def "same cached tree materializes differently with different variables"() {
        given:
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
            baz: String
            always: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($skipBar: Boolean!, $showBaz: Boolean!) {
            foo {
                bar @skip(if: $skipBar)
                baz @include(if: $showBaz)
                always
            }
        }
        '''
        assertValidQuery(graphQLSchema, query, [skipBar: false, showBaz: true])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        // Materialize with different variable combinations
        def tree1 = CachedOperationMaterializer.materialize(
                cached, [skipBar: false, showBaz: true], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())
        def tree2 = CachedOperationMaterializer.materialize(
                cached, [skipBar: true, showBaz: false], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())
        def tree3 = CachedOperationMaterializer.materialize(
                cached, [skipBar: true, showBaz: true], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        // All fields included
        printTree(tree1) == ['Query.foo',
                             'Foo.bar',
                             'Foo.baz',
                             'Foo.always']

        and:
        // Only always included
        printTree(tree2) == ['Query.foo',
                             'Foo.always']

        and:
        // baz and always included
        printTree(tree3) == ['Query.foo',
                             'Foo.baz',
                             'Foo.always']
    }

    def "merged fields with different conditions are kept separate in cached tree"() {
        given:
        String schema = """
        type Query {
            pet: Pet
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($skipDogName: Boolean!) {
            pet {
                ... on Dog {
                    name @skip(if: $skipDogName)
                }
                ... on Cat {
                    name
                }
            }
        }
        '''
        assertValidQuery(graphQLSchema, query, [skipDogName: false])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        then:
        // The two "name" fields have different conditions, so they stay separate
        def printed = printCachedTreeWithConditions(cached)
        printed == ['Query.pet [ALWAYS]',
                    'Dog.name [NOT($skipDogName)]',
                    'Cat.name [ALWAYS]']

        when:
        // skipDogName=false → both included → should be merged to [Cat, Dog].name
        def tree1 = CachedOperationMaterializer.materialize(
                cached, [skipDogName: false], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        printTree(tree1) == ['Query.pet',
                             '[Dog, Cat].name']

        when:
        // skipDogName=true → only Cat.name included
        def tree2 = CachedOperationMaterializer.materialize(
                cached, [skipDogName: true], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        printTree(tree2) == ['Query.pet',
                             'Cat.name']
    }

    def "inline fragment with skip condition excludes all child fields"() {
        given:
        String schema = """
        type Query {
            pet: Pet
        }
        interface Pet {
            name: String
            age: Int
        }
        type Cat implements Pet {
            name: String
            age: Int
        }
        type Dog implements Pet {
            name: String
            age: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($skipDog: Boolean!) {
            pet {
                ... on Dog @skip(if: $skipDog) {
                    name
                    age
                }
                ... on Cat {
                    name
                }
            }
        }
        '''
        assertValidQuery(graphQLSchema, query, [skipDog: false])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        then:
        def printed = printCachedTreeWithConditions(cached)
        printed == ['Query.pet [ALWAYS]',
                    'Dog.name [NOT($skipDog)]',
                    'Cat.name [ALWAYS]',
                    'Dog.age [NOT($skipDog)]']

        when:
        // skipDog=true → only Cat fields
        def tree = CachedOperationMaterializer.materialize(
                cached, [skipDog: true], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        printTree(tree) == ['Query.pet',
                            'Cat.name']
    }

    def "combined skip and include on the same field"() {
        given:
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($skip: Boolean!, $include: Boolean!) {
            foo {
                bar @skip(if: $skip) @include(if: $include)
            }
        }
        '''
        assertValidQuery(graphQLSchema, query, [skip: false, include: true])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        then:
        def printed = printCachedTreeWithConditions(cached)
        printed == ['Query.foo [ALWAYS]',
                    'Foo.bar [(NOT($skip) AND IF($include))]']

        when: "both conditions must be satisfied"
        def t1 = CachedOperationMaterializer.materialize(
                cached, [skip: false, include: true], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())
        def t2 = CachedOperationMaterializer.materialize(
                cached, [skip: true, include: true], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())
        def t3 = CachedOperationMaterializer.materialize(
                cached, [skip: false, include: false], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())
        def t4 = CachedOperationMaterializer.materialize(
                cached, [skip: true, include: false], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        printTree(t1) == ['Query.foo', 'Foo.bar']  // not skipped, included
        printTree(t2) == ['Query.foo']               // skipped
        printTree(t3) == ['Query.foo']               // not included
        printTree(t4) == ['Query.foo']               // both excluded
    }

    def "fragment spread with skip condition"() {
        given:
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
            baz: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($skipFrag: Boolean!) {
            foo {
                ...MyFrag @skip(if: $skipFrag)
                baz
            }
        }
        fragment MyFrag on Foo {
            bar
        }
        '''
        assertValidQuery(graphQLSchema, query, [skipFrag: false])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        then:
        def printed = printCachedTreeWithConditions(cached)
        printed == ['Query.foo [ALWAYS]',
                    'Foo.bar [NOT($skipFrag)]',
                    'Foo.baz [ALWAYS]']

        when:
        def treeIncluded = CachedOperationMaterializer.materialize(
                cached, [skipFrag: false], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())
        def treeSkipped = CachedOperationMaterializer.materialize(
                cached, [skipFrag: true], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        printTree(treeIncluded) == ['Query.foo', 'Foo.bar', 'Foo.baz']
        printTree(treeSkipped) == ['Query.foo', 'Foo.baz']
    }

    def "arguments are coerced during materialization"() {
        given:
        String schema = """
        type Query {
            foo(id: ID!): Foo
        }
        type Foo {
            bar: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($id: ID!) {
            foo(id: $id) {
                bar
            }
        }
        '''
        assertValidQuery(graphQLSchema, query, [id: "123"])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)
        def tree = CachedOperationMaterializer.materialize(
                cached, [id: "123"], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        tree.getTopLevelFields().get(0).getResolvedArguments() == [id: "123"]

        when: "different variables produce different resolved args"
        def tree2 = CachedOperationMaterializer.materialize(
                cached, [id: "456"], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        tree2.getTopLevelFields().get(0).getResolvedArguments() == [id: "456"]
    }

    def "skip/include on interface fields with mixed conditions merges correctly"() {
        given:
        String schema = """
        type Query {
            pets: Pet
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($true: Boolean!, $false: Boolean!) {
            pets {
                ... on Cat {
                    cat_yes_1: name @include(if: true)
                    cat_yes_2: name @skip(if: $false)
                }
                ... on Dog @include(if: $true) {
                    dog_yes_1: name @include(if: $true)
                    dog_yes_2: name @skip(if: $false)
                }
                ... on Pet @skip(if: $false) {
                    pet_name: name
                }
            }
        }
        '''
        assertValidQuery(graphQLSchema, query, ["true": true, "false": false])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)
        def tree = CachedOperationMaterializer.materialize(
                cached, ["true": true, "false": false], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())
        def printedTree = printTree(tree)

        then:
        printedTree == ['Query.pets',
                        'cat_yes_1: Cat.name',
                        'cat_yes_2: Cat.name',
                        'dog_yes_1: Dog.name',
                        'dog_yes_2: Dog.name',
                        'pet_name: [Cat, Dog].name',
        ]
    }

    def "cached tree field count and depth are tracked"() {
        given:
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: Bar
        }
        type Bar {
            baz: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = "{ foo { bar { baz } } }"
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        then:
        cached.fieldCount == 3
        cached.maxDepth == 3
    }

    def "deeply nested fields with mixed conditions"() {
        given:
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: Bar
            name: String
        }
        type Bar {
            baz: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        query($skip: Boolean!) {
            foo {
                name
                bar @skip(if: $skip) {
                    baz
                }
            }
        }
        '''
        assertValidQuery(graphQLSchema, query, [skip: false])
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)

        then:
        def printed = printCachedTreeWithConditions(cached)
        printed == ['Query.foo [ALWAYS]',
                    'Foo.name [ALWAYS]',
                    'Foo.bar [NOT($skip)]',
                    'Bar.baz [NOT($skip)]']

        when:
        def tree1 = CachedOperationMaterializer.materialize(
                cached, [skip: false], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())
        def tree2 = CachedOperationMaterializer.materialize(
                cached, [skip: true], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        printTree(tree1) == ['Query.foo', 'Foo.name', 'Foo.bar', 'Bar.baz']
        printTree(tree2) == ['Query.foo', 'Foo.name']
    }

    def "field aliases work correctly through cached pipeline"() {
        given:
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
        {
            foo {
                myAlias: bar
            }
        }
        '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def cached = CachedNormalizedOperationFactory.createCachedOperation(graphQLSchema, document, null)
        def tree = CachedOperationMaterializer.materialize(
                cached, [:], graphQLSchema, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        printTree(tree) == ['Query.foo', 'myAlias: Foo.bar']
    }
}

package graphql.normalized

import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.MergedField
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import spock.lang.Specification

import static graphql.schema.FieldCoordinates.coordinates

class NormalizedQueryTreeFactoryTest extends Specification {


    def "test"() {
        String schema = """
type Query{ 
    animal: Animal
}
interface Animal {
    name: String
    friends: [Friend]
}

union Pet = Dog | Cat

type Friend {
    name: String
    isBirdOwner: Boolean
    isCatOwner: Boolean
    pets: [Pet] 
}

type Bird implements Animal {
   name: String 
   friends: [Friend]
}

type Cat implements Animal{
   name: String 
   friends: [Friend]
   breed: String 
}

type Dog implements Animal{
   name: String 
   breed: String
   friends: [Friend]
}
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            animal{
                name
                otherName: name
               ... on Cat {
                    name
                    friends {
                        ... on Friend {
                            isCatOwner
                        }
                   } 
               }
               ... on Bird {
                    friends {
                        isBirdOwner
                    }
                    friends {
                        name
                    }
               }
               ... on Dog {
                  name   
               }
        }}
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.animal: Animal (conditional: false)',
                        'Bird.name: String (conditional: true)',
                        'Cat.name: String (conditional: true)',
                        'Dog.name: String (conditional: true)',
                        'otherName: Bird.name: String (conditional: true)',
                        'otherName: Cat.name: String (conditional: true)',
                        'otherName: Dog.name: String (conditional: true)',
                        'Cat.friends: [Friend] (conditional: true)',
                        'Friend.isCatOwner: Boolean (conditional: false)',
                        'Bird.friends: [Friend] (conditional: true)',
                        'Friend.isBirdOwner: Boolean (conditional: false)',
                        'Friend.name: String (conditional: false)']

    }

    def "test2"() {
        String schema = """
        type Query{ 
            a: A
        }
        interface A {
           b: B  
        }
        type A1 implements A {
           b: B 
        }
        type A2 implements A{
            b: B
        }
        interface B {
            leaf: String
        }
        type B1 implements B {
            leaf: String
        } 
        type B2 implements B {
            leaf: String
        } 
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            a {
            ... on A {
                myAlias: b { leaf }
            }
                ... on A1 {
                   b { 
                     ... on B1 {
                        leaf
                        }
                     ... on B2 {
                        leaf
                        }
                   }
                }
                ... on A1 {
                   b { 
                     ... on B1 {
                        leaf
                        }
                   }
                }
                ... on A2 {
                    b {
                       ... on B2 {
                            leaf
                       } 
                    }
                }
            }
        }
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.a: A (conditional: false)',
                        'myAlias: A1.b: B (conditional: true)',
                        'B1.leaf: String (conditional: true)',
                        'B2.leaf: String (conditional: true)',
                        'myAlias: A2.b: B (conditional: true)',
                        'B1.leaf: String (conditional: true)',
                        'B2.leaf: String (conditional: true)',
                        'A1.b: B (conditional: true)',
                        'B1.leaf: String (conditional: true)',
                        'B2.leaf: String (conditional: true)',
                        'A2.b: B (conditional: true)',
                        'B2.leaf: String (conditional: true)']
    }

    def "test3"() {
        String schema = """
        type Query{ 
            a: [A]
            object: Object
        }
        type Object {
            someValue: String
        }
        interface A {
           b: B  
        }
        type A1 implements A {
           b: B 
        }
        type A2 implements A{
            b: B
        }
        interface B {
            leaf: String
        }
        type B1 implements B {
            leaf: String
        } 
        type B2 implements B {
            leaf: String
        } 
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
          object{someValue}
          a {
            ... on A1 {
              b {
                ... on B {
                  leaf
                }
                ... on B1 {
                  leaf
                }
                ... on B2 {
                  ... on B {
                    leaf
                  }
                  leaf
                  leaf
                  ... on B2 {
                    leaf
                  }
                }
              }
            }
          }
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.object: Object (conditional: false)',
                        'Object.someValue: String (conditional: false)',
                        'Query.a: [A] (conditional: false)',
                        'A1.b: B (conditional: true)',
                        'B1.leaf: String (conditional: true)',
                        'B2.leaf: String (conditional: true)']

    }

    def "test impossible type condition"() {

        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet{
            name: String
        }
        union CatOrDog = Cat | Dog
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                ... on Dog {
                    ... on CatOrDog {
                    ... on Cat{
                            name
                            }
                    }
                }
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets: [Pet] (conditional: false)']

    }

    def "query with unions and __typename"() {

        String schema = """
        type Query{ 
            pets: [CatOrDog]
        }
        type Cat {
            catName: String
        }
        type Dog {
            dogName: String
        }
        union CatOrDog = Cat | Dog
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                __typename
                ... on Cat {
                    catName 
                }  
                ... on Dog {
                    dogName
                }
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets: [CatOrDog] (conditional: false)',
                        'Cat.__typename: String! (conditional: true)',
                        'Dog.__typename: String! (conditional: true)',
                        'Cat.catName: String (conditional: true)',
                        'Dog.dogName: String (conditional: true)']

    }

    def "query with interface"() {

        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            id: ID
        }
        type Cat implements Pet{
            id: ID
            catName: String
        }
        type Dog implements Pet{
            id: ID
            dogName: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                id
                ... on Cat {
                    catName 
                }  
                ... on Dog {
                    dogName
                }
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets: [Pet] (conditional: false)',
                        'Cat.id: ID (conditional: true)',
                        'Dog.id: ID (conditional: true)',
                        'Cat.catName: String (conditional: true)',
                        'Dog.dogName: String (conditional: true)']

    }

    def "test5"() {
        String schema = """
        type Query{ 
            a: [A]
        }
        interface A {
           b: String
        }
        type A1 implements A {
           b: String 
        }
        type A2 implements A{
            b: String
            otherField: A
        }
        type A3  implements A {
            b: String
        }
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)


        def query = """
        {
            a {
                b
                ... on A1 {
                   b 
                }
                ... on A2 {
                    b 
                    otherField {
                    ... on A2 {
                            b
                        }
                        ... on A3 {
                            b
                        }
                    }
                    
                }
            }
        }
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.a: [A] (conditional: false)',
                        'A1.b: String (conditional: true)',
                        'A2.b: String (conditional: true)',
                        'A3.b: String (conditional: true)',
                        'A2.otherField: A (conditional: true)',
                        'A2.b: String (conditional: true)',
                        'A3.b: String (conditional: true)']

    }

    def "test6"() {
        String schema = """
        type Query {
            issues: [Issue]
        }

        type Issue {
            id: ID
            author: User
        }
        type User {
            name: String
            createdIssues: [Issue] 
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        def query = """{ issues {
                    author {
                        name
                        ... on User {
                            createdIssues {
                                id
                            }
                        }
                    }
                }}
                """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.issues: [Issue] (conditional: false)',
                        'Issue.author: User (conditional: false)',
                        'User.name: String (conditional: false)',
                        'User.createdIssues: [Issue] (conditional: false)',
                        'Issue.id: ID (conditional: false)']

    }

    def "test7"() {
        String schema = """
        type Query {
            issues: [Issue]
        }

        type Issue {
            authors: [User]
        }
        type User {
            name: String
            friends: [User]
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        def query = """{ issues {
                    authors {
                       friends {
                            friends {
                                name
                            }
                       } 
                   }
                }}
                """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.issues: [Issue] (conditional: false)',
                        'Issue.authors: [User] (conditional: false)',
                        'User.friends: [User] (conditional: false)',
                        'User.friends: [User] (conditional: false)',
                        'User.name: String (conditional: false)']

    }

    def "query with fragment definition"() {
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.foo: Foo (conditional: false)',
                        'Foo.subFoo: String (conditional: false)',
                        'Foo.moreFoos: Foo (conditional: false)',
                        'Foo.subFoo: String (conditional: false)']
    }

    def "query with interface in between"() {
        def graphQLSchema = TestUtil.schema("""
        type Query {
            pets: [Pet]
        }
        interface Pet {
            name: String
            friends: [Human]
        }
        type Human {
            name: String
        }
        type Cat implements Pet {
            name: String
            friends: [Human]
        }
        type Dog implements Pet {
            name: String
            friends: [Human]
        }
        """)
        def query = """
            { pets { friends {name} } }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets: [Pet] (conditional: false)',
                        'Cat.friends: [Human] (conditional: true)',
                        'Human.name: String (conditional: false)',
                        'Dog.friends: [Human] (conditional: true)',
                        'Human.name: String (conditional: false)']
    }


    List<String> printTree(NormalizedQueryTree queryExecutionTree) {
        def result = []
        Traverser<NormalizedField> traverser = Traverser.depthFirst({ it.getChildren() });
        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<NormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<NormalizedField> context) {
                NormalizedField queryExecutionField = context.thisNode();
                result << queryExecutionField.printDetails()
                return TraversalControl.CONTINUE;
            }
        });
        result
    }

    def "field to normalized field is build"() {
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        def subFooField = (document.getDefinitions()[1] as FragmentDefinition).getSelectionSet().getSelections()[0] as Field

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def fieldToNormalizedField = tree.getFieldToNormalizedField()

        expect:
        fieldToNormalizedField.keys().size() == 4
        fieldToNormalizedField.get(subFooField).size() == 2
        fieldToNormalizedField.get(subFooField)[0].level == 2
        fieldToNormalizedField.get(subFooField)[1].level == 3
    }

    def "normalized fields map with interfaces "() {

        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            id: ID
        }
        type Cat implements Pet{
            id: ID
        }
        type Dog implements Pet{
            id: ID
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                id
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        def petsField = (document.getDefinitions()[0] as OperationDefinition).getSelectionSet().getSelections()[0] as Field
        def idField = petsField.getSelectionSet().getSelections()[0] as Field

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def fieldToNormalizedField = tree.getFieldToNormalizedField()


        expect:
        fieldToNormalizedField.size() == 3
        fieldToNormalizedField.get(idField).size() == 2
        fieldToNormalizedField.get(idField)[0].objectType.name == "Cat"
        fieldToNormalizedField.get(idField)[1].objectType.name == "Dog"


    }

    def "query with introspection fields"() {
        String schema = """
        type Query{ 
            foo: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            __typename
            alias: __typename
            __schema {  queryType { name } }
            __type(name: "Query") {name}
            ...F
        }
        fragment F on Query {
            __typename
            alias: __typename
            __schema {  queryType { name } }
            __type(name: "Query") {name}
        }
        
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        def selections = (document.getDefinitions()[0] as OperationDefinition).getSelectionSet().getSelections()
        def typeNameField = selections[0]
        def aliasedTypeName = selections[1]
        def schemaField = selections[2]
        def typeField = selections[3]

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def fieldToNormalizedField = tree.getFieldToNormalizedField()

        expect:
        fieldToNormalizedField.size() == 14
        fieldToNormalizedField.get(typeNameField)[0].objectType.name == "Query"
        fieldToNormalizedField.get(typeNameField)[0].fieldDefinition == graphQLSchema.getIntrospectionTypenameFieldDefinition()
        fieldToNormalizedField.get(aliasedTypeName)[0].alias == "alias"
        fieldToNormalizedField.get(aliasedTypeName)[0].fieldDefinition == graphQLSchema.getIntrospectionTypenameFieldDefinition()

        fieldToNormalizedField.get(schemaField)[0].objectType.name == "Query"
        fieldToNormalizedField.get(schemaField)[0].fieldDefinition == graphQLSchema.getIntrospectionSchemaFieldDefinition()

        fieldToNormalizedField.get(typeField)[0].objectType.name == "Query"
        fieldToNormalizedField.get(typeField)[0].fieldDefinition == graphQLSchema.getIntrospectionTypeFieldDefinition()

    }

    def "fragment is used multiple times with different parents"() {
        String schema = """
        type Query{ 
            pet: Pet
        }
        interface Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pet {
                ... on Dog {
                    ...F
                }
                ... on Cat {
                    ...F
                }
            }
        }
        fragment F on Pet {
            name
        }
        
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pet: Pet (conditional: false)',
                        'Dog.name: String (conditional: true)',
                        'Cat.name: String (conditional: true)'];
    }

    def "same result key but different field"() {
        String schema = """
        type Query{ 
            pet: Pet
        }
        interface Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
            otherField: String
        }
        type Cat implements Pet {
            name: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pet {
                ... on Dog {
                    name: otherField
                }
                ... on Cat {
                    name
                }
            }
        }
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pet: Pet (conditional: false)',
                        'name: Dog.otherField: String (conditional: true)',
                        'Cat.name: String (conditional: true)'];
    }

    def "normalized field to MergedField is build"() {
        given:
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def normalizedFieldToMergedField = tree.getNormalizedFieldToMergedField();
        Traverser<NormalizedField> traverser = Traverser.depthFirst({ it.getChildren() });
        List<MergedField> result = new ArrayList<>()
        when:
        traverser.traverse(tree.getTopLevelFields(), new TraverserVisitorStub<NormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<NormalizedField> context) {
                NormalizedField normalizedField = context.thisNode();
                result.add(normalizedFieldToMergedField[normalizedField])
                return TraversalControl.CONTINUE;
            }
        });

        then:
        result.size() == 4
        result.collect { it.getResultKey() } == ['foo', 'subFoo', 'moreFoos', 'subFoo']
    }

    def "coordinates to NormalizedField is build"() {
        given:
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();

        when:
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def coordinatesToNormalizedFields = tree.coordinatesToNormalizedFields

        then:
        coordinatesToNormalizedFields.size() == 4
        coordinatesToNormalizedFields.get(coordinates("Query", "foo")).size() == 1
        coordinatesToNormalizedFields.get(coordinates("Foo", "moreFoos")).size() == 1
        coordinatesToNormalizedFields.get(coordinates("Foo", "subFoo")).size() == 2
    }

    def "handles mutations"() {
        String schema = """
type Query{ 
    animal: Animal
}

type Mutation {
    createAnimal: Query
}

type Subscription {
    subscribeToAnimal: Query
}

interface Animal {
    name: String
    friends: [Friend]
}

union Pet = Dog | Cat

type Friend {
    name: String
    isBirdOwner: Boolean
    isCatOwner: Boolean
    pets: [Pet] 
}

type Bird implements Animal {
   name: String 
   friends: [Friend]
}

type Cat implements Animal{
   name: String 
   friends: [Friend]
   breed: String 
}

type Dog implements Animal{
   name: String 
   breed: String
   friends: [Friend]
}

schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String mutation = """
        mutation TestMutation{
            createAnimal {
                animal {
                   name
                   otherName: name
                   ... on Cat {
                        name
                        friends {
                            ... on Friend {
                                isCatOwner
                            }
                       } 
                   }
                   ... on Bird {
                        friends {
                            isBirdOwner
                        }
                        friends {
                            name
                        }
                   }
                   ... on Dog {
                      name   
                   }
                }
            }
        }
        """

        assertValidQuery(graphQLSchema, mutation)

        Document document = TestUtil.parseQuery(mutation)

        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def tree = dependencyGraph.createNormalizedQuery(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Mutation.createAnimal: Query (conditional: false)',
                        'Query.animal: Animal (conditional: false)',
                        'Bird.name: String (conditional: true)',
                        'Cat.name: String (conditional: true)',
                        'Dog.name: String (conditional: true)',
                        'otherName: Bird.name: String (conditional: true)',
                        'otherName: Cat.name: String (conditional: true)',
                        'otherName: Dog.name: String (conditional: true)',
                        'Cat.friends: [Friend] (conditional: true)',
                        'Friend.isCatOwner: Boolean (conditional: false)',
                        'Bird.friends: [Friend] (conditional: true)',
                        'Friend.isBirdOwner: Boolean (conditional: false)',
                        'Friend.name: String (conditional: false)']
    }

    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();
        assert graphQL.execute(query, null, variables).errors.size() == 0
    }

    def "normalized arguments"() {
        given:
        String schema = """
        type Query{ 
            dog(id:ID): Dog 
        }
        type Dog {
            name:String
            search(arg1:Input1,arg2: Input1,arg3: Input1): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
            query(\$var1: Input2, \$var2: Input1){dog(id: "123"){
                search(arg1: {foo: "foo", input2: {bar: 123}}, arg2: {foo: "foo", input2: \$var1}, arg3: \$var2) 
            }}
        """

        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        def variables = [
                var1: [bar: 123],
                var2: [foo: "foo", input2: [bar: 123]]
        ]
        // the normalized arg value should be the same regardless of how the value was provided
        def expectedNormalizedArgValue = [foo: new NormalizedInputValue("String", "foo"), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", 123)])]
        when:
        def tree = dependencyGraph.createNormalizedQueryWithRawVariables(graphQLSchema, document, null, variables)
        def topLevelField = tree.getTopLevelFields().get(0)
        def secondField = topLevelField.getChildren().get(0)
        def arg1 = secondField.getNormalizedArgument("arg1")
        def arg2 = secondField.getNormalizedArgument("arg2")
        def arg3 = secondField.getNormalizedArgument("arg3")

        then:
        topLevelField.getNormalizedArgument("id").getTypeName() == "ID"
        topLevelField.getNormalizedArgument("id").getValue() == "123"

        arg1.getTypeName() == "Input1"
        arg1.getValue() == expectedNormalizedArgValue
        arg2.getTypeName() == "Input1"
        arg2.value == expectedNormalizedArgValue
        arg3.getTypeName() == "Input1"
        arg3.value == expectedNormalizedArgValue

    }

    def "normalized arguments with lists"() {
        given:
        String schema = """
        type Query{ 
            search(arg1:[ID!], arg2:[[Input1]], arg3: [Input1]): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
            query($var1: [Input1], $var2: ID!){
                search(arg1:["1",$var2], arg2: [[{foo: "foo1", input2: {bar: 123}},{foo: "foo2", input2: {bar: 456}}]], arg3: $var1) 
            }
        '''

        def variables = [
                var1: [[foo: "foo3", input2: [bar: 789]]],
                var2: "2",
        ]
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)
        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        when:
        def tree = dependencyGraph.createNormalizedQueryWithRawVariables(graphQLSchema, document, null, variables)
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg1 = topLevelField.getNormalizedArgument("arg1")
        def arg2 = topLevelField.getNormalizedArgument("arg2")
        def arg3 = topLevelField.getNormalizedArgument("arg3")

        then:
        arg1.typeName == "[ID!]"
        arg1.value == ["1", "2"]
        arg2.typeName == "[[Input1]]"
        arg2.value == [[
                               [foo: new NormalizedInputValue("String", "foo1"), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", 123)])],
                               [foo: new NormalizedInputValue("String", "foo2"), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", 456)])]
                       ]]

        arg3.getTypeName() == "[Input1]"
        arg3.value == [
                [foo: new NormalizedInputValue("String", "foo3"), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", 789)])],
        ]


    }

    def "normalized arguments with lists 2"() {
        given:
        String schema = """
        type Query{ 
            search(arg1:[[Input1]] ,arg2:[[ID!]!]): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
            query($var1: [Input1], $var2: [ID!]!){
                search(arg1: [$var1],arg2:[["1"],$var2] ) 
            }
        '''

        def variables = [
                var1: [[foo: "foo1", input2: [bar: 123]]],
                var2: "2"
        ]
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)
        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        when:
        def tree = dependencyGraph.createNormalizedQueryWithRawVariables(graphQLSchema, document, null, variables)
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg1 = topLevelField.getNormalizedArgument("arg1")
        def arg2 = topLevelField.getNormalizedArgument("arg2")

        then:
        arg1.typeName == "[[Input1]]"
        arg1.value == [[
                               [foo: new NormalizedInputValue("String", "foo1"), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", 123)])],
                       ]]
        arg2.typeName == "[[ID!]!]"
        arg2.value == [["1"], ["2"]]
    }


    def "recursive schema with a lot of objects"() {
        given:
        String schema = """
        type Query{ 
            foo: Foo 
        }
        interface Foo {
            field: Foo
            id: ID
        }
        type O1 implements Foo {
            field: Foo
            id: ID
        }
        type O2 implements Foo {
            field: Foo
            id: ID
        }
        type O3 implements Foo {
            field: Foo
            id: ID
        }
        type O4 implements Foo {
            field: Foo
            id: ID
        }
        type O5 implements Foo {
            field: Foo
            id: ID
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
            {foo{field{id}}}
        '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        NormalizedQueryTreeFactory dependencyGraph = new NormalizedQueryTreeFactory();
        when:
        def tree = dependencyGraph.createNormalizedQueryWithRawVariables(graphQLSchema, document, null, [:])

        then:
        tree.normalizedFieldToMergedField.size() == 31
        println String.join("\n", printTree(tree))
        /**
         * NF{Query.foo} -> NF{"O1...O5".field,} -> NF{O1...O5.id}*/
    }

}

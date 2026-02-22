package graphql.normalized

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.AbortExecutionException
import graphql.execution.CoercedVariables
import graphql.execution.MergedField
import graphql.execution.RawVariables
import graphql.execution.directives.QueryAppliedDirective
import graphql.introspection.IntrospectionQuery
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import graphql.validation.QueryComplexityLimits
import spock.lang.Specification

import java.util.stream.Collectors
import java.util.stream.IntStream

import static graphql.TestUtil.schema
import static graphql.language.AstPrinter.printAst
import static graphql.parser.Parser.parseValue
import static graphql.schema.FieldCoordinates.coordinates

class ExecutableNormalizedOperationFactoryTest extends Specification {
    static boolean deferSupport

    def setup() {
        // Disable validation complexity limits so ENO limits can be tested
        QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.NONE)
    }

    def cleanup() {
        QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.DEFAULT)
    }

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
                ... on Animal {
                    name
                }
               ... on Cat {
                    name
                    friends {
                        ... on Friend {
                            isCatOwner
                            pets {
                               ... on Dog {
                                name
                               } 
                            }
                        }
                   } 
               }
               ... on Bird {
                    friends {
                        isBirdOwner
                    }
                    friends {
                        name
                        pets {
                           ... on Cat {
                            breed
                           } 
                        }
                    }
               }
               ... on Dog {
                  name   
               }
        }}
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Query.animal: Animal',
                        '--[Bird, Cat, Dog].name: String',
                        '--otherName: [Bird, Cat, Dog].name: String',
                        '--Cat.friends: [Friend]',
                        '---Friend.isCatOwner: Boolean',
                        '---Friend.pets: [Pet]',
                        '----Dog.name: String',
                        '--Bird.friends: [Friend]',
                        '---Friend.isBirdOwner: Boolean',
                        '---Friend.name: String',
                        '---Friend.pets: [Pet]',
                        '----Cat.breed: String'
        ]
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

        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Query.a: A',
                        '--myAlias: [A1, A2].b: B',
                        '---[B1, B2].leaf: String',
                        '--A1.b: B',
                        '---[B1, B2].leaf: String',
                        '--A2.b: B',
                        '---B2.leaf: String'
        ]


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

        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Query.object: Object',
                        '--Object.someValue: String',
                        '-Query.a: [A]',
                        '--A1.b: B',
                        '---[B1, B2].leaf: String'
        ]

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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets']

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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets',
                        '[Cat, Dog].__typename',
                        'Cat.catName',
                        'Dog.dogName']

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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets',
                        '[Cat, Dog].id',
                        'Cat.catName',
                        'Dog.dogName']

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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Query.a: [A]',
                        '--[A1, A2, A3].b: String',
                        '--A2.otherField: A',
                        '---[A2, A3].b: String'
        ]

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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.issues',
                        'Issue.author',
                        'User.name',
                        'User.createdIssues',
                        'Issue.id']

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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.issues',
                        'Issue.authors',
                        'User.friends',
                        'User.friends',
                        'User.name']

    }

    def "parses operation name"() {
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

        def query = """query X_28 { issues {
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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        tree.operation == OperationDefinition.Operation.QUERY
        tree.operationName == "X_28"
        printedTree == ['Query.issues',
                        'Issue.authors',
                        'User.friends',
                        'User.friends',
                        'User.name']

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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.foo',
                        'Foo.subFoo',
                        'Foo.moreFoos',
                        'Foo.subFoo']
    }

    def "query with fragment and type condition"() {
        def graphQLSchema = TestUtil.schema("""
            type Query {
                pet(qualifier : String) : Pet
            }
            interface Pet {
                name(nameArg : String) : String
            }
            
            type Dog implements Pet {
                name(nameArg : String) : String
            }

            type Bird implements Pet {
                name(nameArg : String) : String
            }
            
            type Cat implements Pet {
                name(nameArg : String) : String
            }
        """)
        def query = """
        {
            pet {
                name
                ... on Dog {
                    name
                }
                ... CatFrag
            }
         }
         
        fragment CatFrag on Cat {
            name
        }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Query.pet: Pet',
                        '--[Bird, Cat, Dog].name: String'
        ]
    }

    def "query with fragment and type condition merged together 2"() {
        def graphQLSchema = TestUtil.schema("""
            type Query {
                pet : Pet
            }
            interface Pet {
                name : String
            }
            
            type Dog implements Pet {
                name : String
            }

            type Bird implements Pet {
                name : String
            }
            
            type Cat implements Pet {
                name : String
            }
        """)
        def query = """
        {
            pet {
                name
                ... on Dog {
                    name
                }
                ... CatFrag
            }
         }
         
        fragment CatFrag on Cat {
            name
        }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Query.pet: Pet',
                        '--[Bird, Cat, Dog].name: String'
        ]
    }


    def "query with interface in between"() {
        def graphQLSchema = schema("""
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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets',
                        '[Cat, Dog].friends',
                        'Human.name']
    }

    def "test interface fields with different output types (covariance) on the implementations"() {
        def graphQLSchema = schema("""
        interface Animal {
            parent: Animal
            name: String
        }
        type Cat implements Animal {
            name: String
            parent: Cat
        }
        type Dog implements Animal {
            name: String
            parent: Dog
            isGoodBoy: Boolean
        }
        type Query {
            animal: Animal
        }
        """)

        def query = """
        {
            animal {
                parent {
                    name
                }
            }
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == [
                "-Query.animal: Animal",
                "--[Cat, Dog].parent: Cat, Dog",
                "---[Cat, Dog].name: String",
        ]
    }

    def "__typename in unions get merged"() {
        def graphQLSchema = schema("""

        type Cat {
            name: String
        }
        type Dog {
            name: String
        }
        union CatOrDog = Cat | Dog
        type Query {
            animal: CatOrDog
        }
        """)

        def query = """
        {
            animal {
                ... on Cat {__typename}
                ... on Dog {__typename}
            }
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == [
                "-Query.animal: CatOrDog",
                "--[Cat, Dog].__typename: String!",
        ]
    }

    def "test union fields with different output types (covariance) on the implementations"() {
        def graphQLSchema = schema("""

        interface Animal {
            parent: CatOrDog
            name: String
        }
        type Cat  implements Animal{
            name: String
            parent: Cat
        }
        type Dog  implements Animal {
            name: String
            parent: Dog
            isGoodBoy: Boolean
        }
        union CatOrDog = Cat | Dog
        type Query {
            animal: Animal
        }
        """)

        def query = """
        {
            animal {
                parent {
                ... on Cat {name __typename }  
                ... on Dog {name __typename }
                }
            }
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == [
                "-Query.animal: Animal",
                "--[Cat, Dog].parent: Cat, Dog",
                "---[Cat, Dog].name: String",
                "---[Cat, Dog].__typename: String!"
        ]
    }

    List<String> printTree(ExecutableNormalizedOperation queryExecutionTree) {
        def result = []
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField queryExecutionField = context.thisNode()
                result << queryExecutionField.printDetails()
                return TraversalControl.CONTINUE
            }
        })
        result
    }

    List<String> printTreeAndDirectives(ExecutableNormalizedOperation queryExecutionTree) {
        def result = []
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField queryExecutionField = context.thisNode()
                def queryDirectives = queryExecutionTree.getQueryDirectives(queryExecutionField)

                def fieldDetails = queryExecutionField.printDetails()
                if (queryDirectives != null) {
                    def appliedDirectivesByName = queryDirectives.getImmediateAppliedDirectivesByName()
                    if (!appliedDirectivesByName.isEmpty()) {
                        fieldDetails += " " + printDirectives(appliedDirectivesByName)
                    }
                }
                result << fieldDetails
                return TraversalControl.CONTINUE
            }

            String printDirectives(Map<String, List<QueryAppliedDirective>> stringListMap) {
                String s = stringListMap.collect { entry ->
                    entry.value.collect {
                        " @" + it.name + "(" + it.getArguments().collect {
                            it.name + " : " + '"' + it.value + '"'
                        }.join(",") + ")"
                    }.join(' ')
                }.join(" ")
                return s
            }
        })
        result
    }

    static List<String> printTreeWithLevelInfo(ExecutableNormalizedOperation queryExecutionTree, GraphQLSchema schema) {
        def result = []
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField queryExecutionField = context.thisNode()
                String prefix = ""
                for (int i = 1; i <= queryExecutionField.getLevel(); i++) {
                    prefix += "-"
                }

                def possibleOutputTypes = new LinkedHashSet<String>()
                for (fieldDef in queryExecutionField.getFieldDefinitions(schema)) {
                    possibleOutputTypes.add(GraphQLTypeUtil.simplePrint(fieldDef.type))
                }

                result << (prefix + queryExecutionField.printDetails() + ": " + possibleOutputTypes.join(", "))
                return TraversalControl.CONTINUE
            }
        })
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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def fieldToNormalizedField = tree.getFieldToNormalizedField()


        expect:
        fieldToNormalizedField.size() == 2
        fieldToNormalizedField.get(petsField).size() == 1
        fieldToNormalizedField.get(petsField)[0].printDetails() == "Query.pets"
        fieldToNormalizedField.get(idField).size() == 1
        fieldToNormalizedField.get(idField)[0].printDetails() == "[Cat, Dog].id"


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
        def typeNameField = selections[0] as Field
        def aliasedTypeName = selections[1] as Field
        def schemaField = selections[2] as Field
        def typeField = selections[3] as Field


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def fieldToNormalizedField = tree.getFieldToNormalizedField()

        expect:
        fieldToNormalizedField.size() == 14
        fieldToNormalizedField.get(typeNameField)[0].objectTypeNamesToString() == "Query"
        fieldToNormalizedField.get(typeNameField)[0].getFieldDefinitions(graphQLSchema) == [graphQLSchema.getIntrospectionTypenameFieldDefinition()]
        fieldToNormalizedField.get(aliasedTypeName)[0].alias == "alias"
        fieldToNormalizedField.get(aliasedTypeName)[0].getFieldDefinitions(graphQLSchema) == [graphQLSchema.getIntrospectionTypenameFieldDefinition()]

        fieldToNormalizedField.get(schemaField)[0].objectTypeNamesToString() == "Query"
        fieldToNormalizedField.get(schemaField)[0].getFieldDefinitions(graphQLSchema) == [graphQLSchema.getIntrospectionSchemaFieldDefinition()]

        fieldToNormalizedField.get(typeField)[0].objectTypeNamesToString() == "Query"
        fieldToNormalizedField.get(typeField)[0].getFieldDefinitions(graphQLSchema) == [graphQLSchema.getIntrospectionTypeFieldDefinition()]

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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Query.pet: Pet',
                        '--[Dog, Cat].name: String']
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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pet',
                        'name: Dog.otherField',
                        'Cat.name']
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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def normalizedFieldToMergedField = tree.getNormalizedFieldToMergedField()
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
        List<MergedField> result = new ArrayList<>()
        when:
        traverser.traverse(tree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField normalizedField = context.thisNode()
                result.add(normalizedFieldToMergedField[normalizedField])
                return TraversalControl.CONTINUE
            }
        })

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


        when:
        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
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


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Mutation.createAnimal: Query',
                        '--Query.animal: Animal',
                        '---[Bird, Cat, Dog].name: String',
                        '---otherName: [Bird, Cat, Dog].name: String',
                        '---Cat.friends: [Friend]',
                        '----Friend.isCatOwner: Boolean',
                        '---Bird.friends: [Friend]',
                        '----Friend.isBirdOwner: Boolean',
                        '----Friend.name: String']
    }

    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()
        def ei = ExecutionInput.newExecutionInput(query).variables(variables).build()
        assert graphQL.execute(ei).errors.size() == 0
    }

    def "normalized arguments"() {
        given:
        String schema = """
        type Query{ 
            dog(id:ID): Dog 
        }
        type Dog {
            name: String
            search(arg1: Input1, arg2: Input1, arg3: Input1): Boolean
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

        def variables = [
                var1: [bar: 123],
                var2: [foo: "foo", input2: [bar: 123]]
        ]
        // the normalized arg value should be the same regardless of how the value was provided
        def expectedNormalizedArgValue = [foo: new NormalizedInputValue("String", parseValue('"foo"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("123"))])]
        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.of(variables))
        def topLevelField = tree.getTopLevelFields().get(0)
        def secondField = topLevelField.getChildren().get(0)
        def arg1 = secondField.getNormalizedArgument("arg1")
        def arg2 = secondField.getNormalizedArgument("arg2")
        def arg3 = secondField.getNormalizedArgument("arg3")

        then:
        topLevelField.getNormalizedArgument("id").getTypeName() == "ID"
        printAst(topLevelField.getNormalizedArgument("id").getValue()) == '"123"'

        arg1.getTypeName() == "Input1"
        arg1.getValue() == expectedNormalizedArgValue
        arg2.getTypeName() == "Input1"
        arg2.value == expectedNormalizedArgValue
        arg3.getTypeName() == "Input1"
        arg3.value == expectedNormalizedArgValue
    }

    def "arguments with absent variable values inside input objects"() {
        given:
        def schema = """
        type Query {
            hello(arg: Arg, otherArg: String = "otherValue"): String
        }
        input Arg {
            ids: [ID] = ["defaultId"]
        }
        """
        def graphQLSchema = TestUtil.schema(schema)

        def query = """
        query myQuery(\$varIds: [ID], \$otherVar: String) {
            hello(arg: {ids: \$varIds}, otherArg: \$otherVar)
        }
        """

        assertValidQuery(graphQLSchema, query)
        def document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.emptyVariables())

        then:
        def topLevelField = tree.getTopLevelFields().get(0)

        def arg = topLevelField.getNormalizedArgument("arg")
        arg == new NormalizedInputValue("Arg", [:])
        !topLevelField.normalizedArguments.containsKey("otherArg")

        topLevelField.resolvedArguments.get("arg") == [ids: ["defaultId"]]
        topLevelField.resolvedArguments.get("otherArg") == "otherValue"
    }

    def "arguments with null variable values"() {
        given:
        def schema = """
        type Query {
            hello(arg: Arg, otherArg: String = "otherValue"): String
        }
        input Arg {
            ids: [ID] = ["defaultId"]
        }
        """
        def graphQLSchema = TestUtil.schema(schema)

        def query = """
            query nadel_2_MyService_myQuery(\$varIds: [ID], \$otherVar: String) {
               hello(arg: {ids: \$varIds}, otherArg: \$otherVar)
            }
        """

        assertValidQuery(graphQLSchema, query)
        def document = TestUtil.parseQuery(query)

        def variables = [
                varIds  : null,
                otherVar: null,
        ]
        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.of(variables))

        then:
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg = topLevelField.getNormalizedArgument("arg")
        def otherArg = topLevelField.getNormalizedArgument("otherArg")

        arg == new NormalizedInputValue(
                "Arg",
                [
                        ids: new NormalizedInputValue(
                                "[ID]",
                                null,
                        ),
                ]
        )
        otherArg == new NormalizedInputValue("String", null)

        topLevelField.resolvedArguments.get("arg") == [ids: null]
        topLevelField.resolvedArguments.get("otherArg") == null
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

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.of(variables))
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg1 = topLevelField.getNormalizedArgument("arg1")
        def arg2 = topLevelField.getNormalizedArgument("arg2")
        def arg3 = topLevelField.getNormalizedArgument("arg3")

        then:
        arg1.typeName == "[ID!]"
        arg1.value.collect { printAst(it) } == ['"1"', '"2"']
        arg2.typeName == "[[Input1]]"
        arg2.value == [[
                               [foo: new NormalizedInputValue("String", parseValue('"foo1"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("123"))])],
                               [foo: new NormalizedInputValue("String", parseValue('"foo2"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("456"))])]
                       ]]

        arg3.getTypeName() == "[Input1]"
        arg3.value == [
                [foo: new NormalizedInputValue("String", parseValue('"foo3"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("789"))])],
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

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.of(variables))
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg1 = topLevelField.getNormalizedArgument("arg1")
        def arg2 = topLevelField.getNormalizedArgument("arg2")

        then:
        arg1.typeName == "[[Input1]]"
        arg1.value == [[
                               [foo: new NormalizedInputValue("String", parseValue('"foo1"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("123"))])],
                       ]]
        arg2.typeName == "[[ID!]!]"
        arg2.value.collect { outer -> outer.collect { printAst(it) } } == [['"1"'], ['"2"']]
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
            {foo{field{id}}foo{field{id}}}
        '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.emptyVariables())

        then:
        tree.normalizedFieldToMergedField.size() == 3
        tree.fieldToNormalizedField.size() == 6
        println String.join("\n", printTree(tree))
        /**
         * NF{Query.foo} -> NF{"O1...O5".field,} -> NF{O1...O5.id}*/
    }

    def "diverged fields"() {
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
            catValue: Int
            catFriend(arg: String): CatFriend
        }
        type CatFriend {
          catFriendName: String
        }
        type Dog implements Pet {
             name: String
             dogValue: Float
             dogFriend: DogFriend
        }
        type DogFriend {
           dogFriendName: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
          {pets {
                ... on Cat {
                  friend: catFriend(arg: "hello") {
                    catFriendName
              }}
                ... on Cat {
                  friend: catFriend(arg: "hello") {
                    catFriendName
              }}
                ... on Dog {
                  friend: dogFriend {
                    dogFriendName
              }}
          }}
        '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        then:
        // the two friend fields are not in on ENF
        printedTree == ['-Query.pets: Pet',
                        '--friend: Cat.catFriend: CatFriend',
                        '---CatFriend.catFriendName: String',
                        '--friend: Dog.dogFriend: DogFriend',
                        '---DogFriend.dogFriendName: String']

        tree.normalizedFieldToMergedField.size() == 5
        tree.fieldToNormalizedField.size() == 7
    }

    def "diverged fields 2"() {
        given:
        String schema = """
        type Query {
          pets: Pet
        }
        interface Pet {
          name(arg:String): String
        }
        type Cat implements Pet {
            name(arg: String): String
        }
        
        type Dog implements Pet {
             name(arg: String): String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
          {pets {
                ... on Cat {
                    name(arg: "foo")
              }
                ... on Dog {
                    name(arg: "fooOther")
              }
          }}
        '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        then:
        /**
         * the two name fields are not merged, because they are backed by different fields with different arguments
         * If the arguments are the same, it would be one ENF.
         */
        printedTree == ['-Query.pets: Pet',
                        '--Cat.name: String',
                        '--Dog.name: String'
        ]
    }


    def "diverging fields with the same parent type on deeper level"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name: String
          dogBreed: String
         breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
         breed: String
          name : String
          friends: [Pet]

        }
        ''')
        /**
         * Here F1 and F2 are allowed to diverge (backed by different field definitions) because the parent fields have
         * different concrete parent: P1 has Dog, P2 has Cat.
         */
        def query = '''
        {
          pets {
            ... on Dog {
               friends { #P1
                 name
                 ... on Dog {
                    breed: dogBreed #F1
                 }
               }
            }
            ... on Cat {
             friends {  #P2
                catFriendsName: name
                ... on Dog {
                  breed #F2
                }
               }
            }
            ... on Pet {
              friends { 
                name
               }
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [Pet]',
                        '--Dog.friends: [Pet]',
                        '---[Cat, Dog].name: String',
                        '---breed: Dog.dogBreed: String',
                        '--Cat.friends: [Pet]',
                        '---catFriendsName: [Cat, Dog].name: String',
                        '---Dog.breed: String',
                        '---[Cat, Dog].name: String'
        ]
    }

    def "subselection different with different concrete parents"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name: String
          dogBreed: String
         breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
         breed: String
          name : String
          friends: [Pet]

        }
        ''')
        def query = '''
        {
          pets {
            ... on Dog {
               friends { #P1
                 name
               }
            }
            ... on Cat {
             friends {  
                otherName: name
               }
            }
            friends {
                breed
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [Pet]',
                        '--Dog.friends: [Pet]',
                        '---[Cat, Dog].name: String',
                        '---[Cat, Dog].breed: String',
                        '--Cat.friends: [Pet]',
                        '---otherName: [Cat, Dog].name: String',
                        '---[Cat, Dog].breed: String',
        ]
    }


    def "diverging non-composite fields"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name: String
          dogBreed: String
          breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
         breed: String
          name : String
          friends: [Pet]

        }
        ''')
        def query = '''
        {
          pets {
            ... on Dog {
                breed: dogBreed
            }
            ... on Cat {
                breed: catBreed
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [Pet]',
                        '--breed: Dog.dogBreed: String',
                        '--breed: Cat.catBreed: String'
        ]
    }

    def "different children for different Interfaces"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        
        interface Mammal implements Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Turtle implements Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Mammal & Pet {
          name: String
          dogBreed: String
          breed: String
          friends: [Pet]
        }
        type Cat implements Mammal & Pet {
          catBreed: String
         breed: String
          name : String
          friends: [Pet]
        }
        ''')
        def query = '''
        {
          pets {
          ... on Cat { 
              friends {
               catFriendName: name  
              }
            }
            ... on Dog { 
              friends {
               dogFriendName: name  
              }
            }
            ... on Mammal {
                friends {
                    name
                }
            }
            ... on Pet {
                friends {
                    breed
                }
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [Pet]',
                        '--Cat.friends: [Pet]',
                        '---catFriendName: [Cat, Dog, Turtle].name: String',
                        '---[Cat, Dog, Turtle].name: String',
                        '---[Cat, Dog, Turtle].breed: String',
                        '--Dog.friends: [Pet]',
                        '---dogFriendName: [Cat, Dog, Turtle].name: String',
                        '---[Cat, Dog, Turtle].name: String',
                        '---[Cat, Dog, Turtle].breed: String',
                        '--Turtle.friends: [Pet]',
                        '---[Cat, Dog, Turtle].breed: String'
        ]
    }

    def "diverging fields with Union as parent type"() {
        given:
        def schema = schema('''
        type Query {
         pets: [DogOrCat]
        }
        type Dog {
          name: String
          dogBreed: String
          breed: String
          friends: [DogOrCat]
        }
        type Cat {
          catBreed: String
          breed: String
          name : String
          friends: [DogOrCat]
        }
        union DogOrCat = Dog | Cat
        ''')
        def query = '''
        {
          pets {
            ... on Dog {
               friends { #P1
                 ... on Dog {
                    breed: dogBreed #F1
                 }
               }
            }
            ... on Cat {
             friends {  #P2
                ... on Dog {
                  breed #F2
                }
               }
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [DogOrCat]',
                        '--Dog.friends: [DogOrCat]',
                        '---breed: Dog.dogBreed: String',
                        '--Cat.friends: [DogOrCat]',
                        '---Dog.breed: String'
        ]
    }

    def "union fields are not merged"() {
        given:
        def schema = schema('''
        type Query {
         pets: [DogOrCat]
        }
        type Dog {
          name: String
        }
        type Cat {
          name: String
        }
        union DogOrCat = Dog | Cat
        ''')
        def query = '''
        {
          pets {
            ... on Dog {
                name
            }
            ... on Cat {
                name
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [DogOrCat]',
                        '--Dog.name: String',
                        '--Cat.name: String'
        ]
    }

    def "union fields which are shared in an interface are merged"() {
        given:
        def schema = schema('''
        type Query {
         pets: [DogOrCat]
        }
        interface Pet {
            name: String
        }  
        type Dog implements Pet{
          name: String
        }
        type Cat implements Pet{
          name: String
        }
        union DogOrCat = Dog | Cat
        ''')
        def query = '''
        {
          pets {
            ... on Dog {
                name
            }
            ... on Cat {
                name
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [DogOrCat]',
                        '--[Dog, Cat].name: String'
        ]
    }

    def "fields which don't come from a shared interface are not merged"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
            name: String
        }
        type Dog implements Pet{
          name: String
          breed: String
        }
        
        type Cat implements Pet{
          name: String
          breed: String
        }
        ''')
        def query = '''
        {
          pets {
            ... on Dog {
                breed
            }
            ... on Cat {
                breed
            }
        }}
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [Pet]',
                        '--Dog.breed: String',
                        '--Cat.breed: String'
        ]
    }

    def "fields are merged together on multiple level"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name: String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name: String
          dogBreed: String
          breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
         breed: String
          name : String
          friends: [Pet]

        }

        ''')
        def query = '''
        {
          pets {
            ... on Dog {
               friends { 
                 ... on Dog {
                    friends {
                       name 
                    }
                 }
                 ... on Cat {
                    friends {
                       name 
                    }
                 }
               }
            }
            ... on Cat {
             friends {  
                 ... on Dog {
                    friends {
                       name 
                    }
                 }
                 ... on Cat {
                    friends {
                       name 
                    }
                 }
               }
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [Pet]',
                        '--[Dog, Cat].friends: [Pet]',
                        '---[Dog, Cat].friends: [Pet]',
                        '----[Cat, Dog].name: String'
        ]
    }

    def "fields are not merged together because of different arguments on deeper level"() {
        given:
        def schema = schema('''
        type Query {
         pets: [Pet]
        }
        interface Pet {
         name(arg:String): String
         breed: String
         friends: [Pet]
        }
        type Dog implements Pet {
          name(arg:String): String
          dogBreed: String
          breed: String
          friends: [Pet]

        }
        type Cat implements Pet {
          catBreed: String
         breed: String
          name(arg:String) : String
          friends: [Pet]

        }

        ''')
        def query = '''
        {
          pets {
            ... on Dog {
               friends { 
                 ... on Dog {
                    friends {
                       name 
                    }
                 }
                 ... on Cat {
                    friends {
                       name 
                    }
                 }
               }
            }
            ... on Cat {
             friends {  
                 ... on Dog {
                    friends {
                       name 
                    }
                 }
                 ... on Cat {
                    friends {
                       name(arg: "not-be-merged")
                    }
                 }
               }
            }
          }
        }
        '''
        assertValidQuery(schema, query)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, schema)

        then:
        printedTree == ['-Query.pets: [Pet]',
                        '--Dog.friends: [Pet]',
                        '---[Dog, Cat].friends: [Pet]',
                        '----[Cat, Dog].name: String',
                        '--Cat.friends: [Pet]',
                        '---Dog.friends: [Pet]',
                        '----[Cat, Dog].name: String',
                        '---Cat.friends: [Pet]',
                        '----[Cat, Dog].name: String'
        ]
    }


    def "skip/include is respected"() {
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
          query($true: Boolean!,$false: Boolean!){pets {
                ... on Cat {
                    cat_not: name @skip(if:true)
                    cat_not: name @skip(if:$true)
                    cat_yes_1: name @include(if:true)
                    cat_yes_2: name @skip(if:$false)
              }
                ... on Dog @include(if:$true) {
                    dog_no: name @include(if:false)
                    dog_no: name @include(if:$false)
                    dog_yes_1: name @include(if:$true)
                    dog_yes_2: name @skip(if:$false)
              }
              ... on Pet @skip(if:$true) {
                    not: name
              }
              ... on Pet @skip(if:$false) {
                    pet_name: name
              }
          }}
        '''
        def variables = ["true": Boolean.TRUE, "false": Boolean.FALSE]
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.of(variables))
        println String.join("\n", printTree(tree))
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


    def "query directives are captured is respected"() {
        given:
        String schema = """
        directive @fieldDirective(target : String!) on FIELD
        directive @fieldXDirective(target : String!) on FIELD
        
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
          query q {
              pets {
                ... on Cat {
                    cName : name @fieldDirective(target : "Cat.name")
              }
                ... on Dog {
                    dName : name @fieldDirective(target : "Dog.name") @fieldXDirective(target : "Dog.name")
              }
              ... on Pet {
                    pName : name @fieldDirective(target : "Pet.name")
              }
          }}
        '''

        def variables = [:]
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)

        when:
        def tree = localCreateExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.of(variables))
        def printedTree = printTreeAndDirectives(tree)

        then:
        printedTree == ['Query.pets',
                        'cName: Cat.name  @fieldDirective(target : "Cat.name")',
                        'dName: Dog.name  @fieldDirective(target : "Dog.name")  @fieldXDirective(target : "Dog.name")',
                        'pName: [Cat, Dog].name  @fieldDirective(target : "Pet.name")',
        ]
    }

    def "missing argument"() {
        given:
        String schema = """
        type Query {
            hello(arg: String): String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''{hello} '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def tree = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, RawVariables.emptyVariables())
        println String.join("\n", printTree(tree))
        def printedTree = printTree(tree)


        then:
        printedTree == ['Query.hello']
        tree.getTopLevelFields().get(0).getNormalizedArguments().isEmpty()
    }

    def "reused field via fragments"() {
        String schema = """
        type Query {
          pet: Pet
        }
        type Pet {
          owner: Person
          emergencyContact: Person
        }
        type Person {
          name: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
{ pet {
  owner { ...personName }
  emergencyContact { ...personName }
}}
fragment personName on Person {
  name
}
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == ['-Query.pet: Pet',
                        '--Pet.owner: Person',
                        '---Person.name: String',
                        '--Pet.emergencyContact: Person',
                        '---Person.name: String'
        ]

    }


    def "test interface fields with three different output types (covariance) on the implementations"() {
        def graphQLSchema = schema("""
        interface Animal {
            parent: Animal
            name: String
        }
        type Cat implements Animal {
            name: String
            parent: Cat
        }
        type Dog implements Animal {
            name: String
            parent: Dog
            isGoodBoy: Boolean
        }
        type Bird implements Animal {
            name: String
            parent: Bird
        }
        type Query {
            animal: Animal
        }
        """)

        def query = """
        {
            animal {
                parent {
                    name
                }
            }
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == [
                "-Query.animal: Animal",
                "--[Bird, Cat, Dog].parent: Bird, Cat, Dog",
                "---[Bird, Cat, Dog].name: String",
        ]
    }

    def "covariants with union fields"() {
        def graphQLSchema = schema("""
        type Query {
            animal: Animal
        }
        interface Animal {
            parent: DogOrCat
            name: String
        }
        type Cat implements Animal {
            name: String
            parent: Cat
        }
        type Dog implements Animal {
            name: String
            parent: Dog
            isGoodBoy: Boolean
        }
        union DogOrCat = Dog | Cat
        """)

        def query = """
        {
            animal {
                parent {
                  __typename
                }
            }
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)


        def tree = localCreateExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def printedTree = printTreeWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree == [
                "-Query.animal: Animal",
                "--[Cat, Dog].parent: Cat, Dog",
                "---[Cat, Dog].__typename: String!",
        ]
    }

    def "query cannot exceed max depth"() {
        String schema = """
        type Query {
            animal: Animal
        }
        interface Animal {
            name: String
            friends: [Animal]
        }
        type Bird implements Animal {
            name: String 
            friends: [Animal]
        }
        type Cat implements Animal {
            name: String 
            friends: [Animal]
            breed: String 
        }
        type Dog implements Animal {
            name: String 
            breed: String
            friends: [Animal]
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        // We generate two less fields than the given depth
        // One is due to the top level field
        // One is due to the leaf selection
        def animalSubselection = IntStream.rangeClosed(1, queryDepth - 2)
                .mapToObj {
                    ""
                }
                .reduce("CHILD") { acc, value ->
                    acc.replace("CHILD", "friends { CHILD }")
                }
                .replace("CHILD", "name")

        // Note: there is a total of 51 fields here
        String query = """
        {
            animal {
                $animalSubselection
            }
        }        
        """

        def limit = 50

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        when:
        Exception exception
        try {
            ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                    graphQLSchema,
                    document,
                    null,
                    RawVariables.emptyVariables(),
                    ExecutableNormalizedOperationFactory.Options.defaultOptions().maxChildrenDepth(limit))
        } catch (Exception e) {
            exception = e
        }

        then:
        if (queryDepth > limit) {
            assert exception != null
            assert exception.message.contains("depth exceeded")
            assert exception.message.contains("> 50")
        } else {
            assert exception == null
        }

        where:
        _ | queryDepth
        _ | 49
        _ | 50
        _ | 51
    }

    def "big query is fine as long as depth is under limit"() {
        String schema = """
        type Query {
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
        type Cat implements Animal {
            name: String 
            friends: [Friend]
            breed: String 
        }
        type Dog implements Animal {
            name: String 
            breed: String
            friends: [Friend]
        }
        """

        def garbageFields = IntStream.range(0, 1000)
                .mapToObj {
                    """test_$it: friends { name }"""
                }
                .collect(Collectors.joining("\n"))

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            animal {
                name
                otherName: name
                ... on Animal {
                    name
                }
                ... on Cat {
                    name
                    friends {
                        ... on Friend {
                            isCatOwner
                            pets {
                                ... on Dog {
                                    name
                                }
                            }
                        }
                    }
                }
                ... on Bird {
                    friends {
                        isBirdOwner
                    }
                    friends {
                        name
                        pets {
                            ... on Cat {
                                breed
                            }
                        }
                    }
                }
                ... on Dog {
                    name
                }
                $garbageFields
            }
        }        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        when:
        def result = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables(),
                ExecutableNormalizedOperationFactory.Options.defaultOptions().maxChildrenDepth(5))

        then:
        noExceptionThrown()
    }

    def "big query exceeding fields count"() {
        String schema = """
        type Query {
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
        type Cat implements Animal {
            name: String 
            friends: [Friend]
            breed: String 
        }
        type Dog implements Animal {
            name: String 
            breed: String
            friends: [Friend]
        }
        """

        def garbageFields = IntStream.range(0, 1000)
                .mapToObj {
                    """test_$it: friends { name }"""
                }
                .collect(Collectors.joining("\n"))

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            animal {
                name
                otherName: name
                ... on Animal {
                    name
                }
                ... on Cat {
                    name
                    friends {
                        ... on Friend {
                            isCatOwner
                            pets {
                                ... on Dog {
                                    name
                                }
                            }
                        }
                    }
                }
                ... on Bird {
                    friends {
                        isBirdOwner
                    }
                    friends {
                        name
                        pets {
                            ... on Cat {
                                breed
                            }
                        }
                    }
                }
                ... on Dog {
                    name
                }
                $garbageFields
            }
        }        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        when:
        def result = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables(),
                ExecutableNormalizedOperationFactory.Options.defaultOptions().maxFieldsCount(2013))

        then:
        def e = thrown(AbortExecutionException)
        e.message == "Maximum field count exceeded. 2014 > 2013"
    }

    def "small query exceeding fields count"() {
        String schema = """
        type Query {
            hello: String
        }
        """

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """ {hello a1: hello}"""

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        when:
        def result = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables(),
                ExecutableNormalizedOperationFactory.Options.defaultOptions().maxFieldsCount(1))

        then:
        def e = thrown(AbortExecutionException)
        e.message == "Maximum field count exceeded. 2 > 1"


    }

    def "query not exceeding fields count"() {
        String schema = """
        type Query {
            dogs: [Dog]
        }
        type Dog {
            name: String
            breed: String
        }
        """

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """ {dogs{name breed }}"""

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        when:
        def result = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables(),
                ExecutableNormalizedOperationFactory.Options.defaultOptions().maxFieldsCount(3))

        then:
        notThrown(AbortExecutionException)


    }

    def "query with meta fields exceeding fields count"() {
        String schema = """
        type Query {
            hello: String
        }
        """

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = IntrospectionQuery.INTROSPECTION_QUERY

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        when:
        def result = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables(),
                ExecutableNormalizedOperationFactory.Options.defaultOptions().maxFieldsCount(188))
        println result.normalizedFieldToMergedField.size()

        then:
        def e = thrown(AbortExecutionException)
        e.message == "Maximum field count exceeded. 189 > 188"
    }

    def "can capture depth and field count"() {
        String schema = """
        type Query {
            foo: Foo
        }
        
        type Foo {
            stop : String
            bar : Bar
        }
        
        type Bar {
            stop : String
            foo : Foo
        }
        """

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = "{ foo { bar { foo { bar { foo { stop bar { stop }}}}}}}"

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        when:
        def result = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables()
        )

        then:
        result.getOperationDepth() == 7
        result.getOperationFieldCount() == 8
    }

    def "factory has a default max node count"() {
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            foo: Foo
            name: String
        }
        """

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = "{ foo { ...F1}} "
        int fragmentCount = 12
        for (int i = 1; i < fragmentCount; i++) {
            query += """
             fragment F$i on Foo {
                foo { ...F${i + 1} }
                a: foo{ ...F${i + 1} }
                b: foo{ ...F${i + 1} }
             }
            """
        }
        query += """
        fragment F$fragmentCount on Foo{
            name
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        when:
        def result = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables()
        )
        then:
        def e = thrown(AbortExecutionException)
        e.message == "Maximum field count exceeded. 100001 > 100000"
    }

    def "default max fields can be changed "() {
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            foo: Foo
            name: String
        }
        """

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = "{foo{foo{name}}} "

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory.Options.setDefaultOptions(ExecutableNormalizedOperationFactory.Options.defaultOptions().maxFieldsCount(2))

        when:
        def result = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables()
        )
        then:
        def e = thrown(AbortExecutionException)
        e.message == "Maximum field count exceeded. 3 > 2"
        cleanup:
        ExecutableNormalizedOperationFactory.Options.setDefaultOptions(ExecutableNormalizedOperationFactory.Options.defaultOptions().maxFieldsCount(ExecutableNormalizedOperationFactory.Options.DEFAULT_MAX_FIELDS_COUNT))
    }

    def "fragments used multiple times and directives on it"() {
        String schema = """
        type Query {
            foo: String
        }
        """

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = "{...F1 ...F1 } fragment F1 on Query { foo @include(if: true) } "

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        when:
        def operation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables()
        )
        def rootField = operation.topLevelFields[0]
        def directives = operation.getQueryDirectives(rootField)
        def byName = directives.getImmediateDirectivesByName();
        then:
        byName.size() == 1
        byName["include"].size() == 1
        byName["include"][0] instanceof GraphQLDirective
    }

    def "fragments used multiple times and directives on it deeper"() {
        String schema = """
        type Query {
            foo: Foo
        }
        type Foo {
            hello: String
        }
        """

        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = "{foo{...F1  ...F1 } } fragment F1 on Foo { hello @include(if: true) hello @include(if:true) } "

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        when:
        def operation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                null,
                RawVariables.emptyVariables()
        )
        def enf = operation.topLevelFields[0].children[0]
        def directives = operation.getQueryDirectives(enf)
        def byName = directives.getImmediateDirectivesByName();
        then:
        byName.size() == 1
        byName["include"].size() == 2
        byName["include"][0] instanceof GraphQLDirective
        byName["include"][1] instanceof GraphQLDirective
        byName["include"][0] != byName["include"][1]
    }


    private static ExecutableNormalizedOperation localCreateExecutableNormalizedOperation(
            GraphQLSchema graphQLSchema,
            Document document,
            String operationName,
            CoercedVariables coercedVariableValues
    ) {

        def options = ExecutableNormalizedOperationFactory.Options.defaultOptions().deferSupport(deferSupport)

        return ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(graphQLSchema, document, operationName, coercedVariableValues, options)
    }

    private static ExecutableNormalizedOperation localCreateExecutableNormalizedOperationWithRawVariables(
            GraphQLSchema graphQLSchema,
            Document document,
            String operationName,
            RawVariables rawVariables
    ) {

        def options = ExecutableNormalizedOperationFactory.Options.defaultOptions().deferSupport(deferSupport)

        return ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(
                graphQLSchema,
                document,
                operationName,
                rawVariables,
                options
        )
    }
}

class ExecutableNormalizedOperationFactoryTestWithDeferSupport extends ExecutableNormalizedOperationFactoryTest {
    static {
        deferSupport = true
    }
}

class ExecutableNormalizedOperationFactoryTestNoDeferSupport extends ExecutableNormalizedOperationFactoryTest {
    static {
        deferSupport = false
    }
}

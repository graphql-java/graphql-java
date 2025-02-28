package graphql.normalized.nf

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.Document
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import spock.lang.Specification

class NormalizedDocumentFactoryTest extends Specification {

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
        def tree = NormalizedDocumentFactory.createNormalizedDocument(graphQLSchema, document)
        def printedTree = printDocumentWithLevelInfo(tree, graphQLSchema)

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

    def "document with skip/include with variables"() {
        String schema = """
        type Query{ 
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
        query ($skip: Boolean!, $include: Boolean!) {
            foo {
               name
               bar @skip(if: $skip)  {
                    baz @include(if: $include)
                }
            }
        }
        '''


        assertValidQuery(graphQLSchema, query, [skip: false, include: true])

        Document document = TestUtil.parseQuery(query)
        def tree = NormalizedDocumentFactory.createNormalizedDocument(graphQLSchema, document)
        def printedTree = printDocumentWithLevelInfo(tree, graphQLSchema)

        expect:
        printedTree.join("\n") == '''variables: [skip:false, include:false]
-Query.foo: Foo
--Foo.name: String
--Foo.bar: Bar
variables: [skip:true, include:false]
-Query.foo: Foo
--Foo.name: String
variables: [skip:false, include:true]
-Query.foo: Foo
--Foo.name: String
--Foo.bar: Bar
---Bar.baz: String
variables: [skip:true, include:true]
-Query.foo: Foo
--Foo.name: String'''
    }

    def "document with custom directives"() {
        String schema = """
        directive @cache(time: Int!) on FIELD
        type Query{ 
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
        query {
            foo {
               name
               bar @cache(time:100) {
                    baz 
                }
                bar @cache(time:200) {
                    baz 
                }

            }
        }
        '''


        assertValidQuery(graphQLSchema, query, [skip: false, include: true])

        Document document = TestUtil.parseQuery(query)
        def normalizedDocument = NormalizedDocumentFactory.createNormalizedDocument(graphQLSchema, document)
        def rootField = normalizedDocument.getSingleNormalizedOperation().getRootFields().get(0)
        def bar = rootField.getChildren().get(1)

        expect:
        bar.getAstDirectives().size() == 2
    }


    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()
        def ei = ExecutionInput.newExecutionInput(query).variables(variables).build()
        assert graphQL.execute(ei).errors.size() == 0
    }

    static List<String> printDocumentWithLevelInfo(NormalizedDocument normalizedDocument, GraphQLSchema schema) {
        def result = []
        for (NormalizedDocument.NormalizedOperationWithAssumedSkipIncludeVariables normalizedOperationWithAssumedSkipIncludeVariables : normalizedDocument.normalizedOperations) {
            NormalizedOperation normalizedOperation = normalizedOperationWithAssumedSkipIncludeVariables.normalizedOperation;
            if (normalizedOperationWithAssumedSkipIncludeVariables.assumedSkipIncludeVariables != null) {
                result << "variables: " + normalizedOperationWithAssumedSkipIncludeVariables.assumedSkipIncludeVariables
            }
            Traverser<NormalizedField> traverser = Traverser.depthFirst({ it.getChildren() })
            traverser.traverse(normalizedOperation.getRootFields(), new TraverserVisitorStub<NormalizedField>() {
                @Override
                TraversalControl enter(TraverserContext<NormalizedField> context) {
                    NormalizedField normalizedField = context.thisNode()
                    String prefix = ""
                    for (int i = 1; i <= normalizedField.getLevel(); i++) {
                        prefix += "-"
                    }

                    def possibleOutputTypes = new LinkedHashSet<String>()
                    for (fieldDef in normalizedField.getFieldDefinitions(schema)) {
                        possibleOutputTypes.add(GraphQLTypeUtil.simplePrint(fieldDef.type))
                    }

                    result << (prefix + normalizedField.printDetails() + ": " + possibleOutputTypes.join(", "))
                    return TraversalControl.CONTINUE
                }
            })
        }
        result
    }


}

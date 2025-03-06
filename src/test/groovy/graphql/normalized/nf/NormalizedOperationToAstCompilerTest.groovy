package graphql.normalized.nf

import graphql.GraphQL
import graphql.TestUtil
import graphql.language.AstPrinter
import graphql.language.AstSorter
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput

class NormalizedOperationToAstCompilerTest extends Specification {


    def "test pet interfaces"() {
        String sdl = """
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
           mood: String 
        }

        type Dog implements Animal {
           name: String 
           breed: String
           friends: [Friend]
        }
        """

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
                    mood
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
                    breed
                }
            }
        }
        """
        GraphQLSchema schema = TestUtil.schema(sdl)
        assertValidQuery(schema, query)
        def normalizedDocument = NormalizedDocumentFactory.createNormalizedDocument(schema, Parser.parse(query))
        def normalizedOperation = normalizedDocument.getSingleNormalizedOperation()
        when:
        def result = NormalizedOperationToAstCompiler.compileToDocument(schema, normalizedOperation)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  animal {
    name
    otherName: name
    ... on Bird {
      friends {
        isBirdOwner
        name
        pets {
          ... on Cat {
            breed
          }
        }
      }
    }
    ... on Cat {
      friends {
        isCatOwner
        pets {
          ... on Dog {
            name
          }
        }
      }
      mood
    }
    ... on Dog {
      breed
    }
  }
}
'''
    }

    def "print custom directives"() {
        String sdl = """
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

        GraphQLSchema schema = TestUtil.schema(sdl)
        assertValidQuery(schema, query)
        def normalizedDocument = NormalizedDocumentFactory.createNormalizedDocument(schema, Parser.parse(query))
        def normalizedOperation = normalizedDocument.getSingleNormalizedOperation()
        when:
        def result = NormalizedOperationToAstCompiler.compileToDocument(schema, normalizedOperation)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  foo {
    bar @cache(time: 100) @cache(time: 200) {
      baz
    }
    name
  }
}
'''
    }


    def "print one root field"() {
        def sdl = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        """
        def query = '''
        { foo { bar } }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        assertValidQuery(schema, query)
        def normalizedDocument = NormalizedDocumentFactory.createNormalizedDocument(schema, Parser.parse(query))
        def normalizedOperation = normalizedDocument.getSingleNormalizedOperation()
        def rootField = normalizedOperation.getRootFields().get(0)
        when:
        def result = NormalizedOperationToAstCompiler.compileToDocument(schema, schema.getObjectType("Query"), rootField, "myOperation", OperationDefinition.Operation.QUERY)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''query myOperation {
  foo {
    bar
  }
}
'''
    }

    def "print list of root fields"() {
        def sdl = """
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        """
        def query = '''
        { foo { bar } foo2: foo { bar } }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        assertValidQuery(schema, query)
        def normalizedDocument = NormalizedDocumentFactory.createNormalizedDocument(schema, Parser.parse(query))
        def normalizedOperation = normalizedDocument.getSingleNormalizedOperation()
        def rootFields = normalizedOperation.getRootFields()
        when:
        def result = NormalizedOperationToAstCompiler.compileToDocument(schema, schema.getObjectType("Query"), rootFields, "myOperation", OperationDefinition.Operation.QUERY)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''query myOperation {
  foo {
    bar
  }
  foo2: foo {
    bar
  }
}
'''
    }


    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()
        assert graphQL.execute(newExecutionInput().query(query).variables(variables)).errors.isEmpty()
    }


}

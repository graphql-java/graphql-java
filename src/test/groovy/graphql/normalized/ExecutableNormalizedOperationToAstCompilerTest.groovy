package graphql.normalized

import graphql.GraphQL
import graphql.TestUtil
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.language.OperationDefinition.Operation.MUTATION
import static graphql.language.OperationDefinition.Operation.QUERY
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION
import static graphql.normalized.ExecutableNormalizedOperationToAstCompiler.compileToDocument

class ExecutableNormalizedOperationToAstCompilerTest extends Specification {
    def "test"() {
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
            }
        }
        """
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(QUERY, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  ... on Query {
    animal {
      ... on Bird {
        name
      }
      ... on Cat {
        name
      }
      ... on Dog {
        name
      }
      ... on Bird {
        otherName: name
      }
      ... on Cat {
        otherName: name
      }
      ... on Dog {
        otherName: name
      }
      ... on Cat {
        friends {
          ... on Friend {
            isCatOwner
          }
          ... on Friend {
            pets {
              ... on Dog {
                name
              }
              ... on Cat {
                breed
              }
            }
          }
          ... on Friend {
            isBirdOwner
          }
          ... on Friend {
            name
          }
        }
      }
      ... on Bird {
        friends {
          ... on Friend {
            isCatOwner
          }
          ... on Friend {
            pets {
              ... on Dog {
                name
              }
              ... on Cat {
                breed
              }
            }
          }
          ... on Friend {
            isBirdOwner
          }
          ... on Friend {
            name
          }
        }
      }
    }
  }
}
'''

    }

    def "test arguments"() {
        def sdl = '''
        type Query {
            foo1(arg: String): String
            foo2(a:Int ,b: Boolean, c: Float): String
        }
        '''
        def query = ''' {
            foo1(arg: "hello")
            foo2(a: 123,b: true, c: 123.45)
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(QUERY, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  ... on Query {
    foo1(arg: "hello")
  }
  ... on Query {
    foo2(a: 123, b: true, c: 123.45)
  }
}
'''
    }

    def "test input object arguments"() {
        def sdl = '''
        type Query {
            foo1(arg: I): String
        }
        input I {
            arg1: String
            arg2: Int
            arg3: ID
            arg4: Boolean
            arg5: Float
            nested: I
        }
        '''
        def query = '''{
            foo1(arg: {
             arg1: "Hello"
             arg2: 123
             arg3: "IDID"
             arg4: false
             arg5: 123.123
             nested: {
                 arg1: "Hello2"
                 arg2: 1234
                 arg3: "IDID1"
                 arg4: null
                 arg5: null
             }
            })
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(QUERY, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  ... on Query {
    foo1(arg: {arg1 : "Hello", arg2 : 123, arg3 : "IDID", arg4 : false, arg5 : 123.123, nested : {arg1 : "Hello2", arg2 : 1234, arg3 : "IDID1", arg4 : null, arg5 : null}})
  }
}
'''
    }

    def "test mutations"() {
        def sdl = '''
        type Query {
            foo1(arg: I): String
        }
        type Mutation {
            foo1(arg: I): String
        }
        input I {
            arg1: String
        }
        '''
        def query = '''mutation {
            foo1(arg: {
             arg1: "Mutation"
            })
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(MUTATION, fields)
        then:
        AstPrinter.printAst(document) == '''mutation {
  ... on Mutation {
    foo1(arg: {arg1 : "Mutation"})
  }
}
'''
    }

    def "test subscriptions"() {
        def sdl = '''
        type Query {
            foo1(arg: I): String
        }
        type Subscription {
            foo1(arg: I): String
        }
        input I {
            arg1: String
        }
        '''
        def query = '''subscription {
            foo1(arg: {
             arg1: "Subscription"
            })
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(SUBSCRIPTION, fields)
        then:
        AstPrinter.printAst(document) == '''subscription {
  ... on Subscription {
    foo1(arg: {arg1 : "Subscription"})
  }
}
'''
    }

    private List<ExecutableNormalizedField> createNormalizedFields(GraphQLSchema schema, String query) {
        assertValidQuery(schema, query)
        Document originalDocument = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(schema, originalDocument, null, [:])
        return tree.getTopLevelFields()
    }

    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();
        assert graphQL.execute(query, null, variables).errors.size() == 0
    }
}



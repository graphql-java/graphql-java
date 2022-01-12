package graphql.normalized

import graphql.GraphQL
import graphql.TestUtil
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.language.OperationDefinition.Operation.MUTATION
import static graphql.language.OperationDefinition.Operation.QUERY
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION
import static graphql.normalized.ExecutableNormalizedOperationToAstCompiler.compileToDocument

class ExecutableNormalizedOperationToAstCompilerTest extends Specification {

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
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(schema, QUERY, null, fields)
        def printed = AstPrinter.printAst(document)
        then:
        printed == '''query {
  animal {
    ... on Bird {
      name
      otherName: name
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
      name
      otherName: name
      mood
      friends {
        isCatOwner
        pets {
          ... on Dog {
            name
          }
        }
      }
    }
    ... on Dog {
      name
      otherName: name
      breed
    }
  }
}
'''
    }

    def "test a combination of plain objects and interfaces"() {
        def sdl = '''
        type Query {
            foo(arg: I): Foo
        }
        type Foo {
            bar(arg: I): Bar
        }
        type Bar {
            baz : Baz
        }
        interface Baz {
            boo : String
        }
        type ABaz implements Baz {
            boo : String
            a : String
        }
        type BBaz implements Baz {
            boo : String
            b : String
        }
        input I {
            arg1: String
        }
        '''
        def query = '''query {
    foo(arg: {arg1 : "fooArg"}) {
        bar(arg: {arg1 : "barArg"}) {
            baz {
                ... on ABaz {
                    boo
                    a
                }
            }
        }
    }
}
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(schema, QUERY, null, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  foo(arg: {arg1 : "fooArg"}) {
    bar(arg: {arg1 : "barArg"}) {
      baz {
        ... on ABaz {
          boo
          a
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
            foo2(a: Int, b: Boolean, c: Float): String
        }
        '''
        def query = ''' {
            foo1(arg: "hello")
            foo2(a: 123, b: true, c: 123.45)
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(schema, QUERY, null, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  foo1(arg: "hello")
  foo2(a: 123, b: true, c: 123.45)
}
'''
    }

    def "sets operation name"() {
        def sdl = '''
        type Query {
            foo1(arg: String): String
            foo2(a: Int,b: Boolean, c: Float): String
        }
        '''
        def query = ''' {
            foo1(arg: "hello")
            foo2(a: 123, b: true, c: 123.45)
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(schema, QUERY, "My_Op23", fields)
        then:
        AstPrinter.printAst(document) == '''query My_Op23 {
  foo1(arg: "hello")
  foo2(a: 123, b: true, c: 123.45)
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
        def document = compileToDocument(schema, QUERY, null, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  foo1(arg: {arg1 : "Hello", arg2 : 123, arg3 : "IDID", arg4 : false, arg5 : 123.123, nested : {arg1 : "Hello2", arg2 : 1234, arg3 : "IDID1", arg4 : null, arg5 : null}})
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
        def document = compileToDocument(schema, MUTATION, null, fields)
        then:
        AstPrinter.printAst(document) == '''mutation {
  foo1(arg: {arg1 : "Mutation"})
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
        def document = compileToDocument(schema, SUBSCRIPTION, null, fields)
        then:
        AstPrinter.printAst(document) == '''subscription {
  foo1(arg: {arg1 : "Subscription"})
}
'''
    }

    def "test redundant inline fragments specified in original query"() {
        def sdl = '''
        type Query {
            foo1(arg: I): Foo 
        }
        type Mutation {
            foo1(arg: I): Foo 
        }
        type Foo {
            test: String
        }
        input I {
            arg1: String
        }
        '''
        def query = '''mutation {
            ... on Mutation {
                foo1(arg: {
                    arg1: "Mutation"
                }) {
                    ... on Foo {
                        test
                    }
                }
            }
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(schema, MUTATION, null, fields)
        then:
        AstPrinter.printAst(document) == '''mutation {
  foo1(arg: {arg1 : "Mutation"}) {
    test
  }
}
'''
    }

    def "inserts inline fragment on interface types"() {
        def sdl = '''
        type Query {
            foo1(arg: I): Foo 
        }
        type Mutation {
            foo1(arg: I): Foo 
        }
        interface Foo {
            test: String
        }
        type AFoo implements Foo {
            test: String
        }
        input I {
            arg1: String
        }
        '''
        def query = '''query {
            ... on Query {
                foo1(arg: {
                    arg1: "Query"
                }) {
                    ... on Foo {
                        test
                    }
                }
            }
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(schema, QUERY, null, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  foo1(arg: {arg1 : "Query"}) {
    ... on AFoo {
      test
    }
  }
}
'''
    }

    def "handles concrete and abstract fields"() {
        def sdl = '''
        type Query {
            foo1(arg: I): Foo 
        }
        type Mutation {
            foo1(arg: I): Foo 
        }
        interface Foo {
            test: String
        }
        type AFoo implements Foo {
            test: String
            afoo: String
        }
        input I {
            arg1: String
        }
        '''
        def query = '''query {
            ... on Query {
                foo1(arg: {
                    arg1: "Query"
                }) {
                    test
                    ... on AFoo {
                        afoo
                    }
                    ... on AFoo {
                        __typename
                    }
                }
            }
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(schema, QUERY, null, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  foo1(arg: {arg1 : "Query"}) {
    ... on AFoo {
      test
      afoo
      __typename
    }
  }
}
'''
    }

    def "handles typename outside fragment and inside fragment"() {
        def sdl = '''
        type Query {
            foo1(arg: I): Foo 
        }
        type Mutation {
            foo1(arg: I): Foo 
        }
        interface Foo {
            test: String
        }
        type AFoo implements Foo {
            test: String
        }
        input I {
            arg1: String
        }
        '''
        def query = '''query {
            ... on Query {
                foo1(arg: {
                    arg1: "Query"
                }) {
                    __typename
                    test
                    ... on AFoo {
                        __typename
                    }
                }
            }
        }
        '''
        GraphQLSchema schema = TestUtil.schema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = compileToDocument(schema, QUERY, null, fields)
        then:
        AstPrinter.printAst(document) == '''query {
  foo1(arg: {arg1 : "Query"}) {
    ... on AFoo {
      __typename
      test
    }
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

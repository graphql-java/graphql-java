package graphql.normalized

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TestLiveMockedWiringFactory
import graphql.schema.scalars.JsonScalar
import spock.lang.Specification

import static graphql.language.OperationDefinition.Operation.MUTATION
import static graphql.language.OperationDefinition.Operation.QUERY
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION

class ExecutableNormalizedOperationToAstCompilerTest extends Specification {

    VariablePredicate noVariables = new VariablePredicate() {
        @Override
        boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
            return false
        }
    }

    VariablePredicate jsonVariables = new VariablePredicate() {
        @Override
        boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
            "JSON" == normalizedInputValue.unwrappedTypeName && normalizedInputValue.value != null
        }
    }

    VariablePredicate allVariables = new VariablePredicate() {
        @Override
        boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
            return true
        }
    }

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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def document = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, null, fields, noVariables).document
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''query {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''query {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, "My_Op23", fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''query My_Op23 {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''query {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''mutation {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, SUBSCRIPTION, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''subscription {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''mutation {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''query {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''query {
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
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, null, fields, noVariables)
        then:
        AstPrinter.printAst(result.document) == '''query {
  foo1(arg: {arg1 : "Query"}) {
    ... on AFoo {
      __typename
      test
    }
  }
}
'''
    }

    // --------------------------------------------------------------------------------------
    // Custom JSON handling

    def "test JSON when input is a variable"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: JSON!): String
        }
        
        scalar JSON
        '''
        def query = '''mutation hello($var: JSON!) {
            foo1(arg: $var)
        }
        '''
        GraphQLSchema schema = mkSchema(sdl)

        def vars = [var: ["48x48": "hello"]]
        def fields = createNormalizedFields(schema, query, vars)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, jsonVariables)

        then:
        result.variables == [v0: ["48x48": "hello"]]
        AstPrinter.printAst(result.document) == '''mutation ($v0: JSON!) {
  foo1(arg: $v0)
}
'''
    }

    def "test JSON when input is a string variable"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: JSON!): String
        }
        
        scalar JSON
        '''
        def query = '''mutation hello($var: JSON!) {
            foo1(arg: $var)
        }
        '''
        GraphQLSchema schema = mkSchema(sdl)

        def vars = [var: "hello there"]
        def fields = createNormalizedFields(schema, query, vars)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, jsonVariables)
        then:
        result.variables == [v0: "hello there"]
        AstPrinter.printAst(result.document) == '''mutation ($v0: JSON!) {
  foo1(arg: $v0)
}
'''
    }

    def "test JSON when input is an int variable"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: JSON!): String
        }
        
        scalar JSON
        '''
        def query = '''mutation hello($var: JSON!) {
            foo1(arg: $var)
        }
        '''
        GraphQLSchema schema = mkSchema(sdl)

        def vars = [var: 1]
        def fields = createNormalizedFields(schema, query, vars)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, jsonVariables)

        then:
        result.variables == [v0: 1]
        AstPrinter.printAst(result.document) == '''mutation ($v0: JSON!) {
  foo1(arg: $v0)
}
'''
    }

    def "test JSON scalar when JSON arg is null"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: JSON): String
        }
        
        scalar JSON
        '''
        def query = '''mutation {
            foo1
        }
        '''
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, noVariables)
        then:
        result.variables == [:]
        AstPrinter.printAst(result.document) == '''mutation {
  foo1
}
'''
    }

    def "test JSON scalar when JSON arg is explicitly null"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: JSON): String
        }
        
        scalar JSON
        '''
        def query = '''mutation {
            foo1(arg: null)
        }
        '''
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, noVariables)
        then:
        result.variables == [:]
        AstPrinter.printAst(result.document) == '''mutation {
  foo1(arg: null)
}
'''
    }

    def "test JSON scalar when input is inlined"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: JSON!): String
        }
        
        scalar JSON
        '''
        def query = '''mutation {
            foo1(arg: {one: "two", three: ["four", "five"]})
        }
        '''
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, jsonVariables)
        then:
        result.variables == [v0: [one: "two", three: ["four", "five"]]]
        AstPrinter.printAst(result.document) == '''mutation ($v0: JSON!) {
  foo1(arg: $v0)
}
'''
    }

    def "test JSON scalar when input is inlined, multiple JSON args"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg1: JSON!, arg2: [JSON!]): String
        }
        
        scalar JSON
        '''
        def query = '''mutation {
            foo1(arg1: {one: "two", three: ["four", "five"]}, arg2: [{one: "two", three: ["four", "five"]}])
        }
        '''
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, jsonVariables)
        then:
        result.variables.size() == 2
        result.variables['v0'] == [one: "two", three: ["four", "five"]]
        result.variables['v1'] == [[one: "two", three: ["four", "five"]]]
        AstPrinter.printAst(result.document) == '''mutation ($v0: JSON!, $v1: [JSON!]) {
  foo1(arg1: $v0, arg2: $v1)
}
'''
    }

    def "test JSON scalar inside an input type"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: InputWithJson): String
        }
        
        input InputWithJson {
          id: ID
          json: JSON
        }
        scalar JSON
        '''
        def query = '''mutation {
            foo1(arg: {id: "ID-00", json: {name: "Zlatan", lastName: "Ibrahimoviç", clubs: ["MU", "Barsa", "Inter", "Milan"]}})
        }
        '''

        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, jsonVariables)
        def vars = result.variables
        then:
        vars.size() == 1
        vars['v0'] == [lastName: "Ibrahimoviç", name: "Zlatan", clubs: ["MU", "Barsa", "Inter", "Milan"]]
        AstPrinter.printAst(result.document) == '''mutation ($v0: JSON) {
  foo1(arg: {id : "ID-00", json : $v0})
}
'''
    }

    def "test JSON scalar inside an input type, json value is explicitly null"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: InputWithJson): String
        }
        
        input InputWithJson {
          id: ID
          json: JSON
        }
        scalar JSON
        '''
        def query = '''mutation {
            foo1(arg: {id: "ID-00", json: null})
        }
        '''

        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, noVariables)
        then:
        result.variables == [:]
        AstPrinter.printAst(result.document) == '''mutation {
  foo1(arg: {id : "ID-00", json : null})
}
'''
    }

    def "test JSON scalar inside an input type, json value is null"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: InputWithJson): String
        }
        
        input InputWithJson {
          id: ID
          json: JSON
        }
        scalar JSON
        '''
        def query = '''mutation {
            foo1(arg: {id: "ID-00"})
        }
        '''

        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, noVariables)
        then:
        result.variables == [:]
        AstPrinter.printAst(result.document) == '''mutation {
  foo1(arg: {id : "ID-00"})
}
'''
    }

    def "test JSON scalar inside an input type, json key is illegal graphql input name"() {
        def sdl = '''
        type Query {
            foo: String
        }
        type Mutation {
            foo1(arg: InputWithJson): String
        }
        
        input InputWithJson {
          id: ID
          json: [JSON!]
        }
        scalar JSON
        '''
        def query = '''mutation test($var: InputWithJson) {
            foo1(arg: $var)
        }
        '''
        def variables = [var: [
                id  : "ID-00",
                json: [[name    : "Zlatan",
                        lastName: "Ibrahimoviç",
                        clubs   : ["MU", "Barsa", "Inter", "Milan", null],
                        "48x48" : "Zlatan_48x48.jpg",
                        "96x96" : null
                       ]]
        ]]
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query, variables)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, MUTATION, null, fields, jsonVariables)
        then:
        result.variables.size() == 1
        result.variables['v0'] == [[name    : "Zlatan",
                                    lastName: "Ibrahimoviç",
                                    clubs   : ["MU", "Barsa", "Inter", "Milan", null],
                                    "48x48" : "Zlatan_48x48.jpg",
                                    "96x96" : null
                                   ]]
        AstPrinter.printAst(result.document) == '''mutation ($v0: [JSON!]) {
  foo1(arg: {id : "ID-00", json : $v0})
}
'''
    }


    def "test a combination of plain objects and interfaces will be all variables"() {
        def sdl = '''
        type Query {
            listField1(arg: [Int]): String
            listField2(arg: [I]): String
            foo(arg: I): Foo
            fooNonNull(arg: [I!]!): String
        }
        type Foo {
            bar(arg: I): Bar
        }
        type Bar {
            baz : Baz
        }
        interface Baz {
            boo(arg: I) : String
        }
        type ABaz implements Baz {
            boo(arg: I) : String
            a : String
        }
        type BBaz implements Baz {
            boo(arg: I) : String
            b : String
        }
        input I {
            arg1: String
            arg2: [I]
        }
        '''
        def query = '''
        query whatEv {
            listField1( arg : [1,2,3] )
            listField2( arg : [ {arg1 : "v1", arg2 : [ {arg1 : "v1.1"}] },
                                {arg1 : "v2"},
                                {arg1 : "v3"}] )

            fooNonNull(arg: [{arg1 : "fooNonNullArg1"}, {arg1 : "fooNonNullArg2"}])

            foo(arg: {arg1 : "fooArg"}) {
                bar(arg: {arg1 : "barArg"}) {
                    baz {
                        ... on ABaz {
                            boo(arg : {arg1 : "barFragArg"})
                            a
                        }
                    }
                }
            }
        }
        '''
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)

        when:
        def result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(schema, QUERY, "named", fields, allVariables)
        def document = result.document
        def vars = result.variables
        def ast = AstPrinter.printAst(document)
        then:

        ast == '''query named($v0: [Int], $v1: [I], $v2: [I!]!, $v3: I, $v4: I, $v5: I) {
  listField1(arg: $v0)
  listField2(arg: $v1)
  fooNonNull(arg: $v2)
  foo(arg: $v5) {
    bar(arg: $v4) {
      baz {
        ... on ABaz {
          boo(arg: $v3)
          a
        }
      }
    }
  }
}
'''

        vars == [v0: [1, 2, 3],
                 v1: [[arg1: "v1", arg2: [[arg1: "v1.1"]]], [arg1: "v2"], [arg1: "v3"]],
                 v2: [[arg1: "fooNonNullArg1"], [arg1: "fooNonNullArg2"]],
                 v3: [arg1: "barFragArg"],
                 v4: [arg1: "barArg"],
                 v5: [arg1: "fooArg"]]
    }

    private List<ExecutableNormalizedField> createNormalizedFields(GraphQLSchema schema, String query, Map<String, Object> variables = [:]) {
        assertValidQuery(schema, query, variables)
        Document originalDocument = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory()
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(schema, originalDocument, null, variables)
        return tree.getTopLevelFields()
    }

    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()
        assert graphQL.execute(ExecutionInput.newExecutionInput(query).variables(variables)).errors.size() == 0
    }

    GraphQLSchema mkSchema(String sdl) {
        def wiringFactory = new TestLiveMockedWiringFactory([JsonScalar.JSON_SCALAR])
        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(wiringFactory).build()
        TestUtil.schema(sdl, runtimeWiring)
    }
}

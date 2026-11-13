package graphql.normalized

import graphql.schema.GraphQLSchema

import static graphql.language.OperationDefinition.Operation.QUERY

/**
 * Test related to ENO and directives
 */
class ExecutableNormalizedOperationToAstCompilerDirectivesTest extends ENOToAstCompilerTestBase {
    def bookSDL = '''
        directive @timeout(afterMillis : Int) on FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | QUERY
        
        directive @cached(forMillis : Int) on FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | QUERY
        
        directive @importance(place : String) on FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | QUERY
 
        type Query {
            books(searchString : String) : [Book]
        }
        
        type Book {
         id :  ID
         title : String
         review : String
        }
    '''

    def "can extract variables or inline values for directives on the query"() {
        def sdl = '''
            type Query {
                foo(fooArg : String) : Foo
            }
            
            type Foo {
                bar(barArg : String) : String
            }
            
            directive @optIn(to : [String!]!) repeatable on FIELD
        '''

        def query = '''
            query named($fooArgVar : String, $barArgVar : String,  $skipVar : Boolean!, $optVar : [String!]!) {
                foo(fooArg : $fooArgVar) @skip(if : $skipVar) {
                    bar(barArg : $barArgVar) @optIn(to : ["optToX"]) @optIn(to : $optVar)
                }
            }
        '''
        GraphQLSchema schema = mkSchema(sdl)
        def eno = createNormalizedTree(schema, query,
                [fooArgVar: "fooArgVar", barArgVar: "barArgVar", skipVar: false, optVar: ["optToY"]])

        when:
        def result = localCompileToDocument(schema, QUERY, "named",
                eno.getTopLevelFields(), eno.getNormalizedFieldToQueryDirectives(),
                allVariables)
        def document = result.document
        def vars = result.variables
        def ast = printDoc(document)

        then:
        vars == [v0: "barArgVar", v1: ["optToX"], v2: ["optToY"], v3: "fooArgVar", v4: false]
        //
        // the below is what ir currently produces but its WRONG
        // fix up when the other tests starts to work
        //
        ast == '''query named($v0: String, $v1: [String!]!, $v2: [String!]!, $v3: String, $v4: Boolean!) {
  foo(fooArg: $v3) @skip(if: $v4) {
    bar(barArg: $v0) @optIn(to: $v1) @optIn(to: $v2)
  }
}
'''


        when: "it has no variables"


        result = localCompileToDocument(schema, QUERY, "named",
                eno.getTopLevelFields(), eno.getNormalizedFieldToQueryDirectives(),
                noVariables)
        document = result.document
        vars = result.variables
        ast = printDoc(document)

        then:
        vars == [:]
        ast == '''query named {
  foo(fooArg: "fooArgVar") @skip(if: false) {
    bar(barArg: "barArgVar") @optIn(to: ["optToX"]) @optIn(to: ["optToY"])
  }
}
'''

    }

    def "can handle quite a pathological fragment query as expected"() {

        def pathologicalQuery = '''
        fragment Details on Book @timeout(afterMillis: 25) @cached(forMillis : 25) @importance(place:"FragDef") {
            title
            review @timeout(afterMillis: 5) @cached(forMillis : 5)
            ...InnerDetails @timeout(afterMillis: 26) 
        }
        
        fragment InnerDetails on Book  @timeout(afterMillis: 27) {
            review @timeout(afterMillis: 28)
        }
        
        query Books @timeout(afterMillis: 30) @importance(place:"Operation") {
            books(searchString: "monkey") {
                id
                 ...Details @timeout(afterMillis: 20)
                 ...on Book @timeout(afterMillis: 15) {
                    review @timeout(afterMillis: 10) @cached(forMillis : 10)
                }
            }
        }
    '''

        GraphQLSchema schema = mkSchema(bookSDL)
        def eno = createNormalizedTree(schema, pathologicalQuery, [:])

        when:
        def result = localCompileToDocument(schema, QUERY, "Books",
                eno.getTopLevelFields(), eno.getNormalizedFieldToQueryDirectives(),
                allVariables)
        def document = result.document
        def vars = result.variables
        def ast = printDoc(document)

        then:
        vars == [v0:5, v1:5, v2:28, v3:10, v4:10, v5:"monkey"]
        ast == '''query Books($v0: Int, $v1: Int, $v2: Int, $v3: Int, $v4: Int, $v5: String) {
  books(searchString: $v5) {
    id
    review @cached(forMillis: $v1) @cached(forMillis: $v4) @timeout(afterMillis: $v0) @timeout(afterMillis: $v2) @timeout(afterMillis: $v3)
    title
  }
}
'''


        when: "it has no variables"


        result = localCompileToDocument(schema, QUERY, "Books",
                eno.getTopLevelFields(), eno.getNormalizedFieldToQueryDirectives(),
                noVariables)
        document = result.document
        vars = result.variables
        ast = printDoc(document)

        then:
        vars == [:]
        ast == '''query Books {
  books(searchString: "monkey") {
    id
    review @cached(forMillis: 5) @cached(forMillis: 10) @timeout(afterMillis: 5) @timeout(afterMillis: 28) @timeout(afterMillis: 10)
    title
  }
}
'''

    }

    def "merged fields with the same field will capture all directives"() {
        def query = '''
        query Books {
            books(searchString: "monkey") {
                review @timeout(afterMillis: 10) @cached(forMillis : 10)
                review @timeout(afterMillis: 100) @cached(forMillis : 100)
            }
        }
    '''

        GraphQLSchema schema = mkSchema(bookSDL)
        def eno = createNormalizedTree(schema, query, [:])

        when:
        def result = localCompileToDocument(schema, QUERY, "Books",
                eno.getTopLevelFields(), eno.getNormalizedFieldToQueryDirectives(),
                noVariables)
        def document = result.document
        def ast = printDoc(document)

        then:
        ast == '''query Books {
  books(searchString: "monkey") {
    review @cached(forMillis: 10) @cached(forMillis: 100) @timeout(afterMillis: 10) @timeout(afterMillis: 100)
  }
}
'''
    }
}

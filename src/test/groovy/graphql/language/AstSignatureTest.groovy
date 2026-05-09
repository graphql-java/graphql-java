package graphql.language

import graphql.TestUtil
import graphql.execution.CoercedVariables
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.language.AstPrinter.printAst

class AstSignatureTest extends Specification {

    def query = '''
            query Ouch($secretVariable : String, $otherVariable : Int) {
                fieldZ
                fieldX(password : "hunter2", accountBalance : 200000.23, 
                    avatar : { name : "secretPicture", url : "http://someplace" }
                    favouriteThings : [ "brown", "paper", "packages", "tied", "up", "in", "string" ]
                    likesIceCream : true
                    argToAVariable : $secretVariable
                    anotherArg : $otherVariable
                    )
                fieldY {
                    innerFieldA
                    innerFieldC    
                    innerFieldB
                } 
                ... X   
            }
            
            query Ohh {
                fieldZ
                fieldX
                fieldY {
                    innerFieldA
                    innerFieldC    
                    innerFieldB
                }    
            }
            
            {
                unnamedQuery
                withFields
            }
            
            fragment X on SomeType {
                fieldX(password : "hunter2", accountBalance : 200000.23, 
                    avatar : { name : "secretPicture", url : "http://some place" }
                    favouriteThings : [ "brown", "paper", "packages", "tied", "up", "in", "string" ]
                    likesIceCream : true
                    ) 
            }
            
            type RogueSDLElement {
                field : String
            }
'''
    def "can make a signature for a query"() {

        def expectedQuery = '''query Ouch($var1: String, $var2: Int) {
  fieldX(accountBalance: 0, anotherArg: $var2, argToAVariable: $var1, avatar: {}, favouriteThings: [], likesIceCream: false, password: "")
  fieldY {
    innerFieldA
    innerFieldB
    innerFieldC
  }
  fieldZ
  ...X
}

fragment X on SomeType {
  fieldX(accountBalance: 0, avatar: {}, favouriteThings: [], likesIceCream: false, password: "")
}
'''
        def doc = TestUtil.parseQuery(query)
        when:
        def newDoc = new AstSignature().signatureQuery(doc, "Ouch")
        then:
        newDoc != null
        printAst(newDoc) == expectedQuery
    }

    def "can make a privacy safe document for a query"() {

        def expectedQuery = '''query Ouch($var1: String, $var2: Int) {
  fieldX(accountBalance: 0, anotherArg: $var2, argToAVariable: $var1, avatar: {name : "", url : ""}, favouriteThings: ["", "", "", "", "", "", ""], likesIceCream: false, password: "")
  fieldY {
    innerFieldA
    innerFieldB
    innerFieldC
  }
  fieldZ
  ...X
}

fragment X on SomeType {
  fieldX(accountBalance: 0, avatar: {name : "", url : ""}, favouriteThings: ["", "", "", "", "", "", ""], likesIceCream: false, password: "")
}
'''
        def doc = TestUtil.parseQuery(query)
        when:
        def newDoc = new AstSignature().privacySafeQuery(doc, "Ouch")
        then:
        newDoc != null
        printAst(newDoc) == expectedQuery
    }

    def "can do signature on queries with no name"() {
        def query = """
    {
        allIssues(arg1 : "UGC", arg2 : 666) {
            id
        }
    }"""

        def expectedQuery = """{
  allIssues(arg1: "", arg2: 0) {
    id
  }
}
"""

        def doc = TestUtil.parseQuery(query)
        when:
        def newDoc = new AstSignature().signatureQuery(doc, null)
        then:
        newDoc != null
        printAst(newDoc) == expectedQuery


    }

    @Unroll
    def "signature with input redacts #argumentDescription argument"() {
        expect:
        signatureWithInput("""
            query Test {
                search($argumentSource) {
                    id
                }
            }
        """) == """query Test {
  search($expectedArgument) {
    id
  }
}
"""

        where:
        argumentDescription | argumentSource                    | expectedArgument
        "String"            | 'term: "secret"'                  | 'term: ""'
        "Int"               | 'count: 123'                      | 'count: 0'
        "Float"             | 'ratio: 123.45'                   | 'ratio: 0'
        "Boolean"           | 'flag: true'                      | 'flag: false'
        "enum"              | 'enumArg: ASC'                    | 'enumArg: REDACTED'
        "ID list"           | 'ids: ["abc", "def"]'             | 'ids: ["", ""]'
        "custom scalar"     | 'raw: { secret: "not inspected" }' | 'raw: ""'
    }

    def "signature with input keeps only supplied input object fields"() {
        expect:
        signatureWithInput('''
            query Test {
                search(filter: { term: "secret", nested: { min: 1 } }) {
                    id
                }
            }
        ''') == '''query Test {
  search(filter: {nested : {min : 0}, term : ""}) {
    id
  }
}
'''
    }

    def "signature with input preserves list item shapes"() {
        expect:
        signatureWithInput('''
            query Test {
                search(filters: [{ term: "one" }, { nested: { flag: true } }]) {
                    id
                }
            }
        ''') == '''query Test {
  search(filters: [{term : ""}, {nested : {flag : false}}]) {
    id
  }
}
'''
    }

    def "signature with input omits an argument when its variable is absent"() {
        expect:
        signatureWithInput('''
            query Test($term: String) {
                search(count: 1, term: $term) {
                    id
                }
            }
        ''') == '''query Test($var1: String) {
  search(count: 0) {
    id
  }
}
'''
    }

    def "signature with input omits an input object field when its variable is absent"() {
        expect:
        signatureWithInput('''
            query Test($term: String) {
                search(filter: { nested: { min: 1 }, term: $term }) {
                    id
                }
            }
        ''') == '''query Test($var1: String) {
  search(filter: {nested : {min : 0}}) {
    id
  }
}
'''
    }

    def "signature with input expands variable values into redacted input shapes"() {
        expect:
        signatureWithInput('''
            query Test($filter: FilterInput, $filters: [FilterInput]) {
                search(filter: $filter, filters: $filters) {
                    id
                }
            }
        ''', [
                filter : [
                        term  : "secret",
                        ranges: [[min: 1], [flag: true]],
                        sort  : "DESC"
                ],
                filters: [
                        [term: "one"],
                        [nested: [child: [term: "two"]]]
                ]
        ]) == '''query Test($var1: FilterInput, $var2: [FilterInput]) {
  search(filter: {ranges : [{min : 0}, {flag : false}], sort : REDACTED, term : ""}, filters: [{term : ""}, {nested : {child : {term : ""}}}]) {
    id
  }
}
'''
    }

    def "signature with input redacts variable default values"() {
        expect:
        signatureWithInput('''
            query Test($filter: FilterInput = { term: "default", nested: { min: 1 } }) {
                search(filter: $filter) {
                    id
                }
            }
        ''', [
                filter: [term: "default", nested: [min: 1]]
        ]) == '''query Test($var1: FilterInput = {nested : {min : 0}, term : ""}) {
  search(filter: {nested : {min : 0}, term : ""}) {
    id
  }
}
'''
    }

    def "signature with input handles directives fragments aliases and operation pruning together"() {
        expect:
        signatureWithInput('''
            query Test($filter: FilterInput) @searchMeta(meta: { term: "operation" }, enabled: true) {
                alias: node(filter: { term: "root" }) {
                    ...ResultFields @searchMeta(meta: { term: "spread" })
                    ... on SearchResult @searchMeta(meta: { term: "inline" }) {
                        child(filter: $filter) @searchMeta(meta: { nested: { min: 1 } }, enabled: false) {
                            id
                        }
                    }
                }
            }

            query Other {
                search(term: "not selected") {
                    id
                }
            }

            fragment ResultFields on SearchResult {
                child(filter: { nested: { min: 1 } }) {
                    id
                }
            }
        ''', [
                filter: [nested: [child: [term: "variable"]]]
        ]) == '''query Test($var1: FilterInput) @searchMeta(enabled: false, meta: {term : ""}) {
  node(filter: {term : ""}) {
    ...ResultFields@searchMeta(meta: {term : ""})
    ... on SearchResult @searchMeta(meta: {term : ""}) {
      child(filter: {nested : {child : {term : ""}}}) @searchMeta(enabled: false, meta: {nested : {min : 0}}) {
        id
      }
    }
  }
}

fragment ResultFields on SearchResult {
  child(filter: {nested : {min : 0}}) {
    id
  }
}
'''
    }

    def signatureWithInput(String query, Map<String, Object> variables = [:], String operationName = "Test") {
        def doc = TestUtil.parseQuery(query)
        def newDoc = new AstSignature().signatureWithInput(doc, operationName, inputSignatureSchema(), CoercedVariables.of(variables))
        printAst(newDoc)
    }

    def inputSignatureSchema() {
        TestUtil.schema('''
            directive @searchMeta(meta: FilterInput, enabled: Boolean) on FIELD | QUERY | FRAGMENT_SPREAD | INLINE_FRAGMENT

            scalar JSON

            enum Sort {
                ASC
                DESC
            }

            interface Node {
                id: ID
            }

            type SearchResult implements Node {
                id: ID
                child(filter: FilterInput): SearchResult
            }

            type Query {
                search(
                    filter: FilterInput
                    filters: [FilterInput]
                    fallback: FilterInput
                    term: String
                    optional: String
                    count: Int
                    ratio: Float
                    flag: Boolean
                    enumArg: Sort
                    ids: [ID]
                    raw: JSON
                ): SearchResult
                node(filter: FilterInput): Node
            }

            input FilterInput {
                term: String
                nested: NestedInput
                tags: [String]
                ranges: [NestedInput]
                sort: Sort
                optional: String
            }

            input NestedInput {
                min: Int
                max: Int
                flag: Boolean
                child: FilterInput
            }
        ''')
    }
}

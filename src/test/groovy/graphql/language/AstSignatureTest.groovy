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

    def "signature with input redacts literal single values supplied to list arguments"() {
        expect:
        signatureWithInput('''
            query Test {
                search(ids: "abc") {
                    id
                }
            }
        ''') == '''query Test {
  search(ids: "") {
    id
  }
}
'''
    }

    def "signature with input keeps literal null values"() {
        expect:
        signatureWithInput('''
            query Test {
                search(term: null) {
                    id
                }
            }
        ''') == '''query Test {
  search(term: null) {
    id
  }
}
'''
    }

    def "signature with input redacts malformed input object literals without schema shape"() {
        expect:
        signatureWithInput('''
            query Test {
                search(filter: "secret") {
                    id
                }
            }
        ''') == '''query Test {
  search(filter: "") {
    id
  }
}
'''
    }

    def "signature with input redacts non null argument and variable values"() {
        expect:
        signatureWithInput('''
            query Test($filter: FilterInput!) {
                search(requiredFilter: $filter, requiredTerm: "secret") {
                    id
                }
            }
        ''', [
                filter: [term: "secret"]
        ]) == '''query Test($var1: FilterInput!) {
  search(requiredFilter: {term : ""}, requiredTerm: "") {
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

    def "signature with input redacts null variables as explicit null values"() {
        expect:
        signatureWithInput('''
            query Test($term: String) {
                search(term: $term) {
                    id
                }
            }
        ''', [
                term: null
        ]) == '''query Test($var1: String) {
  search(term: null) {
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

    def "signature with input expands singleton variable values into list shapes"() {
        expect:
        signatureWithInput('''
            query Test($ids: [ID]) {
                search(ids: $ids) {
                    id
                }
            }
        ''', [
                ids: "abc"
        ]) == '''query Test($var1: [ID]) {
  search(ids: [""]) {
    id
  }
}
'''
    }

    def "signature with input redacts invalid input object variable values as empty object shapes"() {
        expect:
        signatureWithInput('''
            query Test($filter: FilterInput) {
                search(filter: $filter) {
                    id
                }
            }
        ''', [
                filter: "not a map"
        ]) == '''query Test($var1: FilterInput) {
  search(filter: {}) {
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

    def "signature with input redacts unknown arguments and unknown input object fields without schema types"() {
        expect:
        signatureWithInput('''
            query Test {
                search(filter: { term: "secret", unknownField: "secret" }, unknownArg: { inner: "secret" }) {
                    id
                }
            }
        ''') == '''query Test {
  search(filter: {term : "", unknownField : ""}, unknownArg: {inner : ""}) {
    id
  }
}
'''
    }

    def "signature with input redacts unknown directive arguments without schema types"() {
        expect:
        signatureWithInput('''
            query Test($known: FilterInput) {
                search @unknown(
                    array: [1, 2.5, "secret", true, DESC, null, { inner: "secret" }]
                    known: $known
                    unknown: $unknown
                ) {
                    id
                }
            }
        ''', [
                known: [term: "secret"]
        ]) == '''query Test($var1: FilterInput) {
  search @unknown(array: [0, 0, "", false, REDACTED, null, {inner : ""}], known: {term : ""}, unknown: $var2) {
    id
  }
}
'''
    }

    def "signature with input redacts absent typed variables in unknown directive arguments as null"() {
        expect:
        signatureWithInput('''
            query Test($term: String) {
                search @unknown(term: $term) {
                    id
                }
            }
        ''') == '''query Test($var1: String) {
  search @unknown(term: null) {
    id
  }
}
'''
    }

    def "signature with input handles mutation and subscription root operation types"() {
        expect:
        signatureWithInput(query) == expectedQuery

        where:
        query                                                                   | expectedQuery
        '''mutation Test { update(term: "secret") { id } }'''                  | '''mutation Test {
  update(term: "") {
    id
  }
}
'''
        '''subscription Test { updates(term: "secret") { id } }'''             | '''subscription Test {
  updates(term: "") {
    id
  }
}
'''
    }

    def "signature with input handles inline fragments without type conditions"() {
        expect:
        signatureWithInput('''
            query Test {
                search(term: "root") {
                    ... {
                        child(filter: { term: "secret" }) {
                            id
                        }
                    }
                }
            }
        ''') == '''query Test {
  search(term: "") {
    ... {
      child(filter: {term : ""}) {
        id
      }
    }
  }
}
'''
    }

    def "signature with input leaves unknown inline fragment type conditions unchanged"() {
        expect:
        signatureWithInput('''
            query Test {
                node(filter: { term: "root" }) {
                    ... on MissingType {
                        child(filter: { term: "secret" }) {
                            id
                        }
                    }
                }
            }
        ''') == '''query Test {
  node(filter: {term : ""}) {
    ... on MissingType {
      child(filter: {term : "secret"}) {
        id
      }
    }
  }
}
'''
    }

    def "signature with input removes aliases from unknown type conditions without redacting unknown values"() {
        expect:
        signatureWithInput('''
            query Test {
                node(filter: { term: "root" }) {
                    ...UnknownFields
                    ... on MissingType {
                        inlineAlias: child(filter: { term: "inline" }) {
                            leafAlias: id
                            ...NestedUnknown
                        }
                        ... {
                            nestedInlineAlias: child(filter: { term: "nested inline" }) {
                                leafAlias: id
                            }
                        }
                    }
                }
            }

            fragment UnknownFields on MissingType {
                fragmentAlias: child(filter: { term: "fragment" }) {
                    leafAlias: id
                    ...NestedUnknown
                }
            }

            fragment NestedUnknown on MissingType {
                nestedAlias: child(filter: { term: "nested" }) {
                    leafAlias: id
                }
            }
        ''') == '''query Test {
  node(filter: {term : ""}) {
    ...UnknownFields
    ... on MissingType {
      child(filter: {term : "inline"}) {
        id
        ...NestedUnknown
      }
      ... {
        child(filter: {term : "nested inline"}) {
          id
        }
      }
    }
  }
}

fragment NestedUnknown on MissingType {
  child(filter: {term : "nested"}) {
    id
  }
}

fragment UnknownFields on MissingType {
  child(filter: {term : "fragment"}) {
    id
    ...NestedUnknown
  }
}
'''
    }

    def "signature with input sorts unnamed executable operation selections and fragments"() {
        expect:
        signatureWithInput('''
            {
                search(term: "secret") {
                    ... on SearchResult {
                        id
                    }
                    ... {
                        child {
                            id
                        }
                    }
                    ...ZFields
                    ...AFields
                    alias: child(filter: { term: "child" }) {
                        id
                    }
                    id
                }
            }

            fragment ZFields on SearchResult {
                id
            }

            fragment AFields on SearchResult {
                child(filter: { nested: { max: 2, min: 1 } }) {
                    id
                }
            }
        ''', [:], null) == '''{
  search(term: "") {
    child(filter: {term : ""}) {
      id
    }
    id
    ...AFields
    ...ZFields
    ... {
      child {
        id
      }
    }
    ... on SearchResult {
      id
    }
  }
}

fragment AFields on SearchResult {
  child(filter: {nested : {max : 0, min : 0}}) {
    id
  }
}

fragment ZFields on SearchResult {
  id
}
'''
    }

    def "signature with input sorter sorts field arguments by name"() {
        expect:
        signatureWithInput('''
            query Test {
                search(term: "secret", count: 12) {
                    id
                }
            }
        ''') == '''query Test {
  search(count: 0, term: "") {
    id
  }
}
'''
    }

    def "signature with input sorter sorts directives and directive arguments by name"() {
        expect:
        signatureWithInput('''
            query Test {
                search @skip(if: true) @searchMeta(meta: { term: "field" }, enabled: true) @include(if: true) {
                    id
                }
            }
        ''') == '''query Test {
  search @include(if: false) @searchMeta(enabled: false, meta: {term : ""}) @skip(if: false) {
    id
  }
}
'''
    }

    def "signature with input sorter sorts input object fields recursively"() {
        expect:
        signatureWithInput('''
            query Test {
                search(filter: {
                    term: "filter"
                    nested: { min: 1, max: 2 }
                    ranges: [{ min: 3, max: 4 }, { flag: true }]
                    tags: ["b", "a"]
                }) {
                    id
                }
            }
        ''') == '''query Test {
  search(filter: {nested : {max : 0, min : 0}, ranges : [{max : 0, min : 0}, {flag : false}], tags : ["", ""], term : ""}) {
    id
  }
}
'''
    }

    def "signature with input sorter sorts variable definition directives and default object fields"() {
        expect:
        signatureWithInput('''
            query Test(
                $filter: FilterInput = {
                    term: "default"
                    nested: { min: 1, max: 2 }
                } @skip(if: true) @searchMeta(meta: { term: "variable", nested: { min: 1, max: 2 } }, enabled: true) @include(if: true)
            ) {
                search(filter: $filter) {
                    id
                }
            }
        ''') == '''query Test($var1: FilterInput = {nested : {max : 0, min : 0}, term : ""}@include(if: false) @searchMeta(enabled: false, meta: {nested : {max : 0, min : 0}, term : ""}) @skip(if: false)) {
  search {
    id
  }
}
'''
    }

    def "signature with input sorter sorts mutation selections"() {
        expect:
        signatureWithInput('''
            mutation Test {
                update(term: "secret") {
                    ...ZFields
                    id
                    child(filter: { term: "child" }) {
                        id
                    }
                    ...AFields
                }
            }

            fragment ZFields on SearchResult {
                id
            }

            fragment AFields on SearchResult {
                child(filter: { nested: { min: 1, max: 2 } }) {
                    id
                }
                id
            }
        ''') == '''mutation Test {
  update(term: "") {
    child(filter: {term : ""}) {
      id
    }
    id
    ...AFields
    ...ZFields
  }
}

fragment AFields on SearchResult {
  child(filter: {nested : {max : 0, min : 0}}) {
    id
  }
  id
}

fragment ZFields on SearchResult {
  id
}
'''
    }

    def "signature with input sorter sorts subscription selections"() {
        expect:
        signatureWithInput('''
            subscription Test {
                updates(term: "secret") {
                    id
                    child(filter: { term: "child" }) {
                        id
                    }
                }
            }
        ''') == '''subscription Test {
  updates(term: "") {
    child(filter: {term : ""}) {
      id
    }
    id
  }
}
'''
    }

    def "signature with input can retain fragments when no operation matches"() {
        expect:
        signatureWithInput('''
            query Test {
                search(term: "not selected") {
                    id
                }
            }

            fragment MissingFields on MissingType {
                child(filter: { term: "secret" }) {
                    id
                }
            }
        ''', [:], "DoesNotExist") == '''fragment MissingFields on MissingType {
  child(filter: {term : "secret"}) {
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

    def "signature with input result collects field and input coordinates"() {
        when:
        def result = signatureWithInputResult('''
            query Test($filter: FilterInput) {
                search(filter: $filter, term: "secret") {
                    id
                }
            }
        ''', [
                filter: [term: "variable", nested: [min: 1]]
        ])

        then:
        result.fieldCoordinates == ["Query.search", "SearchResult.id"]
        result.fieldArgumentCoordinates == ["Query.search(filter:)", "Query.search(term:)"]
        result.inputObjectFieldCoordinates == ["FilterInput.nested", "FilterInput.term", "NestedInput.min"]
        result.usedDirectives == []
        result.directiveArgumentCoordinates == []
    }

    def "signature with input result collects used directives and directive arguments"() {
        when:
        def result = signatureWithInputResult('''
            query Test($include: Boolean!, $meta: FilterInput) @searchMeta(meta: { term: "operation" }, enabled: true) {
                search(term: "secret") @include(if: $include) @searchMeta(meta: $meta) {
                    id
                }
            }
        ''', [
                include: true,
                meta   : [nested: [min: 1]]
        ])

        then:
        result.usedDirectives == ["@include", "@searchMeta"]
        result.directiveArgumentCoordinates == ["@include(if:)", "@searchMeta(enabled:)", "@searchMeta(meta:)"]
        result.inputObjectFieldCoordinates == ["FilterInput.nested", "FilterInput.term", "NestedInput.min"]
    }

    def "signature with input result collects variable definition directives"() {
        when:
        def result = signatureWithInputResult('''
            query Test($term: String @searchMeta(meta: { term: "variable" })) {
                search(term: $term) {
                    id
                }
            }
        ''')

        then:
        result.fieldCoordinates == ["Query.search", "SearchResult.id"]
        result.usedDirectives == ["@searchMeta"]
        result.directiveArgumentCoordinates == ["@searchMeta(meta:)"]
        result.fieldArgumentCoordinates == []
        result.inputObjectFieldCoordinates == ["FilterInput.term"]
    }

    def "signature with input result collects used fragment references once"() {
        when:
        def result = signatureWithInputResult('''
            query Test {
                node(filter: { term: "root" }) {
                    ...ResultFields
                    ...ResultFields
                }
            }

            fragment ResultFields on SearchResult @searchMeta(meta: { term: "fragment" }) {
                child(filter: { nested: { flag: true } }) @skip(if: false) {
                    id
                }
            }
        ''')

        then:
        result.fieldCoordinates == ["Query.node", "SearchResult.child", "SearchResult.id"]
        result.usedDirectives == ["@searchMeta", "@skip"]
        result.fieldArgumentCoordinates == ["Query.node(filter:)", "SearchResult.child(filter:)"]
        result.directiveArgumentCoordinates == ["@searchMeta(meta:)", "@skip(if:)"]
        result.inputObjectFieldCoordinates == ["FilterInput.nested", "FilterInput.term", "NestedInput.flag"]
    }

    def "signature with input result does not collect unreferenced fragment references"() {
        when:
        def result = signatureWithInputResult('''
            query Test {
                search {
                    id
                }
            }

            fragment UnusedFields on SearchResult @searchMeta(meta: { nested: { max: 1 } }) {
                child(filter: { nested: { flag: true } }) {
                    id
                }
            }
        ''')

        then:
        result.fieldCoordinates == ["Query.search", "SearchResult.id"]
        result.usedDirectives == []
        result.fieldArgumentCoordinates == []
        result.directiveArgumentCoordinates == []
        result.inputObjectFieldCoordinates == []
    }

    def "signature with input result ignores unknown reference definitions"() {
        when:
        def result = signatureWithInputResult('''
            query Test {
                search(unknownArg: { term: "secret" }) @unknown(meta: { term: "secret" }) {
                    id
                }
            }
        ''')

        then:
        result.fieldCoordinates == ["Query.search", "SearchResult.id"]
        result.fieldArgumentCoordinates == []
        result.usedDirectives == []
        result.directiveArgumentCoordinates == []
        result.inputObjectFieldCoordinates == []
    }

    def "signature with input result omits absent variable reference coordinates"() {
        when:
        def result = signatureWithInputResult('''
            query Test($term: String) {
                search(count: 1, term: $term) {
                    id
                }
            }
        ''')

        then:
        result.fieldCoordinates == ["Query.search", "SearchResult.id"]
        result.fieldArgumentCoordinates == ["Query.search(count:)"]
        result.inputObjectFieldCoordinates == []
    }

    def "signature with input result ignores introspection fields but collects their directives"() {
        when:
        def result = signatureWithInputResult('''
            query Test($include: Boolean!) {
                search {
                    __typename @include(if: $include)
                    id
                }
            }
        ''', [
                include: true
        ])

        then:
        result.fieldCoordinates == ["Query.search", "SearchResult.id"]
        result.usedDirectives == ["@include"]
        result.directiveArgumentCoordinates == ["@include(if:)"]
    }

    def "signature with input result collects known directives but ignores unknown directive arguments"() {
        when:
        def result = signatureWithInputResult('''
            query Test {
                search @searchMeta(meta: { term: "secret" }, unknown: "secret") {
                    id
                }
            }
        ''')

        then:
        result.usedDirectives == ["@searchMeta"]
        result.directiveArgumentCoordinates == ["@searchMeta(meta:)"]
        result.inputObjectFieldCoordinates == ["FilterInput.term"]
    }

    def "signature with input result ignores missing and recursive fragment references"() {
        when:
        def result = signatureWithInputResult('''
            query Test {
                search {
                    ...Missing @include(if: true)
                    ...MissingTypeFields
                    ...A
                }
            }

            fragment A on SearchResult {
                id
                ...B
            }

            fragment B on SearchResult {
                child {
                    ...A
                }
            }

            fragment MissingTypeFields on MissingType {
                id
            }
        ''')

        then:
        result.fieldCoordinates == ["Query.search", "SearchResult.child", "SearchResult.id"]
        result.usedDirectives == ["@include"]
        result.directiveArgumentCoordinates == ["@include(if:)"]
    }

    def "signature with input result ignores non selectable type conditions"() {
        when:
        def result = signatureWithInputResult('''
            query Test {
                search {
                    ...InputFields
                    ... on FilterInput {
                        term
                    }
                    id
                }
            }

            fragment InputFields on FilterInput {
                term
            }
        ''')

        then:
        result.fieldCoordinates == ["Query.search", "SearchResult.id"]
        result.fieldArgumentCoordinates == []
        result.inputObjectFieldCoordinates == []
    }

    def "signature with input result collects interface and union field references"() {
        when:
        def result = signatureWithInputResult('''
            query Test {
                lookup {
                    ... on SearchUnion {
                        ... on SearchResult {
                            id
                        }
                    }
                }
                node(filter: { term: "secret" }) {
                    ... on Node {
                        id
                    }
                }
            }
        ''')

        then:
        result.fieldCoordinates == ["Node.id", "Query.lookup", "Query.node", "SearchResult.id"]
        result.fieldArgumentCoordinates == ["Query.node(filter:)"]
        result.inputObjectFieldCoordinates == ["FilterInput.term"]
    }

    def "signature with input result can be transformed"() {
        given:
        def result = signatureWithInputResult('''
            query Test {
                search {
                    id
                }
            }
        ''')

        when:
        def transformed = result.transform { builder ->
            builder.usedDirectives(["@custom"])
        }

        then:
        transformed.document == result.document
        transformed.fieldCoordinates == result.fieldCoordinates
        transformed.usedDirectives == ["@custom"]
        transformed.fieldArgumentCoordinates == result.fieldArgumentCoordinates
        transformed.directiveArgumentCoordinates == result.directiveArgumentCoordinates
        transformed.inputObjectFieldCoordinates == result.inputObjectFieldCoordinates
    }

    def signatureWithInput(String query, Map<String, Object> variables = [:], String operationName = "Test") {
        signatureWithInput(query, variables, operationName, inputSignatureSchema())
    }

    def signatureWithInput(String query, Map<String, Object> variables, String operationName, def schema) {
        def result = signatureWithInputResult(query, variables, operationName, schema)
        printAst(result.document)
    }

    def signatureWithInputResult(String query, Map<String, Object> variables = [:], String operationName = "Test") {
        signatureWithInputResult(query, variables, operationName, inputSignatureSchema())
    }

    def signatureWithInputResult(String query, Map<String, Object> variables, String operationName, def schema) {
        def doc = TestUtil.parseQuery(query)
        new AstSignature().signatureWithInput(doc, operationName, schema, CoercedVariables.of(variables))
    }

    def inputSignatureSchema() {
        TestUtil.schema('''
            directive @searchMeta(meta: FilterInput, enabled: Boolean) on FIELD | QUERY | VARIABLE_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT

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

            union SearchUnion = SearchResult

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
                    requiredTerm: String!
                    requiredFilter: FilterInput!
                ): SearchResult
                node(filter: FilterInput): Node
                lookup: SearchUnion
            }

            type Mutation {
                update(term: String): SearchResult
            }

            type Subscription {
                updates(term: String): SearchResult
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

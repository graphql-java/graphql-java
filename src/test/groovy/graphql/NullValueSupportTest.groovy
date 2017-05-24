package graphql

import graphql.execution.NonNullableValueCoercedAsNullException
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

/*
 * Taken from http://facebook.github.io/graphql/#sec-Input-Objects
 *
 *
 
 Test Case   Original Value	        Variables	    Coerced Value
 A          { a: "abc", b: 123 }	null	        { a: "abc", b: 123 }
 B          { a: 123, b: "123" }	null	        { a: "123", b: 123 }
 C          { a: "abc" }	        null	        Error: Missing required field b
 D          { a: "abc", b: null }	null	        Error: b must be non‐null.
 E          { a: null, b: 1 }	    null	        { a: null, b: 1 }
 F          { b: $var }             { var: 123 }    { b: 123 }
 G          { b: $var }             {}	            Error: Missing required field b.
 H          { b: $var }             { var: null }	Error: b must be non‐null.
 I          { a: $var, b: 1 }       { var: null }   { a: null, b: 1 }
 J          { a: $var, b: 1 }       {}              { b: 1 }

 */

class NullValueSupportTest extends Specification {

    def graphqlSpecExamples = '''
        schema {
            query : Query
            mutation : Mutation
        }
        
        type Query {
            a : String
            b: Int!
        }
        
        type Mutation {
            mutate(inputArg : ExampleInputObject) : Query
        }
            
        input ExampleInputObject {
             a: String
             b: Int!
        }
            
        '''

    def "test graphql spec examples that output results"() {
        def fetcher = new CapturingDataFetcher()

        def schema = TestUtil.schema(graphqlSpecExamples, ["Mutation": ["mutate": fetcher]])

        when:

        def result = GraphQL.newGraphQL(schema).build().execute(queryStr, "mutate", "ctx", variables)

        then:
        assert result.errors.isEmpty(): "Validation Failure in case ${testCase} : $result.errors"
        assert fetcher.args == expectedArgs: "Argument Failure in case ${testCase} : was ${fetcher.args}"

        where:

        testCase | queryStr       | variables   || expectedArgs

        // ------------------------------
        'A'      | '''
            mutation mutate {
                mutate(inputArg : { a: "abc", b: 123 }) {
                    a
                }        
            }
        ''' | [:]         || [inputArg: [a: "abc", b: 123]]

        // ------------------------------
        // coerced from string -> int and vice versus
        //
        // spec says it should work.  but we think the spec is wrong since
        // the reference implementation will not cross coerce these types
        //
        /*
        'B'      | '''
            mutation mutate {
                mutate(inputArg : { a: 123, b: "123" }) {
                    a
                }        
            }
        ''' | [:]         || [inputArg: [a: "123", b: 123]]
        */

        // ------------------------------
        'E'      | '''
            mutation mutate {
                mutate(inputArg : { a: null, b: 1 }) {
                    a
                }        
            }
        ''' | [:]         || [inputArg: [a: null, b: 1]]

        // ------------------------------
        'F'      | '''
            mutation mutate($var : Int!) {
                mutate(inputArg : { b: $var }) {
                    a
                }        
            }
        ''' | [var: 123]  || [inputArg: [b: 123]]

        // ------------------------------
        'I'      | '''
            mutation mutate($var : String) {
                mutate(inputArg : { a: $var, b: 1 }) {
                    a
                }        
            }
        ''' | [var: null] || [inputArg: [a: null, b: 1]]

        // ------------------------------
        'J'      | '''
            mutation mutate($var : String) {
                mutate(inputArg : { a: $var, b: 1 }) {
                    a
                }        
            }
        ''' | [:]         || [inputArg: [b: 1]]
    }

    def "test graphql spec examples that output errors"() {
        def fetcher = new CapturingDataFetcher()

        def schema = TestUtil.schema(graphqlSpecExamples, ["Mutation": ["mutate": fetcher]])

        when:

        ExecutionResult result = null
        try {
            result = GraphQL.newGraphQL(schema).build().execute(queryStr, "mutate", "ctx", variables)
        } catch (GraphQLException e) {
            assert false: "Unexpected exception during ${testCase} : ${e.message}"
        }

        then:
        assert !result.errors.isEmpty(): "Expected errors in ${testCase}"
        result.errors[0] instanceof ValidationError
        (result.errors[0] as ValidationError).validationErrorType == expectedError


        where:

        testCase | queryStr       | variables || expectedError

        // ------------------------------
        'C'      | '''
            mutation mutate {
                mutate(inputArg : { a: "abc"}) {
                    a
                }        
            }
        ''' | [:]       || ValidationErrorType.WrongType

        // ------------------------------
        'D'      | '''
            mutation mutate {
                mutate(inputArg : { a: "abc", b: null }) {
                    a
                }        
            }
        ''' | [:]       || ValidationErrorType.WrongType
    }

    def "test graphql spec examples that output exception"() {
        def fetcher = new CapturingDataFetcher()

        def schema = TestUtil.schema(graphqlSpecExamples, ["Mutation": ["mutate": fetcher]])

        when:

        GraphQL.newGraphQL(schema).build().execute(queryStr, "mutate", "ctx", variables)

        then:
        thrown(expectedException)


        where:

        testCase | queryStr       | variables   || expectedException

        // ------------------------------
        'G'      | '''
            mutation mutate($var : Int!) {
                mutate(inputArg : { b: $var }) {
                    a
                }        
            }
        ''' | [:]         || NonNullableValueCoercedAsNullException

        // ------------------------------
        'H'      | '''
            mutation mutate($var : Int!) {
                mutate(inputArg : { b: $var }) {
                    a
                }        
            }
        ''' | [var: null] || NonNullableValueCoercedAsNullException

    }

    def "nulls in literal places are supported in general"() {

        def fetcher = new CapturingDataFetcher()

        def schema = TestUtil.schema("""
            schema { query : Query }
            
            type Query {
                list(arg : [String]) : Int
                scalar(arg : String) : Int
                complex(arg : ComplexInputObject) : Int
            }
            
            input ComplexInputObject {
                 a: String
                 b: Int!
            }
            
            """,
                ["Query": [
                        "list"   : fetcher,
                        "scalar" : fetcher,
                        "complex": fetcher,
                ]])

        when:
        def result = GraphQL.newGraphQL(schema).build().execute(queryStr, null, "ctx", [:])
        assert result.errors.isEmpty(): "Unexpected query errors : ${result.errors}"

        then:
        fetcher.args == expectedArgs

        where:
        queryStr                                   | expectedArgs
        '''{ list(arg : ["abc", null, "xyz"]) }''' | [arg: ["abc", null, "xyz"]]
        '''{ scalar(arg : null) }'''               | [arg: null]
        '''{ complex(arg : null) }'''              | [arg: null]

    }
}

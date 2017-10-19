package graphql

import graphql.execution.InputMapDefinesTooManyFieldsException
import graphql.execution.NonNullableValueCoercedAsNullException
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification
import spock.lang.Unroll

/*
 * Taken from http://facebook.github.io/graphql/#sec-Input-Objects
 *
 *
 
 Test Case   Original Value	        Variables	            Coerced Value
 --------------------------------------------------------------------------------------------
 A          { a: "abc", b: 123 }	null	                { a: "abc", b: 123 }
 B          { a: 123, b: "123" }	null	                { a: "123", b: 123 }
 C          { a: "abc" }	        null	                Error: Missing required field b
 D          { a: "abc", b: null }	null	                Error: b must be non‐null.
 E          { a: null, b: 1 }	    null	                { a: null, b: 1 }
 F          { b: $var }             { var: 123 }            { b: 123 }
 G          { b: $var }             {}	                    Error: Missing required field b.
 H          { b: $var }             { var: null }	        Error: b must be non‐null.
 I          { a: $var, b: 1 }       { var: null }           { a: null, b: 1 }
 J          { a: $var, b: 1 }       {}                      { b: 1 }

 These did not come from the spec but added by us as extra tests

 K          { $var }                { a : "abc", b:123 }    { a: "abc", b: 123 }
 L          { $var }                { b:123 }               { b: 123 }
 M          { $var }                { a : "abc", b:null }   Error: b must be non‐null.
 N          { $var }                { a : "abc" }           Error: b must be non‐null.
 O          { $var }                { a : "abc", b: 123, c:"xyz" }   Error: c is not a valid field

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

    @Unroll
    "test graphql spec examples that output results : #testCase"() {
        def fetcher = new CapturingDataFetcher()

        def schema = TestUtil.schema(graphqlSpecExamples, ["Mutation": ["mutate": fetcher]])

        when:

        def result = GraphQL.newGraphQL(schema).build().execute(queryStr, "mutate", "ctx", variables)

        then:
        assert result.errors.isEmpty(): "Validation Failure in case ${testCase} : $result.errors"
        assert fetcher.args == expectedArgs: "Argument Failure in case ${testCase} : was ${fetcher.args}"

        where:

        testCase | queryStr       | variables                 || expectedArgs

        // ------------------------------
        'A'      | '''
            mutation mutate {
                mutate(inputArg : { a: "abc", b: 123 }) {
                    a
                }        
            }
        ''' | [:]                       || [inputArg: [a: "abc", b: 123]]

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
        ''' | [:]                       || [inputArg: [a: null, b: 1]]

        // ------------------------------
        'F'      | '''
            mutation mutate($var : Int!) {
                mutate(inputArg : { b: $var }) {
                    a
                }        
            }
        ''' | [var: 123]                || [inputArg: [b: 123]]

        // ------------------------------
        'I'      | '''
            mutation mutate($var : String) {
                mutate(inputArg : { a: $var, b: 1 }) {
                    a
                }        
            }
        ''' | [var: null]               || [inputArg: [a: null, b: 1]]

        // ------------------------------
        'J'      | '''
            mutation mutate($var : String) {
                mutate(inputArg : { a: $var, b: 1 }) {
                    a
                }        
            }
        ''' | [:]                       || [inputArg: [b: 1]]

        // ------------------------------
        'K'      | '''
            mutation mutate($var : ExampleInputObject) {
                mutate(inputArg : $var) {
                    a
                }        
            }
        ''' | [var: [a: "abc", b: 123]] || [inputArg: [a: "abc", b: 123]]

        // ------------------------------
        'L'      | '''
            mutation mutate($var : ExampleInputObject) {
                mutate(inputArg : $var) {
                    a
                }        
            }
        ''' | [var: [b: 123]]           || [inputArg: [b: 123]]

    }

    @Unroll
    "test graphql spec examples that output errors #testCase"() {
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

    @Unroll
    "test graphql spec examples that output errors via internally throwing exception : #testCase"() {
        def fetcher = new CapturingDataFetcher()

        def schema = TestUtil.schema(graphqlSpecExamples, ["Mutation": ["mutate": fetcher]])

        when:
        def executionResult = GraphQL.newGraphQL(schema).build().execute(queryStr, "mutate", "ctx", variables)

        then:
        executionResult.data == null
        executionResult.errors.size() == 1
        executionResult.errors[0].errorType == ErrorType.ValidationError




        where:

        testCase | queryStr       | variables                           || expectedException

        // ------------------------------
        'G'      | '''
            mutation mutate($var : Int!) {
                mutate(inputArg : { b: $var }) {
                    a
                }        
            }
        ''' | [:]                                 || NonNullableValueCoercedAsNullException

        // ------------------------------
        'H'      | '''
            mutation mutate($var : Int!) {
                mutate(inputArg : { b: $var }) {
                    a
                }        
            }
        ''' | [var: null]                         || NonNullableValueCoercedAsNullException

        // ------------------------------
        'M'      | '''
            mutation mutate($var : ExampleInputObject) {
                mutate(inputArg : $var) {
                    a
                }        
            }
        ''' | [var: [a: "abc", b: null]]          || NonNullableValueCoercedAsNullException

        // ------------------------------
        'N'      | '''
            mutation mutate($var : ExampleInputObject) {
                mutate(inputArg : $var) {
                    a
                }        
            }
        ''' | [var: [a: "abc"]]                   || NonNullableValueCoercedAsNullException

        // ------------------------------
        'O'      | '''
            mutation mutate($var : ExampleInputObject) {
                mutate(inputArg : $var) {
                    a
                }        
            }
        ''' | [var: [a: "abc", b: 123, c: "xyz"]] || InputMapDefinesTooManyFieldsException

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

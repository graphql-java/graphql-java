package graphql

import graphql.language.SourceLocation
import spock.lang.Specification

class ExecutionResultTest extends Specification {

    def KNOWN_ERRORS = [new InvalidSyntaxError(new SourceLocation(666, 664), "Yikes")]
    def EXPECTED_SPEC_ERRORS = [['message': 'Invalid Syntax : Yikes', 'locations': [[line: 666, column: 664]]]]


    def "data with no errors"() {
        given:
        def er = new ExecutionResultImpl("hello world", null)
        when:
        def actual = er.getData()
        def errors = er.getErrors()
        def specMap = er.toSpecification()
        then:
        actual == "hello world"

        errors.size() == 0

        specMap.size() == 1
        specMap["data"] == "hello world"
    }

    def "errors and data"() {
        given:
        def er = new ExecutionResultImpl("hello world", KNOWN_ERRORS)
        when:
        def actual = er.getData()
        def errors = er.getErrors()
        def specMap = er.toSpecification()
        then:
        actual == "hello world"

        errors.size() == 1
        errors == KNOWN_ERRORS

        specMap.size() == 2
        specMap["data"] == "hello world"
        specMap["errors"] == EXPECTED_SPEC_ERRORS
    }

    def "errors and no data"() {
        given:
        def er = new ExecutionResultImpl(KNOWN_ERRORS)
        when:
        def actual = er.getData()
        def errors = er.getErrors()
        def specMap = er.toSpecification()
        then:
        actual == null

        errors.size() == 1
        errors == KNOWN_ERRORS

        specMap.size() == 1
        specMap["errors"] == EXPECTED_SPEC_ERRORS
    }

    def "can have extensions"() {

        given:
        def extensionsObj = ['list': ['a', 'b']]
        def er = new ExecutionResultImpl("hello world", KNOWN_ERRORS, extensionsObj)
        when:
        def actual = er.getData()
        def errors = er.getErrors()
        def extensions = er.getExtensions()
        def specMap = er.toSpecification()
        then:
        actual == "hello world"

        errors.size() == 1
        errors == KNOWN_ERRORS

        extensions == extensionsObj

        specMap.size() == 3
        specMap["data"] == "hello world"
        specMap["errors"] == EXPECTED_SPEC_ERRORS
        specMap["extensions"] == extensionsObj

    }

}

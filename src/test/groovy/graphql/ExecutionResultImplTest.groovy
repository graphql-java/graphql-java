package graphql

import graphql.language.SourceLocation
import spock.lang.Specification

class ExecutionResultImplTest extends Specification {

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

    def "test set error building "() {
        given:
        def startEr = new ExecutionResultImpl(KNOWN_ERRORS)
        def er = ExecutionResultImpl.newExecutionResult().from(startEr).errors(KNOWN_ERRORS).build()
        when:
        def actual = er.getData()
        def errors = er.getErrors()
        def specMap = er.toSpecification()
        then:
        actual == null
        !er.isDataPresent()

        errors.size() == 1
        errors == KNOWN_ERRORS

        specMap.size() == 1
        specMap["errors"] == EXPECTED_SPEC_ERRORS
    }

    def "test add error building "() {
        given:
        def startEr = new ExecutionResultImpl(KNOWN_ERRORS)

        def NEW_ERRORS = [new InvalidSyntaxError(new SourceLocation(966, 964), "Yowza")]


        def er = ExecutionResultImpl.newExecutionResult().from(startEr).addErrors(NEW_ERRORS).build()
        when:
        def actual = er.getData()
        def errors = er.getErrors()
        def specMap = er.toSpecification()
        then:
        actual == null
        !er.isDataPresent()

        errors.size() == 2

        specMap.size() == 1
        specMap["errors"] == [
                ['message': 'Invalid Syntax : Yikes', 'locations': [[line: 666, column: 664]]],
                ['message': 'Invalid Syntax : Yowza', 'locations': [[line: 966, column: 964]]]
        ]
    }

    def "test add data building"() {
        given:
        def startEr = new ExecutionResultImpl(KNOWN_ERRORS)

        def er = ExecutionResultImpl.newExecutionResult().from(startEr).data("Some Data").build()

        when:
        def actual = er.getData()
        def errors = er.getErrors()

        then:
        actual == "Some Data"
        er.isDataPresent()

        errors.size() == 1
    }

    def "test setting extensions"() {
        given:
        def startEr = new ExecutionResultImpl("Some Data", KNOWN_ERRORS,null)

        def er = ExecutionResultImpl.newExecutionResult().from(startEr).extensions([ext1:"here"]).build()

        when:
        def extensions = er.getExtensions()

        then:
        extensions == [ext1:"here"]
    }

    def "test adding extension"() {
        given:
        def startEr = new ExecutionResultImpl("Some Data", KNOWN_ERRORS,[ext1:"here"])

        def er = ExecutionResultImpl.newExecutionResult().from(startEr).addExtension("ext2","aswell").build()

        when:
        def extensions = er.getExtensions()

        then:
        extensions == [ext1:"here", ext2 : "aswell"]
    }


}

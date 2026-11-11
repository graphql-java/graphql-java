package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLTypeReference.typeRef

class NoDefaultValueCircularRefsTest extends Specification {

    def "circular SDL defaults are rejected: #description"() {
        when:
        TestUtil.schema(sdl)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains(expectedMessage)

        where:
        description     | sdl | expectedMessage
        "self reference" | '''
            type Query { test(arg: A): String }
            input A { x: A = {} }
        '''             | "Invalid circular reference. The default value of Input Object field A.x references itself."
        "mutual types"  | '''
            type Query { test(arg: A): String }
            input A { b: B = {} }
            input B { a: A = {} }
        '''             | "Invalid circular reference"
        "three types"   | '''
            type Query { test(arg: B): String }
            input B { x: B2 = {} }
            input B2 { x: B3 = {} }
            input B3 { x: B = {} }
        '''             | "Invalid circular reference. The default value of Input Object field B.x references itself via the default values of: B2.x, B3.x."
        "list wrapping" | '''
            type Query { test(arg: C): String }
            input C { x: [C] = [{}] }
        '''             | "Invalid circular reference. The default value of Input Object field C.x references itself."
        "nested value"  | '''
            type Query { test(arg: D): String }
            input D { x: D = { x: { x: {} } } }
        '''             | "Invalid circular reference. The default value of Input Object field D.x references itself."
        "cross field"   | '''
            type Query { test(arg: E): String }
            input E {
                x: E = { x: null }
                y: E = { y: null }
            }
        '''             | "Invalid circular reference. The default value of Input Object field E.x references itself via the default values of: E.y."
        "non-null type" | '''
            type Query { test(arg: F): String }
            input F { x: F2! = {} }
            input F2 { x: F = { x: {} } }
        '''             | "Invalid circular reference. The default value of Input Object field F2.x references itself."
        "omitted field" | '''
            type Query { test(arg: A): String }
            input A { x: B = {name: "hi"} }
            input B {
                name: String
                a: A = {}
            }
        '''             | "Invalid circular reference. The default value of Input Object field A.x references itself via the default values of: B.a."
    }

    def "multiple independent cycles are reported"() {
        when:
        TestUtil.schema('''
            type Query { test(a: A, b: P): String }
            input A { x: A = {} }
            input P { x: P = {} }
        ''')

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains("A.x references itself")
        exception.message.contains("P.x references itself")
    }

    def "non-circular SDL defaults are accepted: #description"() {
        when:
        def schema = TestUtil.schema(sdl)

        then:
        schema.getType("A") != null

        where:
        description                 | sdl
        "explicit nested null"      | '''
            type Query { test(arg: A): String }
            input A { b: B = {a: null} }
            input B { a: A = {} }
        '''
        "recursive field unset"     | '''
            type Query { test(arg: A): String }
            input A { b: B = {} }
            input B { a: A }
        '''
        "scalar default"            | '''
            type Query { test(arg: A): String }
            input A { name: String = "hi" }
        '''
        "null default"              | '''
            type Query { test(arg: A): String }
            input A { x: A = null }
        '''
        "empty list"                | '''
            type Query { test(arg: A): String }
            input A { x: [A] = [] }
        '''
        "explicit self field null"  | '''
            type Query { test(arg: A): String }
            input A { x: A = {x: null} }
        '''
    }

    def "circular programmatic defaults are rejected: #description"() {
        when:
        buildProgrammaticSchema(defaultValue, listType)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains("Invalid circular reference")

        where:
        description  | defaultValue       | listType
        "map"        | [:]                | false
        "list"       | [[:]]              | true
        "Java array" | ([[:]] as Object[]) | true
    }

    def "explicit null breaks a programmatic default cycle"() {
        expect:
        buildProgrammaticSchema([x: null], false).getType("A") != null
    }

    private static GraphQLSchema buildProgrammaticSchema(Object defaultValue, boolean listType) {
        def recursiveType = typeRef("A")
        def fieldType = listType ? list(recursiveType) : recursiveType
        def inputType = newInputObject()
                .name("A")
                .field(newInputObjectField()
                        .name("x")
                        .type(fieldType)
                        .defaultValueProgrammatic(defaultValue))
                .build()
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("test")
                        .type(GraphQLString)
                        .argument(newArgument()
                                .name("arg")
                                .type(inputType)
                                .defaultValueProgrammatic([:])))
                .build()
        return newSchema().query(queryType).build()
    }
}

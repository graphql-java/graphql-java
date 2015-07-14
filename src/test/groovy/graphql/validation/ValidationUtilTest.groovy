package graphql.validation

import graphql.language.*
import graphql.schema.*
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.validation.ValidationUtil.isValidLiteralValue

class ValidationUtilTest extends Specification {

    def "null and NonNull is invalid"() {
        expect:
        !isValidLiteralValue(null, new GraphQLNonNull(GraphQLString))
    }

    def "a nonNull value for a NonNull type is valid"() {
        expect:
        isValidLiteralValue(new StringValue("string"), new GraphQLNonNull(GraphQLString))
    }

    def "null is valid when type is NonNull"() {
        expect:
        isValidLiteralValue(null, GraphQLString)
    }

    def "variables are valid"() {
        expect:
        isValidLiteralValue(new VariableReference("var"), GraphQLBoolean)
    }

    def "ArrayValue and ListType is invalid when one entry is invalid"() {
        given:
        def arrayValue = new ArrayValue([new BooleanValue(true)])
        def type = new GraphQLList(GraphQLString)

        expect:
        !isValidLiteralValue(arrayValue, type)
    }

    def "One value is a single element List"() {
        given:
        def singleValue = new BooleanValue(true)
        def type = new GraphQLList(GraphQLBoolean)
        expect:
        isValidLiteralValue(singleValue, type)
    }

    def "a valid array"() {
        given:
        def arrayValue = new ArrayValue([new StringValue("hello")])
        def type = new GraphQLList(GraphQLString)

        expect:
        isValidLiteralValue(arrayValue, type)
    }

    def "a valid scalar"() {
        given:
        expect:
        isValidLiteralValue(new BooleanValue(true), GraphQLBoolean)
    }

    def "invalid scalar"() {
        given:
        expect:
        !isValidLiteralValue(new BooleanValue(true), GraphQLString)
    }

    def "valid enum"() {
        given:
        def enumType = GraphQLEnumType.newEnum().value("PLUTO").build()

        expect:
        isValidLiteralValue(new EnumValue("PLUTO"), enumType)
    }

    def "invalid enum"() {
        given:
        def enumType = GraphQLEnumType.newEnum().value("PLUTO").build()
        expect:
        !isValidLiteralValue(new StringValue("MARS"), enumType)
    }

    def "a valid ObjectValue"() {
        given:
        def inputObjecType = GraphQLInputObjectType.newInputObject()
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("hello")
                .type(GraphQLString)
                .build())
                .build()
        def objectValue = new ObjectValue()
        objectValue.getObjectFields().add(new ObjectField("hello", new StringValue("world")))

        expect:
        isValidLiteralValue(objectValue, inputObjecType)
    }

    def "a invalid ObjectValue with a invalid field"() {
        given:
        def inputObjecType = GraphQLInputObjectType.newInputObject()
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("hello")
                .type(GraphQLString)
                .build())
                .build()
        def objectValue = new ObjectValue()
        objectValue.getObjectFields().add(new ObjectField("hello", new BooleanValue(false)))

        expect:
        !isValidLiteralValue(objectValue, inputObjecType)
    }

    def "a invalid ObjectValue with a missing field"() {
        given:
        def inputObjecType = GraphQLInputObjectType.newInputObject()
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("hello")
                .type(new GraphQLNonNull(GraphQLString))
                .build())
                .build()
        def objectValue = new ObjectValue()

        expect:
       !isValidLiteralValue(objectValue, inputObjecType)
    }
}

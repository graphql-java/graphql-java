package graphql.validation

import graphql.language.*
import graphql.schema.*
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class ValidationUtilTest extends Specification {

    def validationUtil = new ValidationUtil()

    def "getUnmodified type of list of nonNull"() {
        given:
        def unmodifiedType = new TypeName("String")
        expect:
        validationUtil.getUnmodifiedType(new ListType(new NonNullType(unmodifiedType))) == unmodifiedType
    }

    def "getUnmodified type of string"() {
        given:
        def unmodifiedType = new TypeName("String")
        expect:
        validationUtil.getUnmodifiedType(unmodifiedType) == unmodifiedType
    }

    def "getUnmodified type of nonNull"() {
        given:
        def unmodifiedType = new TypeName("String")
        expect:
        validationUtil.getUnmodifiedType(new NonNullType(unmodifiedType)) == unmodifiedType
    }

    def "null and NonNull is invalid"() {
        expect:
        !validationUtil.isValidLiteralValue(null, nonNull(GraphQLString))
    }

    def "NullValue and NonNull is invalid"() {
        expect:
        !validationUtil.isValidLiteralValue(NullValue.Null, nonNull(GraphQLString))
    }

    def "a nonNull value for a NonNull type is valid"() {
        expect:
        validationUtil.isValidLiteralValue(new StringValue("string"), nonNull(GraphQLString))
    }

    def "null is valid when type is NonNull"() {
        expect:
        validationUtil.isValidLiteralValue(null, GraphQLString)
    }

    def "NullValue is valid when type is NonNull"() {
        expect:
        validationUtil.isValidLiteralValue(NullValue.Null, GraphQLString)
    }

    def "variables are valid"() {
        expect:
        validationUtil.isValidLiteralValue(new VariableReference("var"), GraphQLBoolean)
    }

    def "ArrayValue and ListType is invalid when one entry is invalid"() {
        given:
        def arrayValue = new ArrayValue([new BooleanValue(true)])
        def type = list(GraphQLString)

        expect:
        !validationUtil.isValidLiteralValue(arrayValue, type)
    }

    def "One value is a single element List"() {
        given:
        def singleValue = new BooleanValue(true)
        def type = list(GraphQLBoolean)
        expect:
        validationUtil.isValidLiteralValue(singleValue, type)
    }

    def "a valid array"() {
        given:
        def arrayValue = new ArrayValue([new StringValue("hello")])
        def type = list(GraphQLString)

        expect:
        validationUtil.isValidLiteralValue(arrayValue, type)
    }

    def "a valid scalar"() {
        given:
        expect:
        validationUtil.isValidLiteralValue(new BooleanValue(true), GraphQLBoolean)
    }

    def "invalid scalar"() {
        given:
        expect:
        !validationUtil.isValidLiteralValue(new BooleanValue(true), GraphQLString)
    }

    def "valid enum"() {
        given:
        def enumType = GraphQLEnumType.newEnum().name("enumType").value("PLUTO").build()

        expect:
        validationUtil.isValidLiteralValue(new EnumValue("PLUTO"), enumType)
    }

    def "invalid enum value"() {
        given:
        def enumType = GraphQLEnumType.newEnum().name("enumType").value("PLUTO").build()
        expect:
        !validationUtil.isValidLiteralValue(new StringValue("MARS"), enumType)
    }

    def "invalid enum name"() {
        given:
        def enumType = GraphQLEnumType.newEnum().name("enumType").value("PLUTO").build()
        expect:
        !validationUtil.isValidLiteralValue(new EnumValue("MARS"), enumType)
    }

    def "a valid ObjectValue"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("hello")
                .type(GraphQLString))
                .build()
        def objectValue = new ObjectValue()
        objectValue.getObjectFields().add(new ObjectField("hello", new StringValue("world")))

        expect:
        validationUtil.isValidLiteralValue(objectValue, inputObjectType)
    }

    def "a invalid ObjectValue with a invalid field"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("hello")
                .type(GraphQLString))
                .build()
        def objectValue = new ObjectValue()
        objectValue.getObjectFields().add(new ObjectField("hello", new BooleanValue(false)))

        expect:
        !validationUtil.isValidLiteralValue(objectValue, inputObjectType)
    }

    def "a invalid ObjectValue with a missing field"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("hello")
                .type(nonNull(GraphQLString)))
                .build()
        def objectValue = new ObjectValue()

        expect:
        !validationUtil.isValidLiteralValue(objectValue, inputObjectType)
    }
}

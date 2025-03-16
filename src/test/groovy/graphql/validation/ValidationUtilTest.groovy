package graphql.validation

import graphql.GraphQLContext
import graphql.StarWarsSchema
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.VariableReference
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Directives.OneOfDirective
import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.language.ObjectField.newObjectField
import static graphql.schema.GraphQLAppliedDirective.newDirective
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class ValidationUtilTest extends Specification {

    def schema = GraphQLSchema.newSchema()
            .query(StarWarsSchema.queryType)
            .codeRegistry(StarWarsSchema.codeRegistry)
            .build()
    def validationUtil = new ValidationUtil()
    def graphQLContext = GraphQLContext.getDefault()
    def locale = Locale.getDefault()

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
        !validationUtil.isValidLiteralValue(null, nonNull(GraphQLString), schema, graphQLContext, locale)
    }

    def "NullValue and NonNull is invalid"() {
        expect:
        !validationUtil.isValidLiteralValue(NullValue.newNullValue().build(), nonNull(GraphQLString), schema, graphQLContext, locale)
    }

    def "a nonNull value for a NonNull type is valid"() {
        expect:
        validationUtil.isValidLiteralValue(new StringValue("string"), nonNull(GraphQLString), schema, graphQLContext, locale)
    }

    def "null is valid when type is NonNull"() {
        expect:
        validationUtil.isValidLiteralValue(null, GraphQLString, schema, graphQLContext, locale)
    }

    def "NullValue is valid when type is NonNull"() {
        expect:
        validationUtil.isValidLiteralValue(NullValue.newNullValue().build(), GraphQLString, schema, graphQLContext, locale)
    }

    def "variables are valid"() {
        expect:
        validationUtil.isValidLiteralValue(new VariableReference("var"), GraphQLBoolean, schema, graphQLContext, locale)
    }

    def "ArrayValue and ListType is invalid when one entry is invalid"() {
        given:
        def arrayValue = new ArrayValue([new BooleanValue(true)])
        def type = list(GraphQLString)

        expect:
        !validationUtil.isValidLiteralValue(arrayValue, type, schema, graphQLContext, locale)
    }

    def "One value is a single element List"() {
        given:
        def singleValue = new BooleanValue(true)
        def type = list(GraphQLBoolean)
        expect:
        validationUtil.isValidLiteralValue(singleValue, type, schema, graphQLContext, locale)
    }

    def "a valid array"() {
        given:
        def arrayValue = new ArrayValue([new StringValue("hello")])
        def type = list(GraphQLString)

        expect:
        validationUtil.isValidLiteralValue(arrayValue, type, schema, graphQLContext, locale)
    }

    def "a valid scalar"() {
        given:
        expect:
        validationUtil.isValidLiteralValue(new BooleanValue(true), GraphQLBoolean, schema, graphQLContext, locale)
    }

    def "invalid scalar"() {
        given:
        expect:
        !validationUtil.isValidLiteralValue(new BooleanValue(true), GraphQLString, schema, graphQLContext, locale)
    }

    def "valid enum"() {
        given:
        def enumType = GraphQLEnumType.newEnum().name("enumType").value("PLUTO").build()

        expect:
        validationUtil.isValidLiteralValue(new EnumValue("PLUTO"), enumType, schema, graphQLContext, locale)
    }

    def "invalid enum value"() {
        given:
        def enumType = GraphQLEnumType.newEnum().name("enumType").value("PLUTO").build()
        expect:
        !validationUtil.isValidLiteralValue(new StringValue("MARS"), enumType, schema, graphQLContext, locale)
    }

    def "invalid enum name"() {
        given:
        def enumType = GraphQLEnumType.newEnum().name("enumType").value("PLUTO").build()
        expect:
        !validationUtil.isValidLiteralValue(new EnumValue("MARS"), enumType, schema, graphQLContext, locale)
    }

    def "a valid ObjectValue"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("hello")
                        .type(GraphQLString))
                .build()
        def objectValue = ObjectValue.newObjectValue()
        objectValue.objectField(new ObjectField("hello", new StringValue("world")))

        expect:
        validationUtil.isValidLiteralValueForInputObjectType(objectValue.build(), inputObjectType, schema, graphQLContext, locale)
    }

    def "a invalid ObjectValue with a invalid field"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("hello")
                        .type(GraphQLString))
                .build()
        def objectValue = ObjectValue.newObjectValue()
        objectValue.objectField(new ObjectField("hello", new BooleanValue(false)))

        expect:
        !validationUtil.isValidLiteralValueForInputObjectType(objectValue.build(), inputObjectType, schema, graphQLContext, locale)
    }

    def "a invalid ObjectValue with a missing field"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("hello")
                        .type(nonNull(GraphQLString)))
                .build()
        def objectValue = ObjectValue.newObjectValue().build()

        expect:
        !validationUtil.isValidLiteralValueForInputObjectType(objectValue, inputObjectType, schema, graphQLContext, locale)
    }

    def "a valid ObjectValue with a nonNull field and default value"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("hello")
                        .type(nonNull(GraphQLString))
                        .defaultValueProgrammatic("default"))
                .build()
        def objectValue = ObjectValue.newObjectValue()

        expect:
        validationUtil.isValidLiteralValueForInputObjectType(objectValue.build(), inputObjectType, schema, graphQLContext, locale)
    }

    def "a valid @oneOf input literal"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .withAppliedDirective(newDirective().name(OneOfDirective.getName()))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("f1")
                        .type(GraphQLString))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("f2")
                        .type(GraphQLString))
                .build()
        def objectValue = ObjectValue.newObjectValue()
                .objectField(newObjectField().name("f1").value(StringValue.of("v1")).build())
                .build()

        expect:
        validationUtil.isValidLiteralValueForInputObjectType(objectValue, inputObjectType, schema, graphQLContext, locale)
    }

    def "an invalid @oneOf input literal"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .withAppliedDirective(newDirective().name(OneOfDirective.getName()))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("f1")
                        .type(GraphQLString))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("f2")
                        .type(GraphQLString))
                .build()

        def objectValue = ObjectValue.newObjectValue()
                .objectField(newObjectField().name("f1").value(StringValue.of("v1")).build())
                .objectField(newObjectField().name("f2").value(StringValue.of("v2")).build())
                .build()

        expect:
        !validationUtil.isValidLiteralValueForInputObjectType(objectValue, inputObjectType, schema, graphQLContext, locale)
    }
}

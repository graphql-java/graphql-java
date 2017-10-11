package graphql.schema

import spock.lang.Specification

import java.util.function.Supplier

class GraphQLMetadataSupportTest extends Specification {


    def map = [
            "key1": "value1",
            "key2": "value2"
    ]

    Supplier<Map<String, Object>> standardData() {
        { -> map }
    }

    def assertExpectedMetaData(GraphQLMetadataSupport type) {
        def metadata = type.getMetadata()
        assert metadata == [
                "key1"     : "value1",
                "key2"     : "value2",
                "directKey": "directValue"
        ]
        true
    }

    def "basic support"() {
        given:

        def coercing = new Coercing() {

            @Override
            Object serialize(Object dataFetcherResult) {
                return null
            }

            @Override
            Object parseValue(Object input) {
                return null
            }

            @Override
            Object parseLiteral(Object input) {
                return null
            }

        }

        GraphQLMetadataSupport metaDataType

        when:
        metaDataType = GraphQLScalarType.newScalar()
                .metadata(standardData().get())
                .metadata("directKey", "directValue")
                .name("name").coercing(coercing).build()

        then:
        assertExpectedMetaData(metaDataType)

        when:

        metaDataType = GraphQLObjectType.newObject()
                .metadata(standardData().get())
                .metadata("directKey", "directValue")
                .name("name").build()

        then:
        assertExpectedMetaData(metaDataType)

        when:

        metaDataType = GraphQLInputObjectType.newInputObject()
                .metadata(standardData().get())
                .metadata("directKey", "directValue")
                .name("name").build()

        then:
        assertExpectedMetaData(metaDataType)

        when:

        metaDataType = GraphQLInterfaceType.newInterface()
                .metadata(standardData().get())
                .metadata("directKey", "directValue")
                .typeResolver({ -> null })
                .name("name").build()

        then:
        assertExpectedMetaData(metaDataType)

        when:

        metaDataType = GraphQLUnionType.newUnionType()
                .metadata(standardData().get())
                .metadata("directKey", "directValue")
                .possibleType(GraphQLObjectType.newObject().name("foo").build())
                .typeResolver({ -> null })
                .name("name").build()

        then:
        assertExpectedMetaData(metaDataType)

        when:

        metaDataType = GraphQLEnumType.newEnum()
                .metadata(standardData().get())
                .metadata("directKey", "directValue")
                .name("name").build()

        then:
        assertExpectedMetaData(metaDataType)

    }
}

package graphql


import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType
import graphql.schema.PropertyDataFetcher
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment

class DataFetcherTest extends Specification {

    @SuppressWarnings("GroovyUnusedDeclaration")
    class DataHolder {

        private String privateField
        public String publicField
        private Boolean booleanField
        private Boolean booleanFieldWithGet

        String getProperty() {
            return privateField
        }

        void setProperty(String value) {
            privateField = value
        }

        Boolean isBooleanField() {
            return booleanField
        }

        void setBooleanField(Boolean value) {
            booleanField = value
        }

        Boolean getBooleanFieldWithGet() {
            return booleanFieldWithGet
        }

        Boolean setBooleanFieldWithGet(Boolean value) {
            booleanFieldWithGet = value
        }
    }

    DataHolder dataHolder

    def setup() {
        dataHolder = new DataHolder()
        dataHolder.publicField = "publicValue"
        dataHolder.setProperty("propertyValue")
        dataHolder.setBooleanField(true)
        dataHolder.setBooleanFieldWithGet(false)

    }

    def env(String propertyName, GraphQLOutputType type) {
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name(propertyName).type(type).build()
        newDataFetchingEnvironment().source(dataHolder).fieldType(type).fieldDefinition(fieldDefinition).build()
    }

    def "get property value"() {
        given:
        def environment = env("property", GraphQLString)
        when:
        def result = fetcher.get(environment)
        then:
        result == "propertyValue"

        where:
        fetcher                             | _
        new PropertyDataFetcher("property") | _
        PropertyDataFetcher.singleton()     | _
    }

    def "get Boolean property value"() {
        given:
        def environment = env("booleanField", GraphQLBoolean)
        when:
        def result = fetcher.get(environment)
        then:
        result == true

        where:
        fetcher                                 | _
        new PropertyDataFetcher("booleanField") | _
        PropertyDataFetcher.singleton()         | _
    }

    def "get Boolean property value with get"() {
        given:
        def environment = env("booleanFieldWithGet", GraphQLBoolean)
        when:
        def result = fetcher.get(environment)
        then:
        result == false

        where:
        fetcher                                        | _
        new PropertyDataFetcher("booleanFieldWithGet") | _
        PropertyDataFetcher.singleton()                | _
    }

    def "get public field value as property"() {
        given:
        def environment = env("publicField", GraphQLString)
        when:
        def result = fetcher.get(environment)
        then:
        result == "publicValue"

        where:
        fetcher                                | _
        new PropertyDataFetcher("publicField") | _
        PropertyDataFetcher.singleton()        | _
    }
}

package graphql

import graphql.execution.ExecutionContext
import graphql.schema.GraphQLOutputType
import graphql.schema.PropertyDataFetcher
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment

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

    def env(GraphQLOutputType type) {
        newDataFetchingEnvironment().source(dataHolder).executionContext(Mock(ExecutionContext)).fieldType(type).build()
    }

    def "get property value"() {
        given:
        def environment = env(GraphQLString)
        when:
        def result = new PropertyDataFetcher("property").get(environment)
        then:
        result == "propertyValue"
    }

    def "get Boolean property value"() {
        given:
        def environment = env(GraphQLBoolean)
        when:
        def result = new PropertyDataFetcher("booleanField").get(environment)
        then:
        result == true
    }

    def "get Boolean property value with get"() {
        given:
        def environment = env(GraphQLBoolean)
        when:
        def result = new PropertyDataFetcher("booleanFieldWithGet").get(environment)
        then:
        result == false
    }

    def "get public field value as property"() {
        given:
        def environment = env(GraphQLString)
        when:
        def result = new PropertyDataFetcher("publicField").get(environment)
        then:
        result == "publicValue"
    }
}

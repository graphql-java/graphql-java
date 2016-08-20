package graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldDataFetcher
import graphql.schema.PropertyDataFetcher
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString

class DataFetcherTest extends Specification {

    class DataHolder {

        private String privateField
        public String publicField
        private Boolean booleanField
        private Boolean booleanFieldWithGet

        public String getProperty() {
            return privateField
        }

        public void setProperty(String value) {
            privateField = value
        }

        public Boolean isBooleanField() {
            return booleanField;
        }

        public void setBooleanField(Boolean value) {
            booleanField = value
        }

        public Boolean getBooleanFieldWithGet() {
            return booleanFieldWithGet
        }

        public Boolean setBooleanFieldWithGet(Boolean value) {
            booleanFieldWithGet = value
        }
    }
    def DataHolder dataHolder

    def setup() {
        dataHolder = new DataHolder();
        dataHolder.publicField = "publicValue"
        dataHolder.setProperty("propertyValue")
        dataHolder.setBooleanField(true)
        dataHolder.setBooleanFieldWithGet(false)
    }

    def "get field value"() {
        given:
        def environment = new DataFetchingEnvironment(dataHolder, null, null, null, GraphQLString, null, null)
        when:
        def result = new FieldDataFetcher("publicField").get(environment)
        then:
        result == "publicValue"
    }

    def "get property value"() {
        given:
        def environment = new DataFetchingEnvironment(dataHolder, null, null, null, GraphQLString, null, null)
        when:
        def result = new PropertyDataFetcher("property").get(environment)
        then:
        result == "propertyValue"
    }

    def "get Boolean property value"() {
        given:
        def environment = new DataFetchingEnvironment(dataHolder, null, null, null, GraphQLBoolean, null, null)
        when:
        def result = new PropertyDataFetcher("booleanField").get(environment)
        then:
        result == true
    }

    def "get Boolean property value with get"() {
        given:
        def environment = new DataFetchingEnvironment(dataHolder, null, null, null, GraphQLBoolean, null, null)
        when:
        def result = new PropertyDataFetcher("booleanFieldWithGet").get(environment)
        then:
        result == false
    }

    def "get field value as property"() {
        given:
        def environment = new DataFetchingEnvironment(dataHolder, null, null, null, GraphQLString, null, null)
        when:
        def result = new PropertyDataFetcher("publicField").get(environment)
        then:
        result == "publicValue"
    }
}

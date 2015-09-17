package graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldDataFetcher
import graphql.schema.PropertyDataFetcher
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class DataFetcherTest extends Specification {

    class DataHolder {

        private String privateField
        public String publicField

        public String getProperty() {
            return privateField
        }

        public void setProperty(String value) {
            privateField = value
        }
    }
    def DataHolder dataHolder

    def setup() {
        dataHolder = new DataHolder();
        dataHolder.publicField = "publicValue"
        dataHolder.setProperty("propertyValue")
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
}

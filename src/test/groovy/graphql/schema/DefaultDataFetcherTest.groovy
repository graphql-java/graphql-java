package graphql.schema

import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class DefaultDataFetcherTest extends Specification {

    def mapFetcher = Mock(MapDataFetcher)
    def propertyFetcher = Mock(PropertyDataFetcher)
    def fieldFetcher = Mock(FieldDataFetcher)
    def defaultFetcher = new DefaultDataFetcher(mapFetcher, propertyFetcher, fieldFetcher)

    def "fetch value from `Map` source"() {
        given:
        def env = new DataFetchingEnvironment([:], null, null, null, GraphQLString, null, null)

        when:
        defaultFetcher.get(env)

        then:
        1 * mapFetcher.getValue([:])
        0 * propertyFetcher.getProperty()
        0 * fieldFetcher.getField()
    }

    class PropertySource {
        private String field = "value"
        public String getProperty() { return field }
    }

    def "fetch property from source"() {
        given:
        def source = new PropertySource();
        def env = new DataFetchingEnvironment(source, null, null, null, GraphQLString, null, null)

        when:
        defaultFetcher.get(env)

        then:
        0 * mapFetcher.getValue()
        1 * propertyFetcher.getProperty(source)
        0 * fieldFetcher.getField()
    }

    class FieldSource {
        public String field = "value"
    }

    def "fetch field from source"() {
        given:
        def source = new FieldSource();
        def env = new DataFetchingEnvironment(source, null, null, null, GraphQLString, null, null)

        when:
        defaultFetcher.get(env)

        then:
        0 * mapFetcher.getValue()
        propertyFetcher.getProperty(_) >> { throw new NoSuchMethodException() }
        1 * fieldFetcher.getField(source)
    }

}

package graphql.schema

import spock.lang.Specification

class CachedDataFetcherFactoryTest extends Specification {

    def "delegate is only called once"() {
        given:
        def delegate = Mock(DataFetcherFactory)
        def cachedDFF = new CachedDataFetcherFactory(delegate)
        def environment = Mock(DataFetcherFactoryEnvironment)
        def result = Mock(DataFetcher)
        when:
        def returnedDF = cachedDFF.get(environment)
        then:
        returnedDF == result
        1 * delegate.get(environment) >> result

        when:
        returnedDF = cachedDFF.get(environment)
        then:
        returnedDF == result
        0 * delegate.get(environment)
    }
}

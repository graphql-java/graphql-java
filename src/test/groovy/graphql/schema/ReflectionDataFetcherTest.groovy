package graphql.schema

import static graphql.Scalars.GraphQLString
import spock.lang.Specification

class ReflectionDataFetcherTest extends Specification {

    static final EXPECTED = "expected"

    static final String SOME_SUPER_FIELD = "someSuperField";

    def environment = Mock(DataFetchingEnvironment);

    def "setup"(){
        environment.getSource() >> new TestObject(EXPECTED);
    }

    def "testVariousImplementations"(DataFetcher dataFetcher) {

        when:
        String actual=dataFetcher.get(environment);

        then:
        EXPECTED==actual;

        where:
        dataFetcher << [
            new PropertyDataFetcher(SOME_SUPER_FIELD),
            new FieldDataFetcher(SOME_SUPER_FIELD)
        ]
    }
}

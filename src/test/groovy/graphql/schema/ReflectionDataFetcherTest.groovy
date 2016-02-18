package graphql.schema

import static graphql.Scalars.GraphQLString
import spock.lang.Specification

class ReflectionDataFetcherTest extends Specification {

    static final EXPECTED = "expected"

    static final String SOME_SUPER_FIELD = "someSuperField"

    private static final String SOME_SUPER_FIELD_SNAKE_CASE = "some_super_field";

    def environment = Mock(DataFetchingEnvironment);

    def fieldDefinition

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
            GraphQLFieldDefinition.newFieldDefinition().nameAndFieldName(SOME_SUPER_FIELD_SNAKE_CASE,SOME_SUPER_FIELD).type(GraphQLString).build().getDataFetcher(),
            GraphQLFieldDefinition.newFieldDefinition().nameAndFieldName(SOME_SUPER_FIELD_SNAKE_CASE,SOME_SUPER_FIELD).type(GraphQLString).fetchField().build().getDataFetcher(),
            GraphQLFieldDefinition.newFieldDefinition().name(SOME_SUPER_FIELD).type(GraphQLString).build().getDataFetcher(),
            GraphQLFieldDefinition.newFieldDefinition().name(SOME_SUPER_FIELD).type(GraphQLString).fetchField().build().getDataFetcher()
        ]
    }
}

package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective

class GraphQLFieldDefinitionTest extends Specification {

    def "dataFetcher can't be null"() {
        when:
        GraphQLFieldDefinition.newFieldDefinition().dataFetcher(null)
        then:
        def exception = thrown(AssertException)
        exception.getMessage().contains("dataFetcher")
    }

    def "data fetchers will use @fetch directive if present"() {

        def dataFetchingEnvironment = DataFetchingEnvironmentBuilder.newDataFetchingEnvironment().source(
                ["propertyX": "valueX",
                 "propertyY": "valueY"]).build()

        when:
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
                .name("propertyX")
                .type(GraphQLString)
                .build()

        def fetcher = fieldDefinition.getDataFetcher()
        def get = fetcher.get(dataFetchingEnvironment)

        then:
        get == "valueX"

        when:
        fieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
                .name("propertyX")
                .type(GraphQLString)
                .withDirectives(
                // in SDL this would be
                //
                //      propertyX : String @fetch(from : "propertyY")
                //
                newDirective()
                        .name("fetch")
                        .argument(
                        newArgument()
                                .name("from")
                                .type(GraphQLString)
                                .defaultValue("propertyY")
                ).build()
        ).build()

        fetcher = fieldDefinition.getDataFetcher()
        get = fetcher.get(dataFetchingEnvironment)

        then:
        get == "valueY"

        when:
        // missing the from name part
        fieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
                .name("propertyX")
                .type(GraphQLString)
                .withDirectives(
                //
                //      propertyX : String @fetch
                //
                newDirective()
                        .name("fetch")
                        .build()
        ).build()

        fetcher = fieldDefinition.getDataFetcher()
        get = fetcher.get(dataFetchingEnvironment)

        then:
        get == "valueX"

    }
}

package graphql


import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.PropertyDataFetcher
import graphql.schema.SingletonPropertyDataFetcher
import spock.lang.Specification

class LargeSchemaDataFetcherTest extends Specification {

    def howManyFields = 100_000

    def "large schema with lots of fields has the same property data fetcher by default"() {
        def sdl = """
            type Query {
                ${mkFields()}
            }
        """

        when:
        def schema = TestUtil.schema(sdl)
        def codeRegistry = schema.getCodeRegistry()
        def singletonDF = SingletonPropertyDataFetcher.singleton()

        then:

        for (int i = 0; i < howManyFields; i++) {
            def fieldName = "f$i"
            def fieldDef = GraphQLFieldDefinition.newFieldDefinition().name(fieldName).type(Scalars.GraphQLString).build()
            def df = codeRegistry.getDataFetcher(FieldCoordinates.coordinates("Query", fieldName), fieldDef)
            assert df == singletonDF
        }
    }

    def mkFields() {
        StringBuilder sb = new StringBuilder()
        for (int i = 0; i < howManyFields; i++) {
            sb.append("f$i : String\n")
        }
        return sb.toString()
    }
}

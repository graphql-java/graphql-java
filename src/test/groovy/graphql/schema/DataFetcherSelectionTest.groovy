package graphql.schema

import graphql.GraphQL
import graphql.StarWarsData
import graphql.execution.FieldCollector
import graphql.language.AstPrinter
import graphql.language.Field
import graphql.schema.idl.MapEnumValuesProvider
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

import java.util.stream.Collectors

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

/**
 * #377 - this test proves that a data fetcher has enough information to allow sub request
 *  proxying or fine grained control of the fields in an object that should be returned
 */
class DataFetcherSelectionTest extends Specification {

    GraphQLSchema load(String fileName, RuntimeWiring wiring) {
        def stream = getClass().getClassLoader().getResourceAsStream(fileName)

        def typeRegistry = new SchemaParser().parse(new InputStreamReader(stream))
        def schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring)
        schema
    }

    class SelectionCapturingDataFetcher implements DataFetcher {
        final DataFetcher delegate
        final FieldCollector fieldCollector
        final Map<String, String> captureMap

        SelectionCapturingDataFetcher(DataFetcher delegate, Map<String, String> captureMap) {
            this.delegate = delegate
            this.fieldCollector = new FieldCollector()
            this.captureMap = captureMap
        }

        @Override
        Object get(DataFetchingEnvironment environment) {

            //
            // we capture the inner field selections of the current field that is being fetched
            // this would allow proxying or really fine grained controlled object retrieval
            // if one was so included
            //
            def selectionSet = environment.getSelectionSet().get()

            if (!selectionSet.isEmpty()) {
                String subSelection = captureSubSelection(selectionSet)
                def path = environment.getFieldTypeInfo().getPath().toString()
                captureMap.put(path, subSelection)
            }

            return delegate.get(environment)
        }

        String captureSubSelection(Map<String, List<Field>> fields) {
            return fields.values().stream().map({ f -> captureFields(f) }).collect(Collectors.joining("\n"))
        }

        String captureFields(List<Field> fields) {
            return fields.stream().map({ f -> AstPrinter.printAst(f) }).collect(Collectors.joining("\n"))
        }
    }

    // side effect captured here
    Map<String, String> captureMap = new HashMap<>()

    SelectionCapturingDataFetcher captureSelection(DataFetcher delegate) {
        return new SelectionCapturingDataFetcher(delegate, captureMap)
    }

    def episodeValuesProvider = new MapEnumValuesProvider([NEWHOPE: 4, EMPIRE: 5, JEDI: 6])
    def episodeWiring = newTypeWiring("Episode").enumValues(episodeValuesProvider).build()

    RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("QueryType")
            .dataFetchers(
            [
                    "hero" : captureSelection(new StaticDataFetcher(StarWarsData.getArtoo())),
                    "human": captureSelection(StarWarsData.getHumanDataFetcher()),
                    "droid": captureSelection(StarWarsData.getDroidDataFetcher())
            ])
    )
            .type(newTypeWiring("Human")
            .dataFetcher("friends", captureSelection(StarWarsData.getFriendsDataFetcher()))
    )
            .type(newTypeWiring("Droid")
            .dataFetcher("friends", captureSelection(StarWarsData.getFriendsDataFetcher()))
    )

            .type(newTypeWiring("Character")
            .typeResolver(StarWarsData.getCharacterTypeResolver())
    )
            .type(episodeWiring)
            .build()

    def executableStarWarsSchema = load("starWarsSchema.graphqls", wiring)

    def "field selection can be captured via data environment"() {

        captureMap.clear()

        def query = """
        query CAPTURED_VIA_DF {
        
            luke: human(id: "1000") {
                ...HumanFragment             # this is a named fragment
                homePlanet
            }
            
            leia: human(id: "1003") {
                ... on Character {          # this is an inline fragment
                    id 
                    friends {
                        name
                    }
                }
                appearsIn
            }
        }
        fragment HumanFragment on Human {
            name
            ...FriendsAndFriendsFragment
            
        }
        
        fragment FriendsAndFriendsFragment on Character {
            friends {
                name 
                friends {
                    name
                }
           }
        }
        
        """


        expect:
        when:
        GraphQL.newGraphQL(executableStarWarsSchema).build().execute(query).data

        then:

        // captures each stage as it descends
        captureMap == [
                "/luke"        : "name\n" +
                        "friends {\n" +
                        "  name\n" +
                        "  friends {\n" +
                        "    name\n" +
                        "  }\n" +
                        "}\n" +
                        "homePlanet",

                "/luke/friends": "name\n" +
                        "friends {\n" +
                        "  name\n" +
                        "}",

                "/leia"        : "id\n" +
                        "friends {\n" +
                        "  name\n" +
                        "}\n" +
                        "appearsIn",

                "/leia/friends": "name",
        ]
    }

    def "#595 - field selection works for List types"() {

        captureMap.clear()

        def query = """
        query CAPTURED_VIA_DF {
            luke: human(id: "1000") {
                name
                friends {
                    name
                }
                homePlanet
            }
        }
        """


        expect:
        when:
        GraphQL.newGraphQL(executableStarWarsSchema).build().execute(query).data

        then:

        captureMap == [
                "/luke"        : "name\n" +
                        "friends {\n" +
                        "  name\n" +
                        "}\n" +
                        "homePlanet",

                "/luke/friends": "name"
        ]

    }

}

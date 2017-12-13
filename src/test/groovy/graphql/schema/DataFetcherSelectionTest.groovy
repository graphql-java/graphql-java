package graphql.schema

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsData
import graphql.TestUtil
import graphql.execution.FieldCollector
import graphql.language.AstPrinter
import graphql.language.Field
import graphql.schema.idl.MapEnumValuesProvider
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import java.util.stream.Collectors

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

/**
 * #377 - this test proves that a data fetcher has enough information to allow sub request
 *  proxying or fine grained control of the fields in an object that should be returned
 */
class DataFetcherSelectionTest extends Specification {

    class SelectionCapturingDataFetcher implements DataFetcher {
        final DataFetcher delegate
        final FieldCollector fieldCollector
        final Map<String, String> captureMap

        final List<Map<String, Map<String, Object>>> captureFieldArgs

        SelectionCapturingDataFetcher(DataFetcher delegate, Map<String, String> captureMap, List<Map<String, Map<String, Object>>> captureFieldArgs) {
            this.delegate = delegate
            this.fieldCollector = new FieldCollector()
            this.captureMap = captureMap
            this.captureFieldArgs = captureFieldArgs;
        }

        @Override
        Object get(DataFetchingEnvironment environment) {

            //
            // we capture the inner field selections of the current field that is being fetched
            // this would allow proxying or really fine grained controlled object retrieval
            // if one was so included
            //
            def selectionSet = environment.getSelectionSet().get()
            def arguments = environment.getSelectionSet().getArguments()
            captureFieldArgs.add(arguments)

            if (!selectionSet.isEmpty()) {
                for (String fieldName : selectionSet.keySet()) {
                    String ast = captureFields(selectionSet.get(fieldName))
                    captureMap.put(fieldName, ast)
                }
            }

            return delegate.get(environment)
        }

        String captureFields(List<Field> fields) {
            def collect = fields.stream().map({ f -> AstPrinter.printAst(f) }).collect(Collectors.joining(";"))
            return collect
        }
    }

    // side effect captured here
    Map<String, String> captureMap = new HashMap<>()
    List<Map<String, Map<String, Object>>> captureFieldArgs = []

    SelectionCapturingDataFetcher captureSelection(DataFetcher delegate) {
        return new SelectionCapturingDataFetcher(delegate, captureMap, captureFieldArgs)
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

    def executableStarWarsSchema = TestUtil.schemaFile("starWarsSchemaWithArguments.graphqls", wiring)

    void setup() {
        captureMap.clear()
        captureFieldArgs.clear()

    }

    def "field selection can be captured via data environment"() {

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
                "appearsIn"           : "appearsIn",
                "friends/friends/name": "name",
                "friends/friends"     : "friends {\n" +
                        "  name\n" +
                        "}",
                "friends/name"        : "name",
                "friends"             : "friends {\n" +
                        "  name\n" +
                        "}",
                "homePlanet"          : "homePlanet",
                "id"                  : "id",
                "name"                : "name"

        ]
    }

    def "#595 - field selection works for List types"() {

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
                "name"        : "name",
                "friends"     : "friends {\n" +
                        "  name\n" +
                        "}",
                "friends/name": "name",
                "homePlanet"  : "homePlanet",
        ]
    }

    def "#832 - field selection captures field arguments"() {

        def query = '''
        query CAPTURED_VIA_DF($localeVar : String) {
            luke: human(id: "1000") {
                name
                homePlanet(coordsFormat: "republic")
            }

            leia: human(id: "1003") {
                ... on Human {          # this is an inline fragment
                    name
                    homePlanet(includeMoons : true, locale: $localeVar)
                }
            }

            vader: human(id: "1003") {
                ...CharacterFragment           # this is an named fragment
            }
        }
        
        fragment CharacterFragment on Character {
                    name
                    friends(separationCount : 4) {
                        id
                    }
        }

        '''


        expect:
        when:
        ExecutionInput input = ExecutionInput.newExecutionInput().query(query).variables([localeVar: "AU"]).build()
        def executionResult = GraphQL.newGraphQL(executableStarWarsSchema).build().execute(input)

        then:

        executionResult.errors.isEmpty()

        captureFieldArgs == [
                [
                        name      : [:],
                        homePlanet: [
                                includeMoons: false,
                                coordsFormat: "republic"
                        ]
                ],

                [
                        name      : [:],
                        homePlanet: [
                                includeMoons: true,
                                locale      : "AU"
                        ]
                ],
                [name: [:], friends: [separationCount: 4], "friends/id": [:]],
                [id: [:]]
        ]

    }

}

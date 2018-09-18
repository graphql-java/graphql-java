package graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class Issue526 extends Specification {

    enum Episode {
        NEWHOPE, EMPIRE, JEDI
    }

    class Droid {
        List<Episode> appearsIn = [Episode.NEWHOPE, Episode.EMPIRE]

        List<Episode> getAppearsIn() {
            return appearsIn
        }

        Episode getEpisode() {
            return Episode.NEWHOPE
        }

        String getName() {
            return "C3PO"
        }
    }

    def "526 - Java enums can match string generated versions of them"() {

        given:

        def spec = """
            enum Episode {
                NEWHOPE,                    
                EMPIRE,
                JEDI
            }
            
            type Droid {
                name: String,
                episode : Episode,
                appearsIn: [Episode],
            }
            
            type Query {
                droid :  Droid
            }
        """

        def graphQL = TestUtil.graphQL(spec, newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("droid", new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                return new Droid()
            }
        }))).build()

        def query = """
        {
            droid {
                name
                episode
                appearsIn
            }
        }
        """
        def executionInput = newExecutionInput().query(query).build()

        when:

        def executionResult = graphQL.execute(executionInput)

        then:

        executionResult.errors.size() == 0
        executionResult.data == [
                droid: [
                        name     : "C3PO",
                        episode  : "NEWHOPE",
                        appearsIn: [
                                "NEWHOPE", "EMPIRE"
                        ],
                ]
        ]
    }
}

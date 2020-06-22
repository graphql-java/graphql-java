package graphql.schema

import graphql.Assert
import graphql.TestUtil
import graphql.normalized.NormalizedField
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

class DataFetcherNormalizedFieldTest extends Specification {

    def "returns normalized field on interfaces"() {
        given:
        def sdl = """
        type Query {
            myPet: Pet
        }
        interface Pet {
           name: String 
        }
        type Dog implements Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        """
        def query = "{myPet {...on Dog {name} ...on Cat{name}}}"

        List<NormalizedField> capturedNFs = []
        def myPetDf = { env ->
            capturedNFs.addAll(env.getNormalizeField().getChildren(1))
            return null
        } as DataFetcher

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", {
                    it.dataFetcher("myPet", myPetDf)
                })
                .type("Pet", {
                    it.typeResolver({
                        return Assert.assertShouldNeverHappen()
                    })
                })
                .build()


        def graphql = TestUtil.graphQL(sdl, runtimeWiring).build()
        when:
        def result = graphql.execute(query)

        then:
        capturedNFs.collect({ it.print() }) == ["(Dog.name)", "(Cat.name)"]
    }

}

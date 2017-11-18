package graphql.schema.idl

import graphql.GraphQL
import graphql.TestUtil
import spock.lang.Specification

class EchoingWiringFactoryTest extends Specification {

    def "test_basic_echo"() {
        def idl = """
            type Query {
                hero : Hero
                antihero : AntiHero
            }
            
            type Hero {
                id : ID
                name : String
                power : Power
            }

            type AntiHero {
                id : ID
                name : String
                power : Power
            }
            
            type Power {
                name : String
                strength : Int
            }
            
        """
        def schema = TestUtil.schema(idl, EchoingWiringFactory.newEchoingWiring())
        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        def result = graphQL.execute("""
            {
                hero {
                    id
                    name 
                    power {
                        name
                        strength
                    }
                }
            }
            """)

        then:
        result.data == [
                hero: [
                        id   : "id_id",
                        name : "name",
                        power: [
                                name    : "name",
                                strength: 1
                        ]
                ]
        ]
    }
}

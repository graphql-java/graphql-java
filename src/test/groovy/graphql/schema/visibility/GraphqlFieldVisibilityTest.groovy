package graphql.schema.visibility

import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.introspection.IntrospectionQuery
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

import static graphql.schema.visibility.GraphqlFieldVisibility.DEFAULT_VISIBILITY
import static graphql.schema.visibility.GraphqlFieldVisibilityBlacklist.newBlacklist

class GraphqlFieldVisibilityTest extends Specification {

    def "visibility is enforced"() {

        GraphqlFieldVisibility banNameVisibility = newBlacklist().addPattern(".*\\.name").build()
        def schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(banNameVisibility)
                .build()

        def graphQL = GraphQL.newGraphQL(schema).build()

        given:
        def query = """
        {
            hero {
                id
                name
                friends {
                    aliasHandled: name
                }
            }
        }
        """

        when:
        def result = graphQL.execute(query)

        then:
        result.errors[0].getMessage().contains("Field 'name' in type 'Character' is undefined")
        result.errors[1].getMessage().contains("Field 'name' in type 'Character' is undefined")
    }

    def "introspection visibility is enforced"() {


        given:

        def schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(fieldVisibility)
                .build()

        def graphQL = GraphQL.newGraphQL(schema).build()

        when:

        def result = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)

        then:

        List types = result.data["__schema"]["types"] as List
        Map characterType = types.find({ it -> it['name'] == targetTypeName }) as Map
        List fields = characterType['fields'] as List
        fields.size() == expectedCharacterFields

        where:

        fieldVisibility                        | targetTypeName | expectedCharacterFields
        DEFAULT_VISIBILITY                     | 'Character'    | 4
        ban(["Character.name"])                | 'Character'    | 3
        DEFAULT_VISIBILITY                     | 'Droid'        | 5
        ban(["Droid.name", "Droid.appearsIn"]) | 'Droid'        | 3
    }

    private static GraphqlFieldVisibilityBlacklist ban(List<String> regex) {
        newBlacklist().addPatterns(regex).build()
    }

    def "schema printing filters on visibility"() {

        when:
        def schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(DEFAULT_VISIBILITY)
                .build()
        def result = new SchemaPrinter().print(schema)

        then:
        result == """schema {
  query: QueryType
}

#A character in the Star Wars Trilogy
interface Character {
  #The id of the character.
  id: String!
  #The name of the character.
  name: String
  #The friends of the character, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
}

#A mechanical creature in the Star Wars universe.
type Droid {
  #The id of the droid.
  id: String!
  #The name of the droid.
  name: String
  #The friends of the droid, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
  #The primary function of the droid.
  primaryFunction: String
}

#A humanoid creature in the Star Wars universe.
type Human {
  #The id of the human.
  id: String!
  #The name of the human.
  name: String
  #The friends of the human, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
  #The home planet of the human, or null if unknown.
  homePlanet: String
}

type QueryType {
  hero(
  #If omitted, returns the hero of the whole saga. If provided, returns the hero of that particular episode.
  episode: Episode
  ): Character
  human(
  #id of the human
  id: String!
  ): Human
  droid(
  #id of the droid
  id: String!
  ): Droid
}

#One of the films in the Star Wars Trilogy
enum Episode {
  #Released in 1977.
  NEWHOPE
  #Released in 1980.
  EMPIRE
  #Released in 1983.
  JEDI
}
"""



        when:
        schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(ban(['Droid.id', 'Character.name', "QueryType.hero"]))
                .build()
        result = new SchemaPrinter().print(schema)

        then:
        result == """schema {
  query: QueryType
}

#A character in the Star Wars Trilogy
interface Character {
  #The id of the character.
  id: String!
  #The friends of the character, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
}

#A mechanical creature in the Star Wars universe.
type Droid {
  #The name of the droid.
  name: String
  #The friends of the droid, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
  #The primary function of the droid.
  primaryFunction: String
}

#A humanoid creature in the Star Wars universe.
type Human {
  #The id of the human.
  id: String!
  #The name of the human.
  name: String
  #The friends of the human, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
  #The home planet of the human, or null if unknown.
  homePlanet: String
}

type QueryType {
  human(
  #id of the human
  id: String!
  ): Human
  droid(
  #id of the droid
  id: String!
  ): Droid
}

#One of the films in the Star Wars Trilogy
enum Episode {
  #Released in 1977.
  NEWHOPE
  #Released in 1980.
  EMPIRE
  #Released in 1983.
  JEDI
}
"""

    }
}

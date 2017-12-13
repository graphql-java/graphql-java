package graphql.schema.visibility

import graphql.GraphQL
import graphql.GraphQLException
import graphql.StarWarsSchema
import graphql.execution.AsyncExecutionStrategy
import graphql.introspection.IntrospectionQuery
import graphql.language.Field
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

import static BlockedFields.newBlock
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY
import static graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility.NO_INTROSPECTION_FIELD_VISIBILITY

class GraphqlFieldVisibilityTest extends Specification {

    def "visibility is enforced"() {

        GraphqlFieldVisibility banNameVisibility = newBlock().addPattern(".*\\.name").build()
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
        fields.size() == expectedFieldCounts

        where:

        fieldVisibility                        | targetTypeName | expectedFieldCounts
        DEFAULT_FIELD_VISIBILITY               | 'Character'    | 4
        ban(["Character.name"])                | 'Character'    | 3
        DEFAULT_FIELD_VISIBILITY               | 'Droid'        | 5
        ban(["Droid.name", "Droid.appearsIn"]) | 'Droid'        | 3
    }

    private static BlockedFields ban(List<String> regex) {
        newBlock().addPatterns(regex).build()
    }

    def "introspection turned off via field visibility"() {
        given:

        def schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(NO_INTROSPECTION_FIELD_VISIBILITY)
                .build()

        def graphQL = GraphQL.newGraphQL(schema).build()

        when:

        def result = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)

        then:

        !result.errors.isEmpty()
        result.data == null

    }

    def "schema printing filters on visibility"() {

        when:
        def schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(DEFAULT_FIELD_VISIBILITY)
                .build()
        def result = new SchemaPrinter().print(schema)

        then:
        result == """schema {
  query: QueryType
}

#A character in the Star Wars Trilogy
interface Character {
  #Which movies they appear in.
  appearsIn: [Episode]
  #The friends of the character, or an empty list if they have none.
  friends: [Character]
  #The id of the character.
  id: String!
  #The name of the character.
  name: String
}

#A mechanical creature in the Star Wars universe.
type Droid implements Character {
  #Which movies they appear in.
  appearsIn: [Episode]
  #The friends of the droid, or an empty list if they have none.
  friends: [Character]
  #The id of the droid.
  id: String!
  #The name of the droid.
  name: String
  #The primary function of the droid.
  primaryFunction: String
}

#A humanoid creature in the Star Wars universe.
type Human implements Character {
  #Which movies they appear in.
  appearsIn: [Episode]
  #The friends of the human, or an empty list if they have none.
  friends: [Character]
  #The home planet of the human, or null if unknown.
  homePlanet: String
  #The id of the human.
  id: String!
  #The name of the human.
  name: String
}

type QueryType {
  droid(
  #id of the droid
  id: String!
  ): Droid
  hero(
  #If omitted, returns the hero of the whole saga. If provided, returns the hero of that particular episode.
  episode: Episode
  ): Character
  human(
  #id of the human
  id: String!
  ): Human
}

#One of the films in the Star Wars Trilogy
enum Episode {
  #Released in 1980.
  EMPIRE
  #Released in 1983.
  JEDI
  #Released in 1977.
  NEWHOPE
}
"""

        // and with specific bans


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
  #Which movies they appear in.
  appearsIn: [Episode]
  #The friends of the character, or an empty list if they have none.
  friends: [Character]
  #The id of the character.
  id: String!
}

#A mechanical creature in the Star Wars universe.
type Droid implements Character {
  #Which movies they appear in.
  appearsIn: [Episode]
  #The friends of the droid, or an empty list if they have none.
  friends: [Character]
  #The name of the droid.
  name: String
  #The primary function of the droid.
  primaryFunction: String
}

#A humanoid creature in the Star Wars universe.
type Human implements Character {
  #Which movies they appear in.
  appearsIn: [Episode]
  #The friends of the human, or an empty list if they have none.
  friends: [Character]
  #The home planet of the human, or null if unknown.
  homePlanet: String
  #The id of the human.
  id: String!
  #The name of the human.
  name: String
}

type QueryType {
  droid(
  #id of the droid
  id: String!
  ): Droid
  human(
  #id of the human
  id: String!
  ): Human
}

#One of the films in the Star Wars Trilogy
enum Episode {
  #Released in 1980.
  EMPIRE
  #Released in 1983.
  JEDI
  #Released in 1977.
  NEWHOPE
}
"""

    }

    class TestES extends AsyncExecutionStrategy {

        // gives us access to this unit tested method
        GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLObjectType parentType, Field field) {
            return super.getFieldDef(schema, parentType, field)
        }
    }

    def "ensure execution cant get to the field"() {


        when:
        def schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(ban(['Droid.appearsIn']))
                .build()



        def executionStrategy = new AsyncExecutionStrategy() {

            // gives us access to this unit tested method
            GraphQLFieldDefinition getFieldDef(GraphQLSchema graphQLSchema, GraphQLObjectType parentType, Field field) {
                return super.getFieldDef(graphQLSchema, parentType, field)
            }
        }
        executionStrategy.getFieldDef(schema, StarWarsSchema.droidType, new Field("appearsIn"))

        then:
        //
        // normally query validation would prevent us ever getting this far but for belts and braces reasons
        // we test that should you some how invoke the execution strategy - it will follow fields visibility
        // rules
        thrown(GraphQLException)
    }

    def inputType = newInputObject().name("InputType")
            .field(newInputObjectField().name("openField").type(GraphQLString))
            .field(newInputObjectField().name("closedField").type(GraphQLString))
            .build()

    def inputQueryType = GraphQLObjectType.newObject().name("InputQuery")
            .field(newFieldDefinition().name("hello").type(GraphQLString)
            .argument(newArgument().name("arg").type(inputType))
            .dataFetcher({ env -> return "world" })
    )
            .build()

    def "ensure input field are blocked"() {

        when:
        def schema = GraphQLSchema.newSchema()
                .query(inputQueryType)
                .build()

        def graphQL = GraphQL.newGraphQL(schema).build()

        def er = graphQL.execute('''
            {
                hello(arg : {
                    openField: "open", 
                    closedField:"closed"
                    })
            }
        ''')

        then:
        er.getErrors().isEmpty()
        er.getData() == ["hello": "world"]

        when:
        schema = GraphQLSchema.newSchema()
                .query(inputQueryType)
                .fieldVisibility(ban(['InputType.closedField']))
                .build()

        graphQL = GraphQL.newGraphQL(schema).build()

        er = graphQL.execute('''
            {
                hello(arg : {
                    openField: "open", 
                    closedField:"closed"
                    })
            }
        ''')

        then:
        !er.getErrors().isEmpty()
        er.getErrors()[0].message.contains("contains a field not in 'InputType': 'closedField'")
        er.data == null
    }

    def "input introspection is blocked"() {

        given:

        def schema = GraphQLSchema.newSchema()
                .query(inputQueryType)
                .fieldVisibility(fieldVisibility)
                .build()

        def graphQL = GraphQL.newGraphQL(schema).build()

        when:

        def result = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)

        then:

        List types = result.data["__schema"]["types"] as List
        Map typeMap = types.find({ it -> it['name'] == targetTypeName }) as Map
        List fields = typeMap['inputFields'] as List
        fields.size() == expectedFieldCounts

        where:

        fieldVisibility                | targetTypeName | expectedFieldCounts
        DEFAULT_FIELD_VISIBILITY       | 'InputType'    | 2
        ban(["InputType.closedField"]) | 'InputType'    | 1
    }

    def "input schema print is blocked"() {
        when:
        def schema = GraphQLSchema.newSchema()
                .query(inputQueryType)
                .fieldVisibility(DEFAULT_FIELD_VISIBILITY)
                .build()
        def result = new SchemaPrinter().print(schema)

        then:
        result == '''schema {
  query: InputQuery
}

type InputQuery {
  hello(arg: InputType): String
}

input InputType {
  closedField: String
  openField: String
}
'''

        when:
        schema = GraphQLSchema.newSchema()
                .query(inputQueryType)
                .fieldVisibility(ban(["InputType.closedField"]))
                .build()
        result = new SchemaPrinter().print(schema)

        then:
        result == '''schema {
  query: InputQuery
}

type InputQuery {
  hello(arg: InputType): String
}

input InputType {
  openField: String
}
'''

    }
}

package graphql;


import graphql.schema.*;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLFieldArgument.newFieldArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLObjectType.newObject;

public class StarWarsSchema {


    static GraphQLEnumType episodeEnum = newEnum()
            .name("Episode")
            .description("One of the films in the Star Wars Trilogy")
            .value("NEWHOPE", 4, "Released in 1977.")
            .value("EMPIRE", 5, "Released in 1980.")
            .value("JEDI", 6, "Released in 1983.")
            .build();


    static GraphQLInterfaceType characterInterface = newInterface()
            .name("Character")
            .description("A character in the Star Wars Trilogy")
            .field(newFieldDefinition()
                    .name("id")
                    .description("The id of the character.")
                    .type(new GraphQLNonNull(GraphQLString))
                    .build())
            .field(newFieldDefinition()
                    .name("name")
                    .description("The name of the character.")
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("friends")
                    .description("The friends of the character, or an empty list if they have none.")
                    .type(new GraphQLList(new GraphQLTypeReference("Character")))
                    .build())
            .field(newFieldDefinition()
                    .name("appearsIn")
                    .description("Which movies they appear in.")
                    .type(new GraphQLList(episodeEnum))
                    .build())
            .build();

    static GraphQLObjectType humanType = newObject()
            .name("Human")
            .description("A humanoid creature in the Star Wars universe.")
            .withInterface(characterInterface)
            .field(newFieldDefinition()
                    .name("id")
                    .description("The id of the human.")
                    .type(new GraphQLNonNull(GraphQLString))
                    .build())
            .field(newFieldDefinition()
                    .name("name")
                    .description("The name of the human.")
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("friends")
                    .description("The friends of the human, or an empty list if they have none.")
                    .type(new GraphQLList(characterInterface))
                    .dataFetcher(StarWarsData.getFriendsDataFetcher())
                    .build())
            .field(newFieldDefinition()
                    .name("appearsIn")
                    .description("Which movies they appear in.")
                    .type(new GraphQLList(episodeEnum))
                    .build())
            .field(newFieldDefinition()
                    .name("homePlanet")
                    .description("The home planet of the human, or null if unknown.")
                    .type(GraphQLString)
                    .build())
            .build();

    static GraphQLObjectType droidType = newObject()
            .name("Droid")
            .description("A mechanical creature in the Star Wars universe.")
            .withInterface(characterInterface)
            .field(newFieldDefinition()
                    .name("id")
                    .description("The id of the droid.")
                    .type(new GraphQLNonNull(GraphQLString))
                    .build())
            .field(newFieldDefinition()
                    .name("name")
                    .description("The name of the droid.")
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("friends")
                    .description("The friends of the droid, or an empty list if they have none.")
                    .type(new GraphQLList(characterInterface))
                    .dataFetcher(StarWarsData.getFriendsDataFetcher())
                    .build())
            .field(newFieldDefinition()
                    .name("appearsIn")
                    .description("Which movies they appear in.")
                    .type(new GraphQLList(episodeEnum))
                    .build())
            .field(newFieldDefinition()
                    .name("primaryFunction")
                    .description("The primary function of the droid.")
                    .type(GraphQLString)
                    .build())
            .build();


    static GraphQLObjectType queryType = newObject()
            .name("QueryType")
            .field(newFieldDefinition()
                    .name("hero")
                    .type(characterInterface)
                    .dataFetcher(new StaticDataFetcher(StarWarsData.getArtoo()))
                    .build())
            .field(newFieldDefinition()
                    .name("human")
                    .type(humanType)
                    .argument(newFieldArgument()
                            .name("id")
                            .type(new GraphQLNonNull(GraphQLString))
                            .build())
                    .dataFetcher(StarWarsData.getHumanDataFetcher())
                    .build())
            .field(newFieldDefinition()
                    .name("droid")
                    .type(droidType)
                    .argument(newFieldArgument()
                            .name("id")
                            .type(new GraphQLNonNull(GraphQLString))
                            .build())
                    .dataFetcher(StarWarsData.getDroidDataFetcher())
                    .build())
            .build();


    static GraphQLSchema starWarsSchema = GraphQLSchema.newSchema()
            .query(queryType)
            .build();
}

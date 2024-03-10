package graphql;

import graphql.relay.Relay;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

public class RelaySchema {

    public static Relay relay = new Relay();
    public static GraphQLObjectType StuffType = newObject()
            .name("Stuff")
            .field(newFieldDefinition()
                    .name("id")
                    .type(GraphQLString)
            )
            .build();

    public static GraphQLInterfaceType NodeInterface = relay.nodeInterface();

    public static GraphQLObjectType StuffEdgeType = relay.edgeType("Stuff", StuffType, NodeInterface, new ArrayList<>());

    public static GraphQLObjectType StuffConnectionType = relay.connectionType("Stuff", StuffEdgeType, new ArrayList<>());

    public static GraphQLObjectType ThingType = newObject()
            .name("Thing")
            .field(newFieldDefinition()
                    .name("id")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("stuffs")
                    .type(StuffConnectionType))
            .build();


    public static GraphQLObjectType RelayQueryType = newObject()
            .name("RelayQuery")
            .field(relay.nodeField(NodeInterface))
            .field(newFieldDefinition()
                    .name("thing")
                    .type(ThingType)
                    .argument(newArgument()
                            .name("id")
                            .description("id of the thing")
                            .type(GraphQLNonNull.nonNull(GraphQLString))))
            .build();

    public static FieldCoordinates thingCoordinates = FieldCoordinates.coordinates("RelayQuery", "thing");
    public static DataFetcher<?> thingDataFetcher = environment -> null;

    public static GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
            .dataFetcher(thingCoordinates, thingDataFetcher)
            .typeResolver("Node", env -> null)
            .build();

    public static GraphQLSchema Schema = GraphQLSchema.newSchema()
            .codeRegistry(codeRegistry)
            .query(RelayQueryType)
            .additionalType(Relay.pageInfoType)
            .build();
}

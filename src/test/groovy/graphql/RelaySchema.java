package graphql;

import graphql.relay.Relay;
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

    public static GraphQLInterfaceType NodeInterface = relay.nodeInterface(env -> {
        Relay.ResolvedGlobalId resolvedGlobalId = relay.fromGlobalId((String) env.getObject());
        //TODO: implement
        return null;
    });

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
            .field(relay.nodeField(NodeInterface, environment -> {
                //TODO: implement
                return null;
            }))
            .field(newFieldDefinition()
                    .name("thing")
                    .type(ThingType)
                    .argument(newArgument()
                            .name("id")
                            .description("id of the thing")
                            .type(GraphQLNonNull.nonNull(GraphQLString)))
                    .dataFetcher(environment -> {
                        //TODO: implement
                        return null;
                    }))
            .build();


    public static GraphQLSchema Schema = GraphQLSchema.newSchema()
            .query(RelayQueryType)
            .build();
}

package graphql.relay;


import graphql.schema.*;

import java.util.ArrayList;
import java.util.List;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLObjectType.newObject;

public class Relay {

    public static final String NODE = "Node";
    private GraphQLObjectType pageInfoType = newObject()
            .name("PageInfo")
            .description("Information about pagination in a connection.")
            .field(newFieldDefinition()
                    .name("hasNextPage")
                    .type(new GraphQLNonNull(GraphQLBoolean))
                    .description("When paginating forwards, are there more items?"))
            .field(newFieldDefinition()
                    .name("hasPreviousPage")
                    .type(new GraphQLNonNull(GraphQLBoolean))
                    .description("When paginating backwards, are there more items?"))
            .field(newFieldDefinition()
                    .name("startCursor")
                    .type(GraphQLString)
                    .description("When paginating backwards, the cursor to continue."))
            .field(newFieldDefinition()
                    .name("endCursor")
                    .type(GraphQLString)
                    .description("When paginating forwards, the cursor to continue."))
            .build();

    public GraphQLInterfaceType nodeInterface(TypeResolver typeResolver) {
        GraphQLInterfaceType node = newInterface()
                .name(NODE)
                .description("An object with an ID")
                .typeResolver(typeResolver)
                .field(newFieldDefinition()
                        .name("id")
                        .description("The ID of an object")
                        .type(new GraphQLNonNull(GraphQLID)))
                .build();
        return node;
    }

    public GraphQLFieldDefinition nodeField(GraphQLInterfaceType nodeInterface, DataFetcher nodeDataFetcher) {
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("node")
                .description("Fetches an object given its ID")
                .type(nodeInterface)
                .dataFetcher(nodeDataFetcher)
                .argument(newArgument()
                        .name("id")
                        .description("The ID of an object")
                        .type(new GraphQLNonNull(GraphQLID)))
                .build();
        return fieldDefinition;
    }

    public List<GraphQLArgument> getConnectionFieldArguments() {
        List<GraphQLArgument> args = new ArrayList<>();

        args.add(newArgument()
                .name("before")
                .type(GraphQLString)
                .build());
        args.add(newArgument()
                .name("after")
                .type(GraphQLString)
                .build());
        args.add(newArgument()
                .name("first")
                .type(GraphQLInt)
                .build());
        args.add(newArgument()
                .name("last")
                .type(GraphQLInt)
                .build());
        return args;
    }

    public List<GraphQLArgument> getBackwardPaginationConnectionFieldArguments() {
        List<GraphQLArgument> args = new ArrayList<>();

        args.add(newArgument()
                .name("before")
                .type(GraphQLString)
                .build());
        args.add(newArgument()
                .name("last")
                .type(GraphQLInt)
                .build());
        return args;
    }

    public List<GraphQLArgument> getForwardPaginationConnectionFieldArguments() {
        List<GraphQLArgument> args = new ArrayList<>();

        args.add(newArgument()
                .name("after")
                .type(GraphQLString)
                .build());
        args.add(newArgument()
                .name("first")
                .type(GraphQLInt)
                .build());
        return args;
    }

    public GraphQLObjectType edgeType(String name, GraphQLOutputType nodeType, GraphQLInterfaceType nodeInterface, List<GraphQLFieldDefinition> edgeFields) {

        GraphQLObjectType edgeType = newObject()
                .name(name + "Edge")
                .description("An edge in a connection.")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeType)
                        .description("The item at the end of the edge"))
                .field(newFieldDefinition()
                        .name("cursor")
                        .type(new GraphQLNonNull(GraphQLString))
                        .description(""))
                .fields(edgeFields)
                .build();
        return edgeType;
    }

    public GraphQLObjectType connectionType(String name, GraphQLObjectType edgeType, List<GraphQLFieldDefinition> connectionFields) {

        GraphQLObjectType connectionType = newObject()
                .name(name + "Connection")
                .description("A connection to a list of items.")
                .field(newFieldDefinition()
                        .name("edges")
                        .type(new GraphQLList(edgeType)))
                .field(newFieldDefinition()
                        .name("pageInfo")
                        .type(new GraphQLNonNull(pageInfoType)))
                .fields(connectionFields)
                .build();
        return connectionType;
    }


    public GraphQLFieldDefinition mutationWithClientMutationId(String name, String fieldName,
                                                               List<GraphQLInputObjectField> inputFields,
                                                               List<GraphQLFieldDefinition> outputFields,
                                                               DataFetcher dataFetcher) {
        GraphQLInputObjectType inputObjectType = newInputObject()
                .name(name + "Input")
                .field(newInputObjectField()
                        .name("clientMutationId")
                        .type(new GraphQLNonNull(GraphQLString)))
                .fields(inputFields)
                .build();
        GraphQLObjectType outputType = newObject()
                .name(name + "Payload")
                .field(newFieldDefinition()
                        .name("clientMutationId")
                        .type(new GraphQLNonNull(GraphQLString)))
                .fields(outputFields)
                .build();

        return newFieldDefinition()
                .name(fieldName)
                .type(outputType)
                .argument(newArgument()
                        .name("input")
                        .type(new GraphQLNonNull(inputObjectType)))
                .dataFetcher(dataFetcher)
                .build();
    }

    public static class ResolvedGlobalId {

        public ResolvedGlobalId(String type, String id) {
            this.type = type;
            this.id = id;
        }

        /**
         * @deprecated use {@link #getType()}
         */
        @Deprecated
        public String type;
        /**
         * @deprecated use {@link #getId()}
         */
        @Deprecated
        public String id;

        public String getType() {
            return type;
        }

        public String getId() {
            return id;
        }
    }

    public String toGlobalId(String type, String id) {
        return Base64.toBase64(type + ":" + id);
    }

    public ResolvedGlobalId fromGlobalId(String globalId) {
        String[] split = Base64.fromBase64(globalId).split(":", 2);
        if (split.length != 2) {
            throw new IllegalArgumentException(String.format("expecting a valid global id, got %s", globalId));
        }
        return new ResolvedGlobalId(split[0], split[1]);
    }
}

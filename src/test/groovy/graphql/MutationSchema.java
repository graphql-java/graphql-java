package graphql;


import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.*;

import static graphql.Scalars.GraphQLInt;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLSchema.newSchema;

public class MutationSchema {

    public static class NumberHolder {
        int theNumber;

        public NumberHolder(int theNumber) {
            this.theNumber = theNumber;
        }

        public int getTheNumber() {
            return theNumber;
        }

        public void setTheNumber(int theNumber) {
            this.theNumber = theNumber;
        }
    }

    public static class Root {
        NumberHolder numberHolder;

        public Root(int number) {
            this.numberHolder = new NumberHolder(number);
        }

        public NumberHolder changeNumber(int newNumber) {
            this.numberHolder.theNumber = newNumber;
            SubscriptionRoot.numberChanged(newNumber);
            return this.numberHolder;
        }

        public NumberHolder failToChangeTheNumber(int newNumber) {
            throw new RuntimeException("Cannot change the number");
        }

        public NumberHolder getNumberHolder() {
            return numberHolder;
        }
    }

    public static class SubscriptionRoot {
        private Root root;
        static List<String> result = new ArrayList<String>();
        static List<Integer> subscribers = new ArrayList<Integer>();

        public SubscriptionRoot(Root root) {
            this.root = root;
        }

        public void changeNumberSubscribe(int clientId) {
            subscribers.add(clientId);
        }

        public static void numberChanged(int newNumber) {
            for (Integer subscriber : subscribers) {
                // for test purposes only, a true implementation of a subscription mechanism needs to consider
                // the format in which subscribers have requested their response and tailor it accordingly
                result.add("Alert client [" + subscriber + "] that number is now [" + newNumber + "]");
            }
        }

        public Root getRoot() {
            return root;
        }

        public static List<String> getResult() {
            return result;
        }
    }

    public static GraphQLObjectType numberHolderType = GraphQLObjectType.newObject()
            .name("NumberHolder")
            .field(newFieldDefinition()
                    .name("theNumber")
                    .type(GraphQLInt))
            .build();

    public static GraphQLObjectType queryType = GraphQLObjectType.newObject()
            .name("queryType")
            .field(newFieldDefinition()
                    .name("numberHolder")
                    .type(numberHolderType))
            .build();

    public static GraphQLObjectType mutationType = GraphQLObjectType.newObject()
            .name("mutationType")
            .field(newFieldDefinition()
                    .name("changeTheNumber")
                    .type(numberHolderType)
                    .argument(newArgument()
                            .name("newNumber")
                            .type(GraphQLInt))
                    .dataFetcher(new DataFetcher() {
                        @Override
                        public Object get(DataFetchingEnvironment environment) {
                            Integer newNumber = environment.getArgument("newNumber");
                            Root root = (Root) environment.getSource();
                            return root.changeNumber(newNumber);
                        }
                    }))
            .field(newFieldDefinition()
                    .name("failToChangeTheNumber")
                    .type(numberHolderType)
                    .argument(newArgument()
                            .name("newNumber")
                            .type(GraphQLInt))
                    .dataFetcher(new DataFetcher() {
                        @Override
                        public Object get(DataFetchingEnvironment environment) {
                            Integer newNumber = environment.getArgument("newNumber");
                            Root root = (Root) environment.getSource();
                            return root.failToChangeTheNumber(newNumber);
                        }
                    }))
            .build();

    public static GraphQLObjectType subscriptionType = GraphQLObjectType.newObject()
            .name("subscriptionType")
            .field(newFieldDefinition()
                    .name("changeNumberSubscribe")
                    .type(numberHolderType)
                    .argument(newArgument()
                            .name("clientId")
                            .type(GraphQLInt))
                    .dataFetcher(new DataFetcher() {
                        @Override
                        public Object get(DataFetchingEnvironment environment) {
                            Integer clientId = environment.getArgument("clientId");
                            SubscriptionRoot subscriptionRoot = (SubscriptionRoot) environment.getSource();
                            subscriptionRoot.changeNumberSubscribe(clientId);
                            return subscriptionRoot.getRoot().getNumberHolder();
                        }
                    }))
            .build();

    public static GraphQLSchema schema = newSchema()
            .query(queryType)
            .mutation(mutationType)
            .subscription(subscriptionType)
            .build();
}

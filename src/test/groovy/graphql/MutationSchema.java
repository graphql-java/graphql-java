package graphql;


import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.List;

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

    public static class SubscriptionRoot {
        List<String> result = new ArrayList<String>();
        List<Integer> subscribers = new ArrayList<Integer>();
        NumberHolder numberHolder;

        public SubscriptionRoot(int initalNumber) {
            this.numberHolder = new NumberHolder(initalNumber);
        }

        public void subscribeToNumberChanges(int clientId) {
            subscribers.add(clientId);
        }

        public void informSubscribers(int newNumber) {
            for (Integer subscriber : subscribers) {
                result.add("Alert client [" + subscriber + "] that number is now [" + newNumber + "]");
            }
        }

        public NumberHolder changeNumber(int newNumber) {
            this.numberHolder.theNumber = newNumber;
            informSubscribers(newNumber);
            return this.numberHolder;
        }

        public NumberHolder failToChangeTheNumber(int newNumber) {
            throw new RuntimeException("Cannot change the number");
        }

        public NumberHolder getNumberHolder() {
            return numberHolder;
        }

        public List<String> getResult() {
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
                    .dataFetcher(environment -> {
                        Integer newNumber = environment.getArgument("newNumber");
                        SubscriptionRoot root = environment.getSource();
                        return root.changeNumber(newNumber);
                    }))
            .field(newFieldDefinition()
                    .name("failToChangeTheNumber")
                    .type(numberHolderType)
                    .argument(newArgument()
                            .name("newNumber")
                            .type(GraphQLInt))
                    .dataFetcher(environment -> {
                        Integer newNumber = environment.getArgument("newNumber");
                        SubscriptionRoot root = environment.getSource();
                        return root.failToChangeTheNumber(newNumber);
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
                    .dataFetcher(environment -> {
                        Integer clientId = environment.getArgument("clientId");
                        SubscriptionRoot subscriptionRoot = environment.getSource();
                        subscriptionRoot.subscribeToNumberChanges(clientId);
                        return subscriptionRoot.getNumberHolder();
                    }))
            .build();

    public static GraphQLSchema schema = newSchema()
            .query(queryType)
            .mutation(mutationType)
            .subscription(subscriptionType)
            .build();
}

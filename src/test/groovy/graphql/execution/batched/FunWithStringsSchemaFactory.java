package graphql.execution.batched;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLTypeReference.typeRef;

public class FunWithStringsSchemaFactory {


    private static void increment(Map<CallType, AtomicInteger> callCounts, CallType type) {
        if (!callCounts.containsKey(type)) {
            callCounts.put(type, new AtomicInteger(0));
        }
        callCounts.get(type).incrementAndGet();
    }

    public enum CallType {
        VALUE, APPEND, WORDS_AND_LETTERS, SPLIT, SHATTER
    }

    public static FunWithStringsSchemaFactory createBatched(final Map<CallType, AtomicInteger> callCounts) {
        FunWithStringsSchemaFactory factory = new FunWithStringsSchemaFactory();


        factory.setStringObjectValueFetcher(new DataFetcher() {
            @Override
            @Batched
            @SuppressWarnings("unchecked")
            public Object get(DataFetchingEnvironment environment) {
                increment(callCounts, CallType.VALUE);
                List<String> retVal = new ArrayList<>();
                for (String s : (List<String>) environment.getSource()) {
                    retVal.add("null".equals(s) ? null : s);
                }
                return retVal;
            }
        });

        factory.setThrowExceptionFetcher(new DataFetcher() {
            @Override
            @Batched
            public Object get(DataFetchingEnvironment environment) {
                throw new RuntimeException("TestException");
            }
        });

        factory.setReturnBadList(new DataFetcher() {
            @Override
            @Batched
            @SuppressWarnings("unchecked")
            public Object get(DataFetchingEnvironment environment) {
                List<String> retVal = new ArrayList<>();
                for (String s : (List<String>) environment.getSource()) {
                    retVal.add("null".equals(s) ? null : s);
                }
                retVal.add("badValue");
                return retVal;
            }
        });

        factory.setAnyIterable(new DataFetcher() {
            @Override
            @Batched
            @SuppressWarnings("unchecked")
            public Object get(DataFetchingEnvironment environment) {
                List<Iterable<String>> retVal = new ArrayList<>();
                for (String s : (List<String>) environment.getSource()) {
                    retVal.add(new LinkedHashSet<>(Arrays.asList(s, "end")));
                }
                return retVal;
            }
        });

        factory.setAppendFetcher(new DataFetcher() {
            @Override
            @Batched
            @SuppressWarnings("unchecked")
            public Object get(DataFetchingEnvironment environment) {
                increment(callCounts, CallType.APPEND);
                List<String> retVal = new ArrayList<>();
                for (String s : (List<String>) environment.getSource()) {
                    retVal.add(s + environment.getArgument("text"));
                }
                // make it an Iterable thing not just List
                return new ArrayDeque<>(retVal);
            }
        });

        factory.setWordsAndLettersFetcher(new DataFetcher() {
            @Batched
            @Override
            @SuppressWarnings("unchecked")
            public Object get(DataFetchingEnvironment environment) {
                increment(callCounts, CallType.WORDS_AND_LETTERS);
                List<String> sources = environment.getSource();
                List<List<List<String>>> retVal = new ArrayList<>();
                for (String source : sources) {
                    List<List<String>> sentence = new ArrayList<>();
                    splitSentence(source, sentence);
                    retVal.add(sentence);
                }
                return retVal.toArray();
            }
        });

        factory.setSplitFetcher(new DataFetcher() {
            @Batched
            @Override
            @SuppressWarnings("unchecked")
            public Object get(DataFetchingEnvironment environment) {
                increment(callCounts, CallType.SPLIT);
                String regex = environment.getArgument("regex");
                List<String> sources = environment.getSource();
                List<List<String>> retVal = new ArrayList<>();
                if (regex == null) {
                    for (String ignored : sources) {
                        retVal.add(null);
                    }
                    return retVal;
                }
                for (String source : sources) {
                    List<String> retItem = new ArrayList<>();
                    for (String str : source.split(regex)) {
                        if (str.isEmpty()) {
                            retItem.add(null);
                        } else {
                            retItem.add(str);
                        }
                    }
                    retVal.add(retItem);
                }
                return retVal;
            }
        });

        factory.setShatterFetcher(new DataFetcher() {
            @Batched
            @Override
            @SuppressWarnings("unchecked")
            public Object get(DataFetchingEnvironment environment) {
                increment(callCounts, CallType.SHATTER);
                List<String> sources = environment.getSource();
                List<List<String>> retVal = new ArrayList<>();
                for (String source : sources) {
                    List<String> retItem = new ArrayList<>();
                    for (char c : source.toCharArray()) {
                        retItem.add(Character.toString(c));
                    }
                    retVal.add(retItem);
                }
                return retVal;
            }
        });

        return factory;

    }

    private void setAnyIterable(DataFetcher fetcher) {
        this.anyIterableFetcher = fetcher;
    }

    private static void splitSentence(String source, List<List<String>> sentence) {
        for (String word : source.split(" ")) {
            List<String> letters = new ArrayList<>();
            for (char c : word.toCharArray()) {
                letters.add(Character.toString(c));
            }
            sentence.add(letters);
        }
    }


    private DataFetcher stringObjectValueFetcher = e -> "null".equals(e.getSource()) ? null : e.getSource();

    private DataFetcher throwExceptionFetcher = e -> {
        throw new RuntimeException("TestException");
    };

    private DataFetcher returnBadListFetcher = e -> {
        throw new RuntimeException("This field should only be queried in batch.");
    };

    private DataFetcher shatterFetcher = e -> {
        String source = e.getSource();
        if (source.isEmpty()) {
            return null; // trigger error
        }
        List<String> retVal = new ArrayList<>();
        for (char c : source.toCharArray()) {
            retVal.add(Character.toString(c));
        }
        return retVal;
    };

    private DataFetcher wordsAndLettersFetcher = e -> {
        String source = e.getSource();
        List<List<String>> retVal = new ArrayList<>();
        splitSentence(source, retVal);
        return retVal;
    };

    private DataFetcher splitFetcher = e -> {
        String regex = e.getArgument("regex");
        if (regex == null) {
            return null;
        }
        String source = e.getSource();
        List<String> retVal = new ArrayList<>();
        for (String str : source.split(regex)) {
            if (str.isEmpty()) {
                retVal.add(null);
            } else {
                retVal.add(str);
            }
        }
        return retVal;
    };

    private DataFetcher appendFetcher = e -> ((String) e.getSource()) + e.getArgument("text");

    private DataFetcher emptyOptionalFetcher = e -> Optional.empty();

    private DataFetcher optionalFetcher = e -> Optional.of("673-optional-support");

    private DataFetcher completableFutureFetcher = e -> CompletableFuture.completedFuture("completableFuture");

    private DataFetcher anyIterableFetcher = e -> {
        String source = e.getSource();
        return new LinkedHashSet<>(Arrays.asList(source, "end"));
    };

    private void setWordsAndLettersFetcher(DataFetcher fetcher) {
        this.wordsAndLettersFetcher = fetcher;
    }

    private void setShatterFetcher(DataFetcher fetcher) {
        this.shatterFetcher = fetcher;
    }

    private void setSplitFetcher(DataFetcher splitFetcher) {
        this.splitFetcher = splitFetcher;
    }

    private void setAppendFetcher(DataFetcher appendFetcher) {
        this.appendFetcher = appendFetcher;
    }

    private void setStringObjectValueFetcher(DataFetcher fetcher) {
        this.stringObjectValueFetcher = fetcher;
    }

    private void setThrowExceptionFetcher(DataFetcher fetcher) {
        this.throwExceptionFetcher = fetcher;
    }

    private void setReturnBadList(DataFetcher fetcher) {
        this.returnBadListFetcher = fetcher;
    }

    public static class SimpleObject {
        public String getValue() {
            return "interfacesHandled";
        }
    }

    GraphQLSchema createSchema() {

        GraphQLObjectType stringObjectType = newObject()
                .name("StringObject")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString)
                        .dataFetcher(stringObjectValueFetcher))
                .field(newFieldDefinition()
                        .name("nonNullValue")
                        .type(nonNull(GraphQLString))
                        .dataFetcher(stringObjectValueFetcher))
                .field(newFieldDefinition()
                        .name("veryNonNullValue")
                        .type(nonNull(nonNull(GraphQLString)))
                        .dataFetcher(stringObjectValueFetcher))
                .field(newFieldDefinition()
                        .name("throwException")
                        .type(GraphQLString)
                        .dataFetcher(throwExceptionFetcher))
                .field(newFieldDefinition()
                        .name("returnBadList")
                        .type(GraphQLString)
                        .dataFetcher(returnBadListFetcher))
                .field(newFieldDefinition()
                        .name("anyIterable")
                        .type(list(GraphQLString))
                        .dataFetcher(anyIterableFetcher))
                .field(newFieldDefinition()
                        .name("shatter")
                        .type(nonNull(list(nonNull(typeRef("StringObject")))))
                        .dataFetcher(shatterFetcher))

                .field(newFieldDefinition()
                        .name("wordsAndLetters")
                        .type(nonNull(list(nonNull(list(nonNull(nonNull(typeRef("StringObject"))))))))
                        .dataFetcher(wordsAndLettersFetcher))

                .field(newFieldDefinition()
                        .name("split")
                        .description("String#split(regex) but replace empty strings with nulls to help us test null behavior in lists")
                        .type(list(typeRef("StringObject")))
                        .argument(GraphQLArgument.newArgument()
                                .name("regex")
                                .type(GraphQLString))
                        .dataFetcher(splitFetcher))

                .field(newFieldDefinition()
                        .name("splitNotNull")
                        .description("String#split(regex) but replace empty strings with nulls to help us test null behavior in lists")
                        .type(list(nonNull(typeRef("StringObject"))))
                        .argument(GraphQLArgument.newArgument()
                                .name("regex")
                                .type(GraphQLString))
                        .dataFetcher(splitFetcher))


                .field(newFieldDefinition()
                        .name("append")
                        .type(typeRef("StringObject"))
                        .argument(GraphQLArgument.newArgument()
                                .name("text")
                                .type(GraphQLString))
                        .dataFetcher(appendFetcher))

                .field(newFieldDefinition()
                        .name("emptyOptional")
                        .type(GraphQLString)
                        .argument(GraphQLArgument.newArgument()
                                .name("text")
                                .type(GraphQLString))
                        .dataFetcher(emptyOptionalFetcher))

                .field(newFieldDefinition()
                        .name("optional")
                        .type(GraphQLString)
                        .argument(GraphQLArgument.newArgument()
                                .name("text")
                                .type(GraphQLString))
                        .dataFetcher(optionalFetcher))

                .field(newFieldDefinition()
                        .name("completableFuture")
                        .type(GraphQLString)
                        .argument(GraphQLArgument.newArgument()
                                .name("text")
                                .type(GraphQLString))
                        .dataFetcher(completableFutureFetcher))

                .build();


        GraphQLEnumType enumDayType = newEnum()
                .name("Day")
                .value("MONDAY")
                .value("TUESDAY")
                .description("Day of the week")
                .build();


        GraphQLObjectType simpleObjectType = newObject()
                .name("SimpleObject")
                .withInterface(typeRef("InterfaceType"))
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build();


        GraphQLInterfaceType interfaceType = newInterface()
                .name("InterfaceType")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString)
                )
                .typeResolver(env -> {
                    // always this for testing
                    return simpleObjectType;
                })
                .build();

        GraphQLObjectType queryType = newObject()
                .name("StringQuery")
                .field(newFieldDefinition()
                        .name("string")
                        .type(stringObjectType)
                        .argument(GraphQLArgument.newArgument()
                                .name("value")
                                .type(GraphQLString))
                        .dataFetcher(env -> env.getArgument("value"))
                )
                .field(newFieldDefinition()
                        .name("interface")
                        .type(interfaceType)
                        .argument(GraphQLArgument.newArgument()
                                .name("value")
                                .type(GraphQLString))
                        .dataFetcher(env -> CompletableFuture.completedFuture(new SimpleObject()))
                )
                .field(newFieldDefinition()
                        .name("nullEnum")
                        .type(enumDayType)
                        .dataFetcher(env -> null)
                )
                .build();

        return GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(simpleObjectType)
                .build();

    }
}

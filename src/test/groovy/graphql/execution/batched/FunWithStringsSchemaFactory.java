package graphql.execution.batched;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
                return retVal;
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

    GraphQLSchema createSchema() {

        GraphQLObjectType stringObjectType = GraphQLObjectType.newObject()
                .name("StringObject")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("value")
                        .type(Scalars.GraphQLString)
                        .dataFetcher(stringObjectValueFetcher))
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("nonNullValue")
                        .type(new GraphQLNonNull(Scalars.GraphQLString))
                        .dataFetcher(stringObjectValueFetcher))
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("veryNonNullValue")
                        .type(new GraphQLNonNull(new GraphQLNonNull(Scalars.GraphQLString)))
                        .dataFetcher(stringObjectValueFetcher))
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("throwException")
                        .type(Scalars.GraphQLString)
                        .dataFetcher(throwExceptionFetcher))
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("returnBadList")
                        .type(Scalars.GraphQLString)
                        .dataFetcher(returnBadListFetcher))
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("shatter")
                        .type(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(new GraphQLTypeReference("StringObject")))))
                        .dataFetcher(shatterFetcher))

                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("wordsAndLetters")
                        .type(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(new GraphQLNonNull(new GraphQLTypeReference("StringObject"))))))))
                        .dataFetcher(wordsAndLettersFetcher))

                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("split")
                        .description("String#split(regex) but replace empty strings with nulls to help us test null behavior in lists")
                        .type(new GraphQLList(new GraphQLTypeReference("StringObject")))
                        .argument(GraphQLArgument.newArgument()
                                .name("regex")
                                .type(Scalars.GraphQLString))
                        .dataFetcher(splitFetcher))

                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("splitNotNull")
                        .description("String#split(regex) but replace empty strings with nulls to help us test null behavior in lists")
                        .type(new GraphQLList(new GraphQLNonNull(new GraphQLTypeReference("StringObject"))))
                        .argument(GraphQLArgument.newArgument()
                                .name("regex")
                                .type(Scalars.GraphQLString))
                        .dataFetcher(splitFetcher))


                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("append")
                        .type(new GraphQLTypeReference("StringObject"))
                        .argument(GraphQLArgument.newArgument()
                                .name("text")
                                .type(Scalars.GraphQLString))
                        .dataFetcher(appendFetcher))

                .build();


        GraphQLEnumType enumDayType = GraphQLEnumType.newEnum()
                .name("Day")
                .value("MONDAY")
                .value("TUESDAY")
                .description("Day of the week")
                .build();

        GraphQLObjectType queryType = GraphQLObjectType.newObject()
                .name("StringQuery")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("string")
                        .type(stringObjectType)
                        .argument(GraphQLArgument.newArgument()
                                .name("value")
                                .type(Scalars.GraphQLString))
                        .dataFetcher(env -> env.getArgument("value")))
                .name("EnumQuery")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("nullEnum")
                        .type(enumDayType)
                        .dataFetcher(env -> null))
                .build();
        return GraphQLSchema.newSchema()
                .query(queryType)
                .build();

    }
}

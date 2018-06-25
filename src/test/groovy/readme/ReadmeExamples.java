package readme;

import graphql.GraphQL;
import graphql.Scalars;
import graphql.StarWarsData;
import graphql.StarWarsSchema;
import graphql.TypeResolutionEnvironment;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutorServiceExecutionStrategy;
import graphql.language.Directive;
import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.StaticDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.InterfaceWiringEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnionWiringEnvironment;
import graphql.schema.idl.WiringFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static graphql.GarfieldSchema.Cat;
import static graphql.GarfieldSchema.CatType;
import static graphql.GarfieldSchema.Dog;
import static graphql.GarfieldSchema.DogType;
import static graphql.MutationSchema.mutationType;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.StarWarsSchema.queryType;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLUnionType.newUnionType;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * This class holds readme examples so they stay correct and can be compiled.  If this
 * does not parse, chances are the readme examples are now wrong.
 *
 * You should place these examples into the README.next.md and NOT the main README.md.  This allows
 * 'master' to progress yet shows consumers the released information about the project.
 */
@SuppressWarnings({"unused", "Convert2Lambda", "UnnecessaryLocalVariable", "ConstantConditions", "SameParameterValue", "ClassCanBeStatic"})
public class ReadmeExamples {


    public Map<String, Object> getInputFromJSON() {
        return new HashMap<>();
    }

    class Foo {
    }

    void creatingASchema() {
        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType) // must be provided
                .mutation(mutationType) // is optional
                .build();
    }

    void listsAndNonNullLists() {
        GraphQLList.list(GraphQLString); // a list of Strings

        GraphQLNonNull.nonNull(GraphQLString); // a non null String

        // with static imports its even shorter
        newArgument()
                .name("example")
                .type(nonNull(list(GraphQLString)));
    }

    void newType() {
        GraphQLObjectType simpsonCharacter = newObject()
                .name("SimpsonCharacter")
                .description("A Simpson character")
                .field(newFieldDefinition()
                        .name("name")
                        .description("The name of the character.")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("mainCharacter")
                        .description("One of the main Simpson characters?")
                        .type(GraphQLBoolean))
                .build();
    }

    void interfaceType() {
        GraphQLInterfaceType comicCharacter = newInterface()
                .name("ComicCharacter")
                .description("A abstract comic character.")
                .field(newFieldDefinition()
                        .name("name")
                        .description("The name of the character.")
                        .type(GraphQLString))
                .build();
    }

    void inputTypes() {
        GraphQLInputObjectType inputObjectType = newInputObject()
                .name("inputObjectType")
                .field(newInputObjectField()
                        .name("field")
                        .type(GraphQLString))
                .build();
    }

    void enumTypes() {
        GraphQLEnumType colorEnum = newEnum()
                .name("Color")
                .description("Supported colors.")
                .value("RED")
                .value("GREEN")
                .value("BLUE")
                .build();
    }

    void unionType() {
        GraphQLUnionType PetType = newUnionType()
                .name("Pet")
                .possibleType(CatType)
                .possibleType(DogType)
                .typeResolver(new TypeResolver() {
                    @Override
                    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                        if (env.getObject() instanceof Cat) {
                            return CatType;
                        }
                        if (env.getObject() instanceof Dog) {
                            return DogType;
                        }
                        return null;
                    }
                })
                .build();
    }


    void recursiveTypes() {

        GraphQLObjectType person = newObject()
                .name("Person")
                .field(newFieldDefinition()
                        .name("friends")
                        .type(GraphQLList.list(GraphQLTypeReference.typeRef("Person"))))
                .build();
    }

    void executionStrategies() {

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                2, /* core pool size 2 thread */
                2, /* max pool size 2 thread */
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(new ExecutorServiceExecutionStrategy(threadPoolExecutor))
                .mutationExecutionStrategy(new AsyncExecutionStrategy())
                .subscriptionExecutionStrategy(new AsyncExecutionStrategy())
                .build();

    }

    void dataFetching() {

        DataFetcher<Foo> fooDataFetcher = new DataFetcher<Foo>() {
            @Override
            public Foo get(DataFetchingEnvironment environment) {
                // environment.getSource() is the value of the surrounding
                // object. In this case described by objectType
                Foo value = perhapsFromDatabase(); // Perhaps getting from a DB or whatever
                return value;
            }
        };

        GraphQLObjectType objectType = newObject()
                .name("ObjectType")
                .field(newFieldDefinition()
                        .name("foo")
                        .type(GraphQLString)
                        .dataFetcher(fooDataFetcher))
                .build();

    }

    class Review {

    }

    static class EpisodeInput {

        public static EpisodeInput fromMap(Map<String, Object> episodeInput) {
            return null;
        }
    }

    static class ReviewInput {
        public static ReviewInput fromMap(Map<String, Object> reviewInput) {
            return null;
        }

    }

    class ReviewStore {
        Review update(EpisodeInput episodeInput, ReviewInput reviewInput) {
            return null;
        }
    }

    private ReviewStore reviewStore() {
        return new ReviewStore();
    }

    void mutationExample() {

        GraphQLInputObjectType episodeType = GraphQLInputObjectType.newInputObject()
                .name("Episode")
                .field(newInputObjectField()
                        .name("episodeNumber")
                        .type(Scalars.GraphQLInt))
                .build();

        GraphQLInputObjectType reviewInputType = GraphQLInputObjectType.newInputObject()
                .name("ReviewInput")
                .field(newInputObjectField()
                        .name("stars")
                        .type(Scalars.GraphQLString)
                        .name("commentary")
                        .type(Scalars.GraphQLString))
                .build();

        GraphQLObjectType reviewType = newObject()
                .name("Review")
                .field(newFieldDefinition()
                        .name("stars")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("commentary")
                        .type(GraphQLString))
                .build();

        GraphQLObjectType createReviewForEpisodeMutation = newObject()
                .name("CreateReviewForEpisodeMutation")
                .field(newFieldDefinition()
                        .name("createReview")
                        .type(reviewType)
                        .argument(newArgument()
                                .name("episode")
                                .type(episodeType)
                        )
                        .argument(newArgument()
                                .name("review")
                                .type(reviewInputType)
                        )
                        .dataFetcher(mutationDataFetcher())
                )
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(createReviewForEpisodeMutation)
                .build();
    }

    private DataFetcher mutationDataFetcher() {
        return new DataFetcher() {
            @Override
            public Review get(DataFetchingEnvironment environment) {
                //
                // The graphql specification dictates that input object arguments MUST
                // be maps.  You can convert them to POJOs inside the data fetcher if that
                // suits your code better
                //
                // See http://facebook.github.io/graphql/October2016/#sec-Input-Objects
                //
                Map<String, Object> episodeInputMap = environment.getArgument("episode");
                Map<String, Object> reviewInputMap = environment.getArgument("review");

                //
                // in this case we have type safe Java objects to call our backing code with
                //
                EpisodeInput episodeInput = EpisodeInput.fromMap(episodeInputMap);
                ReviewInput reviewInput = ReviewInput.fromMap(reviewInputMap);

                // make a call to your store to mutate your database
                Review updatedReview = reviewStore().update(episodeInput, reviewInput);

                // this returns a new view of the data
                return updatedReview;
            }
        };
    }

    private Object context() {
        return null;
    }

    private Foo perhapsFromDatabase() {
        return new Foo();
    }

    void parsedSchemaExample() {

        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        File schemaFile = loadSchema("starWarsSchema.graphqls");

        TypeDefinitionRegistry typeRegistry = schemaParser.parse(schemaFile);
        RuntimeWiring wiring = buildRuntimeWiring();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
    }

    void parsedSplitSchemaExample() {

        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        File schemaFile1 = loadSchema("starWarsSchemaPart1.graphqls");
        File schemaFile2 = loadSchema("starWarsSchemaPart2.graphqls");
        File schemaFile3 = loadSchema("starWarsSchemaPart3.graphqls");

        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();

        // each compiled registry is merged into the main registry
        typeRegistry.merge(schemaParser.parse(schemaFile1));
        typeRegistry.merge(schemaParser.parse(schemaFile2));
        typeRegistry.merge(schemaParser.parse(schemaFile3));

        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, buildRuntimeWiring());
    }


    RuntimeWiring buildRuntimeWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .scalar(CustomScalar)
                // this uses builder function lambda syntax
                .type("QueryType", typeWiring -> typeWiring
                        .dataFetcher("hero", new StaticDataFetcher(StarWarsData.getArtoo()))
                        .dataFetcher("human", StarWarsData.getHumanDataFetcher())
                        .dataFetcher("droid", StarWarsData.getDroidDataFetcher())
                )
                .type("Human", typeWiring -> typeWiring
                        .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                )
                // you can use builder syntax if you don't like the lambda syntax
                .type(newTypeWiring("Droid")
                        .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                )
                // or full builder syntax if that takes your fancy where you call .build()
                .type(
                        newTypeWiring("Character")
                                .typeResolver(StarWarsData.getCharacterTypeResolver())
                                .build()
                )
                .build();
    }

    RuntimeWiring buildDynamicRuntimeWiring() {
        WiringFactory dynamicWiringFactory = new WiringFactory() {


            @Override
            public boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
                return getDirective(environment.getInterfaceTypeDefinition(), "specialMarker") != null;
            }

            @Override
            public boolean providesTypeResolver(UnionWiringEnvironment environment) {
                return getDirective(environment.getUnionTypeDefinition(), "specialMarker") != null;
            }

            @Override
            public TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
                Directive directive = getDirective(environment.getInterfaceTypeDefinition(), "specialMarker");
                return createTypeResolver(environment.getInterfaceTypeDefinition(), directive);
            }

            @Override
            public TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
                Directive directive = getDirective(environment.getUnionTypeDefinition(), "specialMarker");
                return createTypeResolver(environment.getUnionTypeDefinition(), directive);
            }

            @Override
            public boolean providesDataFetcher(FieldWiringEnvironment environment) {
                return getDirective(environment.getFieldDefinition(), "dataFetcher") != null;
            }

            @Override
            public DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
                Directive directive = getDirective(environment.getFieldDefinition(), "dataFetcher");
                return createDataFetcher(environment.getFieldDefinition(), directive);
            }
        };
        return RuntimeWiring.newRuntimeWiring()
                .wiringFactory(dynamicWiringFactory).build();
    }

    class Wizard {

    }

    class Witch {

    }

    private void typeResolverExample() {
        TypeResolver typeResolver = new TypeResolver() {
            @Override
            public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                Object javaObject = env.getObject();
                if (javaObject instanceof Wizard) {
                    return (GraphQLObjectType) env.getSchema().getType("WizardType");
                } else if (javaObject instanceof Witch) {
                    return (GraphQLObjectType) env.getSchema().getType("WitchType");
                } else {
                    return (GraphQLObjectType) env.getSchema().getType("NecromancerType");
                }
            }
        };
    }

    private DataFetcher createDataFetcher(FieldDefinition definition, Directive directive) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private TypeResolver createTypeResolver(TypeDefinition definition, Directive directive) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private Directive getDirective(TypeDefinition definition, String type) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private Directive getDirective(FieldDefinition fieldDefintion, String type) {
        throw new UnsupportedOperationException("Not implemented");
    }


    public static GraphQLScalarType CustomScalar = new GraphQLScalarType("Custom", "Custom Scalar", new Coercing<Integer, Integer>() {
        @Override
        public Integer serialize(Object input) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Integer parseValue(Object input) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Integer parseLiteral(Object input) {
            throw new UnsupportedOperationException("Not implemented");
        }
    });

    private File loadSchema(String s) {
        return null;
    }

}

package readme;

import graphql.GraphQL;
import graphql.Scalars;
import graphql.StarWarsData;
import graphql.StarWarsSchema;
import graphql.TypeResolutionEnvironment;
import graphql.execution.ExecutorServiceExecutionStrategy;
import graphql.execution.SimpleExecutionStrategy;
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
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaCompiler;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;

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
 * does not compile, chances are the readme examples are now wrong.
 *
 * You should place these examples into the README.next.md and NOT the main README.md.  This allows
 * 'master' to progress yet shows consumers the released information about the project.
 */
@SuppressWarnings({"unused", "Convert2Lambda", "UnnecessaryLocalVariable"})
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
                        .type(new GraphQLList(new GraphQLTypeReference("Person"))))
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
                .mutationExecutionStrategy(new SimpleExecutionStrategy())
                .subscriptionExecutionStrategy(new SimpleExecutionStrategy())
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

    class Episode {

    }

    class ReviewInput {

    }

    class ReviewStore {
        Review update(Episode episode, ReviewInput reviewInput) {
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
                Episode episode = environment.getArgument("episode");
                ReviewInput review = environment.getArgument("review");

                // make a call to your store to mutate your database
                Review updatedReview = reviewStore().update(episode, review);

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

    void compiledSchemaExample() {

        SchemaCompiler schemaCompiler = new SchemaCompiler();
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        File schemaFile = loadSchema("starWarsSchema.graphqls");

        TypeDefinitionRegistry typeRegistry = schemaCompiler.compile(schemaFile);
        RuntimeWiring wiring = buildRuntimeWiring();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
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

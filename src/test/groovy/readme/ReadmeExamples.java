package readme;

import graphql.GarfieldSchema;
import graphql.GraphQL;
import graphql.StarWarsData;
import graphql.StarWarsSchema;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static graphql.GarfieldSchema.CatType;
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
import static graphql.schema.idl.RuntimeWiring.TypeRuntimeWiring.newTypeWiring;

/**
 * This class holds readme examples so they stay correct and can be compiled.  If this
 * does not compile, chances are the readme examples are now wrong.
 */
@SuppressWarnings({"unused", "Convert2Lambda", "ConstantConditions", "Anonymous2MethodRef"})
public class ReadmeExamples {

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
                    public GraphQLObjectType getType(Object object) {
                        if (object instanceof GarfieldSchema.Cat) {
                            return CatType;
                        }
                        if (object instanceof GarfieldSchema.Dog) {
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
                .type(typeWiring -> typeWiring.typeName("QueryType")
                        .dataFetcher("hero", new StaticDataFetcher(StarWarsData.getArtoo()))
                        .dataFetcher("human", StarWarsData.getHumanDataFetcher())
                        .dataFetcher("droid", StarWarsData.getDroidDataFetcher())
                )
                .type(typeWiring -> typeWiring.typeName("Human")
                        .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                )
                // you can use builder syntax if you don't like the lambda syntax
                .type(typeWiring -> typeWiring.typeName("Droid")
                        .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                )
                // or full builder syntax if that takes your fancy
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

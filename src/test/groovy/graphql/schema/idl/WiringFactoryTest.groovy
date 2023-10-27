package graphql.schema.idl


import graphql.TestUtil
import graphql.TypeResolutionEnvironment
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactories
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLUnionType
import graphql.schema.PropertyDataFetcher
import graphql.schema.TypeResolver
import spock.lang.Specification

class WiringFactoryTest extends Specification {

    class NamedTypeResolver implements TypeResolver {
        String name

        NamedTypeResolver(String name) {
            this.name = name
        }

        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    class NamedDataFetcher implements DataFetcher {
        String name

        NamedDataFetcher(String name) {
            this.name = name
        }

        @Override
        Object get(DataFetchingEnvironment environment) {
            return name
        }
    }

    class NamedWiringFactory implements WiringFactory {
        String name

        NamedWiringFactory(String name) {
            this.name = name
        }

        @Override
        boolean providesScalar(ScalarWiringEnvironment environment) {
            return name == environment.getScalarTypeDefinition().getName()
        }

        @Override
        GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
            return GraphQLScalarType.newScalar().name(name).description("Custom scalar").coercing(new Coercing() {
                @Override
                Object serialize(Object input) {
                    throw new UnsupportedOperationException("Not implemented")
                }

                @Override
                Object parseValue(Object input) {
                    throw new UnsupportedOperationException("Not implemented")
                }

                @Override
                Object parseLiteral(Object input) {
                    throw new UnsupportedOperationException("Not implemented")
                }
            })
                    .build()
        }

        @Override
        boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
            return name == environment.getInterfaceTypeDefinition().getName()
        }

        @Override
        TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
            return new NamedTypeResolver(name)
        }

        @Override
        boolean providesTypeResolver(UnionWiringEnvironment environment) {
            return name == environment.getUnionTypeDefinition().getName()
        }

        @Override
        TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
            return new NamedTypeResolver(name)
        }

        @Override
        boolean providesDataFetcher(FieldWiringEnvironment environment) {
            return name == environment.getFieldDefinition().getName()
        }

        @Override
        DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
            return new NamedDataFetcher(name)
        }
    }

    class NamedDataFetcherFactoryWiringFactory implements WiringFactory {
        String name

        NamedDataFetcherFactoryWiringFactory(String name) {
            this.name = name
        }

        @Override
        boolean providesDataFetcherFactory(FieldWiringEnvironment environment) {
            return name == environment.getFieldDefinition().getName()
        }

        @Override
        <T> DataFetcherFactory<T> getDataFetcherFactory(FieldWiringEnvironment environment) {
            return DataFetcherFactories.useDataFetcher(new NamedDataFetcher(name))
        }
    }

    class NamedDefaultDataFetcherWiringFactory implements WiringFactory {
        def fields = []

        @Override
        boolean providesDataFetcher(FieldWiringEnvironment environment) {
            if (environment.getFieldDefinition().getName() == "name") {
                return true
            }
            return false
        }

        @Override
        DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
            new PropertyDataFetcher("name")
        }

        @Override
        DataFetcher getDefaultDataFetcher(FieldWiringEnvironment environment) {
            def name = environment.getFieldDefinition().getName()
            fields.add(name)
            new PropertyDataFetcher(name)
        }
    }


    def "ensure that wiring factory is called to resolve and create data fetchers"() {

        def spec = """             

            schema {
              query: Human
            }

            type Episode {
                name : String
            }
            
            interface Character {
                name: String!
            }
            
            scalar Long

            union Cyborg = Human | Droid
            
            type Droid implements Character {
                id: ID!
                name: String!
                age: Long
                friends: [Character]
                appearsIn: [Episode]!
            }
                
            type Human implements Character {
                id: ID!
                name: String!
                friends: [Character]
                appearsIn: [Episode]!
                homePlanet: String
                cyborg: Cyborg
            }
            
            type Other implements Character {
                name: String!
                fetchedByDefaultDataFetcher: String!
            }
        """

        WiringFactory defaultDataFetcherWiringFactory = new NamedDefaultDataFetcherWiringFactory()

        def combinedWiringFactory = new CombinedWiringFactory([
                new NamedWiringFactory("Character"),
                new NamedWiringFactory("Cyborg"),
                new NamedWiringFactory("Long"),
                new NamedDataFetcherFactoryWiringFactory("cyborg"),
                new NamedWiringFactory("friends"),
                defaultDataFetcherWiringFactory
        ])

        def wiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(combinedWiringFactory)
                .build()

        def schema = TestUtil.schema(spec, wiring)

        expect:

        GraphQLInterfaceType characterType = schema.getType("Character") as GraphQLInterfaceType

        def characterTypeResolver = schema.getCodeRegistry().getTypeResolver(characterType) as NamedTypeResolver
        characterTypeResolver.name == "Character"

        GraphQLUnionType unionType = schema.getType("Cyborg") as GraphQLUnionType

        def unionTypeResolver = schema.getCodeRegistry().getTypeResolver(unionType) as NamedTypeResolver
        unionTypeResolver.name == "Cyborg"


        GraphQLObjectType humanType = schema.getType("Human") as GraphQLObjectType

        def friendsDataFetcher = schema.getCodeRegistry().getDataFetcher(humanType, humanType.getFieldDefinition("friends")) as NamedDataFetcher
        friendsDataFetcher.name == "friends"

        def cyborgDataFetcher = schema.getCodeRegistry().getDataFetcher(humanType, humanType.getFieldDefinition("cyborg")) as NamedDataFetcher
        cyborgDataFetcher.name == "cyborg"

        GraphQLScalarType longScalar = schema.getType("Long") as GraphQLScalarType
        longScalar.name == "Long"

        defaultDataFetcherWiringFactory.fields.contains("fetchedByDefaultDataFetcher")

    }

    def "ensure field wiring environment makes sense"() {
        def spec = """             

            schema {
              query: Human
            }

            type Human {
                id: ID!
                name: String!
                homePlanet: String
            }
        """

        def wiringFactory = new WiringFactory() {

            @Override
            boolean providesDataFetcher(FieldWiringEnvironment environment) {
                assert ["id", "name", "homePlanet"].contains(environment.fieldDefinition.name)
                assert environment.parentType.name == "Human"
                assert environment.registry.getType("Human").isPresent()
                return true
            }

            @Override
            DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
                assert ["id", "name", "homePlanet"].contains(environment.fieldDefinition.name)
                assert environment.parentType.name == "Human"
                assert environment.registry.getType("Human").isPresent()
                new PropertyDataFetcher(environment.fieldDefinition.name)
            }
        }
        def wiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(wiringFactory)
                .build()

        TestUtil.schema(spec, wiring)

        expect:

        true // assertions in callback
    }

    def "default data fetcher is used"() {

        def spec = """   

            type Query {
                id: ID!
                name: String!
                homePlanet: String
            }
        """

        def wiringFactory = new NamedDefaultDataFetcherWiringFactory()
        def wiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(wiringFactory)
                .build()

        TestUtil.schema(spec, wiring)

        expect:

        wiringFactory.fields == ["id", "homePlanet"]
    }

    def "Name"() {
        WiringFactory wf = new WiringFactory() {
            @Override
            boolean providesDataFetcherFactory(FieldWiringEnvironment environment) {
                def fieldDef = environment.getFieldDefinition();
                if (fieldDef.getName() == "class") {
                    return true;
                }
                return false;
            }

            @Override
            DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
                return new DataFetcher() {
                    @Override
                    Object get(DataFetchingEnvironment env) throws Exception {
                        def sourceObject = env.getSource()
                        return sourceObject.getClass().getSimpleName()
                    }
                }
            }
        }
    }
}

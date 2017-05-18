package graphql.schema.idl

import graphql.TypeResolutionEnvironment
import graphql.language.FieldDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.UnionTypeDefinition
import graphql.schema.*
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
        boolean providesTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition definition) {
            return name == definition.getName()
        }

        @Override
        boolean providesTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition definition) {
            return name == definition.getName()
        }

        @Override
        TypeResolver getTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition definition) {
            return new NamedTypeResolver(name)
        }

        @Override
        TypeResolver getTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition definition) {
            return new NamedTypeResolver(name)
        }

        @Override
        boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
            return name == definition.getName()
        }

        @Override
        DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
            return new NamedDataFetcher(name)
        }
    }


    GraphQLSchema generateSchema(String schemaSpec, RuntimeWiring wiring) {
        def typeRegistry = new SchemaParser().parse(schemaSpec)
        def result = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring)
        result
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
            
            union Cyborg = Human | Droid
            
            type Droid implements Character {
                id: ID!
                name: String!
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
        """



        def combinedWiringFactory = new CombinedWiringFactory([
                new NamedWiringFactory("Character"),
                new NamedWiringFactory("Cyborg"),
                new NamedWiringFactory("friends")])

        def wiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(combinedWiringFactory)
                .build()

        def schema = generateSchema(spec, wiring)

        expect:

        GraphQLInterfaceType characterType = schema.getType("Character") as GraphQLInterfaceType

        def characterTypeResolver = characterType.getTypeResolver() as NamedTypeResolver
        characterTypeResolver.name == "Character"

        GraphQLUnionType unionType = schema.getType("Cyborg") as GraphQLUnionType

        def unionTypeResolver = unionType.getTypeResolver() as NamedTypeResolver
        unionTypeResolver.name == "Cyborg"


        GraphQLObjectType humanType = schema.getType("Human") as GraphQLObjectType

        def friendsDataFetcher = humanType.getFieldDefinition("friends").getDataFetcher() as NamedDataFetcher
        friendsDataFetcher.name == "friends"

    }

}

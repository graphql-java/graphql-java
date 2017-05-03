package graphql.schema.idl

import graphql.Scalars
import graphql.TypeResolutionEnvironment
import graphql.schema.*
import spock.lang.Specification

class SchemaDecompilerTest extends Specification {

    def nonNull(GraphQLType type) {
        new GraphQLNonNull(type)
    }

    def list(GraphQLType type) {
        new GraphQLList(type)
    }

    GraphQLSchema starWarsSchema() {
        def wiring =  RuntimeWiring.newRuntimeWiring()
                .type("Character", { type -> type.typeResolver(resolver)})
                .scalar(ASTEROID)
                .build()
        GraphQLSchema schema = load("starWarsSchemaExtended.graphqls", wiring)
        schema
    }


    GraphQLScalarType ASTEROID = new GraphQLScalarType("Asteroid","desc", new Coercing() {
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

    def resolver = new TypeResolver() {

        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    GraphQLSchema load(String fileName, RuntimeWiring wiring) {
        def stream = getClass().getClassLoader().getResourceAsStream(fileName)

        def typeRegistry = new SchemaCompiler().compile(new InputStreamReader(stream))
        def schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring)
        schema
    }

    def "typeString"() {

        GraphQLType type1 = nonNull(list(nonNull(list(nonNull(Scalars.GraphQLInt)))))
        GraphQLType type2 = nonNull(nonNull(list(nonNull(Scalars.GraphQLInt))))

        def typeStr1 = new SchemaDecompiler().typeString(type1)
        def typeStr2 = new SchemaDecompiler().typeString(type2)

        expect:
        typeStr1 == "[[Int!]!]!"
        typeStr2 == "[Int!]!!"

    }

    def "argsString"() {
        def argument1 = new GraphQLArgument("arg1", "desc-arg1", list(nonNull(Scalars.GraphQLInt)), 10)
        def argument2 = new GraphQLArgument("arg2", "desc-arg2", Scalars.GraphQLString, null)
        def argument3 = new GraphQLArgument("arg3", "desc-arg3", Scalars.GraphQLString, "default")
        def argStr = new SchemaDecompiler().argsString([argument1, argument2, argument3])

        expect:

        argStr == "(arg1 : [Int!] = 10, arg2 : String, arg3 : String = \"default\")"
    }

    def "decompile type direct"() {
        GraphQLSchema schema = starWarsSchema()

        def decompile = new SchemaDecompiler().decompile(schema.getType("Character"))

        expect:
        decompile ==
                """interface Character {
   id : ID!
   name : String!
   friends : [Character]
   appearsIn : [Episode]!
}

"""
    }

    def "starWars default Test"() {
        GraphQLSchema schema = starWarsSchema()

        def decompile = new SchemaDecompiler().decompile(schema)

        expect:
        decompile != null
        !decompile.contains("scalar")
        !decompile.contains("__TypeKind")
    }

    def "starWars non default Test"() {
        GraphQLSchema schema = starWarsSchema()

        def options = SchemaDecompiler.Options.defaultOptions()
                .includeIntrospectionTypes(true)
                .includeScalarTypes(true)

        def decompile = new SchemaDecompiler(options).decompile(schema)

        expect:
        decompile != null
        decompile.contains("scalar")
        decompile.contains("__TypeKind")
    }
}

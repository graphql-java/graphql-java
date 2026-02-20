package graphql.schema

import graphql.Scalars
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLTypeReference.typeRef

/**
 * Comparison tests for interface implementations between FastBuilder and standard Builder.
 *
 * These tests verify that FastBuilder tracks interface implementations correctly and in
 * the same sorted order as the standard Builder (alphabetically by type name).
 *
 * CRITICAL: getImplementations() returns a LIST and order matters - implementations
 * should be sorted alphabetically by name.
 */
class FastBuilderComparisonInterfaceTest extends FastBuilderComparisonTest {

    def "single interface with multiple implementations - getImplementations matches in sorted order"() {
        given: "SDL with interface and multiple implementations"
        def sdl = """
            type Query {
                entity: Entity
            }

            interface Entity {
                id: String
            }

            type Zebra implements Entity {
                id: String
                stripes: String
            }

            type Aardvark implements Entity {
                id: String
                tongue: String
            }

            type Meerkat implements Entity {
                id: String
                burrow: String
            }
        """

        and: "programmatically created types"
        def entityInterface = GraphQLInterfaceType.newInterface()
                .name("Entity")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def zebraType = newObject()
                .name("Zebra")
                .withInterface(typeRef("Entity"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("stripes")
                        .type(GraphQLString))
                .build()

        def aardvarkType = newObject()
                .name("Aardvark")
                .withInterface(typeRef("Entity"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("tongue")
                        .type(GraphQLString))
                .build()

        def meerkatType = newObject()
                .name("Meerkat")
                .withInterface(typeRef("Entity"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("burrow")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("entity")
                        .type(entityInterface))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Entity", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [entityInterface, zebraType, aardvarkType, meerkatType],
                [],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "implementations are in sorted order (Aardvark, Meerkat, Zebra)"
        def fastImpls = fastSchema.getImplementations(fastSchema.getType("Entity") as GraphQLInterfaceType)*.name
        def standardImpls = standardSchema.getImplementations(standardSchema.getType("Entity") as GraphQLInterfaceType)*.name

        fastImpls == ["Aardvark", "Meerkat", "Zebra"]
        fastImpls == standardImpls
    }

    def "multiple interfaces with overlapping implementations - all getImplementations match"() {
        given: "SDL with multiple interfaces and overlapping implementations"
        def sdl = """
            type Query {
                node: Node
                named: Named
            }

            interface Node {
                id: String
            }

            interface Named {
                name: String
            }

            type User implements Node & Named {
                id: String
                name: String
                email: String
            }

            type Product implements Node & Named {
                id: String
                name: String
                price: Float
            }

            type Comment implements Node {
                id: String
                text: String
            }
        """

        and: "programmatically created types"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def namedInterface = GraphQLInterfaceType.newInterface()
                .name("Named")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def userType = newObject()
                .name("User")
                .withInterface(typeRef("Node"))
                .withInterface(typeRef("Named"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("email")
                        .type(GraphQLString))
                .build()

        def productType = newObject()
                .name("Product")
                .withInterface(typeRef("Node"))
                .withInterface(typeRef("Named"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("price")
                        .type(Scalars.GraphQLFloat))
                .build()

        def commentType = newObject()
                .name("Comment")
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("text")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .field(newFieldDefinition()
                        .name("named")
                        .type(namedInterface))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })
                .typeResolver("Named", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [nodeInterface, namedInterface, userType, productType, commentType],
                [],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "Node interface has 3 implementations in sorted order"
        def fastNodeImpls = fastSchema.getImplementations(fastSchema.getType("Node") as GraphQLInterfaceType)*.name
        def standardNodeImpls = standardSchema.getImplementations(standardSchema.getType("Node") as GraphQLInterfaceType)*.name

        fastNodeImpls == ["Comment", "Product", "User"]
        fastNodeImpls == standardNodeImpls

        and: "Named interface has 2 implementations in sorted order"
        def fastNamedImpls = fastSchema.getImplementations(fastSchema.getType("Named") as GraphQLInterfaceType)*.name
        def standardNamedImpls = standardSchema.getImplementations(standardSchema.getType("Named") as GraphQLInterfaceType)*.name

        fastNamedImpls == ["Product", "User"]
        fastNamedImpls == standardNamedImpls
    }

    def "interface extending interface - getImplementations matches for both interfaces"() {
        given: "SDL with interface inheritance"
        def sdl = """
            type Query {
                node: Node
                namedNode: NamedNode
            }

            interface Node {
                id: String
            }

            interface NamedNode implements Node {
                id: String
                name: String
            }

            type Person implements NamedNode & Node {
                id: String
                name: String
                age: Int
            }

            type Organization implements NamedNode & Node {
                id: String
                name: String
                address: String
            }

            type Document implements Node {
                id: String
                title: String
            }
        """

        and: "programmatically created types"
        def nodeInterface = GraphQLInterfaceType.newInterface()
                .name("Node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def namedNodeInterface = GraphQLInterfaceType.newInterface()
                .name("NamedNode")
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def personType = newObject()
                .name("Person")
                .withInterface(typeRef("NamedNode"))
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("age")
                        .type(Scalars.GraphQLInt))
                .build()

        def organizationType = newObject()
                .name("Organization")
                .withInterface(typeRef("NamedNode"))
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("address")
                        .type(GraphQLString))
                .build()

        def documentType = newObject()
                .name("Document")
                .withInterface(typeRef("Node"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("title")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("node")
                        .type(nodeInterface))
                .field(newFieldDefinition()
                        .name("namedNode")
                        .type(namedNodeInterface))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", { env -> null })
                .typeResolver("NamedNode", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [nodeInterface, namedNodeInterface, personType, organizationType, documentType],
                [],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "Node interface has 3 object implementations in sorted order"
        def fastNodeImpls = fastSchema.getImplementations(fastSchema.getType("Node") as GraphQLInterfaceType)*.name
        def standardNodeImpls = standardSchema.getImplementations(standardSchema.getType("Node") as GraphQLInterfaceType)*.name

        fastNodeImpls == ["Document", "Organization", "Person"]
        fastNodeImpls == standardNodeImpls

        and: "NamedNode interface has 2 implementations in sorted order"
        def fastNamedNodeImpls = fastSchema.getImplementations(fastSchema.getType("NamedNode") as GraphQLInterfaceType)*.name
        def standardNamedNodeImpls = standardSchema.getImplementations(standardSchema.getType("NamedNode") as GraphQLInterfaceType)*.name

        fastNamedNodeImpls == ["Organization", "Person"]
        fastNamedNodeImpls == standardNamedNodeImpls
    }

    def "object implementing multiple interfaces - tracked correctly in all interfaces"() {
        given: "SDL with object implementing multiple interfaces"
        def sdl = """
            type Query {
                entity: Entity
                timestamped: Timestamped
                versioned: Versioned
            }

            interface Entity {
                id: String
            }

            interface Timestamped {
                createdAt: String
                updatedAt: String
            }

            interface Versioned {
                version: Int
            }

            type Article implements Entity & Timestamped & Versioned {
                id: String
                createdAt: String
                updatedAt: String
                version: Int
                title: String
            }

            type BasicEntity implements Entity {
                id: String
            }
        """

        and: "programmatically created types"
        def entityInterface = GraphQLInterfaceType.newInterface()
                .name("Entity")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def timestampedInterface = GraphQLInterfaceType.newInterface()
                .name("Timestamped")
                .field(newFieldDefinition()
                        .name("createdAt")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("updatedAt")
                        .type(GraphQLString))
                .build()

        def versionedInterface = GraphQLInterfaceType.newInterface()
                .name("Versioned")
                .field(newFieldDefinition()
                        .name("version")
                        .type(Scalars.GraphQLInt))
                .build()

        def articleType = newObject()
                .name("Article")
                .withInterface(typeRef("Entity"))
                .withInterface(typeRef("Timestamped"))
                .withInterface(typeRef("Versioned"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("createdAt")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("updatedAt")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("version")
                        .type(Scalars.GraphQLInt))
                .field(newFieldDefinition()
                        .name("title")
                        .type(GraphQLString))
                .build()

        def basicEntityType = newObject()
                .name("BasicEntity")
                .withInterface(typeRef("Entity"))
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("entity")
                        .type(entityInterface))
                .field(newFieldDefinition()
                        .name("timestamped")
                        .type(timestampedInterface))
                .field(newFieldDefinition()
                        .name("versioned")
                        .type(versionedInterface))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Entity", { env -> null })
                .typeResolver("Timestamped", { env -> null })
                .typeResolver("Versioned", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [entityInterface, timestampedInterface, versionedInterface, articleType, basicEntityType],
                [],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "Entity interface has both implementations"
        def fastEntityImpls = fastSchema.getImplementations(fastSchema.getType("Entity") as GraphQLInterfaceType)*.name
        def standardEntityImpls = standardSchema.getImplementations(standardSchema.getType("Entity") as GraphQLInterfaceType)*.name

        fastEntityImpls == ["Article", "BasicEntity"]
        fastEntityImpls == standardEntityImpls

        and: "Timestamped interface has only Article"
        def fastTimestampedImpls = fastSchema.getImplementations(fastSchema.getType("Timestamped") as GraphQLInterfaceType)*.name
        def standardTimestampedImpls = standardSchema.getImplementations(standardSchema.getType("Timestamped") as GraphQLInterfaceType)*.name

        fastTimestampedImpls == ["Article"]
        fastTimestampedImpls == standardTimestampedImpls

        and: "Versioned interface has only Article"
        def fastVersionedImpls = fastSchema.getImplementations(fastSchema.getType("Versioned") as GraphQLInterfaceType)*.name
        def standardVersionedImpls = standardSchema.getImplementations(standardSchema.getType("Versioned") as GraphQLInterfaceType)*.name

        fastVersionedImpls == ["Article"]
        fastVersionedImpls == standardVersionedImpls
    }

    def "many implementations of single interface - alphabetical sort order preserved"() {
        given: "SDL with many implementations"
        def sdl = """
            type Query {
                animal: Animal
            }

            interface Animal {
                name: String
            }

            type Zebra implements Animal {
                name: String
            }

            type Yak implements Animal {
                name: String
            }

            type Wolf implements Animal {
                name: String
            }

            type Elephant implements Animal {
                name: String
            }

            type Dog implements Animal {
                name: String
            }

            type Cat implements Animal {
                name: String
            }

            type Bear implements Animal {
                name: String
            }

            type Aardvark implements Animal {
                name: String
            }
        """

        and: "programmatically created types"
        def animalInterface = GraphQLInterfaceType.newInterface()
                .name("Animal")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def zebraType = newObject()
                .name("Zebra")
                .withInterface(typeRef("Animal"))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def yakType = newObject()
                .name("Yak")
                .withInterface(typeRef("Animal"))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def wolfType = newObject()
                .name("Wolf")
                .withInterface(typeRef("Animal"))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def elephantType = newObject()
                .name("Elephant")
                .withInterface(typeRef("Animal"))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def dogType = newObject()
                .name("Dog")
                .withInterface(typeRef("Animal"))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def catType = newObject()
                .name("Cat")
                .withInterface(typeRef("Animal"))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def bearType = newObject()
                .name("Bear")
                .withInterface(typeRef("Animal"))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def aardvarkType = newObject()
                .name("Aardvark")
                .withInterface(typeRef("Animal"))
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("animal")
                        .type(animalInterface))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Animal", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [animalInterface, zebraType, yakType, wolfType, elephantType, dogType, catType, bearType, aardvarkType],
                [],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "all implementations are in strict alphabetical order"
        def fastImpls = fastSchema.getImplementations(fastSchema.getType("Animal") as GraphQLInterfaceType)*.name
        def standardImpls = standardSchema.getImplementations(standardSchema.getType("Animal") as GraphQLInterfaceType)*.name

        fastImpls == ["Aardvark", "Bear", "Cat", "Dog", "Elephant", "Wolf", "Yak", "Zebra"]
        fastImpls == standardImpls
    }

    def "interface with no implementations - empty list matches"() {
        given: "SDL with interface that has no implementations"
        def sdl = """
            type Query {
                unused: Unused
                value: String
            }

            interface Unused {
                id: String
            }
        """

        and: "programmatically created types"
        def unusedInterface = GraphQLInterfaceType.newInterface()
                .name("Unused")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("unused")
                        .type(unusedInterface))
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Unused", { env -> null })

        when: "building with both approaches"
        def standardSchema = buildSchemaFromSDL(sdl)
        def fastSchema = buildSchemaWithFastBuilder(
                queryType,
                null,
                null,
                [unusedInterface],
                [],
                codeRegistry
        )

        then: "schemas are equivalent"
        assertSchemasEquivalent(fastSchema, standardSchema)

        and: "both return empty list for implementations"
        def fastImpls = fastSchema.getImplementations(fastSchema.getType("Unused") as GraphQLInterfaceType)
        def standardImpls = standardSchema.getImplementations(standardSchema.getType("Unused") as GraphQLInterfaceType)

        fastImpls.isEmpty()
        standardImpls.isEmpty()
        fastImpls*.name == standardImpls*.name
    }
}

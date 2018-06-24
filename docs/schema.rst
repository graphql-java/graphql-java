Creating a schema
=================


A schema defines your GraphQL API by defining each field that can be queried or
mutated.

``graphql-java`` offers two different ways of defining the schema: Programmatically as Java code or
via a special graphql dsl (called SDL).

If you are unsure which option to use we recommend the SDL.

SDL example:

.. code-block:: graphql

    type Foo {
        bar: String
    }


Java code example:

.. code-block:: java

    GraphQLObjectType fooType = newObject()
        .name("Foo")
        .field(newFieldDefinition()
                .name("bar")
                .type(GraphQLString))
        .build();

DataFetcher and TypeResolver
----------------------------

A ``DataFetcher`` provides the data for a field (and changes something, if it is a mutation).

Every field definition has a ``DataFetcher``. When one is not configured, a
`PropertyDataFetcher <https://github.com/graphql-java/graphql-java/blob/master/src/main/java/graphql/schema/PropertyDataFetcher.java>`_ is used.

``PropertyDataFetcher`` fetches data from ``Map`` and Java Beans. So when the field name matches the Map key or
the property name of the source Object, no ``DataFetcher`` is needed.

A ``TypeResolver`` helps ``graphql-java`` to decide which type a concrete value belongs to.
This is needed for ``Interface`` and ``Union``.

For example imagine you have an ``Interface`` called *MagicUserType* which resolves back to a series of Java classes
called *Wizard*, *Witch* and *Necromancer*.  The type resolver is responsible for examining a runtime object and deciding
what ``GraphqlObjectType`` should be used to represent it, and hence what data fetchers and fields will be invoked.

.. code-block:: java

        new TypeResolver() {
            @Override
            public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                Object javaObject = env.getObject();
                if (javaObject instanceof Wizard) {
                    return env.getSchema().getObjectType("WizardType");
                } else if (javaObject instanceof Witch) {
                    return env.getSchema().getObjectType("WitchType");
                } else {
                    return env.getSchema().getObjectType("NecromancerType");
                }
            }
        };



Creating a schema using the SDL
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When defining a schema via SDL, you provide the needed ``DataFetcher`` s and ``TypeResolver`` s
when the executable schema is created.

Take for example the following static schema definition file called ``starWarsSchema.graphqls``:

.. code-block:: graphql

    schema {
        query: QueryType
    }

    type QueryType {
        hero(episode: Episode): Character
        human(id : String) : Human
        droid(id: ID!): Droid
    }


    enum Episode {
        NEWHOPE
        EMPIRE
        JEDI
    }

    interface Character {
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
    }

    type Droid implements Character {
        id: ID!
        name: String!
        friends: [Character]
        appearsIn: [Episode]!
        primaryFunction: String
    }


The static schema definition file ``starWarsSchema.graphqls`` contains the field and type definitions, but you need a
runtime wiring to make it a truly executable schema.

The runtime wiring contains ``DataFetcher`` s, ``TypeResolvers`` s and custom ``Scalar`` s that are needed to make a fully
executable schema.

You wire this together using this builder pattern:

.. code-block:: java

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
                .type("Droid", typeWiring -> typeWiring
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


Finally, you can generate an executable schema by combining the static schema and the wiring together as shown in this
example:

.. code-block:: java

        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        File schemaFile = loadSchema("starWarsSchema.graphqls");

        TypeDefinitionRegistry typeRegistry = schemaParser.parse(schemaFile);
        RuntimeWiring wiring = buildRuntimeWiring();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);


In addition to the builder style shown above, ``TypeResolver`` s and ``DataFetcher`` s can also be wired in using the
``WiringFactory`` interface.  This allows for a more dynamic runtime wiring since the SDL definitions can be examined in
order to decide what to wire in.  You could for example look at SDL directives, or some other aspect of the SDL
definition to help you decide what runtime to create.

.. code-block:: java

    RuntimeWiring buildDynamicRuntimeWiring() {
        WiringFactory dynamicWiringFactory = new WiringFactory() {
            @Override
            public boolean providesTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition definition) {
                return getDirective(definition,"specialMarker") != null;
            }

            @Override
            public boolean providesTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition definition) {
                return getDirective(definition,"specialMarker") != null;
            }

            @Override
            public TypeResolver getTypeResolver(TypeDefinitionRegistry registry, InterfaceTypeDefinition definition) {
                Directive directive  = getDirective(definition,"specialMarker");
                return createTypeResolver(definition,directive);
            }

            @Override
            public TypeResolver getTypeResolver(TypeDefinitionRegistry registry, UnionTypeDefinition definition) {
                Directive directive  = getDirective(definition,"specialMarker");
                return createTypeResolver(definition,directive);
            }

            @Override
            public boolean providesDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
                return getDirective(definition,"dataFetcher") != null;
            }

            @Override
            public DataFetcher getDataFetcher(TypeDefinitionRegistry registry, FieldDefinition definition) {
                Directive directive = getDirective(definition, "dataFetcher");
                return createDataFetcher(definition,directive);
            }
        };
        return RuntimeWiring.newRuntimeWiring()
                .wiringFactory(dynamicWiringFactory).build();
    }

Creating a schema programmatically
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When the schema is created programmatically ``DataFetcher`` s and ``TypeResolver`` s are provided at type creation:

Example:

.. code-block:: java

    DataFetcher<Foo> fooDataFetcher = environment -> {
            // environment.getSource() is the value of the surrounding
            // object. In this case described by objectType
            Foo value = perhapsFromDatabase(); // Perhaps getting from a DB or whatever
            return value;
    }

    GraphQLObjectType objectType = newObject()
            .name("ObjectType")
            .field(newFieldDefinition()
                    .name("foo")
                    .type(GraphQLString)
                    .dataFetcher(fooDataFetcher))
            .build();



Types
-----

The GraphQL type system supports the following kind of types:

* Scalar
* Object
* Interface
* Union
* InputObject
* Enum



Scalar
^^^^^^

``graphql-java`` supports the following Scalars:


* ``GraphQLString``
* ``GraphQLBoolean``
* ``GraphQLInt``
* ``GraphQLFloat``
* ``GraphQLID``
* ``GraphQLLong``
* ``GraphQLShort``
* ``GraphQLByte``
* ``GraphQLFloat``
* ``GraphQLBigDecimal``
* ``GraphQLBigInteger``



Object
^^^^^^

SDL Example:

.. code-block:: graphql

    type SimpsonCharacter {
        name: String
        mainCharacter: Boolean
    }


Java Example:

.. code-block:: java

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

Interface
^^^^^^^^^

Interfaces are abstract definitions of types.

SDL Example:

.. code-block:: graphql

    interface ComicCharacter {
        name: String;
    }

Java Example:

.. code-block:: java

    GraphQLInterfaceType comicCharacter = newInterface()
        .name("ComicCharacter")
        .description("An abstract comic character.")
        .field(newFieldDefinition()
                .name("name")
                .description("The name of the character.")
                .type(GraphQLString))
        .build();

Union
^^^^^

SDL Example:

.. code-block:: graphql

    type Cat {
        name: String;
        lives: Int;
    }

    type Dog {
        name: String;
        bonesOwned: int;
    }

    union Pet = Cat | Dog


Java Example:

.. code-block:: java

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


Enum
^^^^

SDL Example:

.. code-block:: graphql

    enum Color {
        RED
        GREEN
        BLUE
    }


Java Example:

.. code-block:: java

    GraphQLEnumType colorEnum = newEnum()
        .name("Color")
        .description("Supported colors.")
        .value("RED")
        .value("GREEN")
        .value("BLUE")
        .build();


ObjectInputType
^^^^^^^^^^^^^^^

SDL Example:

.. code-block:: graphql

    input Character {
        name: String
    }


Java Example:

.. code-block:: java

    GraphQLInputObjectType inputObjectType = newInputObject()
        .name("inputObjectType")
        .field(newInputObjectField()
                .name("field")
                .type(GraphQLString))
        .build();


Type References (recursive types)
---------------------------------

GraphQL supports recursive types: For example a ``Person`` can contain a list of friends of the same type.

To be able to declare such a type, ``graphql-java`` has a ``GraphQLTypeReference`` class.

When the schema is created, the ``GraphQLTypeReference`` is replaced with the actual real type Object.

For example:

.. code-block:: java

    GraphQLObjectType person = newObject()
            .name("Person")
            .field(newFieldDefinition()
                    .name("friends")
                    .type(GraphQLList.list(GraphQLTypeReference.typeRef("Person"))))
            .build();

When the schema is declared via SDL, no special handling of recursive types is needed.

Modularising the Schema SDL
---------------------------

Having one large schema file is not always viable.  You can modularise you schema using two techniques.

The first technique is to merge multiple Schema SDL files into one logic unit.  In the case below the schema has
been split into multiple files and merged all together just before schema generation.

.. code-block:: java

    SchemaParser schemaParser = new SchemaParser();
    SchemaGenerator schemaGenerator = new SchemaGenerator();

    File schemaFile1 = loadSchema("starWarsSchemaPart1.graphqls");
    File schemaFile2 = loadSchema("starWarsSchemaPart2.graphqls");
    File schemaFile3 = loadSchema("starWarsSchemaPart3.graphqls");

    TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();

    // each registry is merged into the main registry
    typeRegistry.merge(schemaParser.parse(schemaFile1));
    typeRegistry.merge(schemaParser.parse(schemaFile2));
    typeRegistry.merge(schemaParser.parse(schemaFile3));

    GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, buildRuntimeWiring());

The Graphql SDL type system has another construct for modularising a schema.  You can use `type extensions` to add
extra fields and interfaces to a type.

Imagine you start with a type like this in one schema file.

.. code-block:: graphql

    type Human {
        id: ID!
        name: String!
    }

Another part of your system can extend this type to add more shape to it.

.. code-block:: graphql

    extend type Human implements Character {
        id: ID!
        name: String!
        friends: [Character]
        appearsIn: [Episode]!
    }

You can have as many extensions as you think sensible.  They will be combined in the order
in which they are encountered.  Duplicate fields will be merged as one (however field re-definitions
into new types are not allowed).

.. code-block:: graphql

    extend type Human {
        homePlanet: String
    }


With all these type extensions in place the `Human` type now looks like this at runtime.

.. code-block:: graphql

    type Human implements Character {
        id: ID!
        name: String!
        friends: [Character]
        appearsIn: [Episode]!
        homePlanet: String
    }

This is especially useful at the top level.  You can use extension types to add new fields to the
top level schema "query".  Teams could contribute "sections" on what is being offered as the total
graphql query.


.. code-block:: graphql

    schema {
      query: CombinedQueryFromMultipleTeams
    }

    type CombinedQueryFromMultipleTeams {
        createdTimestamp: String
    }

    # maybe the invoicing system team puts in this set of attributes
    extend type CombinedQueryFromMultipleTeams {
        invoicing: Invoicing
    }

    # and the billing system team puts in this set of attributes
    extend type CombinedQueryFromMultipleTeams {
        billing: Billing
    }

    # and so and so forth
    extend type CombinedQueryFromMultipleTeams {
        auditing: Auditing
    }



Subscription Support
--------------------

Subscriptions are not officially specified yet: ``graphql-java`` supports currently a very basic implementation where you can define a subscription in the schema
with ``GraphQLSchema.Builder.subscription(...)``. This enables you to handle a subscription request:

.. code-block:: graphql

    subscription foo {
        # normal graphql query
    }

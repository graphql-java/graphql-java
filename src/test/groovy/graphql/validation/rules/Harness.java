package graphql.validation.rules;

import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLSchema.newSchema;
import static graphql.schema.GraphQLTypeReference.typeRef;
import static graphql.schema.GraphQLUnionType.newUnionType;


public class Harness {

    private static TypeResolver dummyTypeResolve = env -> null;


    public static GraphQLInterfaceType Being = newInterface()
            .name("Being")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .typeResolver(dummyTypeResolve)
            .build();

    public static GraphQLInterfaceType Pet = newInterface()
            .name("Pet")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .typeResolver(dummyTypeResolve)
            .build();

    public static GraphQLEnumType DogCommand = newEnum()
            .name("DogCommand")
            .value("SIT")
            .value("HEEL")
            .value("DOWN")
            .build();

    public static GraphQLObjectType Dog = newObject()
            .name("Dog")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("nickName")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("barkVolume")
                    .type(GraphQLInt))
            .field(newFieldDefinition()
                    .name("barks")
                    .type(GraphQLBoolean))
            .field(newFieldDefinition()
                    .name("doesKnowCommand")
                    .type(GraphQLBoolean)
                    .argument(newArgument()
                            .name("dogCommand")
                            .type(DogCommand)))
            .field(newFieldDefinition()
                    .name("isHousetrained")
                    .type(GraphQLBoolean)
                    .argument(newArgument()
                            .name("atOtherHomes")
                            .type(GraphQLBoolean)
                            .defaultValue(true)))
            .field(newFieldDefinition()
                    .name("isAtLocation")
                    .type(GraphQLBoolean)
                    .argument(newArgument()
                            .name("x")
                            .type(GraphQLInt))
                    .argument(newArgument()
                            .name("y")
                            .type(GraphQLInt)))
            .withInterface(Being)
            .withInterface(Pet)
            .build();

    public static GraphQLEnumType FurColor = newEnum()
            .name("FurColor")
            .value("BROWN")
            .value("BLACK")
            .value("TAN")
            .value("SPOTTED")
            .build();


    public static GraphQLObjectType Cat = newObject()
            .name("Cat")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("nickName")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("meows")
                    .type(GraphQLBoolean))
            .field(newFieldDefinition()
                    .name("meowVolume")
                    .type(GraphQLInt))
            .field(newFieldDefinition()
                    .name("furColor")
                    .type(FurColor))
            .withInterfaces(Being, Pet)
            .build();

    public static GraphQLUnionType CatOrDog = newUnionType()
            .name("CatOrDog")
            .possibleTypes(Dog, Cat)
            .typeResolver(env -> null)
            .build();

    public static GraphQLInterfaceType Intelligent = newInterface()
            .name("Intelligent")
            .field(newFieldDefinition()
                    .name("iq")
                    .type(GraphQLInt))
            .typeResolver(dummyTypeResolve)
            .build();

    public static GraphQLObjectType Human = newObject()
            .name("Human")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("pets")
                    .type(list(Pet)))
            .field(newFieldDefinition()
                    .name("relatives")
                    .type(list(typeRef("Human"))))
            .field(newFieldDefinition()
                    .name("iq")
                    .type(GraphQLInt))
            .withInterfaces(Being, Intelligent)
            .build();

    public static GraphQLObjectType Alien = newObject()
            .name("Alien")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("numEyes")
                    .type(GraphQLInt))
            .field(newFieldDefinition()
                    .name("iq")
                    .type(GraphQLInt))
            .withInterfaces(Being, Intelligent)
            .build();

    public static GraphQLUnionType DogOrHuman = newUnionType()
            .name("DogOrHuman")
            .possibleTypes(Dog, Human)
            .typeResolver(dummyTypeResolve)
            .build();

    public static GraphQLUnionType HumanOrAlien = newUnionType()
            .name("HumanOrAlien")
            .possibleTypes(Alien, Human)
            .typeResolver(dummyTypeResolve)
            .build();

    public static GraphQLObjectType QueryRoot = newObject()
            .name("QueryRoot")
            .field(newFieldDefinition()
                    .name("alien")
                    .type(Alien))
            .field(newFieldDefinition()
                    .name("dog")
                    .type(Dog))
            .field(newFieldDefinition()
                    .name("cat")
                    .type(Cat))
            .field(newFieldDefinition()
                    .name("pet")
                    .type(Pet))
            .field(newFieldDefinition()
                    .name("catOrDog")
                    .type(CatOrDog))

            .field(newFieldDefinition()
                    .name("dogOrHuman")
                    .type(DogOrHuman))
            .field(newFieldDefinition()
                    .name("humanOrAlien")
                    .type(HumanOrAlien))
            .build();

    public static GraphQLSchema Schema = newSchema()
            .query(QueryRoot)
            .build();


}


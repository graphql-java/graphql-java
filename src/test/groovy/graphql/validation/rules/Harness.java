package graphql.validation.rules;

import graphql.schema.*;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLSchema.newSchema;
import static graphql.schema.GraphQLUnionType.newUnionType;


public class Harness {

    private static TypeResolver dummyTypeResolve = new TypeResolver() {
        @Override
        public GraphQLObjectType getType(Object object) {
            return null;
        }
    };


    public static GraphQLInterfaceType Being = newInterface()
            .name("Being")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString)
                    .build())
            .typeResolver(dummyTypeResolve)
            .build();

    public static GraphQLInterfaceType Pet = newInterface()
            .name("Pet")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString)
                    .build())
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
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("nickName")
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("barkVolume")
                    .type(GraphQLInt)
                    .build())
            .field(newFieldDefinition()
                    .name("barks")
                    .type(GraphQLBoolean)
                    .build())
            .field(newFieldDefinition()
                    .name("doesKnowCommand")
                    .type(GraphQLBoolean)
                    .argument(newArgument()
                            .name("dogCommand")
                            .type(DogCommand)
                            .build())
                    .build())
            .field(newFieldDefinition()
                    .name("isHousetrained")
                    .type(GraphQLBoolean)
                    .argument(newArgument()
                            .name("atOtherHomes")
                            .type(GraphQLBoolean)
                            .defaultValue(true)
                            .build())
                    .build())
            .field(newFieldDefinition()
                    .name("isAtLocation")
                    .type(GraphQLBoolean)
                    .argument(newArgument()
                            .name("x")
                            .type(GraphQLInt)
                            .build())
                    .argument(newArgument()
                            .name("y")
                            .type(GraphQLInt)
                            .build())
                    .build())
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
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("nickName")
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("meows")
                    .type(GraphQLBoolean)
                    .build())
            .field(newFieldDefinition()
                    .name("meowVolume")
                    .type(GraphQLInt)
                    .build())
            .field(newFieldDefinition()
                    .name("furColor")
                    .type(FurColor)
                    .build())
            .withInterfaces(Being, Pet)
            .build();

    public static GraphQLUnionType CatOrDog = newUnionType()
            .name("CatOrDog")
            .possibleTypes(Dog, Cat)
            .typeResolver(new TypeResolver() {
                @Override
                public GraphQLObjectType getType(Object object) {
                    return null;
                }
            })
            .build();

    public static GraphQLInterfaceType Intelligent = newInterface()
            .name("Intelligent")
            .field(newFieldDefinition()
                    .name("iq")
                    .type(GraphQLInt)
                    .build())
            .typeResolver(dummyTypeResolve)
            .build();

    public static GraphQLObjectType Human = newObject()
            .name("Human")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString)
                    .argument(newArgument()
                            .name("surname")
                            .type(GraphQLBoolean)
                            .build())
                    .build())
            .field(newFieldDefinition()
                    .name("pets")
                    .type(new GraphQLList(Pet))
                    .build())
            .field(newFieldDefinition()
                    .name("relatives")
                    .type(new GraphQLList(new GraphQLTypeReference("Human")))
                    .build())
            .field(newFieldDefinition()
                    .name("iq")
                    .type(GraphQLInt)
                    .build())
            .withInterfaces(Being, Intelligent)
            .build();

    public static GraphQLObjectType Alien = newObject()
            .name("Alien")
            .field(newFieldDefinition()
                    .name("numEyes")
                    .type(GraphQLInt)
                    .build())
            .field(newFieldDefinition()
                    .name("iq")
                    .type(GraphQLInt)
                    .build())
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
//    public static GraphQLInputObjectType ComplexInput = newInputObject()
//            .field(newInputObjectField()
//                    .name("requiredField")
//                    .type(new GraphQLNonNull(GraphQLBoolean))
//                    .build())
//            .field(newInputObjectField()
//                    .name("intField")
//                    .type(GraphQLInt)
//                    .build())
//            .field(newInputObjectField()
//                    .name("stringField")
//                    .type(GraphQLString)
//                    .build())
//            .field(newInputObjectField()
//                    .name("booleanField")
//                    .type(GraphQLBoolean)
//                    .build())
//            .field(newInputObjectField()
//                    .name("stringListField")
//                    .type(new GraphQLList(GraphQLString))
//                    .build())
//            .build();


    public static GraphQLObjectType QueryRoot = newObject()
            .name("QueryRoot")
            .field(newFieldDefinition()
                    .name("alien")
                    .type(Alien)
                    .build())
            .field(newFieldDefinition()
                    .name("dog")
                    .type(Dog)
                    .build())
            .field(newFieldDefinition()
                    .name("cat")
                    .type(Cat)
                    .build())
            .field(newFieldDefinition()
                    .name("pet")
                    .type(Pet)
                    .build())
            .field(newFieldDefinition()
                    .name("catOrDog")
                    .type(CatOrDog)
                    .build())

            .field(newFieldDefinition()
                    .name("dogOrHuman")
                    .type(DogOrHuman)
                    .build())
            .field(newFieldDefinition()
                    .name("humanOrAlien")
                    .type(HumanOrAlien)
                    .build())
            .build();

    public static GraphQLSchema Schema = newSchema()
            .query(QueryRoot)
            .build();


}


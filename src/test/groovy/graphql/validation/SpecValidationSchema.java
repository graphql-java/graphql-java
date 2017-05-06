package graphql.validation;

import graphql.Scalars;
import graphql.TypeResolutionEnvironment;
import graphql.schema.FieldDataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;
import graphql.validation.SpecValidationSchemaPojos.Alien;
import graphql.validation.SpecValidationSchemaPojos.Cat;
import graphql.validation.SpecValidationSchemaPojos.Dog;
import graphql.validation.SpecValidationSchemaPojos.Human;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Sample schema used in the spec for validation examples
 * http://facebook.github.io/graphql/#sec-Validation
 * @author dwinsor
 *
 */
public class SpecValidationSchema {
    public static final GraphQLEnumType dogCommand = GraphQLEnumType.newEnum()
            .name("DogCommand")
            .value("SIT")
            .value("DOWN")
            .value("HEEL")
            .build();

    public static final GraphQLEnumType catCommand = GraphQLEnumType.newEnum()
            .name("CatCommand")
            .value("JUMP")
            .build();

    public static final GraphQLInterfaceType sentient = GraphQLInterfaceType.newInterface()
            .name("Sentient")
            .field(new GraphQLFieldDefinition(
                    "name", null, new GraphQLNonNull(Scalars.GraphQLString), new FieldDataFetcher("name"), Collections.emptyList(), null))
            .typeResolver(new TypeResolver() {
                @Override
                public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    if (env.getObject() instanceof Human) return human;
                    if (env.getObject() instanceof Alien) return alien;
                    return null;
                }})
            .build();

    public static final GraphQLInterfaceType pet = GraphQLInterfaceType.newInterface()
            .name("Pet")
            .field(new GraphQLFieldDefinition(
                    "name", null, new GraphQLNonNull(Scalars.GraphQLString), new FieldDataFetcher("name"), Collections.emptyList(), null))
            .typeResolver(new TypeResolver() {
                @Override
                public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    if (env.getObject() instanceof Dog) return dog;
                    if (env.getObject() instanceof Cat) return cat;
                    return null;
                }})
            .build();

    public static final GraphQLObjectType human = GraphQLObjectType.newObject()
            .name("Human")
            .field(new GraphQLFieldDefinition(
                    "name", null, new GraphQLNonNull(Scalars.GraphQLString), new FieldDataFetcher("name"), Collections.emptyList(), null))
            .withInterface(SpecValidationSchema.sentient)
            .build();

    public static final GraphQLObjectType alien = GraphQLObjectType.newObject()
            .name("Alien")
            .field(new GraphQLFieldDefinition(
                    "name", null, new GraphQLNonNull(Scalars.GraphQLString), new FieldDataFetcher("name"), Collections.emptyList(), null))
            .field(new GraphQLFieldDefinition(
                    "homePlanet", null, Scalars.GraphQLString, new FieldDataFetcher("homePlanet"), Collections.emptyList(), null))
            .withInterface(SpecValidationSchema.sentient)
            .build();

    public static final GraphQLArgument dogCommandArg = GraphQLArgument.newArgument()
            .name("dogCommand")
            .type(new GraphQLNonNull(dogCommand))
            .build();

    public static final GraphQLArgument atOtherHomesArg = GraphQLArgument.newArgument()
            .name("atOtherHomes")
            .type(Scalars.GraphQLBoolean)
            .build();

    public static final GraphQLArgument catCommandArg = GraphQLArgument.newArgument()
            .name("catCommand")
            .type(new GraphQLNonNull(catCommand))
            .build();

    public static final GraphQLObjectType dog = GraphQLObjectType.newObject()
            .name("Dog")
            .field(new GraphQLFieldDefinition(
                    "name", null, new GraphQLNonNull(Scalars.GraphQLString), new FieldDataFetcher("name"), Collections.emptyList(), null))
            .field(new GraphQLFieldDefinition(
                    "nickname", null, Scalars.GraphQLString, new FieldDataFetcher("nickname"), Collections.emptyList(), null))
            .field(new GraphQLFieldDefinition(
                    "barkVolume", null, Scalars.GraphQLInt, new FieldDataFetcher("barkVolume"), Collections.emptyList(), null))
            .field(new GraphQLFieldDefinition(
                    "doesKnowCommand", null, new GraphQLNonNull(Scalars.GraphQLBoolean), new FieldDataFetcher("doesKnowCommand"),
                    Arrays.asList(dogCommandArg), null))
            .field(new GraphQLFieldDefinition(
                    "isHousetrained", null, Scalars.GraphQLBoolean, new FieldDataFetcher("isHousetrained"),
                    Arrays.asList(atOtherHomesArg), null))
            .field(new GraphQLFieldDefinition(
                    "owner", null, human, new FieldDataFetcher("owner"), Collections.emptyList(), null))
            .withInterface(SpecValidationSchema.pet)
            .build();

    public static final GraphQLObjectType cat = GraphQLObjectType.newObject()
            .name("Cat")
            .field(new GraphQLFieldDefinition(
                    "name", null, new GraphQLNonNull(Scalars.GraphQLString), new FieldDataFetcher("name"), Collections.emptyList(), null))
            .field(new GraphQLFieldDefinition(
                    "nickname", null, Scalars.GraphQLString, new FieldDataFetcher("nickname"), Collections.emptyList(), null))
            .field(new GraphQLFieldDefinition(
                    "meowVolume", null, Scalars.GraphQLInt, new FieldDataFetcher("meowVolume"), Collections.emptyList(), null))
            .field(new GraphQLFieldDefinition(
                    "doesKnowCommand", null, new GraphQLNonNull(Scalars.GraphQLBoolean), new FieldDataFetcher("doesKnowCommand"),
                    Arrays.asList(catCommandArg), null))
            .withInterface(SpecValidationSchema.pet)
            .build();

    public static final GraphQLUnionType catOrDog = GraphQLUnionType.newUnionType()
            .name("CatOrDog")
            .possibleTypes(cat, dog)
            .typeResolver(env -> {
                if (env.getObject() instanceof Cat) return cat;
                if (env.getObject() instanceof Dog) return dog;
                return null;
            })
            .build();

    public static final GraphQLUnionType dogOrHuman = GraphQLUnionType.newUnionType()
            .name("DogOrHuman")
            .possibleTypes(dog, human)
            .typeResolver(env -> {
                if (env.getObject() instanceof Human) return human;
                if (env.getObject() instanceof Dog) return dog;
                return null;
            })
            .build();

    public static final GraphQLUnionType humanOrAlien = GraphQLUnionType.newUnionType()
            .name("HumanOrAlien")
            .possibleTypes(human, alien)
            .typeResolver(env -> {
                if (env.getObject() instanceof Human) return human;
                if (env.getObject() instanceof Alien) return alien;
                return null;
            })
            .build();

    public static final GraphQLObjectType queryRoot = GraphQLObjectType.newObject()
            .name("QueryRoot")
            .field(new GraphQLFieldDefinition(
                    "dog", null, dog, new FieldDataFetcher("dog"), Collections.emptyList(), null))
            .build();

    @SuppressWarnings("serial")
    public static final Set<GraphQLType> specValidationDictionary = new HashSet<GraphQLType>() {{
        add(dogCommand);
        add(catCommand);
        add(sentient);
        add(pet);
        add(human);
        add(alien);
        add(dog);
        add(cat);
        add(catOrDog);
        add(dogOrHuman);
        add(humanOrAlien);
    }};
    public static final GraphQLSchema specValidationSchema = GraphQLSchema.newSchema()
            .query(queryRoot)
            .build(specValidationDictionary);


}

package graphql.validation;

import graphql.Scalars;
import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;
import graphql.validation.SpecValidationSchemaPojos.Alien;
import graphql.validation.SpecValidationSchemaPojos.Cat;
import graphql.validation.SpecValidationSchemaPojos.Dog;
import graphql.validation.SpecValidationSchemaPojos.Human;

import java.util.HashSet;
import java.util.Set;

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;
import static java.util.Collections.singletonList;

/**
 * Sample schema used in the spec for validation examples
 * http://facebook.github.io/graphql/#sec-Validation
 *
 * @author dwinsor
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
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .typeResolver(new TypeResolver() {
                @Override
                public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    if (env.getObject() instanceof Human) return human;
                    if (env.getObject() instanceof Alien) return alien;
                    return null;
                }
            })
            .build();

    public static final GraphQLInterfaceType pet = GraphQLInterfaceType.newInterface()
            .name("Pet")
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .typeResolver(new TypeResolver() {
                @Override
                public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    if (env.getObject() instanceof Dog) return dog;
                    if (env.getObject() instanceof Cat) return cat;
                    return null;
                }
            })
            .build();

    public static final GraphQLObjectType human = GraphQLObjectType.newObject()
            .name("Human")
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .withInterface(SpecValidationSchema.sentient)
            .build();

    public static final GraphQLObjectType alien = GraphQLObjectType.newObject()
            .name("Alien")
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .field(newFieldDefinition().name("homePlanet").type(Scalars.GraphQLString))
            .withInterface(SpecValidationSchema.sentient)
            .build();

    public static final GraphQLArgument dogCommandArg = GraphQLArgument.newArgument()
            .name("dogCommand")
            .type(nonNull(dogCommand))
            .build();

    public static final GraphQLArgument atOtherHomesArg = GraphQLArgument.newArgument()
            .name("atOtherHomes")
            .type(Scalars.GraphQLBoolean)
            .build();

    public static final GraphQLArgument catCommandArg = GraphQLArgument.newArgument()
            .name("catCommand")
            .type(nonNull(catCommand))
            .build();

    public static final GraphQLObjectType dog = GraphQLObjectType.newObject()
            .name("Dog")
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .field(newFieldDefinition().name("nickname").type(Scalars.GraphQLString))
            .field(newFieldDefinition().name("barkVolume").type(Scalars.GraphQLInt))
            .field(newFieldDefinition().name("doesKnowCommand").type(nonNull(Scalars.GraphQLBoolean))
                    .argument(singletonList(dogCommandArg)))
            .field(newFieldDefinition().name("isHousetrained").type(Scalars.GraphQLBoolean)
                    .argument(singletonList(atOtherHomesArg)))
            .field(newFieldDefinition().name("owner").type(human))
            .withInterface(SpecValidationSchema.pet)
            .build();

    public static final GraphQLObjectType cat = GraphQLObjectType.newObject()
            .name("Cat")
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .field(newFieldDefinition().name("nickname").type(Scalars.GraphQLString))
            .field(newFieldDefinition().name("meowVolume").type(Scalars.GraphQLInt))
            .field(newFieldDefinition().name("doesKnowCommand").type(nonNull(Scalars.GraphQLBoolean))
                    .argument(singletonList(catCommandArg)))
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
            .field(newFieldDefinition().name("dog").type(dog))
            .field(newFieldDefinition().name("pet").type(pet))
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

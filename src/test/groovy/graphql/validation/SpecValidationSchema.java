package graphql.validation;

import graphql.Directives;
import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
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

import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_SPREAD;
import static graphql.introspection.Introspection.DirectiveLocation.INLINE_FRAGMENT;
import static graphql.introspection.Introspection.DirectiveLocation.QUERY;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;
import static java.util.Collections.singletonList;

/**
 * Sample schema used in the spec for validation examples
 * https://spec.graphql.org/October2021/#sec-Validation
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

    public static final TypeResolver sentientTypeResolver = env -> {
        if (env.getObject() instanceof Human) {
            return human;
        }
        if (env.getObject() instanceof Alien) {
            return alien;
        }
        return null;
    };

    public static final GraphQLArgument catCommandArg = GraphQLArgument.newArgument()
            .name("catCommand")
            .type(nonNull(catCommand))
            .build();

    public static final GraphQLArgument dogCommandArg = GraphQLArgument.newArgument()
            .name("dogCommand")
            .type(nonNull(dogCommand))
            .build();

    public static final GraphQLArgument atOtherHomesArg = GraphQLArgument.newArgument()
            .name("atOtherHomes")
            .type(Scalars.GraphQLBoolean)
            .build();

    public static final GraphQLInterfaceType pet = GraphQLInterfaceType.newInterface()
            .name("Pet")
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .build();

    public static final GraphQLObjectType dog = GraphQLObjectType.newObject()
            .name("Dog")
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .field(newFieldDefinition().name("nickname").type(Scalars.GraphQLString))
            .field(newFieldDefinition().name("barkVolume").type(Scalars.GraphQLInt))
            .field(newFieldDefinition().name("doesKnowCommand").type(nonNull(Scalars.GraphQLBoolean))
                    .arguments(singletonList(dogCommandArg)))
            .field(newFieldDefinition().name("isHousetrained").type(Scalars.GraphQLBoolean)
                    .arguments(singletonList(atOtherHomesArg)))
            .field(newFieldDefinition().name("owner").type(human))
            .withInterface(SpecValidationSchema.pet)
            .build();

    public static final GraphQLObjectType cat = GraphQLObjectType.newObject()
            .name("Cat")
            .field(newFieldDefinition().name("name").type(nonNull(Scalars.GraphQLString)))
            .field(newFieldDefinition().name("nickname").type(Scalars.GraphQLString))
            .field(newFieldDefinition().name("meowVolume").type(Scalars.GraphQLInt))
            .field(newFieldDefinition().name("doesKnowCommand").type(nonNull(Scalars.GraphQLBoolean))
                    .arguments(singletonList(catCommandArg)))
            .withInterface(SpecValidationSchema.pet)
            .build();

    public static final TypeResolver petTypeResolver = env -> {
        if (env.getObject() instanceof Dog) {
            return dog;
        }
        if (env.getObject() instanceof Cat) {
            return cat;
        }
        return null;
    };

    public static final GraphQLUnionType catOrDog = GraphQLUnionType.newUnionType()
            .name("CatOrDog")
            .possibleTypes(cat, dog)
            .build();

    public static final TypeResolver catOrDogTypeResolver = env -> {
        if (env.getObject() instanceof Cat) {
            return cat;
        }
        if (env.getObject() instanceof Dog) {
            return dog;
        }
        return null;
    };

    public static final GraphQLUnionType dogOrHuman = GraphQLUnionType.newUnionType()
            .name("DogOrHuman")
            .possibleTypes(dog, human)
            .build();

    public static final TypeResolver dogOrHumanTypeResolver = env -> {
        if (env.getObject() instanceof Human) {
            return human;
        }
        if (env.getObject() instanceof Dog) {
            return dog;
        }
        return null;
    };

    public static final GraphQLUnionType humanOrAlien = GraphQLUnionType.newUnionType()
            .name("HumanOrAlien")
            .possibleTypes(human, alien)
            .build();

    public static final TypeResolver humanOrAlienTypeResolver = env -> {
        if (env.getObject() instanceof Human) {
            return human;
        }
        if (env.getObject() instanceof Alien) {
            return alien;
        }
        return null;
    };

    public static final GraphQLDirective dogDirective = GraphQLDirective.newDirective()
            .name("dogDirective")
            .argument(newArgument().name("arg1").type(GraphQLString).build())
            .validLocations(FIELD, FRAGMENT_SPREAD, FRAGMENT_DEFINITION, INLINE_FRAGMENT, QUERY)
            .build();

    public static final GraphQLInputObjectType oneOfInputType = GraphQLInputObjectType.newInputObject()
            .name("oneOfInputType")
            .withAppliedDirective(Directives.OneOfDirective.toAppliedDirective())
            .field(GraphQLInputObjectField.newInputObjectField()
                    .name("a")
                    .type(GraphQLString))
            .field(GraphQLInputObjectField.newInputObjectField()
                    .name("b")
                    .type(GraphQLString))
            .build();


    public static final GraphQLObjectType queryRoot = GraphQLObjectType.newObject()
            .name("QueryRoot")
            .field(newFieldDefinition().name("dog").type(dog)
                    .argument(newArgument().name("arg1").type(GraphQLString).build())
                    .withDirective(dogDirective)
            )
            .field(newFieldDefinition().name("pet").type(pet))
            .field(newFieldDefinition().name("oneOfField").type(GraphQLString)
                    .argument(newArgument().name("oneOfArg").type(oneOfInputType).build())
            )
            .build();

    public static final GraphQLObjectType subscriptionRoot = GraphQLObjectType.newObject()
            .name("SubscriptionRoot")
            .field(newFieldDefinition().name("dog").type(dog))
            .field(newFieldDefinition().name("cat").type(cat))
            .build();

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

    public static final GraphQLDirective upperDirective = GraphQLDirective.newDirective()
            .name("upper")
            .validLocations(FIELD, FRAGMENT_SPREAD, FRAGMENT_DEFINITION, INLINE_FRAGMENT, QUERY)
            .build();

    public static final GraphQLDirective lowerDirective = GraphQLDirective.newDirective()
            .name("lower")
            .validLocations(FIELD, FRAGMENT_SPREAD, FRAGMENT_DEFINITION, INLINE_FRAGMENT, QUERY)
            .build();

    public static final GraphQLDirective nonNullDirective = GraphQLDirective.newDirective()
            .name("nonNullDirective")
            .argument(newArgument().name("arg1").type(nonNull(GraphQLString)).build())
            .validLocations(FIELD, FRAGMENT_SPREAD, FRAGMENT_DEFINITION, INLINE_FRAGMENT, QUERY)
            .build();

    public static final GraphQLInputObjectType inputType = GraphQLInputObjectType.newInputObject()
            .name("Input")
            .field(GraphQLInputObjectField.newInputObjectField()
                    .name("id")
                    .type(GraphQLString)
                    .build())
            .field(GraphQLInputObjectField.newInputObjectField()
                    .name("name")
                    .type(nonNull(GraphQLString))
                    .build())
            .build();

    public static final GraphQLDirective objectArgumentDirective = GraphQLDirective.newDirective()
            .name("objectArgumentDirective")
            .argument(newArgument().name("myObject").type(nonNull(inputType)).build())
            .validLocations(FIELD, FRAGMENT_SPREAD, FRAGMENT_DEFINITION, INLINE_FRAGMENT, QUERY)
            .build();

    public static final GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
            .typeResolver("Sentient", sentientTypeResolver)
            .typeResolver("Pet", petTypeResolver)
            .typeResolver("CatOrDog", catOrDogTypeResolver)
            .typeResolver("DogOrHuman", dogOrHumanTypeResolver)
            .typeResolver("HumanOrAlien", humanOrAlienTypeResolver)
            .build();

    public static final GraphQLSchema specValidationSchema = GraphQLSchema.newSchema()
            .query(queryRoot)
            .codeRegistry(codeRegistry)
            .subscription(subscriptionRoot)
            .additionalDirective(upperDirective)
            .additionalDirective(lowerDirective)
            .additionalDirective(dogDirective)
            .additionalDirective(nonNullDirective)
            .additionalDirective(objectArgumentDirective)
            .additionalTypes(specValidationDictionary)
            .build();

}

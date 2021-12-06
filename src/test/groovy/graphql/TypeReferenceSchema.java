package graphql;

import graphql.schema.Coercing;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolverProxy;

import java.util.Arrays;
import java.util.HashSet;

import static graphql.GarfieldSchema.CatType;
import static graphql.GarfieldSchema.DogType;
import static graphql.GarfieldSchema.NamedType;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLDirective.newDirective;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLUnionType.newUnionType;

public class TypeReferenceSchema {


    private static final GraphQLScalarType OnOff;

    private static GraphQLDirective serialisedToDirective;

    static {
        serialisedToDirective = newDirective()
                .name("serializeTo")
                .validLocation(DirectiveLocation.SCALAR)
                .argument(newArgument()
                        .name("type")
                        .type(GraphQLTypeReference.typeRef(GraphQLBoolean.getName())))
                .build();

        OnOff = GraphQLScalarType.newScalar()
                .name("OnOff")
                .coercing(new Coercing<Boolean, Boolean>() {

                    private static final String TEST_ONLY = "For testing only";

                    @Override
                    public Boolean serialize(Object input) {
                        throw new UnsupportedOperationException(TEST_ONLY);
                    }

                    @Override
                    public Boolean parseValue(Object input) {
                        throw new UnsupportedOperationException(TEST_ONLY);
                    }

                    @Override
                    public Boolean parseLiteral(Object input) {
                        throw new UnsupportedOperationException(TEST_ONLY);
                    }
                })
                .withDirective(serialisedToDirective)
                .build();
    }

    public static GraphQLScalarType UnionDirectiveInput = OnOff.transform(builder -> builder.name("Union_Directive_Input"));
    public static GraphQLScalarType InputObjectDirectiveInput = OnOff.transform(builder -> builder.name("Input_Object_Directive_Input"));
    public static GraphQLScalarType ObjectDirectiveInput = OnOff.transform(builder -> builder.name("Object_Directive_Input"));
    public static GraphQLScalarType FieldDefDirectiveInput = OnOff.transform(builder -> builder.name("Field_Def_Directive_Input"));
    public static GraphQLScalarType ArgumentDirectiveInput = OnOff.transform(builder -> builder.name("Argument_Directive_Input"));
    public static GraphQLScalarType InputFieldDefDirectiveInput = OnOff.transform(builder -> builder.name("Input_Field_Def_Directive_Input"));
    public static GraphQLScalarType InterfaceDirectiveInput = OnOff.transform(builder -> builder.name("Interface_Directive_Input"));
    public static GraphQLScalarType EnumDirectiveInput = OnOff.transform(builder -> builder.name("Enum_Directive_Input"));
    public static GraphQLScalarType EnumValueDirectiveInput = OnOff.transform(builder -> builder.name("Enum_Value_Directive_Input"));
    public static GraphQLScalarType QueryDirectiveInput = OnOff.transform(builder -> builder.name("Query_Directive_Input"));

    private static GraphQLEnumType HairStyle;
    private static GraphQLDirective enumDirective;
    private static GraphQLDirective enumValueDirective;


    static {

        enumDirective = newDirective()
                .name("enumDirective")
                .validLocation(DirectiveLocation.ENUM)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(EnumDirectiveInput))
                .build();

        enumValueDirective = newDirective()
                .name("enumValueDirective")
                .validLocation(DirectiveLocation.ENUM_VALUE)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(EnumValueDirectiveInput))
                .build();

        HairStyle = newEnum()
                .name("HairStyle")
                .withDirective(enumDirective)
                .value(newEnumValueDefinition()
                        .name("Short")
                        .value("Short")
                        .withDirective(enumValueDirective)
                        .build())
                .value(newEnumValueDefinition()
                        .name("Long")
                        .value("Long")
                        .build())
                .build();
    }

    private static GraphQLUnionType PetType;
    private static GraphQLDirective unionDirective;

    static {
        unionDirective = newDirective()
                .name("unionDirective")
                .validLocation(DirectiveLocation.UNION)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(UnionDirectiveInput))
                .build();

        PetType = newUnionType()
                .name("Pet")
                .possibleType(GraphQLTypeReference.typeRef(CatType.getName()))
                .possibleType(GraphQLTypeReference.typeRef(DogType.getName()))
                .typeResolver(new TypeResolverProxy())
                .withDirective(unionDirective)
                .build();
    }

    private static GraphQLInterfaceType Addressable;
    private static GraphQLDirective interfaceDirective;

    static {

        interfaceDirective = newDirective()
                .name("interfaceDirective")
                .validLocation(DirectiveLocation.INTERFACE)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(InterfaceDirectiveInput))
                .build();

        Addressable = GraphQLInterfaceType.newInterface()
                .name("Addressable")
                .typeResolver(new TypeResolverProxy())
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("address")
                        .type(GraphQLString))
                .withDirective(interfaceDirective)
                .build();
    }

    private static GraphQLInputObjectType PersonInputType;
    private static GraphQLDirective inputObjectDirective;
    private static GraphQLDirective inputFieldDefDirective;

    static {
        inputObjectDirective = newDirective()
                .name("inputObjectDirective")
                .validLocation(DirectiveLocation.INPUT_OBJECT)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(InputObjectDirectiveInput))
                .build();

        inputFieldDefDirective = newDirective()
                .name("inputFieldDefDirective")
                .validLocation(DirectiveLocation.INPUT_FIELD_DEFINITION)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(InputFieldDefDirectiveInput))
                .build();


        PersonInputType = newInputObject()
                .name("Person_Input")
                .field(newInputObjectField()
                        .name("name")
                        .type(GraphQLString)
                        .withDirective(inputFieldDefDirective))
                .withDirective(inputObjectDirective)
                .build();
    }

    private static GraphQLObjectType PersonType;
    private static GraphQLDirective objectDirective;

    static {
        objectDirective = newDirective()
                .name("objectDirective")
                .validLocation(DirectiveLocation.OBJECT)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(ObjectDirectiveInput))
                .build();

        PersonType = newObject()
                .name("Person")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("address")
                        .type(GraphQLString))
                .field(newFieldDefinition()
                        .name("pet")
                        .type(GraphQLTypeReference.typeRef(PetType.getName())))
                .field(newFieldDefinition()
                        .name("hairStyle")
                        .type(GraphQLTypeReference.typeRef(HairStyle.getName())))
                .withInterface(GraphQLTypeReference.typeRef(NamedType.getName()))
                .withInterface(Addressable)
                .withDirective(objectDirective)
                .build();
    }

    private static GraphQLFieldDefinition exists;
    private static GraphQLDirective fieldDefDirective;
    private static GraphQLDirective argumentDirective;

    static {
        fieldDefDirective = newDirective()
                .name("fieldDefDirective")
                .validLocation(DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(FieldDefDirectiveInput))
                .build();

        argumentDirective = newDirective()
                .name("argumentDirective")
                .validLocation(DirectiveLocation.ARGUMENT_DEFINITION)
                .argument(newArgument()
                        .name("enabled")
                        .type(GraphQLTypeReference.typeRef(OnOff.getName())))
                .argument(newArgument()
                        .name("input")
                        .type(ArgumentDirectiveInput))
                .build();


        exists = newFieldDefinition()
                .name("exists")
                .type(GraphQLBoolean)
                .argument(newArgument()
                        .name("person")
                        .type(GraphQLTypeReference.typeRef("Person_Input"))
                        .withDirective(argumentDirective))
                .withDirective(fieldDefDirective)
                .build();
    }

    private static GraphQLFieldDefinition find = newFieldDefinition()
            .name("find")
            .type(GraphQLTypeReference.typeRef("Person"))
            .argument(newArgument()
                    .name("name")
                    .type(GraphQLString))
            .build();

    private static GraphQLObjectType PersonService = newObject()
            .name("PersonService")
            .field(exists)
            .field(find)
            .build();

    public static GraphQLDirective Cache = newDirective()
            .name("cache")
            .validLocations(DirectiveLocation.QUERY)
            .argument(newArgument()
                    .name("enabled")
                    .type(GraphQLTypeReference.typeRef(OnOff.getName())))
            .argument(GraphQLArgument.newArgument()
                    .name("input")
                    .type(QueryDirectiveInput))
            .build();

    public static GraphQLSchema SchemaWithReferences = GraphQLSchema.newSchema()
            .query(PersonService)
            .additionalTypes(new HashSet<>(Arrays.asList(PersonType, PersonInputType, PetType, CatType, DogType, NamedType, HairStyle, OnOff)))
            .additionalDirective(Cache)
            .additionalDirective(fieldDefDirective)
            .additionalDirective(argumentDirective)
            .additionalDirective(inputObjectDirective)
            .additionalDirective(inputFieldDefDirective)
            .additionalDirective(serialisedToDirective)
            .additionalDirective(objectDirective)
            .additionalDirective(unionDirective)
            .additionalDirective(enumDirective)
            .additionalDirective(enumValueDirective)
            .additionalDirective(interfaceDirective)

            .build();
}

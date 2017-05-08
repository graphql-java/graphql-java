package graphql;

import graphql.schema.*;

import java.util.Arrays;
import java.util.HashSet;

import static graphql.GarfieldSchema.*;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLUnionType.newUnionType;

public class TypeReferenceSchema {

    public static GraphQLUnionType PetType = newUnionType()
            .name("Pet")
            .possibleType(new GraphQLTypeReference(CatType.getName()))
            .possibleType(new GraphQLTypeReference(DogType.getName()))
            .typeResolver(new TypeResolverProxy())
            .build();
    
    public static GraphQLInputObjectType PersonInputType = newInputObject()
            .name("Person_Input")
            .field(newInputObjectField()
                    .name("name")
                    .type(GraphQLString))
            .build();
    
    public static GraphQLObjectType PersonType = newObject()
            .name("Person")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("pet")
                    .type(new GraphQLTypeReference(PetType.getName())))
            .withInterface(new GraphQLTypeReference(NamedType.getName()))
            .build();

    public static GraphQLFieldDefinition exists = newFieldDefinition()
            .name("exists")
            .type(GraphQLBoolean)
            .argument(GraphQLArgument.newArgument()
                    .name("person")
                    .type(new GraphQLTypeReference("Person_Input")))
            .build();
    
    public static GraphQLFieldDefinition find = newFieldDefinition()
            .name("find")
            .type(new GraphQLTypeReference("Person"))
            .argument(GraphQLArgument.newArgument()
                    .name("name")
                    .type(GraphQLString))
            .build();

    public static GraphQLObjectType PersonService = newObject()
            .name("PersonService")
            .field(exists)
            .field(find)
            .build();

    public static GraphQLSchema SchemaWithReferences = new GraphQLSchema(PersonService, null,
            new HashSet<>(Arrays.asList(PersonType, PersonInputType, PetType, CatType, DogType, NamedType)));
}

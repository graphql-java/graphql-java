package graphql;


import graphql.schema.*;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLObjectType.newObject;

public class GarfieldSchema {

    public static GraphQLInterfaceType Named = newInterface()
            .name("Named")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString)
                    .build())
            .build();

    public static GraphQLObjectType DogType = newObject()
            .name("Dog")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("barks")
                    .type(GraphQLBoolean)
                    .build())
            .withInterface(Named)
            .build();

    public static GraphQLObjectType CatType = newObject()
            .name("Cat")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("barks")
                    .type(GraphQLBoolean)
                    .build())
            .withInterface(Named)
            .build();

    public static GraphQLUnionType PetType = GraphQLUnionType.newUnionType()
            .possibleType(DogType)
            .possibleType(CatType)
            .typeResolver(new TypeResolver() {
                @Override
                public GraphQLObjectType getType(Object object) {
                    return null;
                }
            })
            .build();

    public static GraphQLObjectType PersonType = newObject()
            .name("Person")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString)
                    .build())
            .field(newFieldDefinition()
                    .name("pets")
                    .type(new GraphQLList(PetType))
                    .build())
            .field(newFieldDefinition()
                    .name("friends")
                    .type(new GraphQLList(Named))
                    .build())
            .withInterface(Named)
            .build();

    public static GraphQLSchema GarfieldSchema = GraphQLSchema.newSchema()
            .query(PersonType)
            .build();


}

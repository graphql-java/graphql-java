package graphql;


import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInterfaceType.newInterface;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLUnionType.newUnionType;

public class GarfieldSchema {

    public interface Named {
        String getName();
    }

    public static class Dog implements Named {
        private final String name;


        private final boolean barks;

        public Dog(String name, boolean barks) {
            this.name = name;
            this.barks = barks;
        }

        public boolean isBarks() {
            return barks;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public static class Cat implements Named {
        private final String name;

        private final boolean meows;

        public Cat(String name, boolean meows) {
            this.name = name;
            this.meows = meows;
        }

        public boolean isMeows() {
            return meows;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public static class Person implements Named {
        private final String name;
        private List<Dog> dogs;
        private List<Cat> cats;
        private List<Named> friends;

        public Person(String name) {
            this(name, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        public Person(String name, List<Cat> cats, List<Dog> dogs, List<Named> friends) {
            this.name = name;
            this.dogs = dogs;
            this.cats = cats;
            this.friends = friends;
        }

        public List<Object> getPets() {
            List<Object> pets = new ArrayList<>();
            pets.addAll(cats);
            pets.addAll(dogs);
            return pets;
        }

        @Override
        public String getName() {
            return name;
        }

        public List<Named> getFriends() {
            return friends;
        }
    }

    public static Cat garfield = new Cat("Garfield", false);
    public static Dog odie = new Dog("Odie", true);
    public static Person liz = new Person("Liz");
    public static Person john = new Person("John", Arrays.asList(garfield), Arrays.asList(odie), Arrays.asList(liz, odie));

    public static GraphQLInterfaceType NamedType = newInterface()
            .name("Named")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .typeResolver(new TypeResolver() {
                @Override
                public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    if (env.getObject() instanceof Dog) {
                        return DogType;
                    }
                    if (env.getObject() instanceof Person) {
                        return PersonType;
                    }
                    if (env.getObject() instanceof Cat) {
                        return CatType;
                    }
                    return null;
                }
            })
            .build();

    public static GraphQLObjectType DogType = newObject()
            .name("Dog")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("barks")
                    .type(GraphQLBoolean))
            .withInterface(NamedType)
            .build();

    public static GraphQLObjectType CatType = newObject()
            .name("Cat")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("meows")
                    .type(GraphQLBoolean))
            .withInterface(NamedType)
            .build();

    public static GraphQLUnionType PetType = newUnionType()
            .name("Pet")
            .possibleType(CatType)
            .possibleType(DogType)
            .typeResolver(env -> {
                if (env.getObject() instanceof Cat) {
                    return CatType;
                }
                if (env.getObject() instanceof Dog) {
                    return DogType;
                }
                return null;
            })
            .build();

    public static GraphQLObjectType PersonType = newObject()
            .name("Person")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("pets")
                    .type(list(PetType)))
            .field(newFieldDefinition()
                    .name("friends")
                    .type(list(NamedType)))
            .withInterface(NamedType)
            .build();

    public static GraphQLSchema GarfieldSchema = GraphQLSchema.newSchema()
            .query(PersonType)
            .build();


}

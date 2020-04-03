package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

class FieldCoordinatesTest extends Specification {
    def parentName = "Foo";
    def fieldName = "Bar";
    def invalidName = "Invalid;";
    def systemFieldName = "__Foo"
    def invalidSystemFieldName = "Invalid";

    def "GetTypeName"() {
        def coordinates = FieldCoordinates.coordinates(parentName, fieldName);
        expect:
        parentName.equals(coordinates.getTypeName());
    }

    def "GetFieldName"() {
        def coordinates = FieldCoordinates.coordinates(parentName, fieldName);
        expect:
        fieldName.equals(coordinates.getFieldName());
    }

    def "Equals"() {
        when: 'The same parent and field names are used to construct different coordinates'
        def coordinates1 = FieldCoordinates.coordinates(parentName, fieldName);
        def coordinates2 = FieldCoordinates.coordinates(parentName, fieldName);
        then: 'Both coordinates should equal each other'
        coordinates1.equals(coordinates2)
    }

    def "HashCode"() {
        when: 'The same parent and field names are used to construct different coordinates'
        def coordinates1 = FieldCoordinates.coordinates(parentName, fieldName);
        def coordinates2 = FieldCoordinates.coordinates(parentName, fieldName);
        then: 'Both coordinate hash codes should equal each other'
        coordinates1.hashCode().equals(coordinates2.hashCode())
    }

    def "ToString"() {
        when: 'The same parent and field names are used to construct different coordinates'
        def coordinates1 = FieldCoordinates.coordinates(parentName, fieldName);
        def coordinates2 = FieldCoordinates.coordinates(parentName, fieldName);
        then: 'Both coordinate toStrings should equal each other'
        coordinates1.toString().equals(coordinates2.toString())
    }

    def "Coordinates with types failure on null parent type"() {
        def fieldDef = newFieldDefinition().name(fieldName).type(GraphQLFloat).build()
        when: 'null parent type is given for coordinates'
        FieldCoordinates.coordinates(null, fieldDef);
        then: 'throw NullPointerException'
        thrown NullPointerException;
    }

    def "Coordinates with types failure on null field definition"() {
        def parentType = newObject().name(parentName)
                .field(newFieldDefinition().name(fieldName).type(GraphQLString))
                .build()
        when: 'null field definition is given for coordinates'
        FieldCoordinates.coordinates(parentType, null);
        then: 'throw NullPointerException'
        thrown NullPointerException;
    }

    def "Coordinates with types failure on null parent type and field definition"() {
        when: 'null parent type and field definition are given for coordinates'
        FieldCoordinates.coordinates((GraphQLFieldsContainer) null, null);
        then: 'throw NullPointerException'
        thrown NullPointerException;
    }

    def "Coordinates with types"() {
        def fieldDef = newFieldDefinition().name(fieldName).type(GraphQLFloat).build()
        def parentType = newObject().name(parentName).field(fieldDef).build()
        when: 'parent type and field definition are given'
        def coordinates = FieldCoordinates.coordinates(parentType, fieldDef);
        then: 'coordinates will contain the parent and field names'
        parentName.equals(coordinates.getTypeName());
        fieldName.equals(coordinates.getFieldName());
    }

    def "Coordinates with validation DEFAULT failure for PARENT"() {
        when:
        FieldCoordinates.coordinates(invalidName, fieldName);
        then:
        thrown AssertException;
    }

    def "Coordinates with validation DEFAULT failure for FIELD"() {
        when:
        FieldCoordinates.coordinates(parentName, invalidName);
        then:
        thrown AssertException;
    }

    def "Coordinates with validation DEFAULT success"() {
        def coordinates = FieldCoordinates.coordinates(parentName, fieldName);
        expect:
        parentName.equals(coordinates.getTypeName());
        fieldName.equals(coordinates.getFieldName());
    }

    def "Coordinates with validation ON failure for PARENT"() {
        when:
        FieldCoordinates.coordinates(invalidName, fieldName, true);
        then:
        thrown AssertException;
    }

    def "Coordinates with validation ON failure for FIELD"() {
        when:
        FieldCoordinates.coordinates(parentName, invalidName, true);
        then:
        thrown AssertException;
    }

    def "Coordinates with validation ON success"() {
        def coordinates = FieldCoordinates.coordinates(parentName, fieldName, true);
        expect:
        parentName.equals(coordinates.getTypeName());
        fieldName.equals(coordinates.getFieldName());
    }

    def "Coordinates with validation OFF success"() {
        def coordinates = FieldCoordinates.coordinates(invalidName, invalidName, false);
        expect:
        invalidName.equals(coordinates.getTypeName());
        invalidName.equals(coordinates.getFieldName());
    }

    def "SystemCoordinates with validation DEFAULT failure"() {
        when:
        FieldCoordinates.systemCoordinates(invalidSystemFieldName);
        then:
        thrown AssertException;
    }

    def "SystemCoordinates with validation DEFAULT success"() {
        def coordinates = FieldCoordinates.systemCoordinates(systemFieldName);
        expect:
        systemFieldName.equals(coordinates.getFieldName());
    }

    def "SystemCoordinates with validation ON failure"() {
        when:
        FieldCoordinates.systemCoordinates(invalidSystemFieldName, true);
        then:
        thrown AssertException;
    }

    def "SystemCoordinates with validation ON success"() {
        def coordinates = FieldCoordinates.systemCoordinates(systemFieldName, true);
        expect:
        systemFieldName.equals(coordinates.getFieldName());
    }

    def "SystemCoordinates with validation OFF success"() {
        def coordinates = FieldCoordinates.systemCoordinates(invalidSystemFieldName, false);
        expect:
        invalidSystemFieldName.equals(coordinates.getFieldName());
    }
}

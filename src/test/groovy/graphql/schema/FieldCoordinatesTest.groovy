package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.Scalars.GraphQLFloat
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

class FieldCoordinatesTest extends Specification {
    def emptyParentName = ""
    def validParentName = "Foo"
    def invalidParentName = "InvalidParent;"
    def validFieldName = "Bar"
    def invalidFieldName = "InvalidField;"
    def validSystemFieldName = "__Foo"
    def invalidSystemFieldName = "Invalid"

    def validSystemFieldDef = newFieldDefinition().name(validSystemFieldName).type(GraphQLFloat).build()
    def validFieldDef = newFieldDefinition().name(validFieldName).type(GraphQLFloat).build()

    GraphQLObjectType validParentType = newObject().name(validParentName).field(validFieldDef).build()
    def emptyParentType = [
            getName: { -> "" },
            getFieldDefinition: { name -> validFieldDef }
    ] as GraphQLFieldsContainer
    def nullParentType = [
        getName: { -> null },
        getFieldDefinition: { name -> validFieldDef }
    ] as GraphQLFieldsContainer


    def "GetTypeName for standard coordinates"() {
        def coordinatesFromStrings = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromTypes = FieldCoordinates.coordinates(validParentType, validFieldDef)
        expect:
        validParentName.equals(coordinatesFromStrings.getTypeName())
        validParentName.equals(coordinatesFromTypes.getTypeName())
    }

    def "GetFieldName for standard coordinates"() {
        def coordinatesFromStrings = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromTypes = FieldCoordinates.coordinates(validParentType, validFieldDef)
        expect:
        validFieldName.equals(coordinatesFromStrings.getFieldName())
        validFieldName.equals(coordinatesFromTypes.getFieldName())
    }

    def "GetTypeName for system coordinates"() {
        def coordinatesFromStrings = FieldCoordinates.systemCoordinates(validFieldName)
        def coordinatesFromTypes = FieldCoordinates.coordinates(nullParentType, validFieldDef)
        expect:
        null == coordinatesFromStrings.getTypeName()
        null == coordinatesFromTypes.getTypeName()
    }

    def "GetFieldName for sytem coordinates"() {
        def coordinatesFromStrings = FieldCoordinates.systemCoordinates(validFieldName)
        def coordinatesFromTypes = FieldCoordinates.coordinates(nullParentType, validFieldDef)
        expect:
        validFieldName.equals(coordinatesFromStrings.getFieldName())
        validFieldName.equals(coordinatesFromTypes.getFieldName())
    }

    def "Equals true"() {
        when: 'The same parent and field names are used to construct different coordinates'
        def coordinatesFromStrings1 = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromStrings2 = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromTypes1 = FieldCoordinates.coordinates(validParentType, validFieldDef)
        def coordinatesFromTypes2 = FieldCoordinates.coordinates(validParentType, validFieldDef)
        then: 'All coordinates should equal each other'
        coordinatesFromStrings1.equals(coordinatesFromStrings2)
        coordinatesFromTypes1.equals(coordinatesFromTypes2)
        coordinatesFromStrings1.equals(coordinatesFromTypes1)
    }

    def "Equals false"() {
        when: 'The different parent and field names are used to construct different coordinates'
        def coordinatesFromStrings = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromTypes = FieldCoordinates.coordinates(nullParentType, validFieldDef)
        then: 'Coordinates should NOT equal each other'
        !coordinatesFromStrings.equals(coordinatesFromTypes)
    }

    def "HashCode equals"() {
        when: 'The same parent and field names are used to construct different coordinates'
        def coordinatesFromStrings1 = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromStrings2 = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromTypes1 = FieldCoordinates.coordinates(validParentType, validFieldDef)
        def coordinatesFromTypes2 = FieldCoordinates.coordinates(validParentType, validFieldDef)
        then: 'All coordinate hash codes should equal each other'
        coordinatesFromStrings1.hashCode().equals(coordinatesFromStrings2.hashCode())
        coordinatesFromTypes1.hashCode().equals(coordinatesFromTypes2.hashCode())
        coordinatesFromStrings1.hashCode().equals(coordinatesFromTypes1.hashCode())
    }

    def "HashCode NOT equals"() {
        when: 'The different parent and field names are used to construct different coordinates'
        def coordinatesFromStrings = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromTypes = FieldCoordinates.coordinates(nullParentType, validFieldDef)
        then: 'Coordinate hash codes should NOT equal each other'
        !coordinatesFromStrings.hashCode().equals(coordinatesFromTypes.hashCode())
    }

    def "ToString equals"() {
        when: 'The same parent and field names are used to construct different coordinates'
        def coordinatesFromStrings1 = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromStrings2 = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromTypes1 = FieldCoordinates.coordinates(validParentType, validFieldDef)
        then: 'All coordinate toStrings should equal each other'
        coordinatesFromStrings1.toString().equals(coordinatesFromStrings2.toString())
        coordinatesFromStrings1.toString().equals(coordinatesFromTypes1.toString())
    }

    def "ToString NOT equals"() {
        when: 'The different parent and field names are used to construct different coordinates'
        def coordinatesFromStrings = FieldCoordinates.coordinates(validParentName, validFieldName)
        def coordinatesFromTypes = FieldCoordinates.coordinates(nullParentType, validFieldDef)
        then: 'Coordinate toStrings should NOT equal each other'
        !coordinatesFromStrings.toString().equals(coordinatesFromTypes.toString())
    }

    def "FieldCoordinate.coordinates(GraphQLFieldsContainer, GraphQLFieldDefinition) creation failure on null parentType"() {
        when: 'null parent type is given for coordinates and no validation'
        FieldCoordinates.coordinates(null, validFieldDef)
        then: 'throw NullPointerException'
        thrown NullPointerException;
    }

    def "FieldCoordinate.coordinates(GraphQLFieldsContainer, GraphQLFieldDefinition) creation failure on null fieldDefinition"() {
        when: 'null field definition is given for coordinates and no validation'
        FieldCoordinates.coordinates(validParentType, null as GraphQLFieldDefinition)
        then: 'throw NullPointerException'
        thrown NullPointerException;
    }

    def "FieldCoordinate.coordinates(GraphQLFieldsContainer, GraphQLFieldDefinition) test validation"() {
        when: 'parent type with null name is given for coordinates'
        def coordinates = FieldCoordinates.coordinates(nullParentType, validFieldDef)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'parent type with empty name is given for coordinates'
        coordinates = FieldCoordinates.coordinates(emptyParentType, validFieldDef)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'valid parent type with name and field definition are given for coordinates'
        coordinates = FieldCoordinates.coordinates(validParentType, validFieldDef)
        coordinates.assertValidNames()
        then: 'succeed'
        validParentName.equals(coordinates.getTypeName())
        validFieldName.equals(coordinates.getFieldName())
    }

    def "FieldCoordinate.coordinates(GraphQLFieldsContainer, GraphQLFieldDefinition) creation success with no validation"() {
        when: 'parent type with null name is given for coordinates and no validation'
        def coordinates = FieldCoordinates.coordinates(nullParentType, validFieldDef)
        then: 'succeed'
        null == coordinates.getTypeName()
        validFieldName.equals(coordinates.getFieldName())

        when: 'parent type with empty name is given for coordinates and no validation'
        coordinates = FieldCoordinates.coordinates(emptyParentType, validFieldDef)
        then: 'succeed'
        emptyParentName.equals(coordinates.getTypeName())
        validFieldName.equals(coordinates.getFieldName())

        when: 'valid parent type with name and field definition are given for coordinates and no validation'
        coordinates = FieldCoordinates.coordinates(validParentType, validFieldDef)
        then: 'succeed'
        validParentName.equals(coordinates.getTypeName())
        validFieldName.equals(coordinates.getFieldName())
    }

    def "FieldCoordinate.coordinates(String, String) test validation"() {
        when: 'null parent name is given for coordinates'
        def coordinates = FieldCoordinates.coordinates(null as String, validFieldName)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'null field name is given for coordinates'
        coordinates = FieldCoordinates.coordinates(validParentName, null)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'null parent name and null field name are given for coordinates'
        coordinates = FieldCoordinates.coordinates((String) null, null)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'invalid parent name is given for coordinates'
        coordinates = FieldCoordinates.coordinates(invalidParentName, validFieldName)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'invalid field name is given for coordinates'
        coordinates = FieldCoordinates.coordinates(validParentName, invalidFieldName)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'invalid parent and field names are given for coordinates'
        coordinates = FieldCoordinates.coordinates(invalidParentName, invalidFieldName)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'valid parent name and field name are given for coordinates'
        coordinates = FieldCoordinates.coordinates(validParentName, validFieldName)
        coordinates.assertValidNames()
        then: 'succeed'
        validParentName.equals(coordinates.getTypeName())
        validFieldName.equals(coordinates.getFieldName())
    }

    def "FieldCoordinate.coordinates(String, String) creation success with no validation"() {
        when: 'null parent name is given for coordinates and no validation'
        def coordinates = FieldCoordinates.coordinates(null as String, validFieldName)
        then: 'succeed'
        null == coordinates.getTypeName()
        validFieldName.equals(coordinates.getFieldName())

        when: 'null field name is given for coordinates and no validation'
        coordinates = FieldCoordinates.coordinates(validParentName, null)
        then: 'succeed'
        validParentName.equals(coordinates.getTypeName())
        null == coordinates.getFieldName()

        when: 'null parent name and null field name are given for coordinates and no validation'
        coordinates = FieldCoordinates.coordinates((String) null, null)
        then: 'succeed'
        null == coordinates.getTypeName()
        null == coordinates.getFieldName()

        when: 'invalid parent name is given for coordinates and no validation'
        coordinates = FieldCoordinates.coordinates(invalidParentName, validFieldName)
        then: 'succeed'
        invalidParentName.equals(coordinates.getTypeName())
        validFieldName.equals(coordinates.getFieldName())

        when: 'invalid field name is given for coordinates and no validation'
        coordinates = FieldCoordinates.coordinates(validParentName, invalidFieldName)
        then: 'succeed'
        validParentName.equals(coordinates.getTypeName())
        invalidFieldName.equals(coordinates.getFieldName())

        when: 'invalid parent and field names are given for coordinates and no validation'
        coordinates = FieldCoordinates.coordinates(invalidParentName, invalidFieldName)
        then: 'succeed'
        invalidParentName.equals(coordinates.getTypeName())
        invalidFieldName.equals(coordinates.getFieldName())

        when: 'valid parent name and field name are given for coordinates and no validation'
        coordinates = FieldCoordinates.coordinates(validParentName, validFieldName)
        then: 'succeed'
        validParentName.equals(coordinates.getTypeName())
        validFieldName.equals(coordinates.getFieldName())
    }

    def "FieldCoordinate.systemCoordinates(String) test validation"() {
        when: 'null field name is given for system coordinates'
        def coordinates = FieldCoordinates.systemCoordinates(null)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'invalid field name is given for system coordinates'
        coordinates = FieldCoordinates.systemCoordinates(invalidSystemFieldName)
        coordinates.assertValidNames()
        then: 'fail assert on validation'
        thrown AssertException

        when: 'valid field name is given for system coordinates'
        coordinates = FieldCoordinates.systemCoordinates(validSystemFieldName)
        coordinates.assertValidNames()
        then: 'succeed'
        null == coordinates.getTypeName()
        validSystemFieldName.equals(coordinates.getFieldName())
    }

    def "FieldCoordinate.systemCoordinates(String) creation success with no validation"() {
        when: 'null field name is given for system coordinates and no validation'
        def coordinates = FieldCoordinates.systemCoordinates(null)
        then: 'succeed'
        null == coordinates.getTypeName()
        null == coordinates.getFieldName()

        when: 'invalid field name is given for system coordinates and no validation'
        coordinates = FieldCoordinates.systemCoordinates(invalidSystemFieldName)
        then: 'succeed'
        null == coordinates.getTypeName()
        invalidSystemFieldName.equals(coordinates.getFieldName())

        when: 'valid field name is given for system coordinates and no validation'
        coordinates = FieldCoordinates.systemCoordinates(validSystemFieldName)
        then: 'succeed'
        null == coordinates.getTypeName()
        validSystemFieldName.equals(coordinates.getFieldName())
    }
}

package graphql

import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLSchema
import graphql.schema.GraphqlFieldVisibility
import spock.lang.Specification

class GraphqlFieldVisibilityTest extends Specification {

    def "basic visibility"() {

        GraphqlFieldVisibility fieldVisibility = new GraphqlFieldVisibility() {
            @Override
            List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
                return fieldsContainer.getFieldDefinitions()
            }

            @Override
            GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
                return fieldsContainer.getFieldDefinition(fieldName)
            }

            @Override
            List<GraphQLEnumValueDefinition> getValues(GraphQLEnumType enumType) {
                return enumType.getValues()
            }

            @Override
            GraphQLEnumValueDefinition getValue(GraphQLEnumType enumType, String enumName) {
                return enumType.getValue(enumName)
            }
        }
        def schema = GraphQLSchema.newSchema()
                .query(StarWarsSchema.queryType)
                .fieldVisibility(fieldVisibility)
                .build()

        def graphQL = GraphQL.newGraphQL(schema).build()

        given:
        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
                name
                friends {
                    name
                }
            }
        }
        """
        def expected = [
                hero: [
                        id     : '2001',
                        name   : 'R2-D2',
                        friends: [
                                [
                                        name: 'Luke Skywalker',
                                ],
                                [
                                        name: 'Han Solo',
                                ],
                                [
                                        name: 'Leia Organa',
                                ],
                        ]
                ]
        ]

        when:
        def result = graphQL.execute(query).data

        then:
        result == expected

    }
}

package graphql.schema

import graphql.Scalars
import graphql.TestUtil
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.NodeUtil
import graphql.language.OperationDefinition
import spock.lang.Specification

import static graphql.TestUtil.mergedField

class DataFetchingFieldSelectionSetImplTest extends Specification {

    def starWarsSchema = TestUtil.schemaFile("starWarsSchemaWithArguments.graphqls")


    def query = '''
            {
                human {
                    name
                    appearsIn
                    friends(separationCount : 2) {
                        name
                        appearsIn
                        friends(separationCount : 5) {
                            name
                            appearsIn
                        }   
                    }
                    ...FriendsAndFriendsFragment
                }
            }
            
            fragment FriendsAndFriendsFragment on Character {
                friends {
                    name 
                    friends {
                        name
                    }
                }
            }
        '''

    List<Field> firstFields(Document document) {
        (document.definitions[0] as OperationDefinition).selectionSet.selections
                .collect({ node -> (Field) node })
    }

    Map<String, FragmentDefinition> getFragments(Document document) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, null)
        getOperationResult.fragmentsByName
    }

    def "glob pattern matching works"() {

        def document = TestUtil.parseQuery(query)

        List<Field> fields = firstFields(document)

        def executionContext = ExecutionContextBuilder.newExecutionContextBuilder()
                .executionId(ExecutionId.generate())
                .fragmentsByName(getFragments(document))
                .graphQLSchema(starWarsSchema).build()

        def selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, starWarsSchema.getType('Human'), mergedField(fields))

        expect:
        !selectionSet.contains(null)
        !selectionSet.contains("")
        !selectionSet.contains("rubbish")
        !selectionSet.contains("rubbish*")
        !selectionSet.contains("rubbish?")

        //
        // glob matching works for single level fields
        //
        selectionSet.contains("appearsIn")
        selectionSet.contains("appearsIn*")
        selectionSet.contains("appearsI?")
        selectionSet.contains("appear?In")
        selectionSet.contains("app*In")

        //
        // glob matching works for multi level fields
        //
        selectionSet.contains("friends/*")
        selectionSet.contains("friends/name")
        selectionSet.contains("friends/name*")
        selectionSet.contains("friends/nam*")
        selectionSet.contains("friends/nam?")

        //
        // we basically have Java glob matching happening here
        // so no need to test all its functions
        //
        selectionSet.contains("friends/friends/name")
        selectionSet.contains("friends/friends/name*")
        selectionSet.contains("friends/friends/*")
        selectionSet.contains("friends/friends/**")

        // PathMatcher matches on segment eg the bit between / and /
        selectionSet.contains("**/friends/name")
        selectionSet.contains("**/name")
        selectionSet.contains("*/name")

        !selectionSet.contains("?/name")
        !selectionSet.contains("friends/friends/rubbish")
    }

    def "test field selection set capture works"() {

        def document = TestUtil.parseQuery(query)

        List<Field> fields = firstFields(document)

        def executionContext = ExecutionContextBuilder.newExecutionContextBuilder()
                .executionId(ExecutionId.generate())
                .fragmentsByName(getFragments(document))
                .graphQLSchema(starWarsSchema).build()

        def selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, starWarsSchema.getType('Human'), mergedField(fields))

        def fieldMap = selectionSet.get()
        expect:
        fieldMap.keySet() == [
                "name",
                "appearsIn",
                "friends",
                "friends/name",
                "friends/appearsIn",
                "friends/friends",
                "friends/friends/name",
                "friends/friends/appearsIn",
        ] as Set

    }


    def "test field argument capture works"() {

        def document = TestUtil.parseQuery(query)

        List<Field> fields = firstFields(document)

        def executionContext = ExecutionContextBuilder.newExecutionContextBuilder()
                .executionId(ExecutionId.generate())
                .fragmentsByName(getFragments(document))
                .graphQLSchema(starWarsSchema).build()

        def selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, starWarsSchema.getType('Human'), mergedField(fields))

        expect:

        selectionSet.arguments['name'] == [:]
        selectionSet.arguments['friends'] == [separationCount: 2]
        selectionSet.arguments['friends/friends'] == [separationCount: 5]

    }

    def "test field type capture works"() {

        def document = TestUtil.parseQuery(query)

        List<Field> fields = firstFields(document)

        def executionContext = ExecutionContextBuilder.newExecutionContextBuilder()
                .executionId(ExecutionId.generate())
                .fragmentsByName(getFragments(document))
                .graphQLSchema(starWarsSchema).build()

        def selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, starWarsSchema.getType('Human'), mergedField(fields))

        expect:

        (selectionSet.definitions['name'].getType() as GraphQLNonNull).getWrappedType() == Scalars.GraphQLString
        (selectionSet.definitions['friends'].getType() as GraphQLList).getWrappedType() == starWarsSchema.getType('Character')
        (selectionSet.definitions['friends/friends'].getType() as GraphQLList).getWrappedType() == starWarsSchema.getType('Character')
    }

    def replayQuery = '''
            {
              things(first: 10) {
                nodes {
                  ... thingInfo @skip(if:false)
                  ... dateInfo @skip(if:true)
                  ...fix
                }
                edges {
                  cursor
                  node {
                    description
                    status {
                      ...status @skip(if:false)
                    }
                  }
                }
                totalCount
              }
            }
            
            fragment thingInfo on Thing {
              key
              summary
              status {
                ...status
              }
            }
            
            fragment fix on Thing {
              stuff  {
                name
              }
            }
            
            fragment status on Status {
              name
            }
            
            fragment dateInfo on Thing {
                createdDate
                lastUpdatedDate
            }
        '''

    def replaySchema = TestUtil.schemaFile("thingRelaySchema.graphqls")

    def relayDocument = TestUtil.parseQuery(replayQuery)

    def replayExecutionContext = ExecutionContextBuilder.newExecutionContextBuilder()
            .executionId(ExecutionId.generate())
            .fragmentsByName(getFragments(relayDocument))
            .graphQLSchema(replaySchema).build()

    def "test getting sub selected fields by name"() {

        def startField = firstFields(relayDocument)
        def startingType = replaySchema.getType('ThingConnection')

        when:
        def selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(replayExecutionContext, startingType, mergedField(startField))

        def selectedNodesField = selectionSet.getField("nodes")

        then:
        selectedNodesField.getName() == "nodes"
        GraphQLTypeUtil.simplePrint(selectedNodesField.fieldDefinition.type) == "[Thing]"
        selectedNodesField.getSelectionSet().contains("key")
        selectedNodesField.getSelectionSet().contains("summary")
        selectedNodesField.getSelectionSet().contains("status")
        selectedNodesField.getSelectionSet().contains("status/name")
        selectedNodesField.getSelectionSet().contains("status*")
        selectedNodesField.getSelectionSet().contains("status/*")

        // directives are respected
        !selectedNodesField.getSelectionSet().contains("createdDate")
        !selectedNodesField.getSelectionSet().contains("lastUpdatedDate")

        when:
        def selectedKeyField = selectedNodesField.getSelectionSet().getField("key")

        then:
        selectedKeyField.getName() == "key"
        GraphQLTypeUtil.simplePrint(selectedKeyField.fieldDefinition.type) == "String"

        when:
        def selectedStatusField = selectedNodesField.getSelectionSet().getField("status")

        then:
        selectedStatusField.getName() == "status"
        GraphQLTypeUtil.simplePrint(selectedStatusField.fieldDefinition.type) == "Status"
        selectedStatusField.getSelectionSet().contains("name")

        // jump straight to compound fq name (which is 2 down from 'nodes')
        when:
        def selectedStatusNameField = selectedNodesField.getSelectionSet().getField("status/name")

        then:
        selectedStatusNameField.getName() == "name"
        GraphQLTypeUtil.simplePrint(selectedStatusNameField.fieldDefinition.type) == "String"

    }

    def "test getting sub selected fields by glob"() {

        def startField = firstFields(relayDocument)
        def startingType = replaySchema.getType('ThingConnection')

        when:
        def selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(replayExecutionContext, startingType, mergedField(startField))
        List<SelectedField> selectedUnderNodesAster = selectionSet.getFields("nodes/*")

        then:

        selectedUnderNodesAster.size() == 4
        def sortedSelectedUnderNodesAster = selectedUnderNodesAster.sort({ sf -> sf.name })

        def fieldNames = sortedSelectedUnderNodesAster.collect({ sf -> sf.name })
        fieldNames == ["key", "status", "stuff", "summary"]

        GraphQLTypeUtil.simplePrint(sortedSelectedUnderNodesAster[0].fieldDefinition.type) == "String"
        GraphQLTypeUtil.simplePrint(sortedSelectedUnderNodesAster[1].fieldDefinition.type) == "Status"
        GraphQLTypeUtil.simplePrint(sortedSelectedUnderNodesAster[2].fieldDefinition.type) == "Stuff"
        GraphQLTypeUtil.simplePrint(sortedSelectedUnderNodesAster[3].fieldDefinition.type) == "String"

        // descend one down from here Status.name which has not further sub selection
        when:
        def statusName = sortedSelectedUnderNodesAster[1].getSelectionSet().getField("name")

        then:
        statusName.name == "name"
        GraphQLTypeUtil.simplePrint(statusName.fieldDefinition.type) == "String"
        statusName.getSelectionSet().get().isEmpty()
    }

    def "test that aster aster is equal to get all"() {
        def startField = firstFields(relayDocument)
        def startingType = replaySchema.getType('ThingConnection')

        when:
        def selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(replayExecutionContext, startingType, mergedField(startField))
        List<SelectedField> allFieldsViaAsterAster = selectionSet.getFields("**")
        List<SelectedField> allFields = selectionSet.getFields()

        then:

        allFieldsViaAsterAster.size() == 14
        allFields.size() == 14
        def allFieldsViaAsterAsterSorted = allFieldsViaAsterAster.sort({ sf -> sf.qualifiedName })
        def allFieldsSorted = allFields.sort({ sf -> sf.qualifiedName })

        def expectedFieldName = [
                "edges",
                "edges/cursor",
                "edges/node",
                "edges/node/description",
                "edges/node/status",
                "edges/node/status/name",
                "nodes",
                "nodes/key",
                "nodes/status",
                "nodes/status/name",
                "nodes/stuff",
                "nodes/stuff/name",
                "nodes/summary",
                "totalCount"
        ]
        allFieldsViaAsterAsterSorted.collect({ sf -> sf.qualifiedName }) == expectedFieldName
        allFieldsSorted.collect({ sf -> sf.qualifiedName }) == expectedFieldName
    }
}
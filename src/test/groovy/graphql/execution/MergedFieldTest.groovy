package graphql.execution

import graphql.execution.incremental.DeferredExecution
import graphql.language.Field
import graphql.language.SelectionSet
import spock.lang.Specification

class MergedFieldTest extends Specification {

    def fa_1 = Field.newField("fa").build()
    def fa_2 = Field.newField("fa").build()
    def fa_3 = Field.newField("fa").build()
    def alias1 = Field.newField("a1").alias("alias1").build()
    def ss = SelectionSet.newSelectionSet([fa_1, fa_2]).build()
    def sub1 = Field.newField("s1").selectionSet(ss).build()
    def deferred1 = new DeferredExecution("defer1")
    def deferred2 = new DeferredExecution("defer2")

    def "can construct from a single field"() {
        when:
        def mergedField = MergedField.newSingletonMergedField(fa_1, null)
        then:
        mergedField.getName() == "fa"
        mergedField.getResultKey() == "fa"
        mergedField.getSingleField() == fa_1
        mergedField.isSingleField()
        !mergedField.hasSubSelection()
        // lazily make the list
        mergedField.getFields() == [fa_1]

        // these should still work
        mergedField.getName() == "fa"
        mergedField.getResultKey() == "fa"
        mergedField.getSingleField() == fa_1
        mergedField.isSingleField()
        !mergedField.hasSubSelection()
    }

    def "can construct from multiple fields"() {
        when:
        def mergedField = MergedField.newMergedField().addField(fa_1).addField(fa_2).build()

        then:
        mergedField.getName() == "fa"
        mergedField.getResultKey() == "fa"
        mergedField.getSingleField() == fa_1
        !mergedField.isSingleField()
        !mergedField.hasSubSelection()
        mergedField.getFields() == [fa_1, fa_2]
    }

    def "can have aliases"() {
        when:
        def mergedField = MergedField.newSingletonMergedField(alias1, null)
        then:
        mergedField.getName() == "a1"
        mergedField.getResultKey() == "alias1"
        mergedField.getSingleField() == alias1
        mergedField.isSingleField()
        !mergedField.hasSubSelection()
    }

    def "can have selection set on single field"() {
        when:
        def mergedField = MergedField.newSingletonMergedField(sub1, null)
        then:
        mergedField.getName() == "s1"
        mergedField.getResultKey() == "s1"
        mergedField.getSingleField() == sub1
        mergedField.isSingleField()
        mergedField.hasSubSelection()
    }

    def "builder can build a singleton via addField()"() {
        when:
        def mergedField = MergedField.newMergedField().addField(sub1).build()
        then:
        mergedField.getName() == "s1"
        mergedField.getResultKey() == "s1"
        mergedField.getSingleField() == sub1
        mergedField.isSingleField()
        mergedField.hasSubSelection()
    }

    def "builder can build a multi via addField() / addField() combo"() {
        when:
        def mergedField = MergedField.newMergedField().addField(fa_1).addField(fa_2).build()
        then:
        mergedField.getName() == "fa"
        mergedField.getResultKey() == "fa"
        mergedField.getSingleField() == fa_1
        !mergedField.isSingleField()
        mergedField.getFields() == [fa_1, fa_2]
    }

    def "builder can build a multi via addField() / fields() combo"() {
        when:
        def mergedField = MergedField.newMergedField().addField(fa_1).fields([fa_2, fa_3]).build()
        then:
        mergedField.getName() == "fa"
        mergedField.getResultKey() == "fa"
        mergedField.getSingleField() == fa_1
        !mergedField.isSingleField()
        mergedField.getFields() == [fa_1, fa_2, fa_3]
    }

    def "builder can build a multi via fields()"() {
        when:
        def mergedField = MergedField.newMergedField().fields([fa_1, fa_2, fa_3]).build()
        then:
        mergedField.getName() == "fa"
        mergedField.getResultKey() == "fa"
        mergedField.getSingleField() == fa_1
        !mergedField.isSingleField()
        mergedField.getFields() == [fa_1, fa_2, fa_3]
    }

    def "can add to an existing field"() {
        when:
        def mergedField = MergedField.newSingletonMergedField(fa_1, null)
        then:
        mergedField.getName() == "fa"
        mergedField.getDeferredExecutions().isEmpty()
        mergedField.getSingleField() == fa_1
        mergedField.getFields() == [fa_1]

        when:
        def mergedField2 = mergedField.newMergedFieldWith(fa_2, null)
        then:
        mergedField2.getName() == "fa"
        mergedField2.getDeferredExecutions().isEmpty()
        mergedField2.getSingleField() == fa_1
        mergedField2.getFields() == [fa_1, fa_2]

        // its a new instance
        !(mergedField2 === mergedField)
    }

    def "builder can handle no deferred executions"() {
        when:
        def mergedField = MergedField.newMergedField().addField(fa_1)
                .addDeferredExecutions([]).build()
        then:
        mergedField.getName() == "fa"
        mergedField.getSingleField() == fa_1
        mergedField.getDeferredExecutions().isEmpty()
    }

    def "builder can handle list of deferred executions"() {
        when:
        def mergedField = MergedField.newMergedField().addField(fa_1)
                .addDeferredExecutions([deferred1, deferred2]).build()
        then:
        mergedField.getName() == "fa"
        mergedField.getSingleField() == fa_1
        mergedField.getDeferredExecutions() == [deferred1, deferred2]

    }

    def "builder can handle a single deferred executions"() {
        when:
        def mergedField = MergedField.newMergedField().addField(fa_1)
                .addDeferredExecution(deferred1).build()
        then:
        mergedField.getName() == "fa"
        mergedField.getSingleField() == fa_1
        mergedField.getDeferredExecutions() == [deferred1]
    }

    def "builder can handle a single deferred execution at a time"() {
        when:
        def mergedField = MergedField.newMergedField().addField(fa_1)
                .addDeferredExecution(deferred1).addDeferredExecution(deferred2).build()
        then:
        mergedField.getName() == "fa"
        mergedField.getSingleField() == fa_1
        mergedField.getDeferredExecutions() == [deferred1,deferred2]
    }
}

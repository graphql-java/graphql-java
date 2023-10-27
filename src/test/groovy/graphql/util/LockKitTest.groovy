package graphql.util

import spock.lang.Specification

class LockKitTest extends Specification {


    def "can run code under a lock (simple test)"() {
        def sideEffect = 0

        when:
        LockKit.ReentrantLock lockedCode = new LockKit.ReentrantLock()
        lockedCode.runLocked { sideEffect++ }

        then:
        sideEffect == 1
    }

    def "can call code under a lock (simple test)"() {

        when:
        LockKit.ReentrantLock lockedCode = new LockKit.ReentrantLock()
        def val = lockedCode.callLocked { return "x" }

        then:
        val == "x"
    }

    def "is reentrant"() {

        def sideEffect = 0

        when:
        LockKit.ReentrantLock lockedCode = new LockKit.ReentrantLock()
        lockedCode.runLocked {
            sideEffect++
            lockedCode.runLocked {
                sideEffect++
            }
        }

        then:
        sideEffect == 2
    }

    def "can compute once"() {
        def sideEffect = 0

        when:
        LockKit.ComputedOnce computedOnce = new LockKit.ComputedOnce()

        then:
        !computedOnce.hasBeenComputed()
        sideEffect == 0

        when:
        computedOnce.runOnce { sideEffect++ }

        then:
        computedOnce.hasBeenComputed()
        sideEffect == 1

        when:
        computedOnce.runOnce { sideEffect++ }

        then:
        computedOnce.hasBeenComputed()
        sideEffect == 1
    }
}

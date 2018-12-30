/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package graphql.util

import spock.lang.Specification

/**
 *
 * @author gkesler
 */
class DependencyGraphTest extends Specification {
    def "test empty graph ordering"() {
        given:
        def graph = DependencyGraph.<String>simple()
            
        when:
        def ordering = graph.orderDependencies()
            
        then:
            graph.size() == 0
            graph.order() == 0
            ordering.hasNext() == false
    }
    
    def "test 1 vertex ordering"() {
        given:
        def v1 = new SimpleVertex<>("v1")
        def graph = DependencyGraph
            .<String>simple()
            .addDependency(v1, v1)
            
        when:
        def ordering = graph.orderDependencies()
        
        then:
        graph.size() == 0
        graph.order() == 1
        ordering.hasNext() == true
        ordering.next() == [v1]
        ordering.hasNext() == false
    }
    
    def "test 2 independent vertices ordering"() {
        given:
        def v1 = new SimpleVertex<>("v1")
        def v2 = new SimpleVertex<>("v2")
        def graph = DependencyGraph
            .<String>simple()
            .addDependency(v1, v1)
            .addDependency(v2, v2)
            
        when:
        def ordering = graph.orderDependencies()
        
        then:
        graph.size() == 0
        graph.order() == 2
        ordering.hasNext() == true
        ordering.next() == [v1, v2]
        ordering.hasNext() == false
    }
    
    def "test 2 dependent vertices ordering"() {
        given:
        def v1 = new SimpleVertex<>("v1")
        def v2 = new SimpleVertex<>("v2")
        def graph = DependencyGraph
            .<String>simple()
            .addDependency(v1, v2)
            
        when:
        def ordering = graph.orderDependencies()
        
        then:
        graph.size() == 1
        graph.order() == 2
        ordering.hasNext() == true
        ordering.next() == [v2]
        ordering.hasNext() == true
        ordering.next() == [v1]
        ordering.hasNext() == false
    }
    
    def "test possible https://en.wikipedia.org/wiki/Dependency_graph example"() {
        given:
            def a = new SimpleVertex("a")
            def b = new SimpleVertex("b")
            def c = new SimpleVertex("c")
            def d = new SimpleVertex("d")
            def graph = DependencyGraph.<String>simple()
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies()
            
        then:
            graph.order() == 4
            graph.size() == 3
            ordering.hasNext() == true
            ordering.next() == [c, d]
            ordering.hasNext() == true
            ordering.next() == [b]
            ordering.hasNext() == true
            ordering.next() == [a]
            ordering.hasNext() == false
    } 
    
    def "test impossible https://en.wikipedia.org/wiki/Dependency_graph example"() {
        given:
            def a = new SimpleVertex("a")
            def b = new SimpleVertex("b")
            def c = new SimpleVertex("c")
            def d = new SimpleVertex("d")
            def graph = DependencyGraph.<String>simple()
                .addDependency(a, b)
                .addDependency(b, d)
                .addDependency(b, c)
                .addDependency(c, d)
                .addDependency(c, a)
                
        when:
            def ordering = graph.orderDependencies()
            ordering.hasNext()
            ordering.next() // [d]
            ordering.hasNext()
            
        then:
            graphql.AssertException e = thrown()
            e.message.contains("couldn't calculate next closure")
    }
    
    def "test illegal next"() {
        given:
            def a = new SimpleVertex("a")
            def b = new SimpleVertex("b")
            def c = new SimpleVertex("c")
            def d = new SimpleVertex("d")
            def graph = DependencyGraph.<String>simple()
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies()
            ordering.hasNext()
            ordering.next()
            ordering.next()
            
        then:
            java.util.NoSuchElementException e = thrown()
            e.message.contains("next closure hasn't been calculated yet")
    }    
    
    def "test hasNext idempotency"() {
        given:
            def a = new SimpleVertex("a")
            def b = new SimpleVertex("b")
            def c = new SimpleVertex("c")
            def d = new SimpleVertex("d")
            def graph = DependencyGraph.<String>simple()
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies()
            
        then:
            ordering.hasNext() == ordering.hasNext()
            ordering.next() == [c, d]
            ordering.hasNext() == ordering.hasNext()
            ordering.next() == [b]
            ordering.hasNext() == ordering.hasNext()
            ordering.next() == [a]
            ordering.hasNext() == ordering.hasNext()
    }
    
    def "test close by value"() {
        given:
            def a = new SimpleVertex("a")
            def b = new SimpleVertex("b")
            def c = new SimpleVertex("c")
            def d = new SimpleVertex("d")
            def graph = DependencyGraph.<String>simple()
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies()
            
        then:
            ordering.hasNext() == true
            ordering.next() == [c, d]
            ordering.close([new SimpleVertex("c"), new SimpleVertex("d")])
            ordering.hasNext() == true
            ordering.next() == [b]
            ordering.close([new SimpleVertex("b")])
            ordering.hasNext() == true
            ordering.next() == [new SimpleVertex("a")]
            ordering.close([a])
            ordering.hasNext() == false
    }
        
    def "test close by id"() {
        given:
            def a = new SimpleVertex("a")
            def b = new SimpleVertex("b")
            def c = new SimpleVertex("c")
            def d = new SimpleVertex("d")
            def graph = DependencyGraph.<String>simple()
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies()
            
        then:
            ordering.hasNext() == true
            ordering.next() == [c, d]
            ordering.close([new SimpleVertex("c").id(c.getId()), new SimpleVertex("d").id(d.getId())])
            ordering.hasNext() == true
            ordering.next() == [b]
            ordering.close([new SimpleVertex("b").id(b.getId())])
            ordering.hasNext() == true
            ordering.next() == [new SimpleVertex("a").id(a.getId())]
            ordering.close([a])
            ordering.hasNext() == false
    }
    
    def "test close by invalid id"() {
        given:
            def a = new SimpleVertex("a")
            def b = new SimpleVertex("b")
            def c = new SimpleVertex("c")
            def d = new SimpleVertex("d")
            def graph = DependencyGraph.<String>simple()
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies()
            ordering.hasNext()
            ordering.next()
            ordering.close([new SimpleVertex("c").id(c.getId()), new SimpleVertex("d").id(12345)])
            
        then:
            java.lang.IllegalArgumentException e = thrown()
            e.message.contains("node not found")
    }
    
    def "test close by invalid value"() {
        given:
            def a = new SimpleVertex("a")
            def b = new SimpleVertex("b")
            def c = new SimpleVertex("c")
            def d = new SimpleVertex("d")
            def graph = DependencyGraph.<String>simple()
                .addDependency(a, b)
                .addDependency(a, c)
                .addDependency(b, d)
                
        when:
            def ordering = graph.orderDependencies()
            ordering.hasNext()
            ordering.next()
            ordering.close([new SimpleVertex("c").id(c.getId()), new SimpleVertex("e")])
            
        then:
            java.lang.IllegalArgumentException e = thrown()
            e.message.contains("node not found")
    }
    
    def "test possible https://en.wikipedia.org/wiki/Dependency_graph example via addEdge"() {
        given:
            def graph = DependencyGraph.<String>simple()
            def a = graph.addNode(new SimpleVertex("a"))
            def b = graph.addNode(new SimpleVertex("b"))
            def c = graph.addNode(new SimpleVertex("c"))
            def d = graph.addNode(new SimpleVertex("d"))
                
        when:
            graph
               .addEdge(new Edge<>(b, a))
               .addEdge(new Edge<>(c, a))
               .addEdge(new Edge<>(d, b))
            def ordering = graph.orderDependencies()
            
        then:
            graph.order() == 4
            graph.size() == 3
            ordering.hasNext() == true
            ordering.next() == [c, d]
            ordering.hasNext() == true
            ordering.next() == [b]
            ordering.hasNext() == true
            ordering.next() == [a]
            ordering.hasNext() == false
    } 
}


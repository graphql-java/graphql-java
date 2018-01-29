/*
 * Copyright 2016 Intuit Inc. All rights reserved. Unauthorized reproduction
 * is a violation of applicable law. This material contains certain
 * confidential or proprietary information and trade secrets of Intuit Inc.
 * 
 * graphql.util.TraverserTest.groovy
 * 
 * Created: Jan 29, 2018 11:56:36 AM
 * Author: gkesler
 */

package graphql.util

import spock.lang.Specification

/**
 *
 * @author gkesler
 */
class TraverserTest extends Specification {
    class Node {
        int number;
        List<Node> children = Collections.emptyList();
    }
    
    def "test pre-order depth-first traversal"() {
        given:
            Node root = new Node(number: 0, children: [
                new Node(number: 1, children: [
                    new Node(number: 3)
                ]),
                new Node(number: 2, children: [
                    new Node(number: 4),
                    new Node(number: 5)
                ])
            ])
        
        when:
            List<Integer> result = new Traverser({Node n -> n.children})
                .traverse(root, new ArrayList<Integer>(), new Traverser.Visitor<Node, List<Node>>() {
                    public Object enter (Traverser.Context<? super Node> context, List<Node> data) {
                        data.add(context.thisNode().number);
                        return data;
                    }
                    
                    public Object leave (Traverser.Context<? super Node> context, List<Node> data) {
                        return data;
                    }
                })
            
        then:
            assert result == [0, 1, 3, 2, 4, 5]
    }
    
    def "test post-order depth-first traversal"() {
        given:
            Node root = new Node(number: 0, children: [
                new Node(number: 1, children: [
                    new Node(number: 3)
                ]),
                new Node(number: 2, children: [
                    new Node(number: 4),
                    new Node(number: 5)
                ])
            ])
        
        when:
            List<Integer> result = new Traverser({Node n -> n.children})
                .traverse(root, new ArrayList<Integer>(), new Traverser.Visitor<Node, List<Node>>() {
                    public Object enter (Traverser.Context<? super Node> context, List<Node> data) {
                        return data;
                    }
                    
                    public Object leave (Traverser.Context<? super Node> context, List<Node> data) {
                        data.add(context.thisNode().number);
                        return data;
                    }
                })
            
        then:
            assert result == [3, 1, 4, 5, 2, 0]
    }
    
    def "test breadth-first traversal"() {
        given:
            Node root = new Node(number: 0, children: [
                new Node(number: 1, children: [
                    new Node(number: 3)
                ]),
                new Node(number: 2, children: [
                    new Node(number: 4),
                    new Node(number: 5)
                ])
            ])
        
        when:
            List<Integer> result = new Traverser(new Traverser.Queue<Node>(), {Node n -> n.children})
                .traverse(root, new ArrayList<Integer>(), new Traverser.Visitor<Node, List<Node>>() {
                    public Object enter (Traverser.Context<? super Node> context, List<Node> data) {
                        data.add(context.thisNode().number);
                        return data;
                    }
                    
                    public Object leave (Traverser.Context<? super Node> context, List<Node> data) {
                        return data;
                    }
                })
            
        then:
            assert result == [0, 1, 2, 3, 4, 5]
    }
}


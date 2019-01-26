/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.language.Node;
import graphql.schema.GraphQLType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 * @author gkesler
 */
public class ResultCollector {
    public ResultCollector operation (NodeVertex<? extends Node, ? extends GraphQLType> operation) {
        Objects.requireNonNull(operation);
        
        operations.add((NodeVertex<Node, GraphQLType>)operation);        
        return this;
    }
    
    public void joinResultsOf (FieldVertex node) {
        Objects.requireNonNull(node);
        
        String on = node.getResponseKey();
        List<Object> result = (List<Object>)node.getResult();
        List<Object> source = (List<Object>)node.getSource();
        // will join relation dataset to target dataset
        // since we don't have any keys to build a cartesian product,
        // will be assuming that relation and target dataset s are alerady sorted by the
        // same key, so a pair {target[i], relation[i]} represents that cartesian product
        // we can use to join them together
        int[] indexHolder = {0};
        result
            .stream()
            .limit(Math.min(source.size(), result.size()))
            .forEach(value -> {
                int index = indexHolder[0]++;
                Map<String, Object> targetMap = (Map<String, Object>)source.get(index);
//                if (targetMap != null) {
//                    Object value = result.get(index);
                    targetMap.put(on, value);

                    // Here, verify if the value to join is valid
                    if (value == null && node.isNotNull()) {
                        // need to set the paren't corresponding value to null
                        bubbleUpNIL(node);
                    }                    
//                }
            });
    }
    
    private void bubbleUpNIL (FieldVertex node) {
        String responseKey = node.getResponseKey();
        node
          .dependencySet()
          .forEach(v -> {
//                if (!v.accept(false, FieldVertex.IS_FIELD))
//                    return;
//                
//                FieldVertex fieldNode = v.as(FieldVertex.class);
                boolean hasNull = false;
                List<Object> results = (List<Object>)v.getResult();
                ListIterator resultsIt = results.listIterator();
                while (resultsIt.hasNext()) {
                    Object o = resultsIt.next();
                    
                    if (o == null)
                        continue;
                    
                    List<Object> items = (o instanceof List)
                          ? (List<Object>)o
                          : Arrays.asList(o);

                    ListIterator itemsIt = items.listIterator();
                    while (itemsIt.hasNext()) {
                        Map<String, Object> value = (Map<String, Object>)itemsIt.next();

                        if (value.getOrDefault(responseKey, this) == null) {
                            itemsIt.set(null);
                            hasNull = true;
                        }
                    }
                    
                    if (hasNull && NodeVertexVisitor.whenFieldVertex(v, node.isNotNull(), n -> n.isNotNullItems())/*node.isNotNullItems()*/) {
                        resultsIt.set(null);
                    } else {
                        hasNull = false;
                    }
                }

                if (hasNull) {
                    NodeVertexVisitor.whenFieldVertex(v, null, n -> {
                        joinResultsOf(n);
                        return null;
                    });
                }
          });
    };
    
/*    
    public List<Object> joinOn (String on, List<Object> relation, List<Object> target) {
        Objects.requireNonNull(on);
        Objects.requireNonNull(relation);
        Objects.requireNonNull(target);
                
        // will join relation dataset to target dataset
        // since we don't have any keys to build a cartesian product,
        // will be assuming that relation and target dataset s are alerady sorted by the
        // same key, so a pair {target[i], relation[i]} represents that cartesian product
        // we can use to join them together
        int[] indexHolder = {0};
        relation
            .stream()
            .limit(Math.min(target.size(), relation.size()))
            .forEach(o -> {
                int index = indexHolder[0]++;
                Map<String, Object> targetMap = (Map<String, Object>)target.get(index);
                if (targetMap != null) {
                    targetMap.put(on, relation.get(index));
                }
            });
        
        return target;
    }
*/    
    public Object getResult () {
        List<Object> result = operations
            .stream()
            .map(NodeVertex::getResult)
            .map(r -> ((List<Object>)r).get(0))
            .collect(Collectors.toList());
        
        return result.size() > 1
            ? result
            : result.get(0);
    }
    
    private final Collection<NodeVertex<Node, GraphQLType>> operations = new ArrayList<>();
}

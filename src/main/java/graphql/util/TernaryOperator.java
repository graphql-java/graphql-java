/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

/**
 *
 * @author gkesler
 * @param <T>
 */
@FunctionalInterface
public interface TernaryOperator<T> extends TriFunction<Boolean, T, T, T> {    
}

package com.squarebit.machinations.machc.vm;

/**
 * Scope, providing information regarding to parent scope and local symbols.
 */
public interface Scope {
    /**
     * Gets the parent scope.
     * @return parent scope.
     */
    Scope getParent();

    /**
     * Finds a local symbol with given name in this scope.
     *
     * @param name the symbol name
     * @return the symbol or null.
     */
    SymbolInfo findLocalSymbol(String name);
}
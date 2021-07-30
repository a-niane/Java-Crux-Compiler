package crux.frontend;

import crux.frontend.ast.Position;
import crux.frontend.types.*;

import javax.swing.*;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Symbol table will map each symbol from Crux source code to its declaration or appearance in the
 * source. the symbol table is made up of scopes, each scope is a map which maps an identifier to
 * it's symbol Scopes are inserted to the table starting from the first scope (Global Scope). The
 * Global scope is the first scope in each Crux program and it contains all the built in functions
 * and names. The symbol table is an ArrayList of scopes.
 */

final class SymbolTable
{
  private final PrintStream err;
  private final ArrayList<Map<String, Symbol>> symbolScopes = new ArrayList<>();

  private boolean encounteredError = false;

  SymbolTable(PrintStream err)
  {
    this.err = err;

    //Only one HashMap needed to be made, containing all the scopes (ArrayList.size() == 1)
    HashMap globalScope = new HashMap<>();

    //int readInt()
    TypeList readIntArgs = new TypeList(); //no parameters
    Symbol readInt = new Symbol ("readInt", new FuncType(readIntArgs, new IntType())); //readInt -> no parameters, returns int
    globalScope.put("readInt", readInt);

    //int readChar()
    TypeList readCharArgs = new TypeList();
    Symbol readChar = new Symbol("readChar", new FuncType(readCharArgs, new IntType()));
    globalScope.put("readChar", readChar);

    //void printBool(bool arg)
    TypeList printBoolArgs = TypeList.of(new BoolType());
    Symbol printBool = new Symbol("printBool", new FuncType(printBoolArgs, new VoidType()));
    globalScope.put("printBool", printBool);

    //void printInt(int arg)
    TypeList printIntArgs = TypeList.of(new IntType());
    Symbol printInt = new Symbol("printInt", new FuncType(printIntArgs, new VoidType())); //symbol takes name and type
    globalScope.put("printInt", printInt);

    //void printChar(int arg)
    TypeList printCharArgs = TypeList.of(new IntType());
    Symbol printChar = new Symbol("printChar", new FuncType(printCharArgs, new VoidType()));
    globalScope.put("printChar", printChar);

    //void println()
    TypeList printlnArgs = new TypeList();
    Symbol println = new Symbol("println", new FuncType(printlnArgs, new VoidType()));
    globalScope.put("println", println);

    symbolScopes.add(globalScope); //one scope contains the six functions (each with distinct names)
  }

  boolean hasEncounteredError()
  {
    return encounteredError;
  }

  void enter() //symbolScope's size + 1
  {
    Map myNewScope = new HashMap<>();
    symbolScopes.add(myNewScope);
  }

  void exit() //symbolScope's size - 1 (delete what was just added by enter)
  {
    symbolScopes.remove(symbolScopes.size()-1); //delete most recent scope
  }

  /**
   * Insert a symbol to the table at the most recent scope. if the name already exists in the
   * current scope that's a declaration error.
   */

  Symbol add(Position pos, String name, Type type) //symbolScope's size SHOULD NOT change
  {
    Map recentScope = symbolScopes.get(symbolScopes.size()-1);
    if (recentScope.containsKey((name))) //if this hashMap already has this name
    {
      err.printf("DeclarationError%s[Symbol Already Exists in the Scope %s.]%n", pos, name); //Position used for error (return an error or change boolean to true, and return new error)
      encounteredError = true;
      return new Symbol(name, "DeclarationError"); //no need to add this error into the scope
    }

    Symbol sym = new Symbol(name, type);
    recentScope.put(name, sym);
    return sym;
  }

  /**
   * lookup a name in the SymbolTable, if the name not found in the table it should encounter an
   * error and return a symbol with ResolveSymbolError error. if the symbol is found then return it.
   */
  Symbol lookup(Position pos, String name)
  {
    var symbol = find(name);
    if (symbol == null) {
      err.printf("ResolveSymbolError%s[Could not find %s.]%n", pos, name);
      encounteredError = true;
      return new Symbol(name, "ResolveSymbolError");
    } else {
      return symbol;
    }
  }

  /**
   * Try to find a symbol in the table starting form the most recent scope.
   */
  private Symbol find(String name)
  {
    for (int i = symbolScopes.size() - 1; i >= 0; i--)
    {
      if (symbolScopes.get(i).containsKey(name)) //if symbol exists in a hashmap
      {
        return symbolScopes.get(i).get(name);
      }
    }
    return null; //no such symbol found
  }
}

package crux.frontend.types;

import crux.frontend.Symbol;
import crux.frontend.ast.*;
import crux.frontend.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class will associate types with the AST nodes from Stage 2
 */

public final class TypeChecker
{
  //Maps ASTNode to Type
  private final HashMap<Node, Type> typeMap = new HashMap<>();

  //any errors created goes here
  private final ArrayList<String> errors = new ArrayList<>();

  public ArrayList<String> getErrors() {
    return errors;
  }

  public void check(DeclarationList ast) {
    var inferenceVisitor = new TypeInferenceVisitor();
    inferenceVisitor.visit(ast);
  }

  /**
   * Helper function, should be used to add error into the errors array
   */

  private void addTypeError(Node n, String message) {
    errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
  }

  /**
   * Helper function, should be used to add types into the typeMap if the Type is an ErrorType then
   * it will call addTypeError
   */

  private void setNodeType(Node n, Type ty) {
    typeMap.put(n, ty);
    if (ty.getClass() == ErrorType.class) {
      var error = (ErrorType) ty;
      addTypeError(n, error.getMessage());
    }
  }

  /**
   * Helper to retrieve Type from the map
   */

  public Type getType(Node n) {
    return typeMap.get(n);
  }


  /**
   * These calls will visit each AST node and try to resolve it's type with the help of the
   * symbolTable.
   */

  private final class TypeInferenceVisitor extends NullNodeVisitor<Void>
  {
    private Symbol currentFunctionSymbol; //saves the first instance of function
    private Type currentFunctionReturnType; //saves the return type of first instance

    //we are in charge of initializing them in visitor
    private boolean lastStatementReturns; //check if the very last statement is return (if applicable)
    private boolean hasBreak; //checks for break statements

    @Override
    public Void visit(Name name) //name is a node, so save its type and stop visiting
    {
      Type nameType = name.getSymbol().getType();
      AddressType retType = new AddressType(nameType);
      setNodeType(name, retType); //associate this node with the foundType
      return null;
    }

    @Override
    public Void visit(ArrayDeclaration arrayDeclaration) //arrayDeclaration is a node, so visits stop here
    {
      Type baseType = arrayDeclaration.getSymbol().getType(); //use symbol to find type

      while(baseType.getClass() == ArrayType.class)
      {
        baseType = ((ArrayType) baseType).getBase(); //continues to look into base
      }
      //setNodeType(arrayDeclaration, baseType);
      //What do we do with this baseType?
      return null;
    }

    @Override
    public Void visit(Assignment assignment) //assignment is a node, so visits stop here
    {
      //getlocation(lhs) and getValue(rhs)
      Expression lhs = assignment.getLocation();
      Expression rhs = assignment.getValue();

      //accept (based on OpExpr visit) and get their types (expressions are nodes)
      lhs.accept(this); //should go to name node
      rhs.accept(this);

      //getType(assignment.getLocation()/getValue())
      Type lhsType = getType(lhs); //should be address
      Type rhsType = getType(rhs);
      if(lhsType.getClass() == AddressType.class)
      {
        Type baseLhs = ((AddressType) lhsType).getBaseType();
        Type assignType = baseLhs.assign(rhsType);
        setNodeType(assignment, assignType);
      }

      setNodeType(assignment, new VoidType());
      return null;
    }

    @Override
    public Void visit(Break brk) //break is a node, so visits stop here
    {
      hasBreak = true; //if this function is called, a break node is found
      return null;
    }

    @Override
    public Void visit(Call call) //call is a node, so visits stop here
    {
      List<Expression> args = call.getArguments(); //input arguments in order
      TypeList tyList = new TypeList(); //initialized if arguments are correct and add into them

      for(int i = 0; i < args.size(); i++)
      {
        args.get(i).accept(this); //should handle nodes properly and add them to typeMap
        tyList.append(typeMap.get(args.get(i))); //expressions are nodes, so save their types
      }

      Type resultType = call.getCallee().getType().call(tyList); //use args to determine if parameters are correct, returns error if not
      setNodeType(call, resultType); //returns type of functionCallResult
      return null;
    }

    @Override
    public Void visit(Continue cont) //continue is a node, so visits end here
    {
      //continue returns no type
      return null;
    }

    @Override
    public Void visit(DeclarationList declarationList) //not a node, so make multiple visits (do not setNodeType)
    {
      //retrieve the list of declarations (using getChildren)
      var declarations = declarationList.getChildren();

      //list should consist of variable, array, and function declarations
      //traverse through list and accept them all
      for (int i = 0; i < declarations.size(); i++)
      {
        declarations.get(i).accept(this); //should handle nodes properly and add them to typeMap
      }
      return null;
    }

    @Override
    public Void visit(Dereference dereference) //is a node, so visits stop here
    {
      //visit dereference.getAddress() to determine the type (saved as resolvedType)
      Expression address = dereference.getAddress();
      address.accept(this); //-> Name -> addressType
      Type resolvedType = getType(address);

      //returnValue = resolvedType.deref() to determine base Type
      if (resolvedType.getClass() == AddressType.class)
      {
        Type returnValue = resolvedType.deref();
        setNodeType(dereference, returnValue); //returnValue
      }
      else
      {
        setNodeType(dereference, resolvedType);
      }
      return null;
    }

    @Override
    public Void visit(FunctionDefinition functionDefinition) //is a node, so visits stop here
    {
      currentFunctionSymbol = functionDefinition.getSymbol();
      currentFunctionReturnType = ((FuncType)functionDefinition.getSymbol().getType()).getRet(); //should be functionType, then get return type

      //check that types of functions args are valid (only int and bool)
      TypeList tyList = new TypeList(); //initialized if arguments are correct and add into them
      List<Symbol> params = functionDefinition.getParameters();
      for(int i = 0; i < params.size(); i++)
      {
        tyList.append(params.get(i).getType()); //accept(this);
      }

      //visit functionbody
      StatementList body = functionDefinition.getStatements();
      body.accept(this);

      return null;
    }

    @Override
    public Void visit(IfElseBranch ifElseBranch) //is a node, so visit stops here
    {
      //conditionType = find and accept condition, determine if type is boolean (condition should be BoolType, else, addTypeError)
      Expression condition = ifElseBranch.getCondition();
      condition.accept(this);
      Type conditionType = getType(condition);

      //visit thenBlock -> statementList
      StatementList thenBlock = ifElseBranch.getThenBlock();
      thenBlock.accept(this);

      //visitElseBlock -> statementList
      StatementList elseBlock = ifElseBranch.getElseBlock();
      elseBlock.accept(this);

      if (!conditionType.equivalent(new BoolType()))
      {
        setNodeType(ifElseBranch, new ErrorType(""));
      }
      return null;
    }

    @Override
    public Void visit(ArrayAccess access) //is a node
    {
      //getBase and visit that
      Name base = access.getBase();
      base.accept(this);

      //getOffset and visit that
      Expression offset = access.getOffset();
      offset.accept(this);

      //results = getType(base).index(getOffset) //saved as a type
      Type baseType = getType(base);
      if(baseType.getClass() == AddressType.class)
      {
        Type resultsBase = ((AddressType) baseType).getBaseType();
        Type results = resultsBase.index(getType(offset));

        setNodeType(access, new AddressType(results));
      }
      return null;
    }

    @Override
    public Void visit(LiteralBool literalBool) //is a node, no additional visits
    {
      setNodeType(literalBool, new BoolType());
      return null;
    }

    @Override //leaves of tree
    public Void visit(LiteralInt literalInt) //is a node, no additional visits
    {
      setNodeType(literalInt, new IntType());
      return null;
    }

    @Override
    public Void visit(Loop loop) //is a node
    {
      //getBody and visit -> as a statementlist that handles break and continue
      StatementList body = loop.getBody();
      body.accept(this);

      return null;
    }

    @Override
    public Void visit(OpExpr op)
    {
      //getLeft -> visit (check if null), then get Type
      Expression lhs = op.getLeft();
      lhs.accept(this);
      Type lhsType = getType(lhs);

      if(op.getRight() == null) //no right hand side, but contains operation
      {
        if(op.getOp() != null && op.getOp().toString().equals("!")) //must be logical not
        {
          Type returnType = lhsType.not();
          setNodeType(op, returnType);
          return null;
        }
      }

      //getRight -> visit (check if null), then getType
      Expression rhs = op.getRight();
      rhs.accept(this);
      Type rhsType = getType(rhs);

      Type type = new ErrorType("");
      if(op.getOp() != null)
      {
        switch (op.getOp().toString())
        {
          case "+":
            type = lhsType.add(rhsType);
            break;
          case "-":
            type = lhsType.sub(rhsType);
            break;
          case "*":
            type = lhsType.mul(rhsType);
            break;
          case "/":
            type = lhsType.div(rhsType);
            break;
          case "||":
            type = lhsType.or(rhsType);
            break;
          case "&&":
            type = lhsType.and(rhsType);
            break;
          default: //op0 : '>=' | '<=' | '!=' | '==' | '<' | '>';
            type = lhsType.compare(rhsType);
        }
      }

      setNodeType(op, type);
      return null;
    }

    @Override
    public Void visit(Return ret)
    {
      if (!lastStatementReturns) //default at false, assuming its the last statement
      {
        //visit ret.getValue(), then find its type
        Expression value = ret.getValue();
        value.accept(this);
        Type type = getType(value);

        //check if type == currentFunctionReturnType
        if(!type.equivalent(currentFunctionReturnType)) //if so setNodeType(ret, type), else setNodeType(ret, new ErrorType())
        {
          setNodeType(ret, new ErrorType(""));
        }
      }
      return null;
    }

    @Override
    public Void visit(StatementList statementList) //not a node, multiple visits, no setNodeType
    {
      var listOfStatements = statementList.getChildren();
      for(int i = 0; i < listOfStatements.size(); i++)
      {
        listOfStatements.get(i).accept(this); //visit each statement (variable, calls, etc)
      }
      return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDeclaration) //is a node
    {
      Type variableType = variableDeclaration.getSymbol().getType();
      if(!variableType.equivalent(new IntType()) && !variableType.equivalent(new BoolType()))
      {
        setNodeType(variableDeclaration, new ErrorType(""));
      }
      return null;
    }
  }
}

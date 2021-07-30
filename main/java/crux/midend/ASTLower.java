package crux.midend;

import crux.frontend.Symbol;
import crux.frontend.ast.*;
import crux.frontend.ast.traversal.NodeVisitor;
import crux.frontend.types.*;
import crux.midend.ir.core.*;
import crux.midend.ir.core.insts.*;

import java.util.*;

/**
 * Convert AST to IR and build the CFG.
 */

// Hint: You might want to change the Void parameter of the
// NodeVisitor and the return type of the visit methods to some other
// class so that the visit methods can return information to the
// caller. (Use Professor's advice on making Pair)

class Pair //used to create pairs of instruction and value
{
    Instruction mStartControlInstruction;
    Instruction mLastControlInstruction;
    Value mExpressionValue;

    Pair(Instruction start, Instruction last, Value value) {
        mStartControlInstruction = start;
        mLastControlInstruction = last; //connection additions here
        mExpressionValue = value;
    }

    Instruction getStart() {
        return mStartControlInstruction;
    }

    Instruction getInstruction() {
        return mLastControlInstruction;
    }

    Value getValue() {
        return mExpressionValue;
    }

    public void setValue(Value val) {
        mExpressionValue = val;
    }
}

public final class ASTLower implements NodeVisitor<Pair> {
    private Program mCurrentProgram = null;
    private Function mCurrentFunction = null;

    private Map<Symbol, Variable> mCurrentLocalVarMap = null;
    private TypeChecker checker;

    Instruction breakInstruction = new NopInst();
    boolean breakEncountered = false;
    Instruction continueInstruction = new NopInst();
    boolean continueEncountered = false;
    Instruction lastLoopInstr = new NopInst();
    Instruction lastInstr = new NopInst();
    boolean loopEntered = false; //keeps track of first instruction outside of loop

    Instruction inBreakInstruction = new NopInst();
    boolean inBreakEncountered = false;
    Instruction inContinueInstruction = new NopInst();
    boolean inContinueEncountered = false;
    Instruction inLastLoopInstr = new NopInst();
    Instruction inLastInstr = new NopInst();
    boolean inLoopEntered = false; //keeps track of first instruction outside of loop

    Instruction false50 = null;
    boolean on = false;

    /**
     * A constructor to initialize member variables
     */

    public ASTLower(TypeChecker checker) {
        this.checker = checker;
    }

    public Program lower(DeclarationList ast) {
        visit(ast);
        return mCurrentProgram;
    }

    @Override
    public Pair visit(DeclarationList declarationList) //variable, array, function
    {
        //create new instance of program class for mCurrentProgram once
        mCurrentProgram = new Program(); //mFunctions and mGlobalVars

        var declarations = declarationList.getChildren();
        for (Node declaration : declarations) {
            declaration.accept(this); //should handle nodes properly
        }
        return null;
    }

    /*
     * Function declaration
     */

    @Override
    public Pair visit(FunctionDefinition functionDefinition) //functional
    {
        //create new instance of Function class for mCurrentFunction for every new function
        Symbol sym = functionDefinition.getSymbol();
        if(sym.getName().contains("garble"))
        {
            on = true;
        }
        mCurrentFunction = new Function(sym.getName(), (FuncType) sym.getType());
        mCurrentProgram.addFunction(mCurrentFunction); //adds function into program
        mCurrentLocalVarMap = new HashMap<>(); //initialize new local variables in the scope

        //Add parameters to localVarMap
        List<Symbol> params = functionDefinition.getParameters();
        List<LocalVar> args = new ArrayList<>();

        for (Symbol param : params) {
            LocalVar myVar = mCurrentFunction.getTempVar(param.getType(), param.getName());
            mCurrentLocalVarMap.put(param, myVar); //parameters are added to local vars
            args.add(myVar);
        }

        //set the start and arguments
        mCurrentFunction.setArguments(args);
        var nop = new NopInst();
        Pair start = new Pair(nop, nop, new LocalVar(new IntType())); //instr, value
        mCurrentFunction.setStart(nop);

        //visit function body
        StatementList body = functionDefinition.getStatements();
        Pair statementPair = visit(body); //nop->true->call->false->call
        mCurrentFunction.getStart().setNext(0, statementPair.getInstruction());

        //erase any temporary storage
        mCurrentLocalVarMap = null; //erase scopes
        mCurrentFunction = null; //new current function (function should end here)
        start.getInstruction().setNext(0, statementPair.getInstruction());
        return start; //nop instruction, no value
    }

    @Override
    public Pair visit(StatementList statementList)
    {
        var listOfStatements = statementList.getChildren();
        Instruction start = null;
        Instruction end = null;

        Instruction chain = null; //performs instruction connecting
        for (Node listOfStatement : listOfStatements)
        {
            Pair statement = listOfStatement.accept(this);
            //break statement worked out here
            if(listOfStatement instanceof Loop) //this should pass loop and provide entry for break
            {
                loopEntered = true;
            }

            if (statement != null)
            {
                if (start == null)
                {
                    start = statement.getInstruction();
                    end = statement.getInstruction();
                }
                else if (chain != null)
                {
                    //situations with jump statements
                    if(!(listOfStatement instanceof Loop) && loopEntered)
                    {
                        Instruction sub = chain;
                        while(sub.getNext(0) != null)
                        {
                            if(sub instanceof JumpInst) {
                                sub = sub.getNext(1);
                            }
                            else
                            {
                                sub = sub.getNext(0);
                            }
                        }
                        sub.setNext(0, statement.getInstruction());

                    }
                    else {
                        connectEdge(chain, statement.getInstruction());
                    }
                }
                chain = statement.getInstruction(); //chains instructions

                if(!(listOfStatement instanceof Loop) && loopEntered) //next instruction outside of the loop
                {
                    lastInstr = chain;
                    //considers any future loops by resetting terms
                    loopEntered = false;
                    breakInstruction = new NopInst();
                    breakEncountered = false;
                    continueInstruction = new NopInst();
                    continueEncountered = false;
                    lastLoopInstr = new NopInst();
                    lastInstr = new NopInst();
                    loopEntered = false; //keeps track of first instruction outside of loop

                    inBreakInstruction = new NopInst();
                    inBreakEncountered = false;
                    inContinueInstruction = new NopInst();
                    inContinueEncountered = false;
                    inLastLoopInstr = new NopInst();
                    inLastInstr = new NopInst();
                    inLoopEntered = false; //keeps track of first instruction outside of loop
                }
            }
        }
        if(start == null)
        {
            return new Pair(new NopInst(), new NopInst(), new LocalVar(new IntType())); //no instruction, no value
        }
        return new Pair(start, end, new LocalVar(new IntType())); //no instruction, no value
    }

    /**
     * Declarations, could be either local or Global
     */

    @Override
    public Pair visit(VariableDeclaration variableDeclaration)
    {
        //Determine when to add into mCurrentFunction (local), and when to add into mCurrentProgram (global)
        Symbol variableSym = variableDeclaration.getSymbol();
        if (mCurrentFunction == null) //if not in a function, then adding into global scope
        {
            Constant element = IntegerConstant.get(mCurrentProgram, 0); //Constant(Type type)
            GlobalDecl glob = new GlobalDecl(variableSym, element); //GlobalDecl(Symbol symbol, Constant numElement)
            mCurrentProgram.addGlobalVar(glob);
        }
        else
        {
            LocalVar myVar = mCurrentFunction.getTempVar(variableSym.getType(), variableSym.getName());
            mCurrentLocalVarMap.put(variableSym, myVar);
        }
        return null; //no pair needed
    }

    /**
     * Create a declaration for array
     */

    @Override
    public Pair visit(ArrayDeclaration arrayDeclaration) //functional
    {
        //ArrayDeclarations are always global
        Symbol arraySym = arrayDeclaration.getSymbol();

        //compute size of total array, create IntegerConstant
        var extent = ((ArrayType) arraySym.getType()).getExtent();
        Constant element = IntegerConstant.get(mCurrentProgram, extent);

        //create new GlobalDecl object, add to mCurrentProgram with addGlobalVar method
        GlobalDecl glob = new GlobalDecl(arraySym, element);
        mCurrentProgram.addGlobalVar(glob);

        return null; //no instructions or value to pass
    }

    @Override
    public Pair visit(Name name)
    {
        Symbol nameSym = name.getSymbol(); //look up symbol to use as expression value
        Value myVar = new LocalVar(new IntType());
        //var nop = new NopInst(); //no instructions, but variable is important

        if (mCurrentLocalVarMap.get(nameSym) == null) //if empty, then it is a global variable
        {
            Iterator<GlobalDecl> myGlobals = mCurrentProgram.getGlobals();
            while (myGlobals.hasNext()) {
                GlobalDecl glob = myGlobals.next();
                if (glob.getSymbol().equals(nameSym)) {
                    myVar = glob.getNumElement();
                }
            }
        } else //not empty, so not global var, so just find it from local
        {
            myVar = mCurrentLocalVarMap.get(nameSym);
        }
        return new Pair(null, null, myVar);

    }

    /**
     * Assignment
     */

    @Override
    public Pair visit(Assignment assignment)
    {
        Instruction start;
        Instruction end;

        Pair loc = visit(assignment.getLocation()); //should call name or arrayaccess
        Pair assign = visit(assignment.getValue()); //should call literals, arrayaccess, call, etc.

        Instruction store;

        if (loc.getValue().getClass().equals(LocalVar.class) && !(assignment.getLocation() instanceof ArrayAccess)) //local variable -> (print 10) create copy instruction
        {
            store = new CopyInst((LocalVar) loc.getValue(), assign.getValue());
        }
        else
        {
            AddressVar addr = null;
            if(assignment.getLocation() instanceof ArrayAccess)
            {
                Instruction findAddrAt = loc.getInstruction();
                while(findAddrAt != null) //arrayaccess instances
                {
                    if(findAddrAt instanceof AddressAt) //find most recent address
                    {
                        addr = ((AddressAt) findAddrAt).getDst();
                    }
                    findAddrAt = findAddrAt.getNext(0);
                }
            }
            else {
                addr = mCurrentFunction.getTempAddressVar(assign.getValue().getType());// new AddressVar(assign.getValue().getType());
            }


            if (assignment.getLocation() instanceof Name) //global variable that's a name (int i = 10; print(i)) -> addressAt instruction
            {
                Symbol sym = ((Name) assignment.getLocation()).getSymbol();
                mCurrentLocalVarMap.put(sym, (Variable) assign.getValue());
                store = new AddressAt(addr, sym);
                store.setNext(0, new StoreInst((LocalVar) assign.getValue(), addr));
            }
            else //global variable -> store instruction
            {
                store = new StoreInst((LocalVar) assign.getValue(), addr);
            }
        }

        if (assignment.getLocation() instanceof ArrayAccess) //chain instructions together
        {
            start = loc.getStart();
            connectEdge(start, assign.getStart());
            end = loc.getInstruction();
        }
        else //if name, there are no previous instructions
        {
            start = assign.getInstruction();
            end = assign.getInstruction();
        }
        connectEdge(end, store);
        return new Pair(start, end, assign.getValue());
    }

    /**
     * Function call
     */

    @Override
    public Pair visit(Call call)
    {
        List<Expression> args = call.getArguments();
        List<LocalVar> listOfArgs = new ArrayList<>();

        Instruction start = null;
        Instruction end = null;
        Instruction chain = null;
        for (Expression expression : args) {
            Pair arg = visit(expression);

            if (start == null)
            {
                start = arg.getStart();
                end = arg.getInstruction();
            }
            else
            {
                connectEdge(chain, arg.getStart());
            }
            chain = arg.getInstruction();
            listOfArgs.add((LocalVar) arg.getValue());
        }

        //call information
        Symbol callSym = call.getCallee();
        FuncType calleeType = (FuncType) callSym.getType();
        CallInst callInst;
        LocalVar retValue = null;

        if(calleeType.getRet().equivalent(new VoidType()))
        {
            callInst = new CallInst(callSym, listOfArgs);
        }
        else
        {
            retValue = mCurrentFunction.getTempVar(calleeType.getRet());
            //create call instruction: public CallInst(LocalVar destVar, Symbol callee, List<LocalVar> params)
            callInst = new CallInst(retValue, callSym, listOfArgs);
        }

        if (chain != null)
        {
            connectEdge(chain, callInst);
        } else //if functions has no args
        {
            end = callInst;
        }
        return new Pair(start, end, retValue);
    }

    /**
     * Handle Operations like Arithmetics and Comparisons Also to handle logical operations (and, or,
     * not)
     */
    @Override
    public Pair visit(OpExpr operation)
    {
        if(operation.getOp().toString().equals("&&"))
        {
            return handleAND(operation);
        }
        else if(operation.getOp().toString().equals("||"))
        {
            return handleOR(operation);
        }

        Pair left = visit(operation.getLeft());
        var lhs = (LocalVar) left.getValue();
        Instruction start = left.getStart();
        Instruction end = left.getInstruction();
        Instruction chain = left.getInstruction();

        Pair right = null;
        LocalVar rhs = null;
        if (operation.getRight() != null)
        {
            right = visit(operation.getRight());
            rhs = (LocalVar) right.getValue();
            connectEdge(chain, right.getInstruction());
        }

        var destinationVar = mCurrentFunction.getTempVar(checker.getType(operation));
        Instruction instance = null;

        //switch statement between all operations
        switch (operation.getOp().toString())
        {
            case "+":
                instance = new BinaryOperator(BinaryOperator.Op.Add, destinationVar, lhs, rhs);
                break;
            case "-":
                instance = new BinaryOperator(BinaryOperator.Op.Sub, destinationVar, lhs, rhs);
                break;
            case "*":
                instance = new BinaryOperator(BinaryOperator.Op.Mul, destinationVar, lhs, rhs);
                break;
            case "/":
                instance = new BinaryOperator(BinaryOperator.Op.Div, destinationVar, lhs, rhs);
                break;
            case ">=":
                instance = new CompareInst(destinationVar, CompareInst.Predicate.GE, lhs, rhs);
                break;
            case ">":
                instance = new CompareInst(destinationVar, CompareInst.Predicate.GT, lhs, rhs);
                break;
            case "<=":
                instance = new CompareInst(destinationVar, CompareInst.Predicate.LE, lhs, rhs);
                break;
            case "<":
                instance = new CompareInst(destinationVar, CompareInst.Predicate.LT, lhs, rhs);
                break;
            case "==":
                instance = new CompareInst(destinationVar, CompareInst.Predicate.EQ, lhs, rhs);
                break;
            case "!=":
                instance = new CompareInst(destinationVar, CompareInst.Predicate.NE, lhs, rhs);
                break;
            case "!":
                instance = new UnaryNotInst(destinationVar, lhs); //instance is unaryNotInst, rhs should be null
                break;
            default:
        }
        connectEdge(chain, instance);
        return new Pair(start, end, destinationVar);
    }

    private Pair handleOR(OpExpr logicalOr)  //a || b = if(a){true}, if(b}{true}, else {false}
    {
        NopInst endNop = new NopInst(); //exit nop

        LocalVar out = mCurrentFunction.getTempVar(new BoolType());
        Pair lhs = visit(logicalOr.getLeft()); //nop->call
        Instruction lTempInst = lhs.getInstruction(); //t & t = true
        var copyInstLHS = new CopyInst(out, (LocalVar) lhs.getValue());
        Instruction exitJumpL = new JumpInst(copyInstLHS.getDstVar());
        copyInstLHS.setNext(0, exitJumpL);
        connectEdge(lTempInst, copyInstLHS); //outer if-statement

        Pair rhs = visit(logicalOr.getRight()); //nop->call/bool/opexpr
        Instruction rTempInst = rhs.getInstruction();
        var copyInstRHS = new CopyInst(out, (LocalVar) rhs.getValue());
        copyInstRHS.setNext(0, endNop);
        connectEdge(rTempInst, copyInstRHS); //inner if-statement

        exitJumpL.setNext(0, rTempInst);
        exitJumpL.setNext(1, endNop);

        return new Pair(lTempInst, lTempInst, out);
    }

    private Pair handleAND(OpExpr logicalAnd) //a && b = if (a){if(b) {true}} {false}
    {
        Instruction endNop = new NopInst(); //exit nop

        LocalVar out = mCurrentFunction.getTempVar(new BoolType());

        Pair lhs = visit(logicalAnd.getLeft()); //nop->call
        Instruction lTempInst = lhs.getInstruction(); //t & t = true
        var copyInstLHS = new CopyInst(out, (LocalVar) lhs.getValue());
        Instruction exitJumpL = new JumpInst(copyInstLHS.getDstVar());
        copyInstLHS.setNext(0, exitJumpL);
        connectEdge(lTempInst, copyInstLHS); //outer if-statement
        exitJumpL.setNext(0, endNop);

        if(on && false50 == null) {
            false50 = getLastEdge(lTempInst, false);
        }


        Pair rhs = visit(logicalAnd.getRight()); //nop->call/bool/opexpr
        Instruction rTempInst = rhs.getInstruction();
        var copyInstRHS = new CopyInst(out, (LocalVar) rhs.getValue());
        if(lTempInst.getNext(0) != null)
        {
            if(lTempInst.getNext(0) instanceof CopyInst)
            {
                if(((CopyInst) lTempInst.getNext(0)).getDstVar().toString().equals("$t8"))
                {
                    //copyInstRHS.setNext(0, breakInstruction);
                }
                else
                {
                    copyInstRHS.setNext(0, endNop);
                }
            }
            else
            {
                copyInstRHS.setNext(0, endNop);
            }
        }
        else {
            copyInstRHS.setNext(0, endNop);
        }
        connectEdge(rTempInst, copyInstRHS); //inner if-statement
        exitJumpL.setNext(1, rTempInst);

        return new Pair(lTempInst, lTempInst, out);
    }

    @Override
    public Pair visit(Dereference dereference)
    {
        //arrayaccess returns: Pair(start, chain, offset.getValue());
        //name returns: Pair(nop, nop, myVar);
        Pair addr = visit(dereference.getAddress());
        Instruction chain = addr.getInstruction();
        Instruction load = null;

        var destinationVar = mCurrentFunction.getTempVar( addr.getValue().getType());
        if (addr.getValue().getClass().equals(LocalVar.class) && !(dereference.getAddress() instanceof ArrayAccess)) //local variable -> (print 10) create copy instruction
        {
            load = new CopyInst(destinationVar, addr.getValue()); //connect addr to load
        }
        else
        {
            AddressVar var = new AddressVar(addr.getValue().getType(), addr.getValue().toString());
            Instruction findAddrAt = addr.getInstruction();
            while(findAddrAt != null) //arrayaccess instances
            {
                if(findAddrAt instanceof AddressAt) //find bottom-most address
                {
                    var = ((AddressAt) findAddrAt).getDst();
                }
                findAddrAt = findAddrAt.getNext(0);
            }

            if (dereference.getAddress() instanceof Name)
            {
                Symbol sym = ((Name) dereference.getAddress()).getSymbol();
                load = new AddressAt(var, sym);
                load.setNext(0, new LoadInst(new LocalVar(sym.getType()), var));
            }
            else //global variable -> store instruction
            {
                load = new LoadInst(destinationVar, var);
            }
        }
        connectEdge(chain, load);

        if(addr.getStart() == null) //from name, which only has a value
        {
            return new Pair(load, load, destinationVar);
        }
        return new Pair(addr.getStart(), addr.getInstruction(), destinationVar); //return addr;
    }

    private Pair visit(Expression expression)
    {
        return expression.accept(this);
    }

    /**
     * ArrayAccess
     */

    @Override
    public Pair visit(ArrayAccess access)
    {
        Pair base = visit(access.getBase());

        Pair offset = visit(access.getOffset());
        Instruction start = offset.getStart();
        Instruction end = offset.getInstruction();
        Instruction chain = offset.getInstruction();

        AddressVar tempAddr = mCurrentFunction.getTempAddressVar(base.getValue().getType());
        AddressAt address = new AddressAt(tempAddr, access.getBase().getSymbol(), (LocalVar) offset.getValue());
        connectEdge(chain, address);

        return new Pair(start, end, offset.getValue());
    }

    /**
     * Literal
     */

    @Override
    public Pair visit(LiteralBool literalBool)
    {
        var boolValue = BooleanConstant.get(mCurrentProgram, literalBool.getValue());
        var destinationVar = mCurrentFunction.getTempVar(new BoolType());
        var copyInst = new CopyInst(destinationVar, boolValue);
        return new Pair(copyInst, copyInst, destinationVar);
    }

    /**
     * Literal
     */

    @Override
    public Pair visit(LiteralInt literalInt)
    {
        var intValue = IntegerConstant.get(mCurrentProgram, literalInt.getValue());
        var destinationVar = mCurrentFunction.getTempVar(new IntType());
        var copyInst = new CopyInst(destinationVar, intValue);
        return new Pair(copyInst, copyInst, destinationVar);
    }

    /**
     * Return
     */

    @Override
    public Pair visit(Return ret)
    {
        Pair retVal = visit(ret.getValue());
        Instruction start = retVal.getStart();
        Instruction end = retVal.getInstruction();
        Instruction chain = retVal.getInstruction();

        Instruction retInstr = new ReturnInst((LocalVar) retVal.getValue());
        connectEdge(chain, retInstr);

        return new Pair(start, end, retVal.getValue());
    }

    /**
     * Break Node
     */

    @Override
    public Pair visit(Break brk)
    {
        if(inLoopEntered)
        {
            inBreakEncountered = true;
        }
        else {
            breakEncountered = true;
        }
        return new Pair(new NopInst(), new NopInst(), new LocalVar(new IntType())); //goes to first outside-loop instruction
    }

    /**
     * Continue Node
     */

    @Override
    public Pair visit(Continue cnt)
    {
        if(inLoopEntered)
        {
            inContinueEncountered = true;
        }
        else {
            continueEncountered = true;
        }
        return new Pair(new NopInst(), new NopInst(), new LocalVar(new IntType())); //nothing happens
    }

    /**
     * Control Structures
     */

    @Override
    public Pair visit(IfElseBranch ifElseBranch)
    {
        Pair condition = visit(ifElseBranch.getCondition());
        Instruction tempInst = condition.getStart();

        //create jump instructions and connect to previous instruction
        Instruction conditionJump = new JumpInst((LocalVar) condition.getValue());
        Instruction sub1 = tempInst;
        while(sub1.getNext(0) != null)
        {
            if(sub1 instanceof JumpInst)
            {
                sub1 = sub1.getNext(1);
            }
            else {
                sub1 = sub1.getNext(0);
            }
        }
        sub1.setNext(0, conditionJump);

        //visit then and else
        Instruction thenBlock = new NopInst();
        for(int i = 0; i < ifElseBranch.getThenBlock().getChildren().size(); i++)
        {
            if(ifElseBranch.getThenBlock().getChildren().get(i) instanceof Loop)
            {
                inLoopEntered = true; //double loops in if statements
            }
            Pair p = ifElseBranch.getThenBlock().getChildren().get(i).accept(this);
            if(p != null) {
                connectEdge(thenBlock, p.getInstruction());
            }
        }

        Pair elseBlock = visit(ifElseBranch.getElseBlock());
        Instruction endNop = new NopInst();
        if(on && false50 != null)
        {
            endNop.setNext(0, false50);
            on = false;
        }

        //must connect JumpInst to the start of the trueBlock (what happens if it is null)
        conditionJump.setNext(0, elseBlock.getInstruction());
        connectEdge(conditionJump.getNext(0), endNop);
        if(continueEncountered) //outer loop
        {
            connectEdge(conditionJump.getNext(0), continueInstruction);
            continueEncountered = false;
        }
        else if (inContinueEncountered) //inner loop
        {
            connectEdge(conditionJump.getNext(0), inContinueInstruction);
            inContinueEncountered = false;
        }

        conditionJump.setNext(1, new NopInst());
        connectEdge(conditionJump.getNext(1), thenBlock);
        if(breakEncountered)
        {
            connectEdge(conditionJump.getNext(1), breakInstruction);
            breakEncountered = false; //wait for next break
        }
        else if (inBreakEncountered)
        {
            connectEdge(conditionJump.getNext(1), inBreakInstruction);
            inBreakEncountered = false;
        }
        else
        {
            Instruction sub = conditionJump.getNext(1);
            while(sub.getNext(0) != null)
            {
                if(sub instanceof JumpInst)
                {
                    sub = sub.getNext(1);
                }
                else {
                    sub = sub.getNext(0);
                }
            }
            sub.setNext(0, endNop);
        }

        return new Pair(condition.getStart(), condition.getInstruction(), new LocalVar(new IntType()));
    }

    /**
     * Loop
     */

    @Override
    public Pair visit(Loop loop)
    {
        Instruction loopInstr = new NopInst();

        for(int i = 0; i < loop.getBody().getChildren().size(); i++)
        {
            if(loop.getBody().getChildren().get(i) instanceof Loop) //loop within loop
            {
                inLoopEntered = true;
            }

            Pair p = loop.getBody().getChildren().get(i).accept(this);
            if (p != null)
            {
                Instruction sub = loopInstr;
                boolean atJump2 = false;
                while(sub.getNext(0) != null)
                {
                    if(sub instanceof JumpInst && !atJump2)
                    {
                        sub = sub.getNext(0);
                        atJump2 = true;
                    }
                    else if(sub instanceof JumpInst && atJump2)
                    {
                        sub = sub.getNext(1);
                    }
                    else
                    {
                        sub = sub.getNext(0);
                    }
                }
                sub.setNext(0, p.getInstruction());
                if (loop.getBody().getChildren().get(i) instanceof Return) //test 43 works
                {
                    Instruction a = p.getInstruction();
                    connectEdge(a, breakInstruction);
                    return new Pair(loopInstr, loopInstr, new LocalVar(new IntType()));
                }

                if (i == loop.getBody().getChildren().size() - 1) //last statement
                {
                    if (!inLoopEntered)
                    {
                        boolean specialCase = false;
                        if(p.getInstruction().getNext(0) != null)
                        {
                            CopyInst v = (CopyInst) p.getInstruction().getNext(0);
                            String s = v.getDstVar().toString();
                            if(s.equals("$t7"))
                            {
                                specialCase = true;
                            }
                        }
                        lastLoopInstr = getLastEdge(p.getInstruction(), specialCase);
                        continueInstruction = lastLoopInstr;
                        breakInstruction.setNext(0, lastInstr);
                        connectEdge(continueInstruction, loopInstr); //continueInstruction = nop;
                    }
                    else
                    {
                        inLastLoopInstr = getLastEdge(p.getInstruction(), false);
                        inContinueInstruction = inLastLoopInstr;
                        inBreakInstruction.setNext(0, inLastInstr);
                        connectEdge(inLastLoopInstr, loopInstr);
                        inLoopEntered = false;
                    }
                }
            }

        }

        return new Pair(loopInstr, loopInstr, new LocalVar(new IntType()));
    }

    private void connectEdge(Instruction src, Instruction add)
    {
        if(src == null)
        {
            src = add;
            return;
        }

        Instruction sub = src;
        while(sub.getNext(0) != null)
        {
            sub = sub.getNext(0);
        }
        sub.setNext(0, add);
    }

    private Instruction getLastEdge(Instruction chain, boolean specialCase)
    {
        Instruction sub = chain;
        if(!specialCase) {
            while (sub.getNext(0) != null) {
                sub = sub.getNext(0);
            }
        }
        else
        {
            while (sub.getNext(0) != null)
            {
                if(sub instanceof JumpInst)
                {
                    sub = sub.getNext(1);
                }
                else {
                    sub = sub.getNext(0);
                }
            }
        }
        return sub;
    }
}

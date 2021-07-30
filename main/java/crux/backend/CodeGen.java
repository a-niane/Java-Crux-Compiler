package crux.backend;

import crux.frontend.Symbol;
import crux.midend.ir.core.*;
import crux.midend.ir.core.insts.*;
import crux.printing.IRValueFormatter;

import java.util.*;

/**
 * Convert the CFG into Assembly Instructions
 */
public final class CodeGen extends InstVisitor {
  private final IRValueFormatter irFormat = new IRValueFormatter();

  private final Program p;
  private final CodePrinter out;

  private HashMap<Variable, Integer> varIndexMap = new HashMap<>(); //tracks variables and respective offsets
  private int varIndex = 0; //number of local variables
  private final String[] callingRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"}; //registers for call
  private HashMap<Instruction, String> labels = new HashMap<>(); //function labels (for jump)
  private int highstack = 2; //for enter statement
  private int maxStack = 0;

  public CodeGen(Program p) {
    this.p = p;
    // Do not change the file name that is outputted or it will
    // break the grader!

    out = new CodePrinter("a.s"); //send help
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  public void genCode()
  {
    // This function should generate code the entire program
    for (Iterator<GlobalDecl> glob_it = p.getGlobals(); glob_it.hasNext();)
    {
      GlobalDecl g = glob_it.next();

      Symbol sym = g.getSymbol();
      Constant con = g.getNumElement();
      IntegerConstant i = (IntegerConstant) con;

      out.printCode(".comm " + sym.getName() + ", " + (i.getValue()*8) + ", 8"); //allocate space on stack using instruction
    }

    for (Iterator<Function> func_it = p.getFunctions(); func_it.hasNext(); ) {
      Function f = func_it.next();
      genCode(f);
      out.printCode(""); //new line dividing multiple functions
    }

    out.close();
  }

  private int labelcount = 1;

  private String getNewLabel()
  {
    return "L" + (labelcount++);
  }

  private void genCode(Function f)
  {
    //1. Assign labels for jump targets
    varIndexMap = new HashMap<>();
    varIndex = 0;
    labels = assignLabels(f);

    //2. Declare functions and print label
    out.printCode(".globl " + f.getName());
    out.printLabel(f.getName() + ":");

    //3. Generate code for arguments (move the values from args regs [%rdi, %rsi, %rdx, %rcx, %r8 and %r9] to stack)
    int argIndex = 0;
    for (LocalVar var : f.getArguments())
    {
      if (argIndex < callingRegs.length)
      {
        //moving register to variable
        out.bufferCode("movq " + callingRegs[argIndex] + ", -" + (8 * (argIndex+1)) + "(%rbp)"); //reg to var
      }
      else
      {
        //NOTE: For extra args, store them on the top of the stack frame.
        out.bufferCode("movq " + (8 * highstack) + "(%rbp), %r10");
        out.bufferCode("movq %r10, -" + (8 * (argIndex+1)) + "(%rbp)"); //moving register to variable
        highstack++;
      }
      varIndex++; //new local variable for dst
      varIndexMap.put(var, varIndex);
      argIndex++;
    }

    //4. Generate instructions for the functions body
    Stack<Instruction> tovisit = new Stack<>();
    HashSet<Instruction> alreadyVisited = new HashSet<>();
    if (f.getStart() != null)
    {
      tovisit.push(f.getStart());
    }

    varIndex = argIndex;

    while (!tovisit.isEmpty())
    {
      Instruction inst = tovisit.pop();

      if(labels.containsKey(inst) && !alreadyVisited.contains(inst)) //jump labels that haven't been visited
      {
        out.bufferLabel(labels.get(inst) + ":");
      }

      inst.accept(this);
      alreadyVisited.add(inst); //complete visit of instruction

      Instruction first = shredNops(inst.getNext(0));
      Instruction second = shredNops(inst.getNext(1));

      if(second != null && !alreadyVisited.contains(second)) //L1: -> jmp L2
      {
        tovisit.push(second); //visit true block first
      }

      if(first != null && !alreadyVisited.contains(first)) //L2 -> leave, ret
      {
        tovisit.push(first);
      }
      else if(first != null && !(inst instanceof ReturnInst) && labels.containsKey(first) && (tovisit.isEmpty() || (tovisit.peek() != first))) //"L1 + l2" unite
      {
        out.bufferCode("jmp " + labels.get(first));
      }
      else if (!(inst instanceof ReturnInst))
      {
        out.bufferCode("leave");
        out.bufferCode("ret");
      }
    }

    //5. Need stack to be 16 byte aligned
    if(maxStack > 0) {
      varIndex += maxStack;
    }

    int ceil = varIndex;
    if (varIndex % 2 == 1)
    {
      ceil++;
    }

    //6. Emit functions prologue
    out.printCode("enter $(8 * " + ceil + "), $0"); // the number of variables plus space on stack we need for calling methods.
    out.outputBuffer(); //save all visits as buffers
    labels = null;
  }


  /**
   * Assigns Labels to any Instruction that might be the target of a conditional or unconditional
   * jump.
   */

  private HashMap<Instruction, String> assignLabels(Function f) {
    HashMap<Instruction, String> labelMap = new HashMap<>();
    Stack<Instruction> tovisit = new Stack<>();
    HashSet<Instruction> discovered = new HashSet<>();
    if (f.getStart() != null)
      tovisit.push(f.getStart());
    while (!tovisit.isEmpty()) {
      Instruction inst = tovisit.pop();

      for (int childIdx = 0; childIdx < inst.numNext(); childIdx++) {
        Instruction child = inst.getNext(childIdx);
        if (discovered.contains(child)) {
          // Found the node for a second time...need a label for merge points
          if (!labelMap.containsKey(child)) {
            labelMap.put(child, getNewLabel());
          }
        } else {
          discovered.add(child);
          tovisit.push(child);
          // Need a label for jump targets also
          if (childIdx == 1 && !labelMap.containsKey(child)) {
            labelMap.put(child, getNewLabel());
          }
        }
      }
    }
    return labelMap;
  }

  public void visit(AddressAt i) //%t0 = addressAt %x, $t0 or "%av0 = addressAt i, null"
  {
    out.bufferLabel("/*AddressAt*/");

    if(i.getOffset() != null) //"%av0 = addressAt i, null (localVar)
    {
      out.bufferCode("movq -" + (8*varIndex) + "(%rbp), %r11");
      out.bufferCode("movq $8, %r10");
      out.bufferCode("imul %r10, %r11");
    }

    if(i.getOffset() != null)
    {
      out.bufferCode("movq " + i.getBase().getName() + "@GOTPCREL(%rip), %r10");
      out.bufferCode("addq %r10, %r11");
    }
    else //global variables
    {
      out.bufferCode("movq " + i.getBase().getName() + "@GOTPCREL(%rip), %r11");
    }

    varIndex++; //new local variable for dst
    varIndexMap.put(i.getDst(), varIndex);
    out.bufferCode("movq %r11, -" + (8 * varIndexMap.get(i.getDst())) + "(%rbp)");
  }

  public void visit(BinaryOperator i) //$t10 = $t8 + $t9
  {
    out.bufferLabel("/*BinaryOperator*/");

    int rhsOffset = varIndexMap.get(i.getRightOperand());
    int lhsOffset = varIndexMap.get(i.getLeftOperand());

    switch(i.getOperator())
    {
      case Add:
        out.bufferCode("movq -" + (8 * lhsOffset) + "(%rbp), %r10");
        out.bufferCode("addq -" + (8 * rhsOffset) + "(%rbp), %r10");
        break;

      case Sub:
        out.bufferCode("movq -" + (8 * lhsOffset) + "(%rbp), %r10");
        out.bufferCode("subq -" + (8 * rhsOffset) + "(%rbp), %r10");
        break;

      case Mul:
        out.bufferCode("movq -" + (8 * lhsOffset) + "(%rbp), %r10");
        out.bufferCode("imulq -" + (8 * rhsOffset) + "(%rbp), %r10");
        break;

      case Div:
        out.bufferCode("movq -" + (8 * lhsOffset) + "(%rbp), %rax");
        out.bufferCode("cqto");
        out.bufferCode("idivq -" + (8 * rhsOffset) + "(%rbp)");
        break;
    }

    varIndex++; //new local variable for dst
    varIndexMap.put(i.getDst(), varIndex);
    if(i.getOperator() != BinaryOperator.Op.Div)
    {
      out.bufferCode("movq %r10, -" + (8 * varIndexMap.get(i.getDst())) + "(%rbp)");
    }
    else
    {
      out.bufferCode("movq %rax, -" + (8* varIndexMap.get(i.getDst()) + "(%rbp)"));
    }
  }

  public void visit(CompareInst i) //$t4 = $t2 >= $t3
  {
    out.bufferLabel("/*CompareInst*/");

    int rhsOffset = varIndexMap.get(i.getRightOperand());
    int lhsOffset = varIndexMap.get(i.getLeftOperand());

    out.bufferCode("movq $0, %r10");
    out.bufferCode("movq $1, %rax");

    out.bufferCode("movq -" + (8 * lhsOffset) + "(%rbp), %r11");
    out.bufferCode("cmp -" + (8 * rhsOffset) + "(%rbp), %r11");

    switch(i.getPredicate())
    {
      case EQ:
        out.bufferCode("cmove %rax, %r10");
        break;

      case NE:
        out.bufferCode("cmovne %rax, %r10");
        break;

      case LT:
        out.bufferCode("cmovl %rax, %r10");
        break;

      case LE:
        out.bufferCode("cmovle %rax, %r10");
        break;

      case GT:
        out.bufferCode("cmovg %rax, %r10");
        break;

      case GE:
        out.bufferCode("cmovge %rax, %r10");
        break;
    }

    varIndex++; //new local variable for dst
    varIndexMap.put(i.getDst(), varIndex);
    out.bufferCode("movq %r10, -" + (8 * varIndexMap.get(i.getDst())) + "(%rbp)");
  }

  public void visit(CopyInst i)
  {
    LocalVar dst = i.getDstVar();
    Value src = i.getSrcValue();

    out.bufferLabel("/*CopyInst*/");

    if(src instanceof Variable) //already saved somewhere in stack
    {
      if(varIndexMap.containsKey(src))
      {
        out.bufferCode("movq -" + (8 * varIndexMap.get(src)) + "(%rbp), %r10");
      }
      else
      {
        out.bufferCode("movq -" + (8 * varIndex) + "(%rbp), %r10");
      }
    }
    else
    {
      if (src instanceof BooleanConstant)
      {
        if (((BooleanConstant) src).getValue()) //0 and 1 to represent true and false
        {
          out.bufferCode("movq $1, %r10");
        }
        else
        {
          out.bufferCode("movq $0, %r10");
        }
      }
      else
      {
        out.bufferCode("movq $" + ((IntegerConstant) src).getValue() + ", %r10");
      }
    }

    if(varIndexMap.containsKey(dst))
    {
      out.bufferCode("movq %r10, -" + (varIndexMap.get(dst) * 8) + "(%rbp)");
    }
    else
    {
      varIndex++;
      out.bufferCode("movq %r10, -" + (varIndex * 8) + "(%rbp)");
      varIndexMap.put(dst, varIndex); //keeping track of local variable
    }
  }


  public void visit(JumpInst i) //jump $t4
  {
    out.bufferLabel("/*JumpInst*/");
    out.bufferCode("movq -" + (8 * varIndexMap.get(i.getPredicate())) + "(%rbp), %r10");
    out.bufferCode("cmp $1, %r10");
    out.bufferCode("je " + labels.get(i.getNext(1)));
  }

  public void visit(LoadInst i) //"$t3 = load %av1"
  {
    out.bufferLabel("/*LoadInst*/");

    out.bufferCode("movq -" + (8 * varIndexMap.get(i.getSrcAddress())) + "(%rbp), %r10");
    out.bufferCode("movq 0(%r10), %r10");

    varIndex++; //new local variable for dst
    varIndexMap.put(i.getDst(), varIndex);
    out.bufferCode("movq %r10, -" + (8 * varIndexMap.get(i.getDst())) + "(%rbp)");
  }

  public void visit(NopInst i) //"nop"
  {
    out.bufferLabel("/*NopInst*/");
  }

  public void visit(StoreInst i) //"store $t0, %av0"
  {
    out.bufferLabel("/*StoreInst*/");
    out.bufferCode("movq -" + (8 * varIndexMap.get(i.getSrcValue())) + "(%rbp), %r10");
    out.bufferCode("movq -" +  (8 * varIndexMap.get(i.getDestAddress())) + "(%rbp), %r11");
    out.bufferCode("movq %r10, 0(%r11)");
  }

  public void visit(ReturnInst i) //return $t87
  {
    out.bufferLabel("/*ReturnInst*/");
    if(varIndexMap.get(i.getReturnValue()) != null)
    {
      out.bufferCode("movq -" + (8 * varIndexMap.get(i.getReturnValue())) + "(%rbp), %rax");
    }
    else
    {
      out.bufferCode("movq -" + (8 * varIndex) + "(%rbp), %rax");
    }
    out.bufferCode("leave");
    out.bufferCode("ret");
  }


  public void visit(CallInst i) // call $t5 = Symbol(readChar:func(TypeList()):int) ()
  {
    out.bufferLabel("/*CallInst*/");

    //Pass arguments to callee
    int argIndex = 0;
    for (Value var : i.getParams())
    {
      if (argIndex < callingRegs.length)
      {
        if (varIndexMap.containsKey(var))
        {
          out.bufferCode("movq -" + (8 * varIndexMap.get(var)) + "(%rbp), " + callingRegs[argIndex]);
        }
        else
        {
          out.bufferCode("movq -" + (8 * (varIndex + argIndex)) + "(%rbp), " + callingRegs[argIndex]);
        }
      }
      else //move registers on the stack (offset of rsp)
      {
        out.bufferCode("movq -" + (8 * varIndexMap.get(var)) + "(%rbp)" + ", %r10");
        int index = argIndex - callingRegs.length;
        out.bufferCode("movq %r10, " + (8 * index) + "(%rsp)");
      }
      argIndex++;
      //NOTE: Check if need to update space on stack (maximum num. of stack parms) we need for calling methods.
    }
    maxStack = argIndex - 6;

    //Generate code for Call instruction
    Symbol callee = i.getCallee();
    out.bufferCode("call " + callee.getName());

    //Get return value (if there is one) into return register
    if(i.getDst() != null)
    {
      varIndex++;
      varIndexMap.put(i.getDst(), varIndex);
      out.bufferCode("movq %rax, -" + (8 * varIndexMap.get(i.getDst())) + "(%rbp)");
    }
  }

  public void visit(UnaryNotInst i) //"$t3 = not $t2"
  {
    out.bufferLabel("/*UnaryNotInst*/");

    out.bufferCode("movq $1, %r10");
    out.bufferCode("subq -" + (8 * varIndexMap.get(i.getInner())) + "(%rbp), %r10");

    if(i.getDst() != null && !varIndexMap.containsKey(i.getDst()))
    {
      varIndex++;
      varIndexMap.put(i.getDst(), varIndex);
    }
    out.bufferCode("movq %r10, -" + (8 * varIndexMap.get(i.getDst())) + "(%rbp)");
  }

  private Instruction shredNops(Instruction i) //gets rid of unnecessary nops in instructions
  {
    if(i instanceof NopInst && !labels.containsKey(i))
    {
      Instruction chain = i;
      while(chain.getNext(0) != null && chain.getNext(0) instanceof NopInst && !labels.containsKey(chain.getNext(0)))
      {
        chain = chain.getNext(0);
      }
      return chain;
    }
    else
    {
      return i;
    }
  }
}

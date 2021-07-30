grammar Crux;
program
 : declarationList EOF //beginning of the program, recursion stops here
 ;

declarationList
 : declaration* //list of declarations (such as variables, arrays, functions)
 ;

declaration
 : variableDeclaration //int
 | arrayDeclaration //bool[]
 | functionDefinition //void print
 ;

variableDeclaration
 : type Identifier ';' //example would be "int x;"
 ;

type
 : Identifier //int, void, bool, etc.
 ;

//Crux Grammar in ANTLR Syntax
literal //only consists of these primitives
 : Integer
 | True
 | False
 ;
    //additions
designator : Identifier ( '[' expression0 ']' )?; //example would be int x = new Integer () bool[2] = true;

//operations take precedence: op0 operations performed before op1 and op2
op0 : '>=' | '<=' | '!=' | '==' | '<' | '>'; //logic/comparison
op1 : '+' | '-' | '||';
op2 : '*' | '/' | '&&';

expression0 : expression1 ( op0 expression1 )?; //expression0 are expressions that stand alone or can be compared

expression1 : expression2 | expression1 op1 expression2; //example is "3" or "4 >= 3"
expression2 : expression3 | expression2 op2 expression3; //example is "4 >= 3" or "1 == 2 == 3"
expression3 : '!' expression3 | '(' expression0 ')' | designator | callExpression | literal; //examples "!booleanChecker" or "(true)" or !false

callExpression : Identifier '(' expressionList ')'; //add function called -> add(1, 2, 3)
expressionList : ( expression0 ( ',' expression0 )* )?; //(1, 2, 3)

parameter : type Identifier; //int age, bool male, etc.
parameterList : ( parameter ( ',' parameter )* )?; //based on above (18, true, ...)

arrayDeclaration : type Identifier '[' Integer ']' ';'; //int [4];
functionDefinition : type Identifier '(' parameterList ')' statementBlock; //int add (int a, int b, int c) {};

assignmentStatement : designator '=' expression0 ';'; //bool name = 4 != 3;
callStatement : callExpression ';';
ifStatement : 'if' expression0 statementBlock ( 'else' statementBlock )?; //"if (true) {}" or "if (true) {} else {}";
loopStatement : 'loop' statementBlock; //while (true) {};
breakStatement : 'break' ';'; //called to exit loops
continueStatement : 'continue' ';'; //called to contine statements
returnStatement : 'return' expression0 ';'; //returns value and stops program
statement : variableDeclaration
           | callStatement
           | assignmentStatement
           | ifStatement
           | loopStatement
           | breakStatement
           | continueStatement
           | returnStatement; //various statements
statementList : ( statement )*; //{int x = 4; bool male = true; if (male) {}; etc.
statementBlock : '{' statementList '}'; //anything under {} is part of this block

//Reserved Keywords
SemiColon: ';';
    //additions
OpenParen:    '('; //parentheses usually used for mathmatical ordering or loops and if-statements
CloseParen: ')';
OpenBrace: '{'; //braces used for statement blocks
CloseBrace: '}';
OpenBracket: '['; //brackets used for arrays and accessing indeces
CloseBracket: ']';
Add: '+';
Sub: '-';
Mult: '*';
Div: '/';
GreaterEqual: '>=';
LesserEqual: '<=';
NotEqual: '!=';
Equal: '==';
GreaterThan: '>';
LessThan: '<';
Assign: '=';
Comma: ',';

//Special Character Sequences
True: 'true';
False: 'false';
    //additions
And: '&&';
Or: '||';
Not: '!';
If: 'if';
Else: 'else';
Loop: 'loop';
Continue: 'continue'; //used for loops to continue iteration
Break: 'break'; //stops loops
Return: 'return'; //returns value and ends program

Integer //integer consists on one mandatory digit, and 0 or more digits proceeding it
 : '0'
 | [1-9] [0-9]*
 ;

Identifier //identifiers must start with one letter, then proceeded by other letters, numbers, and _
 : [a-zA-Z] [a-zA-Z0-9_]*
 ;

WhiteSpaces //white spaces are ignored to prevent confusion
 : [ \t\r\n]+ -> skip
 ;

Comment //comments ignored to prevent confusion
 : '//' ~[\r\n]* -> skip
 ;

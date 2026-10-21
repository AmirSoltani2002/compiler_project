package main.visitor.type;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.*;
import main.ast.nodes.expression.value.*;
import main.ast.nodes.expression.value.primitive.*;
import main.ast.nodes.statement.*;
import main.ast.type.*;
import main.ast.type.primitiveType.*;
import main.compileError.CompileError;
import main.compileError.typeErrors.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.*;
import main.symbolTable.item.*;
import main.visitor.Visitor;

import java.util.*;

public class TypeChecker extends Visitor<Type> {
    public ArrayList<CompileError> typeErrors = new ArrayList<>();
    private Type currentFunctionReturnType;
    private Set<Type> retTypes = new HashSet<Type>() ;

    @Override
    public Type visit(Program program){
        SymbolTable.root = new SymbolTable();
        SymbolTable.top = new SymbolTable();
        for(FunctionDeclaration functionDeclaration : program.getFunctionDeclarations()){
            FunctionItem functionItem = new FunctionItem(functionDeclaration);
            try {
                SymbolTable.root.put(functionItem);
            }catch (ItemAlreadyExists ignored){}
        }
        for(PatternDeclaration patternDeclaration : program.getPatternDeclarations()){
            PatternItem patternItem = new PatternItem(patternDeclaration);
            try{
                SymbolTable.root.put(patternItem);
            }catch (ItemAlreadyExists ignored){}
        }
        program.getMain().accept(this);

        return null;
    }
    @Override
    public Type visit(FunctionDeclaration functionDeclaration){
//        System.out.println(1);

        SymbolTable.push(new SymbolTable());
        retTypes.clear();
      //  System.out.println(1);

        try {
         //   System.out.println(1);
            FunctionItem functionItem = (FunctionItem) SymbolTable.root.getItem(FunctionItem.START_KEY +
                    functionDeclaration.getFunctionName().toString().substring(11));
            ArrayList<Type> currentArgTypes = functionItem.getArgumentTypes();

            //
            // System.out.println(functionDeclaration.getArgs().size());

            for (int i = 0; i < functionDeclaration.getArgs().size(); i++) {
                VarItem argItem = new VarItem(functionDeclaration.getArgs().get(i).getName());
                if (i >= currentArgTypes.size()) {
                    argItem.setType(functionDeclaration.getArgs().get(i).getDefaultVal().accept(this));
                }
                else
                    argItem.setType(currentArgTypes.get(i));
                //System.out.println(currentArgTypes.get(i));
                try {
                    SymbolTable.top.put(argItem);
                }catch (ItemAlreadyExists ignored){}
            }
        }catch (ItemNotFound ignored){}
        for(Statement statement : functionDeclaration.getBody())
        {
            statement.accept(this);}
        for(Statement statement: functionDeclaration.getBody())

            if (statement instanceof  ReturnStatement)
                retTypes.add(((ReturnStatement) statement).getReturnExp().accept(this));
        SymbolTable.pop();
        //TODO:Figure out whether return types of functions are not the same.
        if (retTypes.size() != 1){
            typeErrors.add(new FunctionIncompatibleReturnTypes(functionDeclaration.getLine(), functionDeclaration.getFunctionName().getName()));
            return  new NoType();
        }
        //TODO:Return the infered type of the function
        return retTypes.toArray(new Type[retTypes.size()])[0];
    }
    @Override
    public Type visit(PatternDeclaration patternDeclaration){
        SymbolTable.push(new SymbolTable());
        try {
            PatternItem patternItem = (PatternItem) SymbolTable.root.getItem(PatternItem.START_KEY +
                    patternDeclaration.getPatternName().getName());
            VarItem varItem = new VarItem(patternDeclaration.getTargetVariable());
            varItem.setType(patternItem.getTargetVarType());
            try {
                SymbolTable.top.put(varItem);
            }catch (ItemAlreadyExists ignored){}
            for(Expression expression : patternDeclaration.getConditions()){
                if(!(expression.accept(this) instanceof BoolType)){
                    typeErrors.add(new ConditionIsNotBool(expression.getLine()));
                    SymbolTable.pop();
                    return new NoType();
                }
            }
        //TODO:1-figure out whether return expression of different cases in pattern are of the same type/2-return the infered type
        }catch (ItemNotFound ignored){}
        SymbolTable.pop();
        return null;
    }
    @Override
    public Type visit(MainDeclaration mainDeclaration){
        for (Statement stmt : mainDeclaration.getBody()) {
            stmt.accept(this);
        }
        return null;
    }
    @Override
    public Type visit(AccessExpression accessExpression){
        if(accessExpression.isFunctionCall()) {
            ArrayList<Type> funcArgumentTypes = new ArrayList<>();
            for (Expression argumentExpr: accessExpression.getArguments()){
                funcArgumentTypes.add(argumentExpr.accept(this));
            }
            try{
                //System.out.println(FunctionItem.START_KEY +
                 //       accessExpression.getAccessedExpression().toString().substring(11)) ;

                FunctionItem functionItem = (FunctionItem) SymbolTable.root.getItem(FunctionItem.START_KEY +
                        accessExpression.getAccessedExpression().toString().substring(11));
                functionItem.setArgumentTypes(funcArgumentTypes);
                ArrayList<Type> curArgTypes = functionItem.getArgumentTypes();
                ArrayList<Expression> arg = accessExpression.getArguments();
                //System.out.println(arg.size());
               //System.out.println(curArgTypes);
                if(curArgTypes.size() != arg.size()){
                   // System.out.println(1);

                    typeErrors.add(new LenArgumentTypeMisMatch(accessExpression.getLine()));
                    return new NoType();
                }
              for (int i = 0; i < arg.size(); i++) {
                  //System.out.println(1);

                  if(!curArgTypes.get(i).sameType(arg.get(i).accept(this))){

                        typeErrors.add(new LenArgumentTypeMisMatch(accessExpression.getLine()));
                        return new NoType();
                    }
                }
                //System.out.println(1);
                return functionItem.getFunctionDeclaration().accept(this);
            }catch (ItemNotFound ignored){}

        }
        else {
            for (Expression accessExpr : accessExpression.getDimentionalAccess()) {
                if (!(accessExpr.accept(this) instanceof IntType)) {
                    typeErrors.add(new AccessIndexIsNotInt(accessExpression.getLine()));
                    return new NoType();
                }
            }
            Type accessedType = accessExpression.getAccessedExpression().accept(this);

            if (accessedType instanceof StringType) {
                return new StringType();
            } else if (accessedType instanceof ListType) {
                ListType listType = (ListType) accessedType;
                return listType.getType();
            } else {
                typeErrors.add(new IsNotIndexable(accessExpression.getLine()));
                return new NoType();
            }
            //TODO:index of access list must be int
        }
        return new NoType();
    }

    @Override
    public Type visit(ReturnStatement returnStatement){
        // TODO:Visit return statement.Note that return type of functions are specified here
        retTypes.add(returnStatement.getReturnExp().accept(this));
        return new NoType();
    }
    @Override
    public Type visit(ExpressionStatement expressionStatement){
        return expressionStatement.getExpression().accept(this);

    }
    @Override
    public Type visit(ForStatement forStatement){
        SymbolTable.push(SymbolTable.top.copy());
        //System.out.println(forStatement.getRangeExpression().accept(this));
        VarItem varItem = new VarItem(forStatement.getIteratorId());
        if(forStatement.getRangeExpression().accept(this) instanceof NoType)
            return null;
        varItem.setType(forStatement.getRangeExpression().accept(this));




        try{
            SymbolTable.top.put(varItem);
        }catch (ItemAlreadyExists ignored){
        }

        for(Statement statement : forStatement.getLoopBodyStmts())
        {
            statement.accept(this);}
        SymbolTable.pop();
        return null;
    }
    @Override
    public Type visit(IfStatement ifStatement){
        SymbolTable.push(SymbolTable.top.copy());
        for(Expression expression : ifStatement.getConditions())
            if(!(expression.accept(this) instanceof BoolType))
                typeErrors.add(new ConditionIsNotBool(expression.getLine()));
        for(Statement statement : ifStatement.getThenBody())
            statement.accept(this);
        for(Statement statement : ifStatement.getElseBody())
            statement.accept(this);
        SymbolTable.pop();
        return new NoType();
    }
    @Override
    public Type visit(LoopDoStatement loopDoStatement){
        SymbolTable.push(SymbolTable.top.copy());
        for(Statement statement : loopDoStatement.getLoopBodyStmts())
            statement.accept(this);
        SymbolTable.pop();
        return new NoType();
    }
    @Override
    public Type visit(AssignStatement assignStatement){
        if(assignStatement.isAccessList()){
            VarItem newVarItem = new VarItem(assignStatement.getAssignedId());
            newVarItem.setType(assignStatement.getAccessListExpression().accept(this));
//            try {
//               SymbolTable.top.put(newVarItem);
//            }catch (ItemAlreadyExists ignored){}
        }
        else{
            VarItem newVarItem = new VarItem(assignStatement.getAssignedId());
            //System.out.println(assignStatement.getAssignedId());
            //System.out.println(assignStatement.getAssignExpression().accept(this));
            newVarItem.setType(assignStatement.getAssignExpression().accept(this));
            try {
                SymbolTable.top.put(newVarItem);
            }catch (ItemAlreadyExists ignored){}
        }

        return new NoType();
    }
    @Override
    public Type visit(BreakStatement breakStatement){
        for(Expression expression : breakStatement.getConditions())
            if(!((expression.accept(this)) instanceof BoolType))
                typeErrors.add(new ConditionIsNotBool(expression.getLine()));

        return null;
    }
    @Override
    public Type visit(NextStatement nextStatement){
        for(Expression expression : nextStatement.getConditions())
            if(!((expression.accept(this)) instanceof BoolType))
                typeErrors.add(new ConditionIsNotBool(expression.getLine()));

        return null;
    }
    @Override
    public Type visit(PushStatement pushStatement){
        return new NoType();
    }
    @Override
    public Type visit(PutStatement putStatement){
        //TODO:visit putStatement

        return new NoType();

    }
    @Override
    public Type visit(BoolValue boolValue){
        return new BoolType();
    }
    @Override
    public Type visit(IntValue intValue){
        return new IntType();
    }
    @Override
    public Type visit(FloatValue floatValue){return new FloatType();}
    @Override
    public Type visit(StringValue stringValue){
        return new StringType();
    }
    @Override
    public Type visit(ListValue listValue){
        Type PreExp = new NoType();
        for(Expression exp : listValue.getElements())
            if(PreExp instanceof NoType)
                PreExp = exp.accept(this);
            else if (!(PreExp.equals(exp.accept(this)))) {
                typeErrors.add(new ListElementsTypesMisMatch(exp.getLine()));
                return new NoType();
            }

        return new ListType(PreExp);
    }
    @Override
    public Type visit(FunctionPointer functionPointer){
        return new FptrType(functionPointer.getId().getName());
    }
    @Override
    public Type visit(AppendExpression appendExpression){
        Type appendeeType = appendExpression.getAppendee().accept(this);
        if (appendeeType instanceof ListType)
        {
            for (Expression appended: appendExpression.getAppendeds()){
                if (!(appended.accept(this).sameType(((ListType) appendeeType).getType()))){
                    typeErrors.add(new AppendTypesMisMatch(appendExpression.getLine()));
                    return new NoType();
                }
            }
        }
        else  if(appendeeType instanceof StringType){
            for (Expression appended: appendExpression.getAppendeds()){
                if (!(appended.accept(this) instanceof  StringType)){
                    typeErrors.add(new AppendTypesMisMatch(appendExpression.getLine()));
                    return new NoType();
                }
            }

        }
        else {
            typeErrors.add(new IsNotAppendable(appendExpression.getLine()));
            return  new NoType();
        }
        return appendeeType;
    }



    @Override
    public Type visit(BinaryExpression binaryExpression) {
        Type leftType = binaryExpression.getFirstOperand().accept(this);
        Type rightType = binaryExpression.getSecondOperand().accept(this);
        if (binaryExpression.getOperator().name().equals("PLUS") ||
                binaryExpression.getOperator().name().equals("MINUS") ||
                binaryExpression.getOperator().name().equals("MULT") ||
                binaryExpression.getOperator().name().equals("DIVIDE")) {

                if (leftType instanceof FloatType && rightType instanceof FloatType) {
                    return new FloatType();
                } else if (leftType instanceof IntType && rightType instanceof IntType) {
                    return new IntType();
                }
             else {
                typeErrors.add(new NonSameOperands(binaryExpression.getLine(), binaryExpression.getOperator()));
                return new NoType();
            }
        } else if (binaryExpression.getOperator().name().equals("EQUAL") ||
                binaryExpression.getOperator().name().equals("NOT_EQUAL") ||
                binaryExpression.getOperator().name().equals("GREATER_THAN") ||
                binaryExpression.getOperator().name().equals("LESS_THAN") ||
                binaryExpression.getOperator().name().equals("LESS_EQUAL_THAN") ||
                binaryExpression.getOperator().name().equals("GREATER_EQUAL_THAN")) {
            if (leftType instanceof IntType && rightType instanceof IntType) {

                return new BoolType();
            } else {
                typeErrors.add(new NonSameOperands(binaryExpression.getLine(), binaryExpression.getOperator()));
                return new NoType();
            }
        } else {
            typeErrors.add(new UnsupportedOperandType(binaryExpression.getLine(), binaryExpression.getOperator().name()));
            return new NoType();
        }
    }
    @Override
    public Type visit(UnaryExpression unaryExpression){
        Type unaryType = unaryExpression.getExpression().accept(this);
        if (unaryType instanceof IntType && (unaryExpression.getOperator().name().equals("DEC") || unaryExpression.getOperator().name().equals("INC"))){
            return unaryType;
        }
        if ((unaryType instanceof IntType || unaryType instanceof FloatType) && unaryExpression.getOperator().name().equals("MINUS")){
            return unaryType;
        }
        if (unaryType instanceof BoolType && unaryExpression.getOperator().name().equals("NOT")){
            return unaryType;
        }
        typeErrors.add(new UnsupportedOperandType(unaryExpression.getLine(), unaryExpression.getOperator().name()));
        return new NoType();
    }
    @Override
    public Type visit(ChompStatement chompStatement){
        if (!(chompStatement.getChompExpression().accept(this) instanceof StringType)) {
            typeErrors.add(new ChompArgumentTypeMisMatch(chompStatement.getLine()));
            return new NoType();
        }

        return new StringType();
    }
    @Override
    public Type visit(ChopStatement chopStatement){
        if (!(chopStatement.getChopExpression().accept(this) instanceof StringType)) {
            typeErrors.add(new ChopArgumentTypeMisMatch(chopStatement.getLine()));
            return new NoType();
        }
        return new StringType();
    }
    @Override
    public Type visit(Identifier identifier){
        try {
            VarItem varItem = (VarItem) SymbolTable.top.getItem(VarItem.START_KEY + identifier.getName());
            return varItem.getType();
        } catch (ItemNotFound ignored) {}
        return new NoType();
    }
    @Override
    public Type visit(LenStatement lenStatement){
        Type exprType = lenStatement.getExpression().accept(this);
        if (!(exprType instanceof ListType) && !(exprType instanceof StringType)) {
            typeErrors.add(new LenArgumentTypeMisMatch(lenStatement.getLine()));
            return new NoType();
        }
        return new IntType();
    }
    @Override
    public Type visit(MatchPatternStatement matchPatternStatement){
        try{
            PatternItem patternItem = (PatternItem)SymbolTable.root.getItem(PatternItem.START_KEY +
                    matchPatternStatement.getPatternId().getName());
            patternItem.setTargetVarType(matchPatternStatement.getMatchArgument().accept(this));
            return patternItem.getPatternDeclaration().accept(this);
        }catch (ItemNotFound ignored){}
        return new NoType();
    }
    @Override
    public Type visit(RangeExpression rangeExpression){
        RangeType rangeType = rangeExpression.getRangeType();

        if(rangeType.equals(RangeType.LIST)){
            // TODO --> mind that the lists are declared explicitly in the grammar in this node, so handle the errors
            for (Expression rangeElementExpr1: rangeExpression.getRangeExpressions()){
                for (Expression rangeElementExpr2: rangeExpression.getRangeExpressions()){
                    if (!(rangeElementExpr1.accept(this).sameType(rangeElementExpr2.accept(this)))){
                        typeErrors.add(new ListElementsTypesMisMatch(rangeExpression.getLine()));
                        return new NoType();
                    }
                }
            }
        }
        else if (rangeType.equals(RangeType.IDENTIFIER)) {
            for (Expression rangeExpr: rangeExpression.getRangeExpressions()){
                if (!(rangeExpr.accept(this) instanceof ListType)){
                    typeErrors.add(new IsNotIterable(rangeExpression.getLine()));
                    return new NoType();
                }
            }
        }
        else if(rangeType.equals(RangeType.DOUBLE_DOT)){
            for (Expression rangeExpr: rangeExpression.getRangeExpressions()){
                if (!(rangeExpr.accept(this) instanceof IntType)){
                    //System.out.println(rangeExpression.getLine());
                    typeErrors.add(new RangeValuesMisMatch(rangeExpression.getLine()));
                    return new NoType();
                }
            }
        }
        if(rangeExpression.getRangeExpressions().get(0).accept(this) instanceof  ListType) {
            return ((ListType) rangeExpression.getRangeExpressions().get(0).accept(this)).getType() ;
        }
        return rangeExpression.getRangeExpressions().get(0).accept(this);
    }

    private boolean isSameType(Type type1, Type type2) {
        // Implement a method to compare two types for equality
        return type1.getClass().equals(type2.getClass());
    }

}

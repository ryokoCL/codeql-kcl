package com.kcl.parser;

import com.kcl.ast.Module;
import com.kcl.ast.*;

import java.util.List;

public class DefaultVisitor implements ContextVisitor<String> {
    @Override
    public void visit(Program node) {
        node.getPkgs().forEach((pkg, modules) -> modules.forEach(module -> this.visit(module, "pkg")));

    }

    @Override
    public void visit(Module node, String Id) {
        node.getBody().forEach(stmtNodeRef -> this.visit(stmtNodeRef, "module"));

    }

    @Override
    public void visit(List<? extends Node<?>> node, String Id) {
        node.forEach(node1 -> this.visit(node1, Id));

    }

    @Override
    public void visit(Node<?> node, String Id) {
        if (node == null) {
            return;
        }

        Id = node.getId();

        switch (node.getNode()) {
            case Stmt stmt -> {
                switch (stmt) {
                    case TypeAliasStmt typeAliasStmt -> this.visit(typeAliasStmt, Id);
                    case ExprStmt exprStmt -> this.visit(exprStmt, Id);
                    case UnificationStmt unificationStmt -> this.visit(unificationStmt, Id);
                    case AssignStmt assignStmt -> this.visit(assignStmt, Id);
                    case AugAssignStmt augAssignStmt -> this.visit(augAssignStmt, Id);
                    case AssertStmt assertStmt -> this.visit(assertStmt, Id);
                    case IfStmt ifStmt -> this.visit(ifStmt, Id);
                    case ImportStmt importStmt -> this.visit(importStmt, Id);
                    case SchemaAttr schemaAttr -> this.visit(schemaAttr, Id);
                    case SchemaStmt schemaStmt -> this.visit(schemaStmt, Id);
                    case RuleStmt ruleStmt -> this.visit(ruleStmt, Id);
                    default -> throw new RuntimeException(stmt.toString());
                }
            }

            case Expr expr -> {
                switch (expr) {
                    case IdentifierExpr identifierExpr -> this.visit(identifierExpr, Id);
                    case UnaryExpr unaryExpr -> this.visit(unaryExpr, Id);
                    case BinaryExpr binaryExpr -> this.visit(binaryExpr, Id);
                    case IfExpr ifExpr -> this.visit(ifExpr, Id);
                    case SelectorExpr selectorExpr -> this.visit(selectorExpr, Id);
                    case CallExpr callExpr -> this.visit(callExpr, Id);
                    case ParenExpr parenExpr -> this.visit(parenExpr, Id);
                    case QuantExpr quantExpr -> this.visit(quantExpr, Id);
                    case ListExpr listExpr -> this.visit(listExpr, Id);
                    case ListIfItemExpr listIfItemExpr -> this.visit(listIfItemExpr, Id);
                    case ListComp listComp -> this.visit(listComp, Id);
                    case StarredExpr starredExpr -> this.visit(starredExpr, Id);
                    case DictComp dictComp -> this.visit(dictComp, Id);
                    case ConfigIfEntryExpr configIfEntryExpr -> this.visit(configIfEntryExpr, Id);
                    case SchemaExpr schemaExpr -> this.visit(schemaExpr, Id);
                    case ConfigExpr configExpr -> this.visit(configExpr, Id);
                    case CheckExpr checkExpr -> this.visit(checkExpr, Id);
                    case LambdaExpr lambdaExpr -> this.visit(lambdaExpr, Id);
                    case Subscript subscript -> this.visit(subscript, Id);
                    case Compare compare -> this.visit(compare, Id);
                    case JoinedString joinedString -> this.visit(joinedString, Id);
                    case FormattedValue formattedValue -> this.visit(formattedValue, Id);
                    case NumberLit numberLit -> this.visit(numberLit, Id);
                    case StringLit stringLit -> this.visit(stringLit, Id);
                    case NameConstantLit nameConstantLit -> this.visit(nameConstantLit, Id);
                    case MissingExpr missingExpr -> this.visit(missingExpr, Id);
                    default -> throw new RuntimeException(expr.toString());
                }
            }
            case String string -> visit(string, Id);
            case Type type -> visit(type, Id);
            case SchemaConfig schemaConfig -> visit(schemaConfig, Id);
            case ConfigEntry configEntry -> visit(configEntry, Id);
            case Identifier identifier -> visit(identifier, Id);
            case Keyword keyword -> visit(keyword, Id);
            case CompClause compClause -> visit(compClause, Id);
            case Arguments arguments -> visit(arguments, Id);
            default -> throw new RuntimeException(node.toString());
        }

    }

    @Override
    public void visit(TypeAliasStmt typeAliasStmt, String Id) {
        visit(typeAliasStmt.getTypeValue(), Id);
        visit(typeAliasStmt.getTy(), Id);
        visit(typeAliasStmt.getTypeName(), Id);
    }

    @Override
    public void visit(ExprStmt exprStmt, String Id) {
        visit(exprStmt.getExprs(), Id);
    }

    @Override
    public void visit(UnificationStmt unificationStmt, String Id) {
        visit(unificationStmt.getValue(), Id);
        visit(unificationStmt.getTarget(), Id);
    }


//    @Override
//    public void visit(Comment node, String Id) {
//
//    }

    @Override
    public void visit(SchemaStmt node, String Id) {
        if (node == null)
            return;

        visit(node.getDoc(), Id);
        visit(node.getName(), Id);
        visit(node.getParentName(), Id);
        visit(node.getForHostName(), Id);
        visit(node.getArgs(), Id);
        visit(node.getMixins(), Id);
        visit(node.getBody(), Id);
        visit(node.getDecorators(), Id);
        visit(node.getChecks(), Id);
        visit(node.getIndexSignature(), Id);
    }

    @Override
    public void visit(SchemaAttr node, String Id) {
        visit(node.getName(), Id);
        visit(node.getOp(), Id);
        visit(node.getValue(), Id);

    }

    @Override
    public void visit(RuleStmt ruleStmt, String Id) {
        visit(ruleStmt.getForHostName(), Id);
        visit(ruleStmt.getName(), Id);
        visit(ruleStmt.getChecks(), Id);
        visit(ruleStmt.getArgs(), Id);
        visit(ruleStmt.getParentRules(), Id);
        visit(ruleStmt.getDecorators(), Id);
    }

    @Override
    public void visit(AssignStmt node, String Id) {
        visit(node.getTargets(), Id);
        visit(node.getValue(), Id);
        visit(node.getTy(), Id);

    }

    @Override
    public void visit(AugAssignStmt augAssignStmt, String Id) {
        visit(augAssignStmt.getValue(), Id);
        visit(augAssignStmt.getTarget(), Id);
    }

    @Override
    public void visit(AssertStmt assertStmt, String Id) {
        visit(assertStmt.getIfCond(), Id);
        visit(assertStmt.getMsg(), Id);
        visit(assertStmt.getTest(), Id);
    }

    @Override
    public void visit(IfStmt ifStmt, String Id) {
        visit(ifStmt.getBody(), Id);
        visit(ifStmt.getOrelse(), Id);
        visit(ifStmt.getCond(), Id);
    }

    @Override
    public void visit(ImportStmt importStmt, String Id) {
        visit(importStmt.getPath(), Id);
        visit(importStmt.getAsname(), Id);
    }


    @Override
    public void visit(SchemaExpr node, String Id) {
        visit(node.getName(), Id);
        visit(node.getArgs(), Id);
        visit(node.getKwargs(), Id);
        visit(node.getConfig(), Id);
    }

    @Override
    public void visit(ConfigExpr node, String Id) {
        visit(node.getItems(), Id);

    }

    @Override
    public void visit(CheckExpr checkExpr, String Id) {
        visit(checkExpr.getIfCond(), Id);
        visit(checkExpr.getMsg(), Id);
        visit(checkExpr.getTest(), Id);
    }

    @Override
    public void visit(LambdaExpr lambdaExpr, String Id) {
        visit(lambdaExpr.getArgs(), Id);
        visit(lambdaExpr.getBody(), Id);
        visit(lambdaExpr.getReturnTy(), Id);
    }

    @Override
    public void visit(Subscript subscript, String Id) {
        visit(subscript.getValue(), Id);
        visit(subscript.getIndex(), Id);
        visit(subscript.getLower(), Id);
        visit(subscript.getUpper(), Id);
        visit(subscript.getStep(), Id);
    }

    @Override
    public void visit(Compare compare, String Id) {
        visit(compare.getComparators(), Id);
        visit(compare.getLeft(), Id);
    }

    @Override
    public void visit(IdentifierExpr node, String Id) {
        visit(node.getNames(), Id);
    }

    @Override
    public void visit(UnaryExpr unaryExpr, String Id) {
        visit(unaryExpr.getOperand(), Id);
    }

    @Override
    public void visit(BinaryExpr binaryExpr, String Id) {
        visit(binaryExpr.getLeft(), Id);
        visit(binaryExpr.getRight(), Id);
    }

    @Override
    public void visit(IfExpr ifExpr, String Id) {
        visit(ifExpr.getBody(), Id);
        visit(ifExpr.getCond(), Id);
        visit(ifExpr.getOrelse(), Id);
    }

    @Override
    public void visit(SelectorExpr selectorExpr, String Id) {
        visit(selectorExpr.getValue(), Id);
        visit(selectorExpr.getAttr(), Id);
    }

    @Override
    public void visit(CallExpr callExpr, String Id) {
        visit(callExpr.getArgs(), Id);
        visit(callExpr.getFunc(), Id);
        visit(callExpr.getKeywords(), Id);
    }

    @Override
    public void visit(ParenExpr parenExpr, String Id) {
        visit(parenExpr.getExpr(), Id);
    }

    @Override
    public void visit(QuantExpr quantExpr, String Id) {
        visit(quantExpr.getIfCond(), Id);
        visit(quantExpr.getTarget(), Id);
        visit(quantExpr.getTest(), Id);
        visit(quantExpr.getVariables(), Id);
    }

    @Override
    public void visit(ListExpr listExpr, String Id) {
        visit(listExpr.getElts(), Id);
    }

    @Override
    public void visit(ListIfItemExpr listIfItemExpr, String Id) {
        visit(listIfItemExpr.getExprs(), Id);
        visit(listIfItemExpr.getIfCond(), Id);
        visit(listIfItemExpr.getOrelse(), Id);
    }

    @Override
    public void visit(ListComp listComp, String Id) {
        visit(listComp.getGenerators(), Id);
        visit(listComp.getElt(), Id);
    }

    @Override
    public void visit(StarredExpr starredExpr, String Id) {
        visit(starredExpr.getValue(), Id);
    }

    @Override
    public void visit(DictComp dictComp, String Id) {
        visit(dictComp.getEntry(), Id);
        visit(dictComp.getGenerators(), Id);
    }

    @Override
    public void visit(ConfigIfEntryExpr configIfEntryExpr, String Id) {
        visit(configIfEntryExpr.getItems(), Id);
        visit(configIfEntryExpr.getIfCond(), Id);
        visit(configIfEntryExpr.getOrelse(), Id);
    }


    @Override
    public void visit(NumberLit node, String Id) {

    }

    @Override
    public void visit(StringLit node, String Id) {

    }

    @Override
    public void visit(NameConstantLit nameConstantLit, String Id) {
    }

    @Override
    public void visit(JoinedString joinedString, String Id) {
        visit(joinedString.getValues(), Id);
    }

    @Override
    public void visit(FormattedValue formattedValue, String Id) {
        visit(formattedValue.getValue(), Id);
    }

    @Override
    public void visit(MissingExpr missingExpr, String Id) {

    }

    @Override
    public void visit(UnaryOp op, String Id) {

    }

    @Override
    public void visit(BinOp op, String Id) {

    }

    @Override
    public void visit(CmpOp cmpOp, String Id) {

    }


    @Override
    public void visit(Type type, String Id) {
        switch (type) {
            case AnyType anyType -> this.visit(anyType, Id);
            case NamedType namedType -> this.visit(namedType, Id);
            case BasicType basicType -> this.visit(basicType, Id);
            case ListType listType -> this.visit(listType, Id);
            case DictType dictType -> this.visit(dictType, Id);
            case UnionType unionType -> this.visit(unionType, Id);
            case FunctionType functionType -> this.visit(functionType, Id);
            default -> throw new RuntimeException(type.toString());
        }
    }

    @Override
    public void visit(AnyType anyType, String Id) {

    }

    @Override
    public void visit(NamedType namedType, String Id) {
    }

    @Override
    public void visit(BasicType node, String Id) {

    }

    @Override
    public void visit(ListType listType, String Id) {
        //type
        if (listType.getValue().getInnerType().isPresent())
            visit(listType.getValue().getInnerType().get(), Id);
    }

    @Override
    public void visit(DictType dictType, String Id) {
        //type
        if (dictType.getValue().getKeyType().isPresent())
            visit(dictType.getValue().getKeyType().get(), Id);
        if (dictType.getValue().getValueType().isPresent())
            visit(dictType.getValue().getValueType().get(), Id);
    }

    @Override
    public void visit(UnionType unionType, String Id) {
        visit(unionType.getValue().getTypeElements(), Id);
    }

    @Override
    public void visit(FunctionType functionType, String Id) {
        //type
        if (functionType.getValue().getParamsTy().isPresent())
            visit(functionType.getValue().getParamsTy().get(), Id);
        if (functionType.getValue().getRetTy().isPresent())
            visit(functionType.getValue().getRetTy().get(), Id);
    }

    @Override
    public void visit(String node, String Id) {

    }

    @Override
    public void visit(SchemaConfig schemaConfig, String Id) {
        visit(schemaConfig.getConfig(), Id);
        visit(schemaConfig.getName(), Id);
        visit(schemaConfig.getArgs(), Id);
        visit(schemaConfig.getKwargs(), Id);
    }

    @Override
    public void visit(CompClause compClause, String Id) {
        visit(compClause.getIfs(), Id);
    }


    @Override
    public void visit(Arguments node, String Id) {
        visit(node.getDefaults(), Id);
        visit(node.getArgs(), Id);
        visit(node.getTyList(), Id);
    }

    @Override
    public void visit(Keyword node, String Id) {
        visit(node.getArg(), Id);
        visit(node.getValue(), Id);

    }

    public void visit(ConfigEntry node, String Id) {
        visit(node.getKey(), Id);
        visit(node.getValue(), Id);

    }

    @Override
    public void visit(Identifier node, String Id) {
        visit(node.getNames(), Id);
    }

    @Override
    public void visit(AugOp node, String Id) {

    }

    @Override
    public void visit(QuantOperation op, String Id) {

    }

    @Override
    public void visit(NumberLitValue node, String Id) {

    }

    @Override
    public void visit(NumberBinarySuffix node, String Id) {

    }

    @Override
    public void visit(ConfigEntryOperation node, String Id) {

    }

    @Override
    public void visit(ExprContext node, String Id) {

    }
}

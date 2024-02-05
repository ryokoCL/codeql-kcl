package com.kcl.parser;

import com.kcl.ast.Module;
import com.kcl.ast.*;

import java.util.List;

public interface ContextVisitor<C> {
    void visit(Program program);

    void visit(Module module, C c);

    void visit(List<? extends Node<?>> listNode, C c);

    void visit(Node<?> node, C c);

    //stmt
    void visit(TypeAliasStmt typeAliasStmt, C c);

    void visit(ExprStmt exprStmt, C c);

    void visit(UnificationStmt unificationStmt, C c);

    void visit(AssignStmt assignStmt, C c);

    void visit(AugAssignStmt augAssignStmt, C c);

    void visit(AssertStmt assertStmt, C c);

    void visit(IfStmt ifStmt, C c);

    void visit(ImportStmt importStmt, C c);

    void visit(SchemaStmt schemaStmt, C c);

    void visit(SchemaAttr schemaAttr, C c);

    void visit(RuleStmt ruleStmt, C c);

    //expr
    void visit(IdentifierExpr identifierExpr, C c);

    void visit(UnaryExpr unaryExpr, C c);

    void visit(BinaryExpr binaryExpr, C c);

    void visit(IfExpr ifExpr, C c);

    void visit(SelectorExpr selectorExpr, C c);

    void visit(CallExpr callExpr, C c);

    void visit(ParenExpr parenExpr, C c);

    void visit(QuantExpr quantExpr, C c);

    void visit(ListExpr listExpr, C c);

    void visit(ListIfItemExpr listIfItemExpr, C c);

    void visit(ListComp listComp, C c);

    void visit(StarredExpr starredExpr, C c);

    void visit(DictComp dictComp, C c);

    void visit(ConfigIfEntryExpr configIfEntryExpr, C c);

    void visit(SchemaExpr schemaExpr, C c);

    void visit(ConfigExpr configExpr, C c);

    void visit(CheckExpr checkExpr, C c);

    void visit(LambdaExpr lambdaExpr, C c);

    void visit(Subscript subscript, C c);

    void visit(Compare compare, C c);

    void visit(NumberLit numberLit, C c);

    void visit(StringLit stringLit, C c);

    void visit(NameConstantLit nameConstantLit, C c);

    void visit(JoinedString joinedString, C c);

    void visit(FormattedValue formattedValue, C c);

    void visit(MissingExpr missingExpr, C c);

    //op
    void visit(UnaryOp op, C c);

    void visit(BinOp op, C c);

    void visit(CmpOp cmpOp, C c);

    void visit(AugOp op, C c);

    void visit(QuantOperation op, C c);

    //type
    void visit(Type type, C c);

    void visit(AnyType anyType, C c);

    void visit(NamedType namedType, C c);

    void visit(BasicType basicType, C c);

    void visit(ListType listType, C c);

    void visit(DictType dictType, C c);

    void visit(UnionType unionType, C c);

    void visit(FunctionType functionType, C c);

    //other
    void visit(String node, C c);

    void visit(SchemaConfig schemaConfig, C c);

    void visit(CompClause compClause, C c);

    void visit(ConfigEntry configEntry, C c);

    void visit(NumberBinarySuffix suffix, C c);

    void visit(NumberLitValue numberLitValue, C c);

    void visit(ConfigEntryOperation op, C c);

    void visit(Identifier identifier, C c);

    void visit(ExprContext exprContext, C c);

    void visit(Keyword keyword, C c);

    void visit(Arguments arguments, C c);
}

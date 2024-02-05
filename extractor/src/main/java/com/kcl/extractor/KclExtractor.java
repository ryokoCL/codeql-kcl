package com.kcl.extractor;

import com.kcl.api.Spec;
import com.kcl.ast.Module;
import com.kcl.ast.*;
import com.kcl.parser.ContextVisitor;
import com.kcl.parser.KclAstParser;
import com.kcl.util.SematicUtil;
import com.semmle.util.collections.CollectionUtil;
import com.semmle.util.files.FileUtil;
import com.semmle.util.trap.TrapWriter;
import com.semmle.util.trap.TrapWriter.Label;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class KclExtractor implements IExtractor, ContextVisitor<KclExtractor.Context> {

    private ExtractorConfig config;

    private TrapWriter trapWriter;

    private LocationManager locationManager;

    private TextualExtractor textualExtractor;

    private LexicalExtractor lexicalExtractor;

    private SyntacticContextManager contextManager;
    private ConcurrentMap<File, Optional<String>> packageTypeCache;

    private Spec.LoadPackage_Result specResult;

    private KclAstParser.ParseResult parseResult;

//    private Program

    public KclExtractor(ExtractorConfig config, ExtractorState state) {
        this.config = config;
        this.packageTypeCache = state.getPackageTypeCache();
        this.contextManager = new SyntacticContextManager();
    }


    public ParseResultInfo extract(TextualExtractor textualExtractor) {
        this.textualExtractor = textualExtractor;
        this.locationManager = textualExtractor.getLocationManager();
        String sourceFile = textualExtractor.getExtractedFile().getAbsolutePath();
        String source = textualExtractor.getSource();
        ExtractionMetrics metrics = textualExtractor.getMetrics();
        this.trapWriter = textualExtractor.getTrapwriter();
        try {
            //parse file
            metrics.startPhase(ExtractionMetrics.ExtractionPhase.KclAstParser_parse);
            this.parseResult = KclAstParser.parse(Path.of(sourceFile));
            this.specResult = this.parseResult.getSpec();
            Path jsonPath = Paths.get(EnvironmentVariables.getScratchDir()).resolve("kcl.json");
            FileUtil.write(jsonPath.toFile(), specResult.getProgram());
            metrics.stopPhase(ExtractionMetrics.ExtractionPhase.KclAstParser_parse);

            //extract
            metrics.startPhase(ExtractionMetrics.ExtractionPhase.KclExtractor_extract);
            this.lexicalExtractor = new LexicalExtractor(textualExtractor);
            Program program = this.parseResult.getProgram();
            visit(program);
            ParseResultInfo loc = lexicalExtractor.extractLines(source, locationManager.getFileLabel());
            metrics.stopPhase(ExtractionMetrics.ExtractionPhase.KclExtractor_extract);
            return loc;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public void visit(Program program) {
        String root = program.getRoot();
        Label rootLabel = trapWriter.globalID("kcl;{" + root + "}," + this.locationManager.getStartLine() + ',' + this.locationManager.getStartColumn());
        this.trapWriter.addTuple("roots", rootLabel, root);

        program.getPkgs().forEach((pkg, modules) -> {
            Label packageLabel = this.trapWriter.freshLabel();
            this.trapWriter.addTuple("packages", packageLabel, pkg, rootLabel);

            AtomicInteger idx = new AtomicInteger();
            modules.forEach(module -> {
                Label moduleLabel = trapWriter.freshLabel();
                this.trapWriter.addTuple("modules", moduleLabel, module.getName(), locationManager.getFileLabel(), packageLabel, idx.get());

                contextManager.enterContainer(moduleLabel);
                this.visit(module, new Context(packageLabel, moduleLabel, idx.get()));
                contextManager.leaveContainer();
                idx.getAndIncrement();
//            emitNodeSymbol(nd, toplevelLabel);
            });
        });
    }


    @Override
    public void visit(Module module, Context c) {
        visit(module.getBody(), c);
        int idx = 0;
        for (Node<Comment> commentNode : module.getComments()) {
            Label commentLabel = this.trapWriter.globalID(commentNode.getId());
            this.trapWriter.addTuple("comments", commentLabel, c.current, idx,
                    commentNode.getNode().getText(),
                    this.textualExtractor.mkToString(this.textualExtractor.getLine(commentNode))
            );
            idx++;
        }
    }


    @Override
    public void visit(List<? extends Node<?>> listNode, Context c) {
        if (CollectionUtil.isNullOrEmpty(listNode)) {
            return;
        }

        Label listLabel = this.trapWriter.freshLabel();

        String tableName = switch (listNode.get(0).getNode()) {
            case String ignored -> "string_lists";
            case Identifier ignored -> "identifier_lists";
            case Expr ignored -> "expr_lists";
            case Stmt ignored -> "stmt_lists";
            case Keyword ignored -> "keyword_lists";
            case Type ignored -> "type_lists";
            default -> throw new RuntimeException(listNode.get(0).getId());
        };
        this.trapWriter.addTuple(tableName, listLabel, c.current, c.childIndex);
        int idx = 0;
        for (Node<?> node : listNode) {
            visit(node, new Context(c.current, listLabel, idx));
            idx++;
        }
    }


    @Override
    public void visit(Node<?> node, Context c) {
        if (node == null) {
            return;
        }

        Label lbl = this.trapWriter.globalID(node.getId());
        c.pushLableInfo(lbl, node.getId());

        Context newContext = new Context(c.current, lbl, c.childIndex);
        newContext.pushLableInfo(lbl, node.getId());

        switch (node.getNode()) {
            case Stmt stmt -> {
                String tostring = this.textualExtractor.mkToString(this.textualExtractor.getLine(node));

                int kind = switch (stmt) {
                    case TypeAliasStmt ignored -> 0;
                    case ExprStmt ignored -> 1;
                    case UnificationStmt ignored -> 2;
                    case AssignStmt ignored -> 3;
                    case AugAssignStmt ignored -> 4;
                    case AssertStmt ignored -> 5;
                    case IfStmt ignored -> 6;
                    case ImportStmt ignored -> 7;
                    case SchemaAttr ignored -> 8;
                    case SchemaStmt ignored -> 9;
                    case RuleStmt ignored -> 10;
                    default -> throw new RuntimeException(stmt.toString());
                };

                this.trapWriter.addTuple("stmts", lbl, kind, c.current, c.childIndex, tostring);

                contextManager.setCurrentStatement(stmt);

                switch (stmt) {
                    case TypeAliasStmt typeAliasStmt -> this.visit(typeAliasStmt, newContext);
                    case ExprStmt exprStmt -> this.visit(exprStmt, newContext);
                    case UnificationStmt unificationStmt -> this.visit(unificationStmt, newContext);
                    case AssignStmt assignStmt -> this.visit(assignStmt, newContext);
                    case AugAssignStmt augAssignStmt -> this.visit(augAssignStmt, newContext);
                    case AssertStmt assertStmt -> this.visit(assertStmt, newContext);
                    case IfStmt ifStmt -> this.visit(ifStmt, newContext);
                    case ImportStmt importStmt -> this.visit(importStmt, newContext);
                    case SchemaAttr schemaAttr -> this.visit(schemaAttr, newContext);
                    case SchemaStmt schemaStmt -> this.visit(schemaStmt, newContext);
                    case RuleStmt ruleStmt -> this.visit(ruleStmt, newContext);
                    default -> throw new RuntimeException(stmt.toString());
                }
            }

            case Expr expr -> {
                int kind = switch (expr) {
                    case IdentifierExpr ignored -> 0;
                    case UnaryExpr ignored -> 1;
                    case BinaryExpr ignored -> 2;
                    case IfExpr ignored -> 3;
                    case SelectorExpr ignored -> 4;
                    case CallExpr ignored -> 5;
                    case ParenExpr ignored -> 6;
                    case QuantExpr ignored -> 7;
                    case ListExpr ignored -> 8;
                    case ListIfItemExpr ignored -> 9;
                    case ListComp ignored -> 10;
                    case StarredExpr ignored -> 11;
                    case DictComp ignored -> 12;
                    case ConfigIfEntryExpr ignored -> 13;
                    case SchemaExpr ignored -> 14;
                    case ConfigExpr ignored -> 15;
                    case CheckExpr ignored -> 16;
                    case LambdaExpr ignored -> 17;
                    case Subscript ignored -> 18;
                    case Compare ignored -> 19;
                    case JoinedString ignored -> 20;
                    case FormattedValue ignored -> 21;
                    case NumberLit ignored -> 22;
                    case StringLit ignored -> 23;
                    case NameConstantLit ignored -> 24;
                    case MissingExpr ignored -> 25;
                    default -> throw new IllegalStateException("Unexpected value: " + expr);
                };
                String tostring = this.textualExtractor.mkToString(this.textualExtractor.getLine(node));

                this.trapWriter.addTuple("exprs", lbl, kind, c.current, c.childIndex, tostring);

                switch (expr) {
                    case IdentifierExpr identifierExpr -> this.visit(identifierExpr, newContext);
                    case UnaryExpr unaryExpr -> this.visit(unaryExpr, newContext);
                    case BinaryExpr binaryExpr -> this.visit(binaryExpr, newContext);
                    case IfExpr ifExpr -> this.visit(ifExpr, newContext);
                    case SelectorExpr selectorExpr -> this.visit(selectorExpr, newContext);
                    case CallExpr callExpr -> this.visit(callExpr, newContext);
                    case ParenExpr parenExpr -> this.visit(parenExpr, newContext);
                    case QuantExpr quantExpr -> this.visit(quantExpr, newContext);
                    case ListExpr listExpr -> this.visit(listExpr, newContext);
                    case ListIfItemExpr listIfItemExpr -> this.visit(listIfItemExpr, newContext);
                    case ListComp listComp -> this.visit(listComp, newContext);
                    case StarredExpr starredExpr -> this.visit(starredExpr, newContext);
                    case DictComp dictComp -> this.visit(dictComp, newContext);
                    case ConfigIfEntryExpr configIfEntryExpr -> this.visit(configIfEntryExpr, newContext);
                    case SchemaExpr schemaExpr -> this.visit(schemaExpr, newContext);
                    case ConfigExpr configExpr -> this.visit(configExpr, newContext);
                    case CheckExpr checkExpr -> this.visit(checkExpr, newContext);
                    case LambdaExpr lambdaExpr -> this.visit(lambdaExpr, newContext);
                    case Subscript subscript -> this.visit(subscript, newContext);
                    case Compare compare -> this.visit(compare, newContext);
                    case JoinedString joinedString -> this.visit(joinedString, newContext);
                    case FormattedValue formattedValue -> this.visit(formattedValue, newContext);
                    case NumberLit numberLit -> this.visit(numberLit, newContext);
                    case StringLit stringLit -> this.visit(stringLit, newContext);
                    case NameConstantLit nameConstantLit -> this.visit(nameConstantLit, newContext);
                    case MissingExpr missingExpr -> this.visit(missingExpr, newContext);
                    default -> throw new RuntimeException(expr.toString());
                }
            }
            case String string -> visit(string, newContext);
            case Type type -> visit(type, newContext);
            case SchemaConfig schemaConfig -> visit(schemaConfig, newContext);
            case ConfigEntry configEntry -> visit(configEntry, newContext);
            case Identifier identifier -> visit(identifier, newContext);
            case Keyword keyword -> visit(keyword, newContext);
            case CompClause compClause -> visit(compClause, newContext);
            case Arguments arguments -> visit(arguments, newContext);
            default -> throw new RuntimeException(node.toString());
        }
        this.locationManager.emitNodeLocation(node, lbl);
    }

    public void visit(Arguments arguments, Context c) {
        //list<identifier>
        this.visit(arguments.getArgs(), c.withNewIdx(0));

        //list<expr>
        this.visit(arguments.getDefaults(), c.withNewIdx(0));

        //list<type>
        this.visit(arguments.getTyList(), c.withNewIdx(0));
    }


    @Override
    public void visit(SchemaStmt schemaStmt, Context c) {
        //string
        visit(schemaStmt.getDoc(), c.withNewIdx(0));
        visit(schemaStmt.getName(), c.withNewIdx(1));

        //identifier
        visit(schemaStmt.getParentName(), c.withNewIdx(0));
        visit(schemaStmt.getForHostName(), c.withNewIdx(1));

        //list<stmt>
        visit(schemaStmt.getBody(), c.withNewIdx(0));
    }


    @Override
    public void visit(SchemaAttr schemaAttr, Context c) {
        //string
//        visit(schemaAttr.getDoc(), c.withNewIdx(0));
        visit(schemaAttr.getName(), c.withNewIdx(1));

        //augop
        visit(schemaAttr.getOp(), c.withNewIdx(0));

        //expr
        visit(schemaAttr.getValue(), c.withNewIdx(0));

        //list_expr
        visit(schemaAttr.getDecorators(), c.withNewIdx(0));

        //type
        visit(schemaAttr.getTy(), new Context(c.parent, c.current, 0));
    }


    @Override
    public void visit(AssignStmt assignStmt, Context c) {
        //List<NodeRef<Identifier>>
        visit(assignStmt.getTargets(), c.withNewIdx(0));

        //expr
        visit(assignStmt.getValue(), c.withNewIdx(0));

        //type
        visit(assignStmt.getTy(), c.withNewIdx(0));
    }

    @Override
    public void visit(TypeAliasStmt typeAliasStmt, Context c) {
        //identifier
        visit(typeAliasStmt.getTypeName(), c.withNewIdx(0));

        //string
        visit(typeAliasStmt.getTypeValue(), c.withNewIdx(0));

        //type
        visit(typeAliasStmt.getTy(), c.withNewIdx(0));
    }

    @Override
    public void visit(ExprStmt exprStmt, Context c) {
        visit(exprStmt.getExprs(), c.withNewIdx(0));
    }

    @Override
    public void visit(UnificationStmt unificationStmt, Context c) {
        //identifier
        visit(unificationStmt.getTarget(), c.withNewIdx(0));

        //SchemaConfig
        visit(unificationStmt.getValue(), c.withNewIdx(0));
    }

    @Override
    public void visit(SchemaConfig schemaConfig, Context c) {
        //identifier
        visit(schemaConfig.getName(), c.withNewIdx(0));

        //list<expr>
        visit(schemaConfig.getArgs(), c.withNewIdx(0));

        //list<keyword>
        visit(schemaConfig.getKwargs(), c.withNewIdx(0));

        //expr
        visit(schemaConfig.getConfig(), c.withNewIdx(0));
    }

    @Override
    public void visit(AugAssignStmt augAssignStmt, Context c) {
        //identifier
        visit(augAssignStmt.getTarget(), c.withNewIdx(0));

        //expr
        visit(augAssignStmt.getValue(), c.withNewIdx(0));

        //op
        visit(augAssignStmt.getOp(), c.withNewIdx(0));
    }

    @Override
    public void visit(AssertStmt assertStmt, Context c) {
        //expr
        visit(assertStmt.getIfCond(), c.withNewIdx(0));
        visit(assertStmt.getMsg(), c.withNewIdx(1));
        visit(assertStmt.getTest(), c.withNewIdx(2));
    }

    @Override
    public void visit(IfStmt ifStmt, Context c) {
        //list<stmt>
        visit(ifStmt.getBody(), c.withNewIdx(0));
        visit(ifStmt.getOrelse(), c.withNewIdx(1));

        //expr
        visit(ifStmt.getCond(), c.withNewIdx(0));
    }

    @Override
    public void visit(ImportStmt importStmt, Context c) {
        //string
        visit(importStmt.getName(), c.withNewIdx(0));
        visit(importStmt.getPkgName(), c.withNewIdx(1));
        visit(importStmt.getRawpath(), c.withNewIdx(2));

        //node<string>
        visit(importStmt.getPath(), c.withNewIdx(0));
        visit(importStmt.getAsname(), c.withNewIdx(1));
    }

    @Override
    public void visit(RuleStmt ruleStmt, Context c) {
        //string
        visit(ruleStmt.getName(), c.withNewIdx(0));

        //list<identifier>
        visit(ruleStmt.getParentRules(), c.withNewIdx(0));

        //expr
        visit(ruleStmt.getDecorators(), c.withNewIdx(0));
        visit(ruleStmt.getChecks(), c.withNewIdx(1));

        //arguments
        visit(ruleStmt.getArgs(), c.withNewIdx(0));
    }

    @Override
    public void visit(SchemaExpr schemaExpr, Context c) {
        //identifier
        visit(schemaExpr.getName(), c.withNewIdx(0));

        //List<NodeRef<Expr>>
        visit(schemaExpr.getArgs(), c.withNewIdx(0));

        //List<NodeRef<Keyword>>
        visit(schemaExpr.getKwargs(), c.withNewIdx(0));

        //NodeRef<Expr>
        visit(schemaExpr.getConfig(), c.withNewIdx(0));
    }


    @Override
    public void visit(IdentifierExpr identifierExpr, Context c) {
        Label label = trapWriter.freshLabel();
        this.trapWriter.addTuple("identifiers", label, c.current, c.childIndex, identifierExpr.getName());

        //透出identifier的location
        String astId = c.labelInfo.get(c.current);
        Node<?> node = this.parseResult.getNodeMap().get(astId);
        this.locationManager.emitNodeLocation(node, label);

        //String
        this.visit(identifierExpr.getPkgpath(), new Context(label, trapWriter.freshLabel(), 0));

        //ExprContext
        this.visit(identifierExpr.getCtx(), new Context(label, trapWriter.freshLabel(), 0));
    }


    @Override
    public void visit(ConfigExpr configExpr, Context c) {
        int idx = 0;
        for (NodeRef<ConfigEntry> item : configExpr.getItems()) {
            this.visit(item, c.withNewIdx(idx));
            idx++;
        }
    }


    @Override
    public void visit(UnaryExpr unaryExpr, Context c) {
        //Expr
        visit(unaryExpr.getOperand(), c.withNewIdx(0));

        //op
        visit(unaryExpr.getOp(), c.withNewIdx(0));
    }


    @Override
    public void visit(UnaryOp op, Context c) {
        int kind = switch (op) {
            case UAdd -> 0;
            case USub -> 1;
            case Invert -> 2;
            case Not -> 3;
        };
        this.trapWriter.addTuple("unaryops", this.trapWriter.freshLabel(), kind, c.current);
    }


    @Override
    public void visit(BinaryExpr binaryExpr, Context c) {
        //Expr
        visit(binaryExpr.getLeft(), c.withNewIdx(0));
        visit(binaryExpr.getRight(), c.withNewIdx(1));

        //op
        visit(binaryExpr.getOp(), c.withNewIdx(0));
    }


    @Override
    public void visit(BinOp op, Context c) {
        int kind = switch (op) {
            case Add -> 0;
            case Sub -> 1;
            case Mul -> 2;
            case Div -> 3;
            case Mod -> 4;
            case Pow -> 5;
            case FloorDiv -> 6;
            case LShift -> 7;
            case RShift -> 8;
            case BitXor -> 9;
            case BitAnd -> 10;
            case BitOr -> 11;
            case And -> 12;
            case Or -> 13;
            case As -> 14;
        };

        this.trapWriter.addTuple("binaryops", this.trapWriter.freshLabel(), kind, c.current);
    }


    @Override
    public void visit(IfExpr ifExpr, Context c) {
        //Expr
        visit(ifExpr.getBody(), c.withNewIdx(0));
        visit(ifExpr.getCond(), c.withNewIdx(1));
        visit(ifExpr.getOrelse(), c.withNewIdx(2));
    }


    @Override
    public void visit(SelectorExpr selectorExpr, Context c) {
        //identifier
        visit(selectorExpr.getAttr(), c.withNewIdx(0));

        //Expr
        visit(selectorExpr.getValue(), c.withNewIdx(0));

        //ExprContext
        visit(selectorExpr.getCtx(), c.withNewIdx(0));
    }


    @Override
    public void visit(CallExpr callExpr, Context c) {
        //list<expr>
        visit(callExpr.getArgs(), c.withNewIdx(0));

        //expr
        visit(callExpr.getFunc(), c.withNewIdx(0));

        //keyword
        visit(callExpr.getKeywords(), c.withNewIdx(0));
    }


    @Override
    public void visit(ParenExpr parenExpr, Context c) {
        visit(parenExpr.getExpr(), c.withNewIdx(0));
    }


    @Override
    public void visit(QuantExpr quantExpr, Context c) {
        //expr
        visit(quantExpr.getTarget(), c.withNewIdx(0));
        visit(quantExpr.getTest(), c.withNewIdx(1));
        visit(quantExpr.getIfCond(), c.withNewIdx(2));

        //list<identifier>
        visit(quantExpr.getVariables(), c.withNewIdx(0));

        //op
        visit(quantExpr.getOp(), c.withNewIdx(0));

        //ctx
        visit(quantExpr.getCtx(), c.withNewIdx(0));
    }


    @Override
    public void visit(QuantOperation op, Context c) {
        int kind = switch (op) {
            case All -> 0;
            case Any -> 1;
            case Filter -> 2;
            case Map -> 3;
        };
        this.trapWriter.addTuple("quantoperations", this.trapWriter.freshLabel(), kind, c.current);
    }


    @Override
    public void visit(ListExpr listExpr, Context c) {
        //list<expr>
        visit(listExpr.getElts(), c.withNewIdx(0));

        //ctx
        visit(listExpr.getCtx(), c.withNewIdx(0));
    }


    @Override
    public void visit(ListIfItemExpr listIfItemExpr, Context c) {
        //list<expr>
        visit(listIfItemExpr.getExprs(), c.withNewIdx(0));

        //expr
        visit(listIfItemExpr.getIfCond(), c.withNewIdx(0));
        visit(listIfItemExpr.getOrelse(), c.withNewIdx(1));
    }


    @Override
    public void visit(ListComp listComp, Context c) {
        //expr
        visit(listComp.getElt(), c.withNewIdx(0));

        //list<expr>
        visit(listComp.getGenerators(), c.withNewIdx(1));
    }

    @Override
    public void visit(StarredExpr starredExpr, Context c) {
        //expr
        visit(starredExpr.getValue(), c.withNewIdx(0));

        //ctx
        visit(starredExpr.getCtx(), c.withNewIdx(0));
    }

    @Override
    public void visit(DictComp dictComp, Context c) {
        //config entry
        visit(dictComp.getEntry(), c.withNewIdx(0));

        //list<expr>
        visit(dictComp.getGenerators(), c.withNewIdx(0));
    }

    @Override
    public void visit(ConfigIfEntryExpr configIfEntryExpr, Context c) {
        //config entry
        visit(configIfEntryExpr.getItems(), c.withNewIdx(0));

        //expr
        visit(configIfEntryExpr.getIfCond(), c.withNewIdx(0));
        visit(configIfEntryExpr.getOrelse(), c.withNewIdx(1));
    }

    @Override
    public void visit(CompClause compClause, Context c) {
        this.trapWriter.addTuple("compclause", c.current, c.parent);

        //list<identifier>
        visit(compClause.getTargets(), c.withNewIdx(0));

        //list<expr>
        visit(compClause.getIfs(), c.withNewIdx(0));

        //expr
        visit(compClause.getIter(), c.withNewIdx(0));
    }

    @Override
    public void visit(CheckExpr checkExpr, Context c) {
        //expr
        visit(checkExpr.getIfCond(), c.withNewIdx(0));
        visit(checkExpr.getTest(), c.withNewIdx(1));
        visit(checkExpr.getMsg(), c.withNewIdx(2));
    }

    @Override
    public void visit(LambdaExpr lambdaExpr, Context c) {
        //Arguments
        visit(lambdaExpr.getArgs(), c.withNewIdx(0));

        //list<stmt>
        visit(lambdaExpr.getBody(), c.withNewIdx(0));

        //type
        visit(lambdaExpr.getReturnTy(), c.withNewIdx(0));
    }

    @Override
    public void visit(Subscript subscript, Context c) {
        //ctx
        visit(subscript.getCtx(), c.withNewIdx(0));

        //expr
        visit(subscript.getValue(), c.withNewIdx(0));
        visit(subscript.getIndex(), c.withNewIdx(1));
        visit(subscript.getLower(), c.withNewIdx(2));
        visit(subscript.getUpper(), c.withNewIdx(3));
        visit(subscript.getStep(), c.withNewIdx(4));
    }

    @Override
    public void visit(Compare compare, Context c) {
        //list<expr>
        visit(compare.getComparators(), c.withNewIdx(0));

        //expr
        visit(compare.getLeft(), c.withNewIdx(1));

        //op
        int idx = 0;
        for (CmpOp op : compare.getOps()) {
            visit(op, c.withNewIdx(idx));
            idx++;
        }
    }

    @Override
    public void visit(CmpOp cmpOp, Context c) {
        int kind = switch (cmpOp) {
            case Eq -> 0;
            case NotEq -> 1;
            case Lt -> 2;
            case LtE -> 3;
            case Gt -> 4;
            case GtE -> 5;
            case Is -> 6;
            case In -> 7;
            case NotIn -> 8;
            case Not -> 9;
            case IsNot -> 10;
        };

        this.trapWriter.addTuple("cmpops", this.trapWriter.freshLabel(), kind, c.current, c.childIndex);
    }

    @Override
    public void visit(JoinedString joinedString, Context c) {
        //string
        visit(joinedString.getRawValue(), c.withNewIdx(0));

        //expr
        visit(joinedString.getValues(), c.withNewIdx(0));
    }

    @Override
    public void visit(FormattedValue formattedValue, Context c) {
        //string
        visit(formattedValue.getFormatSpec(), c.withNewIdx(0));

        //expr
        visit(formattedValue.getValue(), c.withNewIdx(0));
    }

    @Override
    public void visit(MissingExpr missingExpr, Context c) {

    }


    @Override
    public void visit(NumberLit numberLit, Context c) {
        //NumberBinarySuffix
        if (numberLit.getBinarySuffix() != null && numberLit.getBinarySuffix().isPresent())
            visit(numberLit.getBinarySuffix().get(), c.withNewIdx(0));

        //NumberLitValue
        visit(numberLit.getValue(), c);
    }


    @Override
    public void visit(StringLit stringLit, Context c) {
        this.trapWriter.addTuple("literals", trapWriter.freshLabel(), 3, c.current, stringLit.getValue());
    }


    @Override
    public void visit(NameConstantLit nameConstantLit, Context c) {
        this.trapWriter.addTuple("literals", trapWriter.freshLabel(), 4, c.current, nameConstantLit.getValue().symbol());
    }


    @Override
    public void visit(ConfigEntry configEntry, Context c) {
        this.trapWriter.addTuple("configentrys", c.current, c.parent, c.childIndex);

        //Expr
        visit(configEntry.getKey(), c.withNewIdx(0));
        visit(configEntry.getValue(), c.withNewIdx(1));

        //op
        visit(configEntry.getOperation(), c.withNewIdx(0));
    }


    @Override
    public void visit(NumberBinarySuffix suffix, Context c) {
        this.trapWriter.addTuple("numberbinarysuffixs", trapWriter.freshLabel(), suffix.value(), c.parent);
    }


    @Override
    public void visit(NumberLitValue numberLitValue, Context c) {
        String value;
        int kind;
        switch (numberLitValue) {
            case IntNumberLitValue litValue -> {
                value = String.valueOf(litValue.getValue());
                kind = 1;
            }
            case FloatNumberLitValue litValue -> {
                value = String.valueOf(litValue.getValue());
                kind = 2;
            }
            default -> throw new RuntimeException(numberLitValue.toString());
        }
//        Label label = ;
        this.trapWriter.addTuple("literals", trapWriter.freshLabel(), kind, c.current, value);
//        this.locationManager.emitNodeLocation(c.current, label);
    }


    @Override
    public void visit(ConfigEntryOperation op, Context c) {
        Label opLabel = this.trapWriter.freshLabel();
        int kind = switch (op) {
            case Union -> 0;
            case Override -> 1;
            case Insert -> 2;
        };
        this.trapWriter.addTuple("configentry_operation", opLabel, kind, op.symbol(), c.current);
    }


    @Override
    public void visit(BasicType basicType, Context c) {
        //strings
        visit(basicType.getValue().toString(), c.withNewIdx(0));
    }

    @Override
    public void visit(AnyType anyType, Context c) {
        //strings
        visit(anyType.getValue().toString(), c.withNewIdx(0));
    }

    @Override
    public void visit(NamedType namedType, Context c) {
        //identifiers
        visit(namedType.getValue().getIdentifier(), c.withNewIdx(0));
    }

    @Override
    public void visit(ListType listType, Context c) {
        //type
        if (listType.getValue().getInnerType().isPresent())
            visit(listType.getValue().getInnerType().get(), c.withNewIdx(0));
    }

    @Override
    public void visit(DictType dictType, Context c) {
        //type
        if (dictType.getValue().getKeyType().isPresent())
            visit(dictType.getValue().getKeyType().get(), c.withNewIdx(0));
        if (dictType.getValue().getValueType().isPresent())
            visit(dictType.getValue().getValueType().get(), c.withNewIdx(1));
    }

    @Override
    public void visit(UnionType unionType, Context c) {
        visit(unionType.getValue().getTypeElements(), c.withNewIdx(0));
    }

    @Override
    public void visit(FunctionType functionType, Context c) {
        //type
        if (functionType.getValue().getParamsTy().isPresent())
            visit(functionType.getValue().getParamsTy().get(), c.withNewIdx(0));
        if (functionType.getValue().getRetTy().isPresent())
            visit(functionType.getValue().getRetTy().get(), c.withNewIdx(1));
    }

    @Override
    public void visit(Type type, Context c) {

        if (type instanceof LiteralType literalType) {
            Label valueLabel = this.trapWriter.freshLabel();

            int kind;
            String value;
            switch (literalType.getValue()) {
                case BoolLiteralType boolLiteralType -> {
                    kind = 0;
                    value = String.valueOf(boolLiteralType.isValue());
                }
                case IntLiteralType intLiteralType -> {
                    kind = 1;
                    value = String.valueOf(intLiteralType.getValue().getValue());
                    this.trapWriter.addTuple("numberbinarysuffixs", intLiteralType.getValue().getSuffix(), valueLabel);
                }
                case FloatLiteralType floatLiteralType -> {
                    kind = 2;
                    value = String.valueOf(floatLiteralType.getValue());
                }
                case StrLiteralType strLiteralType -> {
                    kind = 3;
                    value = strLiteralType.getValue();
                }
                default -> throw new IllegalStateException("Unexpected value: " + literalType.getValue());
            }

            this.trapWriter.addTuple("literals", kind, c.current, value);
        } else {
            int kind = 0;
            String typeName = "null";
            switch (type) {
                case AnyType ignored -> {
                    kind = 0;
                    typeName = "Any";
                }
                case NamedType ignored -> {
                    kind = 1;
                    typeName = "Named";
                }
                case BasicType basicType -> {
                    switch (basicType.getValue()) {
                        case Bool -> {
                            typeName = "Bool";
                            kind = 2;
                        }
                        case Int -> {
                            typeName = "Int";
                            kind = 3;
                        }
                        case Float -> {
                            typeName = "Float";
                            kind = 4;
                        }
                        case Str -> {
                            typeName = "Str";
                            kind = 5;
                        }
                    }
                }
                case ListType ignored -> {
                    typeName = "List";
                    kind = 6;
                }
                case DictType ignored -> {
                    typeName = "Dict";
                    kind = 7;
                }
                case UnionType ignored -> {
                    typeName = "Union";
                    kind = 8;
                }
                case FunctionType ignored -> {
                    typeName = "Function";
                    kind = 9;
                }
                default -> throw new IllegalStateException("Unexpected value: " + type);
            }

            Label valueLabel = this.trapWriter.freshLabel();
            Context newContext = new Context(c.current, valueLabel, c.childIndex);

            this.trapWriter.addTuple("types", c.current, kind, valueLabel, c.parent, c.childIndex, typeName);

            switch (type) {
                case AnyType anyType -> this.visit(anyType, newContext);
                case NamedType namedType -> this.visit(namedType, newContext);
                case BasicType basicType -> this.visit(basicType, newContext);
                case ListType listType -> this.visit(listType, newContext);
                case DictType dictType -> this.visit(dictType, newContext);
                case UnionType unionType -> this.visit(unionType, newContext);
                case FunctionType functionType -> this.visit(functionType, newContext);
                default -> throw new RuntimeException(type.toString());
            }
        }
    }


    @Override
    public void visit(AugOp op, Context c) {
        if (op == null) {
            return;
        }
        Label augOp = trapWriter.freshLabel();
        int kind = switch (op) {
            case Assign -> 0;
            case Add -> 0;
            case Sub -> 1;
            case Mul -> 2;
            case Div -> 3;
            case Mod -> 4;
            case Pow -> 5;
            case FloorDiv -> 6;
            case LShift -> 7;
            case RShift -> 8;
            case BitXor -> 9;
            case BitAnd -> 10;
            case BitOr -> 11;
        };
        this.trapWriter.addTuple("augops", augOp, kind, c.parent, op.symbol());
    }


    @Override
    public void visit(Identifier identifier, Context c) {
        this.trapWriter.addTuple("identifiers", c.current, c.parent, c.childIndex, identifier.getName());

        try {
            String astID = c.getNodeId(c.current);
            Spec.Symbol identSymbol = SematicUtil.findSymbolByAstId(this.specResult, astID);
            if (identSymbol != null && identSymbol.hasTy()) {
                String schemaFullName = identSymbol.getTy().getPkgPath() + "." + identSymbol.getTy().getSchemaName();
                Spec.Symbol appConfigSymbol = SematicUtil.findSymbol(specResult,
                        specResult.getFullyQualifiedNameMapOrDefault(schemaFullName, null));
                String nameId = SematicUtil.findNodeBySymbol(this.specResult, appConfigSymbol.getDef());
                String schemaId = this.parseResult.getSchemaMap().get(nameId);
                Node<?> schemaNode = this.parseResult.getNodeMap().get(schemaId);
                this.trapWriter.addTuple("schemas", c.current, this.trapWriter.globalID(schemaNode.getId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //String
        this.visit(identifier.getPkgpath(), new Context(c.current, trapWriter.freshLabel(), 1));

        //ExprContext
        this.visit(identifier.getCtx(), new Context(c.current, trapWriter.freshLabel(), 1));
    }


    @Override
    public void visit(String node, Context c) {
        if (node == null) {
            return;
        }
        this.trapWriter.addTuple("strings", c.current, c.parent, c.childIndex, node);
    }


    @Override
    public void visit(ExprContext exprContext, Context c) {
        int kind = switch (exprContext) {
            case Load -> 0;
            case Store -> 1;
        };
        this.trapWriter.addTuple("expr_contexts", c.current, kind, c.parent);
    }


    @Override
    public void visit(Keyword keyword, Context c) {
        this.trapWriter.addTuple("keywords", c.current, c.childIndex);

        visit(keyword.getArg(), c.withNewIdx(0));
        visit(keyword.getValue(), c.withNewIdx(0));
    }

    public static class Context {
        private final TrapWriter.Label parent;
        private final TrapWriter.Label current;
        private final int childIndex;
        private final Map<Label, String> labelInfo = new HashMap<>();

        public Context(Label parent, int childIndex) {
            this.parent = parent;
            this.childIndex = childIndex;
            this.current = null;
        }

        public Context(Label parent, Label current, int childIndex) {
            this.parent = parent;
            this.current = current;
            this.childIndex = childIndex;
        }

        public Context withNewIdx(int childIndex) {
            return new Context(this.parent, this.current, childIndex);
        }

        public void pushLableInfo(Label label, String id) {
            this.labelInfo.put(label, id);
        }

        public String getNodeId(Label label) {
            return this.labelInfo.get(label);
        }
    }

}



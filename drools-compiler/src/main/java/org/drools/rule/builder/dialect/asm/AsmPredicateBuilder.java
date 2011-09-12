package org.drools.rule.builder.dialect.asm;

import org.drools.*;
import org.drools.common.*;
import org.drools.compiler.*;
import org.drools.lang.descr.*;
import org.drools.rule.*;
import org.drools.rule.builder.*;
import org.drools.spi.*;
import org.mvel2.asm.*;

import java.util.*;

import static org.drools.rule.builder.dialect.java.JavaRuleBuilderHelper.*;
import static org.mvel2.asm.Opcodes.*;

public class AsmPredicateBuilder implements PredicateBuilder {

    public void build(final RuleBuildContext context,
                      final BoundIdentifiers usedIdentifiers,
                      final Declaration[] previousDeclarations,
                      final Declaration[] localDeclarations,
                      final PredicateConstraint predicateConstraint,
                      final PredicateDescr predicateDescr,
                      final AnalysisResult analysis) {
        
        final String className = "predicate" + context.getNextId();
        predicateDescr.setClassMethodName( className );

        final Map vars = createVariableContext( className,
                                               (String) predicateDescr.getContent(),
                                               context,
                                               previousDeclarations,
                                               localDeclarations,
                                               usedIdentifiers.getGlobals() );

        generateMethodTemplate("predicateMethod", context, vars);

        byte[] bytecode = createPredicateBytecode(context, vars);
        registerInvokerBytecode(context, vars, bytecode, predicateConstraint);
    }

    private byte[] createPredicateBytecode(final RuleBuildContext ruleContext, final Map vars) {
        final String packageName = (String)vars.get("package");
        final String invokerClassName = (String)vars.get("invokerClassName");
        final String ruleClassName = (String)vars.get("ruleClassName");
        final String internalRuleClassName = (packageName + "." + ruleClassName).replace(".", "/");
        final String methodName = (String)vars.get("methodName");

        final ClassGenerator generator = new ClassGenerator(packageName + "." + invokerClassName,
                                                            ruleContext.getPackageBuilder().getRootClassLoader(),
                                                            ruleContext.getDialect("java").getPackageRegistry().getTypeResolver())
                .setInterfaces(PredicateExpression.class, CompiledInvoker.class);

        generator.addStaticField(ACC_PRIVATE + ACC_FINAL, "serialVersionUID", Long.TYPE, new Long(510L));

        generator.addDefaultConstructor(new ClassGenerator.MethodBody() {
            public void body(MethodVisitor mv) { }
        }).addMethod(ACC_PUBLIC, "createContext", generator.methodDescr(Object.class), new ClassGenerator.MethodBody() {
            public void body(MethodVisitor mv) {
                mv.visitInsn(ACONST_NULL);
                mv.visitInsn(ARETURN);
            }
        }).addMethod(ACC_PUBLIC, "hashCode", generator.methodDescr(Integer.TYPE), new ClassGenerator.MethodBody() {
            public void body(MethodVisitor mv) {
                int hashCode = (Integer)vars.get("hashCode");
                push(hashCode);
                mv.visitInsn(IRETURN);
            }
        }).addMethod(ACC_PUBLIC, "getMethodBytecode", generator.methodDescr(List.class), new ClassGenerator.MethodBody() {
            public void body(MethodVisitor mv) {
                mv.visitVarInsn(ALOAD, 0);
                invokeVirtual(Object.class, "getClass", Class.class);
                push(ruleClassName);
                push(packageName);
                push(methodName);
                push(internalRuleClassName + ".class");
                invokeStatic(Rule.class, "getMethodBytecode", List.class, Class.class, String.class, String.class, String.class, String.class);
                mv.visitInsn(ARETURN);

            }
        }).addMethod(ACC_PUBLIC, "equals", generator.methodDescr(Boolean.TYPE, Object.class),
                    new ConsequenceGenerator.EqualsMethod()
        ).addMethod(ACC_PUBLIC, "evaluate", generator.methodDescr(Boolean.TYPE, Object.class, Tuple.class, Declaration[].class, Declaration[].class, WorkingMemory.class, Object.class), new String[]{"java/lang/Exception"}, new ClassGenerator.MethodBody() {
            private int offset = 7;

            public void body(MethodVisitor mv) {
                final Declaration[] previousDeclarations = (Declaration[])vars.get("declarations");
                final String[] previousDeclarationTypes = (String[])vars.get("declarationTypes");
                final Declaration[] localDeclarations = (Declaration[])vars.get("localDeclarations");
                final String[] localDeclarationTypes = (String[])vars.get("localDeclarationTypes");
                final String[] globals = (String[])vars.get("globals");
                final String[] globalTypes = (String[])vars.get("globalTypes");

                int[] previousDeclarationsParamsPos = parseDeclarations(previousDeclarations, previousDeclarationTypes, 3, false);
                int[] localDeclarationsParamsPos = parseDeclarations(localDeclarations, localDeclarationTypes, 4, true);

                // @{ruleClassName}.@{methodName}(@foreach{previousDeclarations}, @foreach{localDeclarations}, @foreach{globals})
                StringBuilder predicateMethodDescr = new StringBuilder("(");
                for (int i = 0; i < previousDeclarations.length; i++) {
                    load(previousDeclarationsParamsPos[i]); // previousDeclarations[i]
                    predicateMethodDescr.append(typeDescr(previousDeclarationTypes[i]));
                }
                for (int i = 0; i < localDeclarations.length; i++) {
                    load(localDeclarationsParamsPos[i]); // localDeclarations[i]
                    predicateMethodDescr.append(typeDescr(localDeclarationTypes[i]));
                }

                // @foreach{type : globalTypes, identifier : globals} @{type} @{identifier} = ( @{type} ) workingMemory.getGlobal( "@{identifier}" );
                for (int i = 0; i < globals.length; i++) {
                    mv.visitVarInsn(ALOAD, 5); // workingMemory
                    push(globals[i]);
                    invokeInterface(WorkingMemory.class, "getGlobal", Object.class, String.class);
                    mv.visitTypeInsn(CHECKCAST, internalName(globalTypes[i]));
                    predicateMethodDescr.append(typeDescr(globalTypes[i]));
                }

                predicateMethodDescr.append(")Z");
                mv.visitMethodInsn(INVOKESTATIC, internalRuleClassName, methodName, predicateMethodDescr.toString());
                mv.visitInsn(IRETURN);
            }

            private int[] parseDeclarations(Declaration[] declarations, String[] declarationTypes, int declarationRegistry, boolean isLocal) {
                int[] declarationsParamsPos = new int[declarations.length];
                // DeclarationTypes[i] value[i] = (DeclarationTypes[i])localDeclarations[i].getValue((InternalWorkingMemory)workingMemory, object);
                for (int i = 0; i < declarations.length; i++) {
                    declarationsParamsPos[i] = offset;
                    mv.visitVarInsn(ALOAD, declarationRegistry); // declarations
                    push(i);
                    mv.visitInsn(AALOAD);  // declarations[i]
                    mv.visitVarInsn(ALOAD, 5); // workingMemory
                    cast(InternalWorkingMemory.class);
                    if (isLocal) {
                        mv.visitVarInsn(ALOAD, 1); // object
                    } else {
                        // tuple.get(declarations[i])).getObject()
                        mv.visitVarInsn(ALOAD, 2); // tuple
                        mv.visitVarInsn(ALOAD, declarationRegistry);
                        push(i);
                        mv.visitInsn(AALOAD);  // declarations[i]
                        invokeInterface(Tuple.class, "get", InternalFactHandle.class, Declaration.class);
                        invokeInterface(InternalFactHandle.class, "getObject", Object.class);
                    }

                    String readMethod = declarations[i].getNativeReadMethod().getName();
                    boolean isObject = readMethod.equals("getValue");
                    String returnedType = isObject ? "Ljava/lang/Object;" : typeDescr(declarationTypes[i]);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/drools/rule/Declaration", readMethod, "(Lorg/drools/common/InternalWorkingMemory;Ljava/lang/Object;)" + returnedType);
                    if (isObject) mv.visitTypeInsn(CHECKCAST, internalName(declarationTypes[i]));
                    offset += store(offset, declarationTypes[i]); // obj[i]
                }
                return declarationsParamsPos;
            }
        });

        return generator.generateBytecode();
    }
}

package org.drools.compiler.integrationtests;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.drools.compiler.compiler.PMMLCompiler;
import org.drools.compiler.compiler.PMMLCompilerFactory;
import org.drools.compiler.compiler.PackageBuilder;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.core.util.DroolsStreamUtils;
import org.drools.core.util.FileManager;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.rule.Package;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.definition.KnowledgePackage;
import org.kie.api.definition.rule.Rule;
import org.kie.api.definition.type.FactType;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;

public class KnowledgeBuilderTest {

    private FileManager fileManager;

    @Before
    public void setUp() throws Exception {
        this.fileManager = new FileManager().setUp();
    }

    @After
    public void tearDown() throws Exception {
        this.fileManager.tearDown();
    }

    @Test
    public void testCompositeKnowledgeBuilder() throws Exception {
        String rule = "package org.drools.compiler.test\n" +
                      "rule R1 when\n" +
                      "   $fieldA : FactA( $fieldB : fieldB )\n" +
                      "   FactB( this == $fieldB, fieldA == $fieldA )\n" +
                      "then\n" +
                      "end";

        String declarationA = "package org.drools.compiler.test\n" +
                              "declare FactA\n" +
                              "    fieldB: FactB\n" +
                              "end\n";

        String declarationB = "package org.drools.compiler.test\n" +
                              "declare FactB\n" +
                              "    fieldA: FactA\n" +
                              "end\n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( rule.getBytes() ), ResourceType.DRL );
        kbuilder.add( ResourceFactory.newByteArrayResource( declarationA.getBytes() ), ResourceType.DRL );
        kbuilder.add( ResourceFactory.newByteArrayResource( declarationB.getBytes() ), ResourceType.DRL );
        assertTrue( kbuilder.hasErrors() );

        kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( declarationA.getBytes() ), ResourceType.DRL );
        kbuilder.add( ResourceFactory.newByteArrayResource( declarationB.getBytes() ), ResourceType.DRL );
        kbuilder.add( ResourceFactory.newByteArrayResource( rule.getBytes() ), ResourceType.DRL );
        assertTrue( kbuilder.hasErrors() );

        KnowledgeBuilder kbuilder2 = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder2.batch()
                .add( ResourceFactory.newByteArrayResource( rule.getBytes() ), ResourceType.DRL )
                .add( ResourceFactory.newByteArrayResource( declarationA.getBytes() ), ResourceType.DRL )
                .add( ResourceFactory.newByteArrayResource(declarationB.getBytes()), ResourceType.DRL )
                .build();

        if ( kbuilder2.hasErrors() ) {
            fail( kbuilder.getErrors().toString() );
        }

        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( kbuilder2.getKnowledgePackages() );
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        FactType aType = kbase.getFactType( "org.drools.compiler.test", "FactA" );
        Object a = aType.newInstance();
        FactType bType = kbase.getFactType( "org.drools.compiler.test", "FactB" );
        Object b = bType.newInstance();
        aType.set( a, "fieldB", b );
        bType.set( b, "fieldA", a );

        // JBRULES-3683 - check that the recurisive type declaration doesn't cause a StackOverflowError
        a.toString();
        b.toString();

        ksession.insert( a );
        ksession.insert( b );

        int rules = ksession.fireAllRules();
        assertEquals( 1, rules );
    }

    @Test
    @Ignore("toString recursion of type declarations breaks phreak")
    public void testDifferentPackages() throws Exception {
        String rule = "package org.drools.compiler.test.rule\n" +
                      "import org.drools.compiler.testA.FactA\n" +
                      "import org.drools.compiler.testB.FactB\n" +
                      "rule R1 when\n" +
                      "   $fieldA : FactA( $fieldB : fieldB, bigint == 1 )\n" +
                      "   FactB( this == $fieldB, fieldA == $fieldA )\n" +
                      "then\n" +
                      "   list.add(\"OK\");" +
                      "end";

        String declarationA = "package org.drools.compiler.testA\n" +
                              "import org.drools.compiler.testB.FactB\n" +
                              "import java.math.BigInteger\n" +
                              "declare FactA\n" +
                              "    fieldB: FactB\n" +
                              "    bigint: BigInteger\n" +
                              "end\n";

        String declarationB = "package org.drools.compiler.testB\n" +
                              "import org.drools.compiler.testA.FactA\n" +
                              "global java.util.List list\n" +
                              "declare FactB\n" +
                              "    fieldA: FactA\n" +
                              "end\n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.batch()
                .type( ResourceType.DRL )
                .add( ResourceFactory.newByteArrayResource( rule.getBytes() ) )
                .add( ResourceFactory.newByteArrayResource( declarationA.getBytes() ) )
                .add( ResourceFactory.newByteArrayResource( declarationB.getBytes() ) )
                .build();


        if ( kbuilder.hasErrors() ) {
            fail( kbuilder.getErrors().toString() );
        }

        String declarationC = "package org.drools.compiler.testA\n" +
                              "declare FactC\n" +
                              "    field : UnknownClass\n" +
                              "end\n";

        kbuilder.add( ResourceFactory.newByteArrayResource( declarationC.getBytes() ), ResourceType.DRL );

        assertTrue( kbuilder.hasErrors() );
        kbuilder.undo();
        assertFalse( kbuilder.hasErrors() );

        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( kbuilder.getKnowledgePackages() );
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        List list = new ArrayList();
        ksession.setGlobal( "list", list );

        FactType aType = kbase.getFactType( "org.drools.compiler.testA", "FactA" );
        Object a = aType.newInstance();
        FactType bType = kbase.getFactType( "org.drools.compiler.testB", "FactB" );
        Object b = bType.newInstance();
        aType.set( a, "fieldB", b );
        aType.set( a, "bigint", new BigInteger( "1" ) );
        bType.set( b, "fieldA", a );
        ksession.insert( a );
        ksession.insert( b );

        int rules = ksession.fireAllRules();
        assertEquals( 1, rules );
        assertEquals( "OK", list.get( 0 ) );
    }

    @Test
    public void testUndoTypeDeclaration() throws Exception {
        String rule = "package org.drools.compiler.test\n" +
                      "import org.drools.compiler.test.FactA\n" +
                      "import org.drools.compiler.test.FactB\n" +
                      "rule R1 when\n" +
                      "   FactA( i == 1 )\n" +
                      "   FactB( i == 1 )\n" +
                      "then\n" +
                      "   list.add(\"OK\");" +
                      "end\n";

        String declarationA = "package org.drools.compiler.test\n" +
                              "global java.util.List list\n" +
                              "declare FactA\n" +
                              "    j : int\n" +
                              "end\n";

        String declarationB = "package org.drools.compiler.test\n" +
                              "declare FactB\n" +
                              "    i : int\n" +
                              "end\n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( declarationB.getBytes() ), ResourceType.DRL );
        assertFalse( kbuilder.hasErrors() );

        kbuilder.batch()
                .type( ResourceType.DRL )
                .add( ResourceFactory.newByteArrayResource( rule.getBytes() ) )
                .add( ResourceFactory.newByteArrayResource( declarationA.getBytes() ) )
                .build();

        assertTrue( kbuilder.hasErrors() );
        kbuilder.undo();
        assertFalse( kbuilder.hasErrors() );

        declarationA = "package org.drools.compiler.test\n" +
                       "global java.util.List list\n" +
                       "declare FactA\n" +
                       "    i : int\n" +
                       "end\n";

        kbuilder.batch()
                .type( ResourceType.DRL )
                .add( ResourceFactory.newByteArrayResource( rule.getBytes() ) )
                .add( ResourceFactory.newByteArrayResource( declarationA.getBytes() ) )
                .build();

        assertFalse( kbuilder.hasErrors() );

        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( kbuilder.getKnowledgePackages() );
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        List list = new ArrayList();
        ksession.setGlobal( "list", list );

        FactType aType = kbase.getFactType( "org.drools.compiler.test", "FactA" );
        Object a = aType.newInstance();
        aType.set( a, "i", 1 );

        FactType bType = kbase.getFactType( "org.drools.compiler.test", "FactB" );
        Object b = bType.newInstance();
        bType.set( b, "i", 1 );

        ksession.insert( a );
        ksession.insert( b );

        int rules = ksession.fireAllRules();
        assertEquals( 1, rules );
        assertEquals( "OK", list.get( 0 ) );
    }

    @Test
    public void testUndoRule() throws Exception {
        String rule = "package org.drools.compiler.test\n" +
                      "global java.util.List list\n" +
                      "import org.drools.compiler.test.FactA\n" +
                      "import org.drools.compiler.test.FactB\n" +
                      "rule R1 when\n" +
                      "   FactA( j == 1 )\n" +
                      "   FactB( i == 1 )\n" +
                      "then\n" +
                      "   list.add(\"OK\");" +
                      "end\n";

        String declarationA = "package org.drools.compiler.test\n" +
                              "declare FactA\n" +
                              "    i : int\n" +
                              "end\n";

        String declarationB = "package org.drools.compiler.test\n" +
                              "declare FactB\n" +
                              "    i : int\n" +
                              "end\n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( declarationB.getBytes() ), ResourceType.DRL );
        assertFalse( kbuilder.hasErrors() );

        kbuilder.batch()
                .type( ResourceType.DRL )
                .add( ResourceFactory.newByteArrayResource( rule.getBytes() ) )
                .add( ResourceFactory.newByteArrayResource( declarationA.getBytes() ) )
                .build();

        assertTrue( kbuilder.hasErrors() );
        kbuilder.undo();
        assertFalse( kbuilder.hasErrors() );

        rule = "package org.drools.compiler.test\n" +
               "global java.util.List list\n" +
               "import org.drools.compiler.test.FactA\n" +
               "import org.drools.compiler.test.FactB\n" +
               "rule R1 when\n" +
               "   FactA( i == 1 )\n" +
               "   FactB( i == 1 )\n" +
               "then\n" +
               "   list.add(\"OK\");" +
               "end\n";

        kbuilder.batch()
                .type( ResourceType.DRL )
                .add( ResourceFactory.newByteArrayResource( rule.getBytes() ) )
                .add( ResourceFactory.newByteArrayResource( declarationA.getBytes() ) )
                .build();

        assertFalse( kbuilder.hasErrors() );

        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( kbuilder.getKnowledgePackages() );
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        List list = new ArrayList();
        ksession.setGlobal( "list", list );

        FactType aType = kbase.getFactType( "org.drools.compiler.test", "FactA" );
        Object a = aType.newInstance();
        aType.set( a, "i", 1 );

        FactType bType = kbase.getFactType( "org.drools.compiler.test", "FactB" );
        Object b = bType.newInstance();
        bType.set( b, "i", 1 );

        ksession.insert( a );
        ksession.insert( b );

        int rules = ksession.fireAllRules();
        assertEquals( 1, rules );
        assertEquals( "OK", list.get( 0 ) );
    }

    @Test
    public void testAddKPackageSingle() throws Exception {
        String rule = "package org.drools.compiler.test\n" +
                      "import org.drools.compiler.StockTick\n" +
                      "rule R1 when\n" +
                      "   StockTick()\n" +
                      "then\n" +
                      "end\n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( rule.getBytes() ), ResourceType.DRL );
        assertFalse( kbuilder.getErrors().toString(), kbuilder.hasErrors() );

        Collection<KnowledgePackage> kpkgs = kbuilder.getKnowledgePackages();
        assertEquals( 1, kpkgs.size() );

        KnowledgePackage kpkg = kpkgs.iterator().next();

        byte[] skpkg = DroolsStreamUtils.streamOut( kpkg );

        kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( skpkg ), ResourceType.PKG );
        assertFalse( kbuilder.getErrors().toString(), kbuilder.hasErrors() );

        kpkgs = kbuilder.getKnowledgePackages();
        assertEquals( 1, kpkgs.size() );
        kpkg = kpkgs.iterator().next();
        assertEquals( 1, kpkg.getRules().size() );
    }

    @Test
    public void testAddKPackageCollection() throws Exception {
        String rule = "package org.drools.compiler.test\n" +
                      "import org.drools.compiler.StockTick\n" +
                      "declare StockTick @role(event) end\n" +
                      "rule R1 when\n" +
                      "   StockTick()\n" +
                      "then\n" +
                      "end\n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( rule.getBytes() ), ResourceType.DRL );
        assertFalse( kbuilder.getErrors().toString(), kbuilder.hasErrors() );

        Collection<KnowledgePackage> kpkgs = kbuilder.getKnowledgePackages();
        assertEquals( 2, kpkgs.size() );

        byte[] skpkg = DroolsStreamUtils.streamOut( kpkgs );

        kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( skpkg ), ResourceType.PKG );
        assertFalse( kbuilder.getErrors().toString(), kbuilder.hasErrors() );

        kpkgs = kbuilder.getKnowledgePackages();
        assertEquals( 2, kpkgs.size() );
    }

    @Test
    public void testAddPackageSingle() throws Exception {
        String rule = "package org.drools.compiler.test\n" +
                      "import org.drools.compiler.StockTick\n" +
                      "rule R1 when\n" +
                      "   StockTick()\n" +
                      "then\n" +
                      "end\n";

        PackageBuilder pkgbuilder = new PackageBuilder();
        pkgbuilder.addPackageFromDrl( new StringReader( rule ) );
        assertFalse( pkgbuilder.getErrors().toString(), pkgbuilder.hasErrors() );

        Package pkg = pkgbuilder.getPackage();

        byte[] spkg = DroolsStreamUtils.streamOut( pkg );

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( spkg ), ResourceType.PKG );
        assertFalse( kbuilder.getErrors().toString(), kbuilder.hasErrors() );

        Collection<KnowledgePackage> kpkgs = kbuilder.getKnowledgePackages();
        assertEquals( 1, kpkgs.size() );
        KnowledgePackage kpkg = kpkgs.iterator().next();
        assertEquals( 1, kpkg.getRules().size() );
    }

    @Test
    public void testAddPackageArray() throws Exception {
        String rule = "package org.drools.compiler.test\n" +
                      "import org.drools.compiler.StockTick\n" +
                      "declare StockTick @role(event) end\n" +
                      "rule R1 when\n" +
                      "   StockTick()\n" +
                      "then\n" +
                      "end\n";

        PackageBuilder pkgbuilder = new PackageBuilder();
        pkgbuilder.addPackageFromDrl( new StringReader( rule ) );
        assertFalse( pkgbuilder.getErrors().toString(), pkgbuilder.hasErrors() );

        Package[] pkgs = pkgbuilder.getPackages();

        byte[] spkgs = DroolsStreamUtils.streamOut( pkgs );

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( spkgs ), ResourceType.PKG );
        assertFalse( kbuilder.getErrors().toString(), kbuilder.hasErrors() );

        Collection<KnowledgePackage> kpkgs = kbuilder.getKnowledgePackages();
        assertEquals( 2, kpkgs.size() );
    }


    @Test
    public void testResourceMapping() throws Exception {
        String rule = "package org.drools.compiler.test\n" +
                "rule R1 when\n" +
                " \n" +
                "then\n" +
                "end\n";

        Resource res1 = ResourceFactory.newByteArrayResource( rule.getBytes() );
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( res1, ResourceType.DRL );
        assertFalse( kbuilder.getErrors().toString(), kbuilder.hasErrors() );

        KnowledgePackage kp1 = kbuilder.getKnowledgePackages().iterator().next();
        assertEquals( 1, kp1.getRules().size() );
        Rule r = kp1.getRules().iterator().next();
        assertEquals( res1, ((RuleImpl) r).getRule().getResource() );

        String pmml = "<PMML version=\"4.0\"><Header/></PMML>";

        Resource res2 = ResourceFactory.newByteArrayResource( pmml.getBytes() );
        KnowledgeBuilder kbuilder2 = KnowledgeBuilderFactory.newKnowledgeBuilder();

        PMMLCompilerFactory.setProvider(new PMMLCompiler() {
            public String compile(InputStream stream, Map<String, PackageRegistry> registries) {
                return "rule R2 when then end";
            }
        });

        kbuilder2.add( res2, ResourceType.PMML );
        assertFalse( kbuilder2.getErrors().toString(), kbuilder2.hasErrors() );

        KnowledgePackage kp2 = kbuilder2.getKnowledgePackages().iterator().next();
        assertEquals( 1, kp2.getRules().size() );
        Rule r2 = kp2.getRules().iterator().next();
        assertEquals( res2, ((RuleImpl) r2).getRule().getResource() );

    }
}

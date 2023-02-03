/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Testing Classfile building module.
 * @run junit ModuleBuilderTest
 */
import jdk.internal.classfile.*;

import jdk.internal.classfile.attribute.ModuleAttribute;
import jdk.internal.classfile.attribute.ModuleExportInfo;
import jdk.internal.classfile.attribute.ModuleMainClassAttribute;
import jdk.internal.classfile.attribute.ModuleOpenInfo;
import jdk.internal.classfile.attribute.ModulePackagesAttribute;
import jdk.internal.classfile.attribute.ModuleProvideInfo;
import jdk.internal.classfile.attribute.ModuleRequireInfo;
import jdk.internal.classfile.Attributes;
import jdk.internal.classfile.jdktypes.ModuleDesc;
import jdk.internal.classfile.jdktypes.PackageDesc;
import org.junit.jupiter.api.Test;

import java.lang.constant.ClassDesc;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleBuilderTest {
    private final ModuleDesc modName = ModuleDesc.of("some.module.structure");
    private final String modVsn = "ab75";
    private final ModuleDesc require1 = ModuleDesc.of("1require.some.mod"); String vsn1 = "1the.best.version";
    private final ModuleDesc require2 = ModuleDesc.of("2require.some.mod"); String vsn2 = "2the.best.version";
    private final ModuleDesc[] et1 = new ModuleDesc[] {ModuleDesc.of("1t1"), ModuleDesc.of("1t2")};
    private final ModuleDesc[] et2 = new ModuleDesc[] {ModuleDesc.of("2t1")};
    private final ModuleDesc[] et3 = new ModuleDesc[] {ModuleDesc.of("3t1"), ModuleDesc.of("3t2"), ModuleDesc.of("3t3")};
    private final ModuleDesc[] ot3 = new ModuleDesc[] {ModuleDesc.of("t1"), ModuleDesc.of("t2")};

    private final ClassModel moduleModel;
    private final ModuleAttribute attr;

    public ModuleBuilderTest() {
        byte[] modInfo = Classfile.buildModule(
                ModuleAttribute.of(modName, mb -> mb
                        .moduleVersion(modVsn)

                        .requires(require1, 77, vsn1)
                        .requires(require2, 99, vsn2)

                        .exports(PackageDesc.of("0"), 0, et1)
                        .exports(PackageDesc.of("1"), 1, et2)
                        .exports(PackageDesc.of("2"), 2, et3)
                        .exports(PackageDesc.of("3"), 3)
                        .exports(PackageDesc.of("4"), 4)

                        .opens(PackageDesc.of("o0"), 0)
                        .opens(PackageDesc.of("o1"), 1)
                        .opens(PackageDesc.of("o2"), 2, ot3)

                        .uses(ClassDesc.of("some.Service"))
                        .uses(ClassDesc.of("another.Service"))

                        .provides(ClassDesc.of("some.nice.Feature"), ClassDesc.of("impl"), ClassDesc.of("another.impl"))),
                List.of(PackageDesc.of("foo.bar.baz"), PackageDesc.of("quux"), PackageDesc.of("foo.bar.baz"), PackageDesc.of("quux")),
                clb -> clb.with(ModuleMainClassAttribute.of(ClassDesc.of("main.Class")))
                          .with(ModuleMainClassAttribute.of(ClassDesc.of("overwritten.main.Class"))));
        moduleModel = Classfile.parse(modInfo);
        attr = ((ModuleAttribute) moduleModel.attributes().stream()
                .filter(a -> a.attributeMapper() == Attributes.MODULE)
                .findFirst()
                .orElseThrow());
    }

    @Test
    void testCreateModuleInfo() {
        // Build the module-info.class bytes
        byte[] modBytes = Classfile.buildModule(ModuleAttribute.of(modName, mb -> mb.moduleVersion(modVsn)));

        // Verify
        var cm = Classfile.parse(modBytes);

        var attr =cm.findAttribute(Attributes.MODULE).get();
        assertEquals(modName.moduleName(), attr.moduleName().name().stringValue());
        assertEquals(0, attr.moduleFlagsMask());
        assertEquals(modVsn, attr.moduleVersion().get().stringValue());
    }

    @Test
    void testAllAttributes() {
        assertEquals(3, moduleModel.attributes().size());
    }

    @Test
    void testVerifyRequires() {
        assertEquals(2, attr.requires().size());
        ModuleRequireInfo r = attr.requires().get(0);
        assertEquals(require1.moduleName(), r.requires().name().stringValue());
        assertEquals(vsn1, r.requiresVersion().get().stringValue());
        assertEquals(77, r.requiresFlagsMask());

        r = attr.requires().get(1);
        assertEquals(require2.moduleName(), r.requires().name().stringValue());
        assertEquals(vsn2, r.requiresVersion().get().stringValue());
        assertEquals(99, r.requiresFlagsMask());
    }

    @Test
    void testVerifyExports() {
        List<ModuleExportInfo> exports = attr.exports();
        assertEquals(5, exports.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, exports.get(i).exportsFlagsMask());
            assertEquals(String.valueOf(i), exports.get(i).exportedPackage().name().stringValue());
        }
        assertEquals(2, exports.get(0).exportsTo().size());
        for (int i = 0; i < 2; i++) {
            assertEquals(exports.get(0).exportsTo().get(i).name().stringValue(), et1[i].moduleName());
        }

        assertEquals(1, exports.get(1).exportsTo().size());
        assertEquals(et2[0].moduleName(), exports.get(1).exportsTo().get(0).name().stringValue());

        assertEquals(3, exports.get(2).exportsTo().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(exports.get(2).exportsTo().get(i).name().stringValue(), et3[i].moduleName());
        }

        assertEquals(0, exports.get(3).exportsTo().size());
        assertEquals(0, exports.get(4).exportsTo().size());
    }

    @Test
    void testVerifyOpens() {
        List<ModuleOpenInfo> opens = attr.opens();
        assertEquals(3, opens.size());
        assertEquals(0, opens.get(0).opensTo().size());
        assertEquals(0, opens.get(1).opensTo().size());
        assertEquals(2, opens.get(2).opensTo().size());
        assertEquals(2, opens.get(2).opensFlagsMask());
        assertEquals(ot3[1].moduleName(), opens.get(2).opensTo().get(1).name().stringValue());
    }

    @Test
    void testVerifyUses() {
        var uses = attr.uses();
        assertEquals(2, uses.size());
        assertEquals("another/Service", uses.get(1).asInternalName());
    }

    @Test
    void testVerifyProvides() {
        var provides = attr.provides();
        assertEquals(1, provides.size());
        ModuleProvideInfo p = provides.get(0);
        assertEquals("some/nice/Feature", p.provides().asInternalName());
        assertEquals(2, p.providesWith().size());
        assertEquals("another/impl", p.providesWith().get(1).asInternalName());
    }

    @Test
    void verifyPackages() {
        ModulePackagesAttribute a = moduleModel.findAttribute(Attributes.MODULE_PACKAGES).orElseThrow();
        assertEquals(List.of("0", "1", "2", "3", "4", "o0", "o1", "o2", "foo.bar.baz", "quux"), a.packages().stream().map(pe -> pe.asSymbol().packageName()).toList());
    }

    @Test
    void verifyMainclass() {
        ModuleMainClassAttribute a = moduleModel.findAttribute(Attributes.MODULE_MAIN_CLASS).orElseThrow();
        assertEquals("overwritten/main/Class", a.mainClass().asInternalName());
    }

    @Test
    void verifyIsModuleInfo() throws Exception {
        assertTrue(moduleModel.isModuleInfo());

        ClassModel m = Classfile.parse(Paths.get(URI.create(ModuleBuilderTest.class.getResource("ModuleBuilderTest.class").toString())));
        assertFalse(m.isModuleInfo());
    }
}

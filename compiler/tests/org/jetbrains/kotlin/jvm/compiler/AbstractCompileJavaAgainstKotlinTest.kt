/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.jvm.compiler

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.AnnotationArgumentsRenderingPolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.DescriptorRendererModifier
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.lazy.JvmResolveUtil
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.kotlin.test.KotlinTestUtils.*
import org.jetbrains.kotlin.test.TestCaseWithTmpdir
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.test.util.RecursiveDescriptorComparator.validateAndCompareDescriptorWithFile
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.Assert
import java.io.File
import java.io.IOException
import java.lang.annotation.Retention

abstract class AbstractCompileJavaAgainstKotlinTest : TestCaseWithTmpdir() {

    @Throws(IOException::class)
    protected fun doTest(ktFilePath: String) {
        Assert.assertTrue(ktFilePath.endsWith(".kt"))
        val ktFile = File(ktFilePath)
        val javaFile = File(ktFilePath.replaceFirst("\\.kt$".toRegex(), ".java"))
        val javaErrorFile = File(ktFilePath.replaceFirst("\\.kt$".toRegex(), ".javaerr.txt"))

        val out = File(tmpdir, "out")
        val compiledSuccessfully = compileKotlinWithJava(listOf(javaFile), listOf(ktFile),
                                                         out, testRootDisposable, javaErrorFile)
        if (!compiledSuccessfully) return

        val environment = KotlinCoreEnvironment.createForTests(
                testRootDisposable,
                newConfiguration(ConfigurationKind.ALL, TestJdkKind.FULL_JDK, getAnnotationsJar(), out),
                EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        val analysisResult = JvmResolveUtil.analyze(environment)
        val packageView = analysisResult.moduleDescriptor.getPackage(LoadDescriptorUtil.TEST_PACKAGE_FQNAME)
        assertFalse("Nothing found in package " + LoadDescriptorUtil.TEST_PACKAGE_FQNAME, packageView.isEmpty())

        val expectedFile = File(ktFilePath.replaceFirst("\\.kt$".toRegex(), ".txt"))
        validateAndCompareDescriptorWithFile(packageView, CONFIGURATION, expectedFile)
    }

    companion object {
        // Do not render parameter names because there are test cases where classes inherit from JDK collections,
        // and some versions of JDK have debug information in the class files (including parameter names), and some don't
        private val CONFIGURATION = AbstractLoadJavaTest.COMPARATOR_CONFIGURATION.withRenderer(
                DescriptorRenderer.withOptions {
                    withDefinedIn = false
                    parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
                    verbose = true
                    annotationArgumentsRenderingPolicy = AnnotationArgumentsRenderingPolicy.UNLESS_EMPTY
                    excludedAnnotationClasses = setOf(FqName(Retention::class.java.name))
                    modifiers = DescriptorRendererModifier.ALL
                }
        )
    }
}

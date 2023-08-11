/*
 * Copyright 2023 Gamekins contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gamekins.util

import hudson.FilePath
import hudson.model.Run
import hudson.tasks.junit.TestResultAction
import org.gamekins.test.TestUtils
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File

class JUnitUtilTest : FeatureSpec({

    lateinit var root : String
    lateinit var path : FilePath

    beforeSpec {
        //Needed because of bug in mockk library which does not release mocked objects
        mockkStatic(JUnitUtil::class)
        val rootDirectory = javaClass.classLoader.getResource("test-project.zip")
        rootDirectory shouldNotBe null
        root = rootDirectory!!.file.removeSuffix(".zip")
        root shouldEndWith "test-project"
        TestUtils.unzip("$root.zip", root)
        path = FilePath(null, root)
    }

    afterSpec {
        unmockkAll()
        File(root).deleteRecursively()
    }

    feature("getTestCount") {

        scenario("test-project, path only")
        {
            JUnitUtil.getTestCount(path) shouldBe 5
        }

        scenario("run is null")
        {
            JUnitUtil.getTestCount(null, null) shouldBe 0
        }

        val run = mockkClass(Run::class)
        every { run.getAction(TestResultAction::class.java) } returns null
        scenario("run has not TestResultAction")
        {
            JUnitUtil.getTestCount(null, run) shouldBe 0
        }

        val action = mockkClass(TestResultAction::class)
        every { action.totalCount } returns 5
        every { run.getAction(TestResultAction::class.java) } returns action
        scenario("run with TestResultAction")
        {
            JUnitUtil.getTestCount(null, run) shouldBe 5
        }

        scenario("test-project")
        {
            JUnitUtil.getTestCount(path, null) shouldBe 5
        }
    }

    feature("getTestFailCount") {

        scenario("test-project, path only")
        {
            JUnitUtil.getTestFailCount(path) shouldBe 0
        }

        scenario("run is null")
        {
            JUnitUtil.getTestFailCount(null, null) shouldBe 0
        }

        val run = mockkClass(Run::class)
        every { run.getAction(TestResultAction::class.java) } returns null
        scenario("run has not TestResultAction")
        {
            JUnitUtil.getTestFailCount(null, run) shouldBe 0
        }

        val action = mockkClass(TestResultAction::class)
        every { action.failCount } returns 1
        every { run.getAction(TestResultAction::class.java) } returns action
        scenario("run with TestResultAction")
        {
            JUnitUtil.getTestFailCount(null, run) shouldBe 1
        }

        scenario("test-project")
        {
            JUnitUtil.getTestFailCount(path, null) shouldBe 0
        }
    }

    feature("getTestNames") {
        val set = hashSetOf("nestedWhileForLoops", "nestedForLoops")
        JUnitUtil.getTestNames(FilePath(File("$root/target/surefire-reports/TEST-com.example.NestedLoopTest.xml"))) shouldBe set
    }
})
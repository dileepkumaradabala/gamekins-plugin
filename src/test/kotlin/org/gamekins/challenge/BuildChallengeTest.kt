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

package org.gamekins.challenge

import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.unmockkAll
import org.gamekins.util.Constants.Parameters

class BuildChallengeTest : FeatureSpec({

    lateinit var challenge : BuildChallenge

    beforeSpec {
        challenge = BuildChallenge(Parameters(branch = "main"))
    }

    afterSpec {
        unmockkAll()
    }

    feature("isSolvable")
    {
        val run = mockkClass(Run::class)
        val parameters = Parameters()
        val listener = TaskListener.NULL

        challenge.isSolvable(parameters, run, listener) shouldBe true
    }

    feature("isSolved") {
        val run = mockkClass(Run::class)
        val parameters = Parameters()
        val listener = TaskListener.NULL

        every { run.result } returns Result.FAILURE
        scenario("Build Failed")
        {
            challenge.isSolved(parameters, run, listener) shouldBe false
        }

        every { run.result } returns Result.SUCCESS
        scenario("Build Successful")
        {
            challenge.isSolved(parameters, run, listener) shouldBe true
            challenge.getSolved() shouldNotBe 0
            challenge.getScore() shouldBe 1
        }
    }

    feature("printToXML") {
        scenario("No Reason, no Indentation")
        {
            challenge.printToXML("", "") shouldBe
                    "<BuildChallenge created=\"${challenge.getCreated()}\" solved=\"${challenge.getSolved()}\" branch=\"main\"/>"
        }

        scenario("No Reason, with Indentation")
        {
            challenge.printToXML("", "    ") shouldStartWith "    <"
        }

        scenario("With Reason, no Indentation")
        {
            challenge.printToXML("test", "") shouldBe
                    "<BuildChallenge created=\"${challenge.getCreated()}\" solved=\"${challenge.getSolved()}\" branch=\"main\" reason=\"test\"/>"
        }
    }

    feature("toString")
    {
        challenge.toString() shouldBe "Let the Build run successfully in branch main"
    }
})
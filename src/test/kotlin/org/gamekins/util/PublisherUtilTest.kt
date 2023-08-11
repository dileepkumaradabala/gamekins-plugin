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

import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor
import hudson.FilePath
import hudson.model.Job
import hudson.model.Result
import hudson.model.User
import hudson.util.DescribableList
import hudson.util.StreamTaskListener
import org.gamekins.challenge.ChallengeFactory
import org.gamekins.challenge.LineCoverageChallenge
import org.gamekins.statistics.Statistics
import org.gamekins.test.TestUtils
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import jenkins.branch.MultiBranchProject
import org.gamekins.achievement.Achievement
import org.gamekins.challenge.quest.QuestFactory
import org.gamekins.file.SourceFileDetails
import org.gamekins.questtask.QuestTaskFactory
import org.gamekins.util.Constants.Parameters
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject
import java.io.File
import java.lang.NullPointerException
import java.util.concurrent.CopyOnWriteArrayList

class PublisherUtilTest : FeatureSpec( {

    lateinit var root : String
    lateinit var path : FilePath
    val jacocoResultsPath = "**/target/site/jacoco/"
    val jacocoCSVPath = "**/target/site/jacoco/jacoco.csv"
    val run = mockkClass(hudson.model.Run::class)
    val parameters = Parameters()
    val job = mockkClass(Job::class)
    val project = mockkClass(WorkflowMultiBranchProject::class)
    val multiProject = mockkClass(MultiBranchProject::class)
    val multiProperty = mockkClass(org.gamekins.property.GameMultiBranchProperty::class)
    val property = mockkClass(org.gamekins.property.GameJobProperty::class)
    val descList = mockkClass(DescribableList::class)
    val descList2 = mockkClass(DescribableList::class)
    val statistics = mockkClass(Statistics::class)
    val classDetails = mockkClass(SourceFileDetails::class)
    val user = mockkClass(hudson.model.User::class)
    val userProperty = mockkClass(org.gamekins.GameUserProperty::class)
    val challenge = mockkClass(LineCoverageChallenge::class)

    beforeSpec {
        val rootDirectory = javaClass.classLoader.getResource("test-project.zip")
        rootDirectory shouldNotBe null
        root = rootDirectory.file.removeSuffix(".zip")
        root shouldEndWith "test-project"
        TestUtils.unzip("$root.zip", root)
        path = FilePath(null, root)

        parameters.branch = "master"
        parameters.jacocoCSVPath = jacocoCSVPath
        parameters.projectName = "test-project"
        parameters.projectTests = 100
        parameters.projectCoverage = 80.0
        parameters.workspace = path

        every { run.parent } returns job
        every { job.parent } returns project
        every { project.properties } returns
                descList as DescribableList<AbstractFolderProperty<*>, AbstractFolderPropertyDescriptor>?
        every { descList.get(org.gamekins.property.GameMultiBranchProperty::class.java) } returns
                multiProperty
        every { multiProperty.getStatistics() } returns statistics
        every { multiProperty.owner } returns project
        every { project.save() } returns Unit
        every { statistics.addRunEntry(any(), any(), any(), any()) } returns Unit
        every { run.getNumber() } returns 1
        every { run.result } returns Result.SUCCESS
        every { run.startTimeInMillis } returns 0

        mockkStatic(JacocoUtil::class)
        mockkStatic(JUnitUtil::class)
        mockkStatic(PublisherUtil::class)
        mockkStatic(User::class)
        every { JUnitUtil.getTestCount(any(), any()) } returns 4
        every { JacocoUtil.getProjectCoverage(any(), any()) } returns 0.4

        every { job.getProperty(org.gamekins.property.GameJobProperty::class.java.name) } returns
                property
        every { property.getStatistics() } returns statistics
        every { property.getOwner() } returns multiProject
        every { multiProject.save() } returns Unit

        mockkStatic(GitUtil::class)
        every { GitUtil.getLastChangedClasses(any(), any(), any(), any()) } returns arrayListOf(classDetails)

        mockkStatic(PropertyUtil::class)
        mockkStatic(ChallengeFactory::class)
        mockkStatic(QuestFactory::class)
        mockkStatic(QuestTaskFactory::class)
        every { user.properties } returns mapOf()
        every { user.fullName } returns "Name"
        every { user.save() } returns Unit
    }

    afterSpec {
        unmockkAll()
        File(root).deleteRecursively()
    }

    /**
     * There is some real issue with mocking non-static object methods.
     */
    feature("checkUser") {
        val map = hashMapOf("generated" to 0, "solved" to 0, "solvedAchievements" to 0, "generatedQuests" to 0, "solvedQuests" to 0, "generatedQuestTasks" to 0, "solvedQuestTasks" to 0)

        every { PropertyUtil.realUser(user) } returns false
        scenario("User is not real")
        {
            PublisherUtil.checkUser(user, run, arrayListOf(classDetails), parameters, Result.SUCCESS) shouldBe map
        }

        every { user.getProperty(org.gamekins.GameUserProperty::class.java) } returns null
        every { PropertyUtil.realUser(user) } returns true
        scenario("User has no GameUserProperty")
        {
            PublisherUtil.checkUser(user, run, arrayListOf(classDetails), parameters, Result.SUCCESS) shouldBe map
        }

        every { user.getProperty(org.gamekins.GameUserProperty::class.java) } returns userProperty
        every { userProperty.isParticipating(any()) } returns false
        scenario("User is not participating")
        {
            PublisherUtil.checkUser(user, run, arrayListOf(classDetails), parameters, Result.SUCCESS) shouldBe map
        }

        every { userProperty.isParticipating(any()) } returns true
        every { ChallengeFactory.generateBuildChallenge(any(), any(), any(), any(), any()) } returns true
        every { userProperty.getCurrentChallenges(any()) } returns CopyOnWriteArrayList()
        every { userProperty.getStoredChallenges(any()) } returns CopyOnWriteArrayList()
        every { userProperty.getUnsolvedAchievements(any()) } returns CopyOnWriteArrayList()
        every { userProperty.getCurrentQuests(any()) } returns CopyOnWriteArrayList()
        every { userProperty.getCurrentQuestTasks(any()) } returns CopyOnWriteArrayList()
        every { ChallengeFactory.generateNewChallenges(any(), any(), any(), any(), any()) } returns 0
        every { QuestFactory.generateNewQuests(any(), any(), any(), any(), any()) } returns 0
        every { QuestTaskFactory.generateNewQuestTasks(any(), any(), any(), any(), any()) } returns 0
        scenario("BuildChallenge generated")
        {
            PublisherUtil.checkUser(user, run, arrayListOf(classDetails), parameters, Result.SUCCESS) shouldBe hashMapOf("generated" to 1, "solved" to 0, "solvedAchievements" to 0, "generatedQuests" to 0, "solvedQuests" to 0, "generatedQuestTasks" to 0, "solvedQuestTasks" to 0)
        }

        every { ChallengeFactory.generateBuildChallenge(any(), any(), any(), any(), any()) } returns false
        every { challenge.isSolved(any(), any(), any()) } returns false
        every { challenge.isSolvable(any(), any(), any()) } returns true
        every { userProperty.getCurrentChallenges(any()) } returns CopyOnWriteArrayList(listOf(challenge))
        scenario("User is valid, but nothing changed")
        {
            PublisherUtil.checkUser(user, run, arrayListOf(classDetails), parameters, Result.SUCCESS) shouldBe map
        }

        every { challenge.isSolved(any(), any(), any()) } returns true
        every { challenge.isSolvable(any(), any(), any()) } returns false
        every { challenge.getScore() } returns 1
        every { challenge.toEscapedString() } returns ""
        every { userProperty.completeChallenge(any(), any()) } returns Unit
        every { userProperty.addScore(any(), any()) } returns Unit
        every { userProperty.rejectChallenge(any(), any(), any()) } returns Unit
        every { userProperty.getUser() } returns user
        scenario("Solved challenge")
        {
            PublisherUtil.checkUser(user, run, arrayListOf(classDetails), parameters, Result.SUCCESS) shouldBe hashMapOf("generated" to 0, "solved" to 1, "solvedAchievements" to 0, "generatedQuests" to 0, "solvedQuests" to 0, "generatedQuestTasks" to 0, "solvedQuestTasks" to 0)
        }

        val achievement = mockkClass(Achievement::class)
        every { achievement.isSolved(any(), any(), any(), any(), any()) } returns false
        every { userProperty.getUnsolvedAchievements(any()) } returns CopyOnWriteArrayList(listOf(achievement))
        every { userProperty.completeAchievement(any(), any()) } returns Unit
        scenario("Solved Achievement")
        {
            PublisherUtil.checkUser(user, run, arrayListOf(classDetails), parameters, Result.SUCCESS) shouldBe hashMapOf("generated" to 0, "solved" to 1, "solvedAchievements" to 0, "generatedQuests" to 0, "solvedQuests" to 0, "generatedQuestTasks" to 0, "solvedQuestTasks" to 0)
        }

        every { achievement.isSolved(any(), any(), any(), any(), any()) } returns true
        scenario("Has solved Achievement")
        {
            PublisherUtil.checkUser(user, run, arrayListOf(classDetails), parameters, Result.SUCCESS) shouldBe hashMapOf("generated" to 0, "solved" to 1, "solvedAchievements" to 1, "generatedQuests" to 0, "solvedQuests" to 0, "generatedQuestTasks" to 0, "solvedQuestTasks" to 0)
        }
    }

    feature("doCheckJacocoCSVPath") {
        scenario("Folder contains file")
        {
            PublisherUtil.doCheckJacocoCSVPath(path, jacocoCSVPath) shouldBe true
        }

        scenario("Folder does not contain file")
        {
            PublisherUtil.doCheckJacocoCSVPath(FilePath(null, path.remote + "/src"), jacocoCSVPath) shouldBe false
        }
    }

    feature("doCheckJacocoCSVPath OSCompatibility") {
        val filepathLinux = mockkClass(FilePath::class)
        val pathLinux = mockkClass(FilePath::class)
        val listLinux = arrayListOf(filepathLinux)
        every { pathLinux.act(any<JacocoUtil.FilesOfAllSubDirectoriesCallable>()) } returns listLinux
        every { filepathLinux.remote } returns jacocoCSVPath
        scenario("Linux-style path")
        {
            PublisherUtil.doCheckJacocoCSVPath(pathLinux, jacocoCSVPath) shouldBe true
        }

        val filepathWin = mockkClass(FilePath::class)
        val pathWin = mockkClass(FilePath::class)
        val listWin = arrayListOf(filepathWin)
        every { pathWin.act(any<JacocoUtil.FilesOfAllSubDirectoriesCallable>()) } returns listWin
        every { filepathWin.remote } returns jacocoCSVPath.replace('/', '\\')
        scenario("Windows-style path")
        {
            PublisherUtil.doCheckJacocoCSVPath(pathWin, jacocoCSVPath) shouldBe true
        }
    }

    feature("doCheckJacocoResultsPath") {
        scenario("Folder contains file")
        {
            PublisherUtil.doCheckJacocoResultsPath(path, jacocoResultsPath) shouldBe true
        }

        scenario("Folder does not contain file")
        {
            PublisherUtil.doCheckJacocoResultsPath(FilePath(null, path.remote + "/src"), jacocoResultsPath) shouldBe false
        }
    }

    feature("doCheckJacocoResultsPath OSCompatibility") {
        val filepathLinux = mockkClass(FilePath::class)
        val pathLinux = mockkClass(FilePath::class)
        val listLinux = arrayListOf(filepathLinux)
        every { pathLinux.act(any<JacocoUtil.FilesOfAllSubDirectoriesCallable>()) } returns listLinux
        every { filepathLinux.remote } returns jacocoResultsPath + "index.html"
        scenario("Linux-style path")
        {
            PublisherUtil.doCheckJacocoResultsPath(pathLinux, jacocoResultsPath) shouldBe true
        }

        val filepathWin = mockkClass(FilePath::class)
        val pathWin = mockkClass(FilePath::class)
        val listWin = arrayListOf(filepathWin)
        every { pathWin.act(any<JacocoUtil.FilesOfAllSubDirectoriesCallable>()) } returns listWin
        every { filepathWin.remote } returns jacocoResultsPath.replace('/', '\\') + "index.html"
        scenario("Windows-style path")
        {
            PublisherUtil.doCheckJacocoResultsPath(pathWin, jacocoResultsPath) shouldBe true
        }
    }

    feature("retrieveLastChangedClasses") {
        scenario("A completely uncovered File exists")
        {
            PublisherUtil.retrieveLastChangedClasses(parameters, listOf(),
                removeClassesWithoutJacocoFiles = false, removeFullyCoveredClasses = false, sort = false) shouldHaveSize
                    1

        }
        every { classDetails.coverage } returns 0.1
        every { classDetails.filesExists() } returns true
        scenario("A barely covered File exists")
        {
            PublisherUtil.retrieveLastChangedClasses(parameters, listOf(),
                removeClassesWithoutJacocoFiles = true, removeFullyCoveredClasses = true, sort = true) shouldHaveSize
                    1
        }
        every { classDetails.coverage } returns 1.0
        every { GitUtil.getLastChangedClasses(any(), any(), any(), any()) } returns arrayListOf(classDetails)
        scenario("Existing class fully covered")
        {
            PublisherUtil.retrieveLastChangedClasses(parameters, listOf(),
                removeClassesWithoutJacocoFiles = true, removeFullyCoveredClasses = true, sort = true) shouldHaveSize
                    0
        }

        every { classDetails.coverage } returns 0.1
        every { classDetails.filesExists() } returns false
        every { GitUtil.getLastChangedClasses(any(), any(), any(), any()) } returns arrayListOf(classDetails)
        scenario("Non existent files")
        {
            PublisherUtil.retrieveLastChangedClasses(parameters, listOf(),
                removeClassesWithoutJacocoFiles = true, removeFullyCoveredClasses = true, sort = true) shouldHaveSize
                    0
        }
    }

    feature("updateStatistics") {
        val outputString = "[Gamekins] No entry for Statistics added"

        var listener = StreamTaskListener(File("$root/output.txt"))
        var output : String
        scenario("Added entry for Statistics")
        {
            PublisherUtil.updateStatistics(run, parameters, 0, 0, 0, 0, 0, 0, 0, listener)
            output = FilePath(null, "$root/output.txt").readToString()
            output shouldNotContain outputString
        }

        every { project.properties } returns
                descList2 as DescribableList<AbstractFolderProperty<*>, AbstractFolderPropertyDescriptor>?
        every { descList2.get(org.gamekins.property.GameMultiBranchProperty::class.java) } returns
                null
        scenario("No entry for Statistics added")
        {
            PublisherUtil.updateStatistics(run, parameters, 0, 0, 0, 0, 0, 0, 0, listener)
            output = FilePath(null, "$root/output.txt").readToString()
            File("$root/output.txt").delete() shouldBe true
            listener = StreamTaskListener(File("$root/output.txt"))
            output shouldContain outputString
        }

        every { job.parent } returns multiProject
        scenario("MultiBranchProject: Added entry for Statistics")
        {
            PublisherUtil.updateStatistics(run, parameters, 0, 0, 0, 0, 0, 0, 0, listener)
            output = FilePath(null, "$root/output.txt").readToString()
            output shouldNotContain outputString
        }

        every { job.getProperty(org.gamekins.property.GameJobProperty::class.java.name) } returns null
        scenario("No GameJobProperty")
        {
            val e = shouldThrow<NullPointerException> {
                PublisherUtil.updateStatistics(run, parameters, 0, 0, 0, 0, 0, 0, 0, listener)
            }
            e.message shouldBe "null cannot be cast to non-null type org.gamekins.property.GameJobProperty"
        }
    }
})
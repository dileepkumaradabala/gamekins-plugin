package io.jenkins.plugins.gamekins.util

import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor
import hudson.FilePath
import hudson.model.Job
import hudson.model.Result
import hudson.util.DescribableList
import hudson.util.StreamTaskListener
import io.jenkins.plugins.gamekins.challenge.ChallengeFactory
import io.jenkins.plugins.gamekins.challenge.LineCoverageChallenge
import io.jenkins.plugins.gamekins.statistics.Statistics
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.AnnotationSpec
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
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject
import java.io.File
import java.lang.NullPointerException
import java.util.concurrent.CopyOnWriteArrayList

class PublisherUtilTest : AnnotationSpec() {

    private lateinit var root : String
    private lateinit var path : FilePath
    private val jacocoResultsPath = "**/target/site/jacoco/"
    private val jacocoCSVPath = "**/target/site/jacoco/jacoco.csv"
    private val run = mockkClass(hudson.model.Run::class)
    private val constants = HashMap<String, String>()
    private val job = mockkClass(Job::class)
    private val project = mockkClass(WorkflowMultiBranchProject::class)
    private val multiProject = mockkClass(MultiBranchProject::class)
    private val multiProperty = mockkClass(io.jenkins.plugins.gamekins.property.GameMultiBranchProperty::class)
    private val property = mockkClass(io.jenkins.plugins.gamekins.property.GameJobProperty::class)
    private val descList = mockkClass(DescribableList::class)
    private val descList2 = mockkClass(DescribableList::class)
    private val statistics = mockkClass(Statistics::class)
    private val classDetails = mockkClass(JacocoUtil.ClassDetails::class)
    private val user = mockkClass(hudson.model.User::class)
    private val userProperty = mockkClass(io.jenkins.plugins.gamekins.GameUserProperty::class)
    private val challenge = mockkClass(LineCoverageChallenge::class)

    @BeforeAll
    fun initAll() {
        val rootDirectory = javaClass.classLoader.getResource("test-project.zip")
        rootDirectory shouldNotBe null
        root = rootDirectory.file.removeSuffix(".zip")
        root shouldEndWith "test-project"
        TestUtils.unzip("$root.zip", root)
        path = FilePath(null, root)

        constants["branch"] = "master"
        constants["jacocoCSVPath"] = jacocoCSVPath
        constants["projectName"] = "test-project"

        every { run.parent } returns job
        every { job.parent } returns project
        every { project.properties } returns
                descList as DescribableList<AbstractFolderProperty<*>, AbstractFolderPropertyDescriptor>?
        every { descList.get(io.jenkins.plugins.gamekins.property.GameMultiBranchProperty::class.java) } returns
                multiProperty
        every { multiProperty.getStatistics() } returns statistics
        every { multiProperty.owner } returns project
        every { project.save() } returns Unit
        every { statistics.addRunEntry(any(), any(), any(), any()) } returns Unit
        every { run.getNumber() } returns 1
        every { run.result } returns Result.SUCCESS
        every { run.startTimeInMillis } returns 0

        mockkStatic(JacocoUtil::class)
        every { JacocoUtil.getTestCount(any(), any()) } returns 4
        every { JacocoUtil.getProjectCoverage(any(), any()) } returns 0.4

        every { job.getProperty(io.jenkins.plugins.gamekins.property.GameJobProperty::class.java.name) } returns
                property
        every { property.getStatistics() } returns statistics
        every { property.getOwner() } returns multiProject
        every { multiProject.save() } returns Unit

        mockkStatic(GitUtil::class)
        every { GitUtil.getLastChangedClasses(any(), any(), any(), any(), any()) } returns arrayListOf(classDetails)

        mockkStatic(PropertyUtil::class)
        mockkStatic(ChallengeFactory::class)
        every { user.properties } returns mapOf()
        every { user.fullName } returns "Name"
        every { user.save() } returns Unit
    }

    @AfterAll
    fun cleanUp() {
        unmockkAll()
        File(root).deleteRecursively()
    }

    /**
     * There is some real issue with mocking non-static object methods.
     */
    @Test
    fun checkUser() {
        val map = hashMapOf("generated" to 0, "solved" to 0)

        every { PropertyUtil.realUser(user) } returns false
        PublisherUtil.checkUser(user, run, arrayListOf(classDetails), constants, Result.SUCCESS, path) shouldBe map

        every { user.getProperty(io.jenkins.plugins.gamekins.GameUserProperty::class.java) } returns null
        every { PropertyUtil.realUser(user) } returns true
        PublisherUtil.checkUser(user, run, arrayListOf(classDetails), constants, Result.SUCCESS, path) shouldBe map

        every { user.getProperty(io.jenkins.plugins.gamekins.GameUserProperty::class.java) } returns userProperty
        every { userProperty.isParticipating(any()) } returns false
        PublisherUtil.checkUser(user, run, arrayListOf(classDetails), constants, Result.SUCCESS, path) shouldBe map

        every { userProperty.isParticipating(any()) } returns true
        every { ChallengeFactory.generateBuildChallenge(any(), any(), any(), any(), any()) } returns true
        every { userProperty.getCurrentChallenges(any()) } returns CopyOnWriteArrayList()
        every { ChallengeFactory.generateNewChallenges(any(), any(), any(), any(), any()) } returns 0
        PublisherUtil.checkUser(user, run, arrayListOf(classDetails), constants, Result.SUCCESS, path) shouldBe hashMapOf("generated" to 1, "solved" to 0)

        every { ChallengeFactory.generateBuildChallenge(any(), any(), any(), any(), any()) } returns false
        every { challenge.isSolved(any(), any(), any(), any()) } returns false
        every { challenge.isSolvable(any(), any(), any(), any()) } returns true
        every { userProperty.getCurrentChallenges(any()) } returns CopyOnWriteArrayList(listOf(challenge))
        PublisherUtil.checkUser(user, run, arrayListOf(classDetails), constants, Result.SUCCESS, path) shouldBe map

        every { challenge.isSolved(any(), any(), any(), any()) } returns true
        every { challenge.isSolvable(any(), any(), any(), any()) } returns false
        every { challenge.getScore() } returns 1
        every { userProperty.completeChallenge(any(), any()) } returns Unit
        every { userProperty.addScore(any(), any()) } returns Unit
        every { userProperty.rejectChallenge(any(), any(), any()) } returns Unit
        PublisherUtil.checkUser(user, run, arrayListOf(classDetails), constants, Result.SUCCESS, path) shouldBe hashMapOf("generated" to 0, "solved" to 1)
    }

    @Test
    fun doCheckJacocoCSVPath() {
        PublisherUtil.doCheckJacocoCSVPath(path, jacocoCSVPath) shouldBe true
        PublisherUtil.doCheckJacocoCSVPath(FilePath(null, path.remote + "/src"), jacocoCSVPath) shouldBe false
    }

    @Test
    fun doCheckJacocoResultsPath() {
        PublisherUtil.doCheckJacocoResultsPath(path, jacocoResultsPath) shouldBe true
        PublisherUtil.doCheckJacocoResultsPath(FilePath(null, path.remote + "/src"), jacocoResultsPath) shouldBe false
    }

    @Test
    fun retrieveLastChangedClasses() {
        PublisherUtil.retrieveLastChangedClasses(path, 50, constants, listOf(),
                removeClassesWithoutJacocoFiles = false, removeFullCoveredClasses = false, sort = false) shouldHaveSize
                1

        every { classDetails.coverage } returns 0.1
        every { classDetails.filesExists() } returns true
        PublisherUtil.retrieveLastChangedClasses(path, 50, constants, listOf(),
                removeClassesWithoutJacocoFiles = true, removeFullCoveredClasses = true, sort = true) shouldHaveSize
                1

        every { classDetails.coverage } returns 1.0
        PublisherUtil.retrieveLastChangedClasses(path, 50, constants, listOf(),
                removeClassesWithoutJacocoFiles = true, removeFullCoveredClasses = true, sort = true) shouldHaveSize
                0

        every { classDetails.coverage } returns 0.1
        every { classDetails.filesExists() } returns false
        every { GitUtil.getLastChangedClasses(any(), any(), any(), any(), any()) } returns arrayListOf(classDetails)
        PublisherUtil.retrieveLastChangedClasses(path, 50, constants, listOf(),
                removeClassesWithoutJacocoFiles = true, removeFullCoveredClasses = true, sort = true) shouldHaveSize
                0
    }

    @Test
    fun updateStatistics() {
        val outputString = "[Gamekins] No entry for Statistics added"

        var listener = StreamTaskListener(File("$root/output.txt"))
        PublisherUtil.updateStatistics(run, constants, path, 0, 0, listener)
        var output = FilePath(null, "$root/output.txt").readToString()
        output shouldNotContain outputString

        every { project.properties } returns
                descList2 as DescribableList<AbstractFolderProperty<*>, AbstractFolderPropertyDescriptor>?
        every { descList2.get(io.jenkins.plugins.gamekins.property.GameMultiBranchProperty::class.java) } returns
                null
        PublisherUtil.updateStatistics(run, constants, path, 0, 0, listener)
        output = FilePath(null, "$root/output.txt").readToString()
        File("$root/output.txt").delete() shouldBe true
        listener = StreamTaskListener(File("$root/output.txt"))
        output shouldContain outputString

        every { job.parent } returns multiProject
        PublisherUtil.updateStatistics(run, constants, path, 0, 0, listener)
        output = FilePath(null, "$root/output.txt").readToString()
        output shouldNotContain outputString

        every { job.getProperty(io.jenkins.plugins.gamekins.property.GameJobProperty::class.java.name) } returns null
        val e = shouldThrow<NullPointerException> {
            PublisherUtil.updateStatistics(run, constants, path, 0, 0, listener)
        }
        e.message shouldBe "null cannot be cast to non-null type io.jenkins.plugins.gamekins.property.GameJobProperty"
    }
}
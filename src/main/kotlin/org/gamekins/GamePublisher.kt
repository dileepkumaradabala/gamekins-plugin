/*
 * Copyright 2022 Gamekins contributors
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

package org.gamekins

import hudson.EnvVars
import hudson.FilePath
import hudson.Launcher
import hudson.model.*
import hudson.tasks.BuildStepMonitor
import hudson.tasks.Notifier
import org.gamekins.challenge.Challenge
import org.gamekins.property.GameJobProperty
import org.gamekins.property.GameMultiBranchProperty
import jenkins.tasks.SimpleBuildStep
import org.gamekins.event.EventHandler
import org.gamekins.event.build.BuildFinishedEvent
import org.gamekins.event.build.BuildStartedEvent
import org.gamekins.util.*
import org.gamekins.util.Constants.Parameters
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.StaplerProxy
import javax.annotation.Nonnull

/**
 * Class that is called after the build of a job in Jenkins is finished. This one executes the main functionality of
 * Gamekins by creating and solving [Challenge]s.
 *
 * [jacocoResultsPath] and [jacocoCSVPath] must be of type String?, as Jenkins wants to instantiate the
 * [GamePublisher] with null when Gamekins is not activated.
 *
 * @author Philipp Straubinger
 * @since 0.1
 */

class GamePublisher @DataBoundConstructor constructor(@set:DataBoundSetter var jacocoResultsPath: String?,
                                                      @set:DataBoundSetter var jacocoCSVPath: String?)
    : Notifier(), SimpleBuildStep, StaplerProxy {

    /**
     * Starts the execution of Gamekins for a specific [run] with its [result]. The [parameters] contain needed data
     * like the paths to the JaCoCo files. The [parameters] includes the workspace, the folder with the code and
     * execution rights, and the [listener] reports the events to the console output of Jenkins.
     */
    private fun executePublisher(
        run: Run<*, *>, parameters: Parameters, result: Result?,
        listener: TaskListener
    ) {

        //Extracts the branch
        when (run.parent.parent) {
            is WorkflowMultiBranchProject -> {
                parameters.branch = run.parent.name
            }
            else -> {
                parameters.branch = GitUtil.getBranch(parameters.workspace)
            }
        }

        EventHandler.addEvent(BuildStartedEvent(parameters.projectName, parameters.branch, run))

        //Checks whether the paths of the JaCoCo files are correct
        if (!PublisherUtil.doCheckJacocoResultsPath(parameters.workspace, parameters.jacocoResultsPath)) {
            listener.logger.println("[Gamekins] JaCoCo folder is not correct")
            PublisherUtil.generateBuildAndTestChallenges(parameters, result, listener)
            EventHandler.addEvent(BuildFinishedEvent(parameters.projectName, parameters.branch, run))
            return
        }
        if (!PublisherUtil.doCheckJacocoCSVPath(parameters.workspace, parameters.jacocoCSVPath)) {
            listener.logger.println("[Gamekins] JaCoCo csv file could not be found")
            PublisherUtil.generateBuildAndTestChallenges(parameters, result, listener)
            EventHandler.addEvent(BuildFinishedEvent(parameters.projectName, parameters.branch, run))
            return
        }

        listener.logger.println("[Gamekins] Start")
        listener.logger.println("[Gamekins] Solve Challenges and generate new Challenges")

        //Computes the last changed classes
        val files = PublisherUtil.retrieveLastChangedSourceAndTestFiles(parameters,
            removeFullyCoveredClasses = false, removeClassesWithoutJacocoFiles = false, listener = listener)

        //Generate some project statistics
        parameters.projectCoverage = JacocoUtil.getProjectCoverage(parameters.workspace,
            parameters.jacocoCSVPath.split("/".toRegex())
                    [parameters.jacocoCSVPath.split("/".toRegex()).size - 1])
        parameters.projectTests = JUnitUtil.getTestCount(parameters.workspace, run)

        //Checks for each user his Challenges and generates new ones if needed
        var generated = 0
        var solved = 0
        var solvedAchievements = 0
        var solvedQuests = 0
        var generatedQuests = 0
        var solvedQuestTasks = 0
        var generatedQuestTasks = 0
        for (user in User.getAll()) {
            val results = PublisherUtil.checkUser(user, run, ArrayList(files), parameters, result, listener)
            generated += (if (results["generated"] != null) results["generated"] else 0)!!
            solved += (if (results["solved"] != null) results["solved"] else 0)!!
            solvedAchievements += (if (results["solvedAchievements"] != null) results["solvedAchievements"] else 0)!!
            generatedQuests += (if (results["generatedQuests"] != null) results["generatedQuests"] else 0)!!
            solvedQuests += (if (results["solvedQuests"] != null) results["solvedQuests"] else 0)!!
            generatedQuestTasks += (if (results["generatedQuestTasks"] != null) results["generatedQuestTasks"] else 0)!!
            solvedQuestTasks += (if (results["solvedQuestTasks"] != null) results["solvedQuestTasks"] else 0)!!
        }

        listener.logger.println("[Gamekins] Solved $solved Challenges and generated $generated Challenges")
        listener.logger.println("[Gamekins] Solved $solvedAchievements Achievements")
        listener.logger.println("[Gamekins] Solved $solvedQuests Quests and generated $generatedQuests Quests")
        listener.logger.println("[Gamekins] Solved $solvedQuestTasks Quest Tasks and " +
                "generated $generatedQuestTasks Quest Tasks")
        listener.logger.println("[Gamekins] Update Statistics")

        //Updates the Statistics
        PublisherUtil.updateStatistics(run, parameters, generated, solved, solvedAchievements, solvedQuests,
            generatedQuests, solvedQuestTasks, generatedQuestTasks, listener)

        EventHandler.addEvent(BuildFinishedEvent(parameters.projectName, parameters.branch, run))

        listener.logger.println("[Gamekins] Finished")
    }

    override fun getRequiredMonitorService(): BuildStepMonitor {
        return BuildStepMonitor.STEP
    }

    override fun getTarget(): Any {
        return this
    }

    override fun needsToRunAfterFinalized(): Boolean {
        return true
    }

    /**
     * This method is called by jobs of type [AbstractProject] and starts the execution of Gamekins.
     *
     * @see Notifier.perform
     */
    override fun perform(build: AbstractBuild<*, *>, launcher: Launcher, listener: BuildListener): Boolean {
        if (build.project == null || build.project.getProperty(GameJobProperty::class.java) == null
            || !build.project.getProperty(GameJobProperty::class.java).activated
        ) {
            listener.logger.println(Constants.NOT_ACTIVATED)
            return true
        }

        val parameters = Parameters(jacocoCSVPath = jacocoCSVPath!!, jacocoResultsPath = jacocoResultsPath!!,
            workspace = build.workspace!!)
        parameters.projectName = build.project.fullName
        parameters.currentChallengesCount = build.project.getProperty(GameJobProperty::class.java)
            .currentChallengesCount
        parameters.currentQuestsCount = build.project.getProperty(GameJobProperty::class.java)
            .currentQuestsCount
        parameters.storedChallengesCount = build.project.getProperty(GameJobProperty::class.java)
            .currentStoredChallengesCount
        parameters.searchCommitCount = build.project.getProperty(GameJobProperty::class.java)
            .searchCommitCount
        parameters.pitConfiguration = build.project.getProperty(GameJobProperty::class.java)
            .pitConfiguration
        executePublisher(build, parameters, build.result, listener)
        return true
    }

    /**
     * This method is called by jobs of type [WorkflowJob] and [WorkflowMultiBranchProject] and starts the
     * execution of Gamekins.
     *
     * @see SimpleBuildStep.perform
     */
    override fun perform(
        @Nonnull run: Run<*, *>, @Nonnull workspace: FilePath, @Nonnull env: EnvVars,
        @Nonnull launcher: Launcher, @Nonnull listener: TaskListener
    ) {

        val parameters = Parameters(jacocoCSVPath = jacocoCSVPath!!,
            jacocoResultsPath = jacocoResultsPath!!, workspace = workspace)
        if (run.parent.parent is WorkflowMultiBranchProject) {
            val project = run.parent.parent as WorkflowMultiBranchProject
            if (project.properties.get(GameMultiBranchProperty::class.java) == null
                || !project.properties.get(GameMultiBranchProperty::class.java).activated
            ) {
                listener.logger.println(Constants.NOT_ACTIVATED)
                return
            }
            parameters.projectName = project.fullName
            parameters.currentChallengesCount = project.properties.get(GameMultiBranchProperty::class.java)
                .currentChallengesCount
            parameters.currentQuestsCount = project.properties.get(GameMultiBranchProperty::class.java)
                .currentQuestsCount
            parameters.storedChallengesCount = project.properties.get(GameMultiBranchProperty::class.java)
                .currentStoredChallengesCount
            parameters.searchCommitCount = project.properties.get(GameMultiBranchProperty::class.java)
                .searchCommitCount
            parameters.pitConfiguration = project.properties.get(GameMultiBranchProperty::class.java)
                .pitConfiguration
            parameters.showPitOutput = project.properties.get(GameMultiBranchProperty::class.java)
                .showPitOutput
        } else {
            if (run.parent.getProperty(GameJobProperty::class.java) == null
                || !run.parent.getProperty(GameJobProperty::class.java).activated
            ) {
                listener.logger.println(Constants.NOT_ACTIVATED)
                return
            }
            parameters.projectName = run.parent.fullName
            parameters.currentChallengesCount = run.parent.getProperty(GameJobProperty::class.java)
                .currentChallengesCount
            parameters.currentQuestsCount = run.parent.getProperty(GameJobProperty::class.java)
                .currentQuestsCount
            parameters.storedChallengesCount = run.parent.getProperty(GameJobProperty::class.java)
                .currentStoredChallengesCount
            parameters.searchCommitCount = run.parent.getProperty(GameJobProperty::class.java)
                .searchCommitCount
            parameters.pitConfiguration = run.parent.getProperty(GameJobProperty::class.java)
                .pitConfiguration
            parameters.showPitOutput = run.parent.getProperty(GameJobProperty::class.java)
                .showPitOutput
        }

        executePublisher(run, parameters, run.result, listener)
    }
}

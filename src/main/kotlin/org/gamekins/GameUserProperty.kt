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

package org.gamekins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import hudson.model.*
import org.gamekins.challenge.Challenge
import org.gamekins.challenge.DummyChallenge
import org.gamekins.statistics.Statistics
import net.sf.json.JSONObject
import org.gamekins.achievement.Achievement
import org.gamekins.challenge.CoverageChallenge
import org.gamekins.challenge.quest.Quest
import org.gamekins.questtask.QuestTask
import org.gamekins.util.Constants
import org.gamekins.util.Pair
import org.gamekins.util.PropertyUtil
import org.kohsuke.stapler.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Property that is added to each [User] to extend his configuration and ability. Stores all current, completed and
 * rejected [Challenge]s, author names in git, score and participation information.
 *
 * @author Philipp Straubinger
 * @since 0.1
 */
class GameUserProperty : UserProperty(), Action, StaplerProxy {

    private var completedAchievements: HashMap<String, CopyOnWriteArrayList<Achievement>> = HashMap()
    private val completedChallenges: HashMap<String, CopyOnWriteArrayList<Challenge>> = HashMap()
    private var completedQuests: HashMap<String, CopyOnWriteArrayList<Quest>> = HashMap()
    private var completedQuestTasks: HashMap<String, CopyOnWriteArrayList<QuestTask>> = HashMap()
    private var currentAvatar: String = Constants.Default.AVATAR
    private val currentChallenges: HashMap<String, CopyOnWriteArrayList<Challenge>> = HashMap()
    private var currentQuests: HashMap<String, CopyOnWriteArrayList<Quest>> = HashMap()
    private var currentQuestTasks: HashMap<String, CopyOnWriteArrayList<QuestTask>> = HashMap()
    private var gitNames: CopyOnWriteArraySet<String>? = null
    private var lastLeaderboard: String = ""
    private var lastProject: String = ""
    private val participation: HashMap<String, String> = HashMap()
    private val pseudonym: UUID = UUID.randomUUID()
    private val rejectedChallenges: HashMap<String, CopyOnWriteArrayList<Pair<Challenge, String>>> = HashMap()
    private var rejectedQuests: HashMap<String, CopyOnWriteArrayList<Pair<Quest, String>>> = HashMap()
    private val score: HashMap<String, Int> = HashMap()
    private var sendNotifications: Boolean = true
    private var storedChallenges: HashMap<String, CopyOnWriteArrayList<Challenge>> = HashMap()
    private var unfinishedQuests: HashMap<String, CopyOnWriteArrayList<Quest>> = HashMap()
    private var unsolvedAchievements: HashMap<String, CopyOnWriteArrayList<Achievement>> = HashMap()

    /**
     * Adds an additional [score] to one project [projectName], since one user can participate in multiple projects.
     */
    fun addScore(projectName: String, score: Int) {
        this.score[projectName] = this.score[projectName]!! + score
    }

    /**
     * Sets an [achievement] of project [projectName] to complete an removes it from the [unsolvedAchievements].
     */
    fun completeAchievement(projectName: String, achievement: Achievement) {
        completedAchievements.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
        val completedAchievements = completedAchievements[projectName]!!
        completedAchievements.add(achievement)
        this.completedAchievements[projectName] = completedAchievements
        val unsolvedAchievements = unsolvedAchievements[projectName]!!
        unsolvedAchievements.remove(achievement)
        this.unsolvedAchievements[projectName] = unsolvedAchievements
    }

    /**
     * Sets a [challenge] of project [projectName] to complete and removes it from the [currentChallenges].
     */
    fun completeChallenge(projectName: String, challenge: Challenge) {
        var challenges: CopyOnWriteArrayList<Challenge>
        if (challenge !is DummyChallenge) {
            completedChallenges.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
            challenges = completedChallenges[projectName]!!
            challenges.add(challenge)
            completedChallenges[projectName] = challenges
        }

        challenges = currentChallenges[projectName]!!
        challenges.remove(challenge)
        currentChallenges[projectName] = challenges
    }

    /**
     * Sets a [quest] of project [projectName] to complete and removes it from the [currentQuests].
     */
    fun completeQuest(projectName: String, quest: Quest) {
        var quests: CopyOnWriteArrayList<Quest>
        if (quest.steps.size > 0) {
            completedQuests.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
            quests = completedQuests[projectName]!!
            quests.add(quest)
            completedQuests[projectName] = quests
        }

        quests = currentQuests[projectName]!!
        quests.remove(quest)
        currentQuests[projectName] = quests
    }

    /**
     * Sets a [questTask] of project [projectName] to complete and removes it from the [currentQuestTasks].
     */
    fun completeQuestTask(projectName: String, questTask: QuestTask) {
        completedQuestTasks.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
        var questTasks: CopyOnWriteArrayList<QuestTask> = completedQuestTasks[projectName]!!
        questTasks.add(questTask)
        completedQuestTasks[projectName] = questTasks

        questTasks = currentQuestTasks[projectName]!!
        questTasks.remove(questTask)
        currentQuestTasks[projectName] = questTasks
    }

    /**
     * Computes the initial git names of the user by full, display and user name.
     */
    private fun computeInitialGitNames(): CopyOnWriteArraySet<String> {
        val set = CopyOnWriteArraySet<String>()
        if (user != null) {
            set.add(user.fullName)
            set.add(user.displayName)
            set.add(user.id)
        }
        return set
    }

    /**
     * Returns the number of total [Achievement]s available in [completedAchievements] and [unsolvedAchievements] for
     * a specific [projectName].
     */
    fun doGetAchievementsCount(rsp: StaplerResponse, @QueryParameter projectName: String) {
        rsp.contentType = Constants.TYPE_PLAIN
        val printer = rsp.writer
        if (projectName.isEmpty() || completedAchievements[projectName] == null
            || unsolvedAchievements[projectName] == null) {
            printer.print(0)
        } else {
            printer.print(completedAchievements[projectName]!!.size + unsolvedAchievements[projectName]!!.size)
        }
        printer.flush()
    }

    /**
     * Returns the list of completed [Achievement]s for a specific [projectName].
     */
    fun doGetCompletedAchievements(rsp: StaplerResponse, @QueryParameter projectName: String) {
        val json = jacksonObjectMapper().writeValueAsString(completedAchievements[projectName])
        rsp.contentType = Constants.TYPE_JSON
        val printer = rsp.writer
        printer.print(json)
        printer.flush()
    }

    fun doGetLastLeaderboard(rsp: StaplerResponse) {
        rsp.contentType = Constants.TYPE_PLAIN
        val printer = rsp.writer
        printer.print(lastLeaderboard)
        printer.flush()
    }

    /**
     * Returns the list of projects the user is currently participating.
     */
    fun doGetProjects(rsp: StaplerResponse) {
        val projects = arrayListOf<String>()
        if (participation.keys.contains(lastProject)) projects.add(lastProject)
        projects.addAll(participation.keys)
        val json = jacksonObjectMapper().writeValueAsString(projects.distinct())
        rsp.contentType = Constants.TYPE_JSON
        val printer = rsp.writer
        printer.print(json)
        printer.flush()
    }

    /**
     * Returns the list of unsolved [Achievement]s.
     */
    fun doGetUnsolvedAchievements(rsp: StaplerResponse, @QueryParameter projectName: String) {
        val json = jacksonObjectMapper().writeValueAsString(unsolvedAchievements[projectName]?.filter { !it.secret })
        rsp.contentType = Constants.TYPE_JSON
        val printer = rsp.writer
        printer.print(json)
        printer.flush()
    }

    /**
     * Returns the list of unsolved secret [Achievement]s for a specific [projectName].
     */
    fun doGetUnsolvedSecretAchievementsCount(rsp: StaplerResponse, @QueryParameter projectName: String) {
        rsp.contentType = Constants.TYPE_PLAIN
        val printer = rsp.writer
        if (projectName.isEmpty() || unsolvedAchievements[projectName] == null) {
            printer.print(0)
        } else {
            printer.print(unsolvedAchievements[projectName]!!.filter { it.secret }.size)
        }
        printer.flush()
    }

    /**
     * Returns true if this user asks for his [Achievement]s. False if another user wants to see it.
     */
    fun doIsCurrentUser(rsp: StaplerResponse) {
        rsp.contentType = Constants.TYPE_PLAIN
        val printer = rsp.writer
        printer.print(this.user == User.current())
        printer.flush()
    }

    /**
     * Returns the list of completed achievements.
     */
    fun getCompletedAchievements(projectName: String): CopyOnWriteArrayList<Achievement> {
        return completedAchievements[projectName]!!
    }

    /**
     * Returns the list of completed Challenges by [projectName].
     */
    fun getCompletedChallenges(projectName: String): CopyOnWriteArrayList<Challenge> {
        return completedChallenges[projectName]!!
    }

    /**
     * Returns the list of completed Quests by [projectName].
     */
    fun getCompletedQuests(projectName: String): CopyOnWriteArrayList<Quest> {
        return completedQuests[projectName]!!
    }

    /**
     * Returns the list of completed QuestTasks by [projectName].
     */
    fun getCompletedQuestTasks(projectName: String): CopyOnWriteArrayList<QuestTask> {
        return completedQuestTasks[projectName]!!
    }

    /**
     * Returns the filename of the current avatar.
     */
    fun getCurrentAvatar(): String {
        return currentAvatar
    }

    /**
     * Returns the list of current Challenges by [projectName].
     */
    fun getCurrentChallenges(projectName: String): CopyOnWriteArrayList<Challenge> {
        return currentChallenges[projectName]!!
    }

    /**
     * Returns the list of current Quests by [projectName].
     */
    fun getCurrentQuests(projectName: String): CopyOnWriteArrayList<Quest> {
        return currentQuests[projectName]!!
    }

    /**
     * Returns the list of current QuestTasks by [projectName].
     */
    fun getCurrentQuestTasks(projectName: String): CopyOnWriteArrayList<QuestTask> {
        return currentQuestTasks[projectName]!!
    }

    override fun getDisplayName(): String {
        return "Achievements"
    }

    /**
     * Returns the git author name sof the user.
     */
    fun getGitNames(): CopyOnWriteArraySet<String> {
        return gitNames ?: CopyOnWriteArraySet()
    }

    override fun getIconFileName(): String {
        return "/plugin/gamekins/icons/trophy.png"
    }

    /**
     * Returns the [gitNames] as String with line breaks after each entry.
     */
    fun getNames(): String {
        if (user == null) return ""
        if (gitNames == null) gitNames = computeInitialGitNames()
        val builder = StringBuilder()
        for (name in gitNames!!) {
            builder.append(name).append("\n")
        }
        return builder.substring(0, builder.length - 1)
    }

    /**
     * Returns the [sendNotifications].
     */
    fun getNotifications(): Boolean {
        return this.sendNotifications
    }

    /**
     * Returns the [pseudonym] of the user for [Statistics].
     */
    fun getPseudonym(): String {
        return pseudonym.toString()
    }

    /**
     * Returns the list of rejected Challenges by [projectName].
     */
    fun getRejectedChallenges(projectName: String?): CopyOnWriteArrayList<Pair<Challenge, String>> {
        return rejectedChallenges[projectName]!!
    }

    /**
     * Returns the list of stored Challenges by [projectName].
     */
    fun getStoredChallenges(projectName: String?): CopyOnWriteArrayList<Challenge> {
        return storedChallenges[projectName]!!
    }

    /**
     * Returns the list of rejected Quests by [projectName].
     */
    fun getRejectedQuests(projectName: String?): CopyOnWriteArrayList<Pair<Quest, String>> {
        return rejectedQuests[projectName]!!
    }

    /**
     * Returns the score of the user by [projectName].
     */
    fun getScore(projectName: String): Int {
        if (isParticipating(projectName) && score[projectName] == null) {
            score[projectName] = 0
        }
        return score[projectName]!!
    }

    override fun getTarget(): Any? {
        val referer = Stapler.getCurrentRequest().getHeader("Referer")
        if (referer != null && (referer.endsWith("leaderboard") || referer.endsWith("leaderboard/"))) {
            val match = "/job/(.+)/leaderboard".toRegex().find(referer)
            lastProject = match?.groupValues?.get(1) ?: lastProject
            lastProject = lastProject.replace("job/", "")
            lastProject = lastProject.replace("%20", " ")

            lastLeaderboard = referer
        }

        return if (User.current() == this.user
            || Stapler.getCurrentRequest().requestURI.toString().contains("achievements")) this else null
    }

    /**
     * Returns the name of the team the user is participating in the project [projectName].
     */
    fun getTeamName(projectName: String): String {
        val name: String? = participation[projectName]
        //Should not happen since each call is of getTeamName() is surrounded with a call to isParticipating()
        return name ?: "null"
    }

    /**
     * Returns the list of unfinished Quests by [projectName].
     */
    fun getUnfinishedQuests(projectName: String): CopyOnWriteArrayList<Quest> {
        return unfinishedQuests[projectName]!!
    }

    /**
     * Returns the list of unsolved achievements.
     */
    fun getUnsolvedAchievements(projectName: String): CopyOnWriteArrayList<Achievement> {
        return unsolvedAchievements[projectName]!!
    }

    override fun getUrlName(): String {
        return "achievements"
    }

    /**
     * Returns the parent/owner/user of the property.
     */
    fun getUser(): User {
        return user
    }

    /**
     * Checks whether the user is participating in the project [projectName].
     */
    fun isParticipating(projectName: String): Boolean {
        return participation.containsKey(projectName)
    }

    /**
     * Checks whether the user is participating in team [teamName] in the project [projectName].
     */
    fun isParticipating(projectName: String, teamName: String): Boolean {
        return participation[projectName] == teamName
    }

    /**
     * Returns a list of subprojects the user is participating.
     */
    fun isParticipatingInSubProjects(projectName: String): ArrayList<String> {
        val list = arrayListOf<String>()
        for (project in participation.keys) {
            if (project.startsWith(projectName)) {
                list.add(project)
            }
        }

        return list
    }

    /**
     * Adds a new [Challenge] to the user.
     */
    fun newChallenge(projectName: String, challenge: Challenge) {
        if (projectName.isBlank()) return
        currentChallenges.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
        val challenges = currentChallenges[projectName]!!
        challenges.add(challenge)
        currentChallenges[projectName] = challenges
    }

    /**
     * Adds a new [Quest] to the user.
     */
    fun newQuest(projectName: String, quest: Quest) {
        if (projectName.isBlank()) return
        currentQuests.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
        val quests = currentQuests[projectName]!!
        quests.add(quest)
        currentQuests[projectName] = quests
    }

    /**
     * Adds a new [QuestTask] to the user.
     */
    fun newQuestTask(projectName: String, questTask: QuestTask) {
        if (projectName.isBlank()) return
        currentQuestTasks.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
        val quests = currentQuestTasks[projectName]!!
        quests.add(questTask)
        currentQuestTasks[projectName] = quests
    }

    /**
     * Returns the XML representation of the user.
     */
    fun printToXML(projectName: String, indentation: String): String {
        val print = StringBuilder()
        print.append(indentation).append("<User id=\"").append(pseudonym).append("\" project=\"")
                .append(projectName).append("\" score=\"").append(getScore(projectName)).append("\">\n")

        print.append(indentation).append("    <CurrentChallenges count=\"")
                .append(getCurrentChallenges(projectName).size).append("\">\n")
        for (challenge in getCurrentChallenges(projectName)) {
            print.append(challenge.printToXML("", "$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </CurrentChallenges>\n")

        print.append(indentation).append("    <CompletedChallenges count=\"")
                .append(getCompletedChallenges(projectName).size).append("\">\n")
        for (challenge in getCompletedChallenges(projectName)) {
            print.append(challenge.printToXML("", "$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </CompletedChallenges>\n")

        print.append(indentation).append("    <RejectedChallenges count=\"")
                .append(getRejectedChallenges(projectName).size).append("\">\n")
        for (pair in rejectedChallenges[projectName]!!) {
            print.append(pair.first.printToXML(pair.second, "$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </RejectedChallenges>\n")

        print.append(indentation).append("    <StoredChallenges count=\"")
            .append(getStoredChallenges(projectName).size).append("\">\n")
        for (challenge in getStoredChallenges(projectName)) {
            print.append(challenge.printToXML("", "$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </StoredChallenges>\n")

        print.append(indentation).append("    <CurrentQuests count=\"")
            .append(getCurrentQuests(projectName).size).append("\">\n")
        for (quest in getCurrentQuests(projectName)) {
            print.append(quest.printToXML("", "$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </CurrentQuests>\n")

        print.append(indentation).append("    <CompletedQuests count=\"")
            .append(getCompletedQuests(projectName).size).append("\">\n")
        for (quest in getCompletedQuests(projectName)) {
            print.append(quest.printToXML("", "$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </CompletedQuests>\n")

        print.append(indentation).append("    <RejectedQuests count=\"")
            .append(getRejectedQuests(projectName).size).append("\">\n")
        for (pair in rejectedQuests[projectName]!!) {
            print.append(pair.first.printToXML(pair.second, "$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </RejectedQuests>\n")

        print.append(indentation).append("    <UnfinishedQuests count=\"")
            .append(getCompletedQuests(projectName).size).append("\">\n")
        for (quest in getCompletedQuests(projectName)) {
            print.append(quest.printToXML("", "$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </UnfinishedQuests>\n")

        print.append(indentation).append("    <CurrentQuestTasks count=\"")
            .append(getCurrentQuestTasks(projectName).size).append("\">\n")
        for (quest in getCurrentQuestTasks(projectName)) {
            print.append(quest.printToXML("$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </CurrentQuestTasks>\n")

        print.append(indentation).append("    <CompletedQuestTasks count=\"")
            .append(getCompletedQuestTasks(projectName).size).append("\">\n")
        for (quest in getCompletedQuestTasks(projectName)) {
            print.append(quest.printToXML("$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </CompletedQuestTasks>\n")

        print.append(indentation).append("    <Achievements count=\"")
            .append(getCompletedAchievements(projectName).size).append("\">\n")
        for (achievement in completedAchievements[projectName]!!) {
            print.append(achievement.printToXML("$indentation        ")).append("\n")
        }
        print.append(indentation).append("    </Achievements>\n")

        print.append(indentation).append("</User>")
        return print.toString()
    }

    /**
     * Called by Jenkins after the object has been created from his XML representation. Used for data migration.
     */
    @Suppress("unused", "SENSELESS_COMPARISON")
    private fun readResolve(): Any {

        //Add stored challenges if newly introduced
        if (participation.size != 0 && storedChallenges.size == 0) {
            participation.keys.forEach { project ->
                storedChallenges[project] = CopyOnWriteArrayList()
            }
        }

        //Add new achievements
        participation.keys.forEach { project ->
            GamePublisherDescriptor.achievements
                .filter { !completedAchievements[project]!!.contains(it)
                        && !unsolvedAchievements[project]!!.contains(it) }
                .forEach { unsolvedAchievements[project]!!.add(it.clone()) }
        }

        //Update achievements with changed fullyQualifiedFunctionName or secret
        updateAchievements()

        //Remove challenges where it was not possible to generate a SourceFileDetails
        completedChallenges.forEach { (p, challenges) ->
            val coverageChallenges = ArrayList(challenges.filterIsInstance<CoverageChallenge>())
            coverageChallenges.removeIf { it.details == null }
            val finalChallenges = ArrayList(challenges.filter { it !is CoverageChallenge })
            finalChallenges.addAll(coverageChallenges)
            completedChallenges[p] = CopyOnWriteArrayList(finalChallenges)
        }

        currentChallenges.forEach { (p, challenges) ->
            val coverageChallenges = ArrayList(challenges.filterIsInstance<CoverageChallenge>())
            coverageChallenges.removeIf { it.details == null }
            val finalChallenges = ArrayList(challenges.filter { it !is CoverageChallenge })
            finalChallenges.addAll(coverageChallenges)
            currentChallenges[p] = CopyOnWriteArrayList(finalChallenges)
        }

        rejectedChallenges.forEach { (p, challenges) ->
            val rejChallenges = CopyOnWriteArrayList(challenges.filter { it.first is CoverageChallenge })
            rejChallenges.removeIf { (challenge, _) ->
                challenge is CoverageChallenge && challenge.details == null
            }
            val finalChallenges = ArrayList(challenges.filter { it.first !is CoverageChallenge })
            finalChallenges.addAll(rejChallenges)
            rejectedChallenges[p] = CopyOnWriteArrayList(finalChallenges)
        }

        storedChallenges.forEach { (p, challenges) ->
            val coverageChallenges = ArrayList(challenges.filterIsInstance<CoverageChallenge>())
            coverageChallenges.removeIf { it.details == null }
            val finalChallenges = ArrayList(challenges.filter { it !is CoverageChallenge })
            finalChallenges.addAll(coverageChallenges)
            storedChallenges[p] = CopyOnWriteArrayList(finalChallenges)
        }

        //Default avatar
        if (currentAvatar.isEmpty()) currentAvatar = Constants.Default.AVATAR

        //Last leaderboard
        if (lastLeaderboard == null) lastLeaderboard = ""

        //Unfinished Quests
        if (unfinishedQuests == null) unfinishedQuests = HashMap()
        if (participation.size != 0 && unfinishedQuests.size == 0) {
            participation.keys.forEach {
                unfinishedQuests[it] = CopyOnWriteArrayList()
            }
        }
        for (project in rejectedQuests) {
            for ((quest, reason) in project.value) {
                if (quest.getCurrentStepNumber() > 0) {
                    unfinishedQuests[project.key]?.add(quest)
                    project.value.remove(Pair(quest, reason))
                }
            }
        }

        //QuestTasks
        if (currentQuestTasks == null) currentQuestTasks = HashMap()
        if (participation.size != 0 && currentQuestTasks.size == 0) {
            participation.keys.forEach {
                currentQuestTasks[it] = CopyOnWriteArrayList()
            }
        }
        if (completedQuestTasks == null) completedQuestTasks = HashMap()
        if (participation.size != 0 && completedQuestTasks.size == 0) {
            participation.keys.forEach {
                completedQuestTasks[it] = CopyOnWriteArrayList()
            }
        }

        return this
    }

    /**
     * Updates the git names and notifications preferences if the user configuration is saved.
     */
    override fun reconfigure(req: StaplerRequest, form: JSONObject?): UserProperty {
        if (form != null) {
            setNames(form.getString("names"))
            setNotifications(form.getBoolean("notifications"))
        }
        return this
    }

    /**
     * Rejects a given [challenge] of project [projectName] with a [reason].
     */
    fun rejectChallenge(projectName: String, challenge: Challenge, reason: String) {
        challenge.setRejectedTime(System.currentTimeMillis())
        rejectedChallenges.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
        val challenges = rejectedChallenges[projectName]!!
        challenges.add(Pair(challenge, reason))
        rejectedChallenges[projectName] = challenges
        val currentChallenges = currentChallenges[projectName]!!
        currentChallenges.remove(challenge)
        this.currentChallenges[projectName] = currentChallenges
    }

    /**
     * Rejects a given stored [challenge] of project [projectName] with a [reason].
     */
    fun rejectStoredChallenge(projectName: String, challenge: Challenge, reason: String) {
        rejectedChallenges.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
        val challenges = rejectedChallenges[projectName]!!
        challenges.add(Pair(challenge, reason))
        rejectedChallenges[projectName] = challenges
        val storedChallenges = storedChallenges[projectName]!!
        storedChallenges.remove(challenge)
        this.storedChallenges[projectName] = storedChallenges
    }

    /**
     * Rejects a given [quest] of project [projectName] with a [reason]. Also handles unfinished quests.
     */
    fun rejectQuest(projectName: String, quest: Quest, reason: String) {
        if (quest.steps.size == 0) {
            completeQuest(projectName, quest)
        } else if (quest.getCurrentStepNumber() > 0){
            unfinishedQuests.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
            val quests = unfinishedQuests[projectName]!!
            quests.add(quest)
            unfinishedQuests[projectName] = quests
            val currentQuests = currentQuests[projectName]!!
            currentQuests.remove(quest)
            this.currentQuests[projectName] = currentQuests
        } else {
            rejectedQuests.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
            val quests = rejectedQuests[projectName]!!
            quests.add(Pair(quest, reason))
            rejectedQuests[projectName] = quests
            val currentQuests = currentQuests[projectName]!!
            currentQuests.remove(quest)
            this.currentQuests[projectName] = currentQuests
        }
    }

    /**
     * Removes the participation of the user in project [projectName].
     */
    fun removeParticipation(projectName: String) {
        participation.remove(projectName)
    }

    /**
     * Removes all Challenges for a specific [projectName] and resets the score.
     */
    fun reset(projectName: String) {
        completedChallenges[projectName] = CopyOnWriteArrayList()
        currentChallenges[projectName] = CopyOnWriteArrayList()
        rejectedChallenges[projectName] = CopyOnWriteArrayList()
        storedChallenges[projectName] = CopyOnWriteArrayList()
        score[projectName] = 0
        completedAchievements[projectName] = CopyOnWriteArrayList()
        val list = CopyOnWriteArrayList<Achievement>()
        GamePublisherDescriptor.achievements.forEach { list.add(it.clone()) }
        unsolvedAchievements[projectName] = list
        completedQuests[projectName] = CopyOnWriteArrayList()
        currentQuests[projectName] = CopyOnWriteArrayList()
        rejectedQuests[projectName] = CopyOnWriteArrayList()
        unfinishedQuests[projectName] = CopyOnWriteArrayList()
        completedQuestTasks[projectName] = CopyOnWriteArrayList()
        currentQuestTasks[projectName] = CopyOnWriteArrayList()
    }

    /**
     * Sets the filename of the current avatar.
     */
    fun setCurrentAvatar(name: String) {
        currentAvatar = name

        user.save()
    }

    /**
     * Sets the git names.
     */
    @DataBoundSetter
    fun setNames(names: String) {
        val split = names.split("\n".toRegex())
        gitNames = CopyOnWriteArraySet(split)
    }

    /**
     * Sets the option, whether the user wants to receive notifications.
     */
    @DataBoundSetter
    fun setNotifications(notifications: Boolean) {
        this.sendNotifications = notifications
    }

    /**
     * Adds the participation of the user for project [projectName] and team [teamName].
     */
    fun setParticipating(projectName: String, teamName: String) {
        participation[projectName] = teamName
        score.putIfAbsent(projectName, 0)
        completedChallenges.putIfAbsent(projectName, CopyOnWriteArrayList())
        currentChallenges.putIfAbsent(projectName, CopyOnWriteArrayList())
        rejectedChallenges.putIfAbsent(projectName, CopyOnWriteArrayList())
        storedChallenges.putIfAbsent(projectName, CopyOnWriteArrayList())
        unsolvedAchievements.putIfAbsent(projectName, CopyOnWriteArrayList(GamePublisherDescriptor.achievements))
        completedAchievements.putIfAbsent(projectName, CopyOnWriteArrayList())
        completedQuests.putIfAbsent(projectName, CopyOnWriteArrayList())
        currentQuests.putIfAbsent(projectName, CopyOnWriteArrayList())
        rejectedQuests.putIfAbsent(projectName, CopyOnWriteArrayList())
        unfinishedQuests.putIfAbsent(projectName, CopyOnWriteArrayList())
        completedQuestTasks.putIfAbsent(projectName, CopyOnWriteArrayList())
        currentQuestTasks.putIfAbsent(projectName, CopyOnWriteArrayList())
    }

    /**
     * Sets the user of the property during start of Jenkins and computes the initial git namens.
     */
    override fun setUser(u: User) {
        user = u
        if (gitNames == null) gitNames = computeInitialGitNames()
        if (!PropertyUtil.realUser(user)) unsolvedAchievements = hashMapOf()
    }

    /**
     * Updates changed achievements.
     */
    private fun updateAchievements() {
        for (project in participation.keys) {
            for (achievement in GamePublisherDescriptor.achievements) {

                var ach = unsolvedAchievements[project]!!.find { it == achievement }
                if (ach != null && (ach.fullyQualifiedFunctionName != achievement.fullyQualifiedFunctionName
                            || ach.secret != achievement.secret
                            || ach.additionalParameters != achievement.additionalParameters
                            || ach.unsolvedBadgePath != achievement.unsolvedBadgePath
                            || ach.badgePath != achievement.badgePath)) {

                    val list = unsolvedAchievements[project]!!
                    list.remove(ach)
                    list.add(achievement.clone())
                    unsolvedAchievements[project] = list
                }

                ach = completedAchievements[project]!!.find { it == achievement }
                if (ach != null
                    && (ach.unsolvedBadgePath != achievement.unsolvedBadgePath
                            || ach.badgePath != achievement.badgePath)) {

                    ach.updateBadgePaths(badgePath = achievement.badgePath,
                        unsolvedBadgePath = achievement.unsolvedBadgePath)
                }

                updateChangedTitleDescription(project, achievement)
            }

            var list = unsolvedAchievements[project]!!
            list.removeAll(list.intersect(completedAchievements[project]!!))
            list = CopyOnWriteArrayList(list.distinct())
            unsolvedAchievements[project] = list
        }
    }

    /**
     * Updates achievements with changed title or description.
     */
    private fun updateChangedTitleDescription(project: String, achievement: Achievement) {
        var ach = unsolvedAchievements[project]!!.find {
            (it.title == achievement.title && it.description != achievement.description)
                    || (it.title != achievement.title && it.description == achievement.description) }
        if (ach != null) {
            val list = unsolvedAchievements[project]!!
            list.remove(ach)
            list.add(achievement.clone())
            unsolvedAchievements[project] = list
        }

        ach = completedAchievements[project]!!.find {
            (it.title == achievement.title && it.description != achievement.description)
                    || (it.title != achievement.title && it.description == achievement.description) }
        if (ach != null) {
            val list = completedAchievements[project]!!
            list.remove(ach)
            list.add(achievement.clone(ach))
            completedAchievements[project] = list
        }
    }

    /**
     * Restores a given [challenge] of project [projectName].
     */
    fun restoreChallenge(projectName: String, challenge: Challenge) {

        val chalReasonPair = rejectedChallenges[projectName]!!.find { (chal, _) -> chal == challenge }!!

        rejectedChallenges[projectName]!!.remove(chalReasonPair)
    }

    /**
     * Stores a given [challenge] of project [projectName].
     */
    fun storeChallenge(projectName: String, challenge: Challenge) {
        storedChallenges.computeIfAbsent(projectName) { CopyOnWriteArrayList() }
        val challenges = storedChallenges[projectName]!!
        challenges.add(challenge)
        storedChallenges[projectName] = challenges
        val currentChallenges = currentChallenges[projectName]!!
        currentChallenges.remove(challenge)
        this.currentChallenges[projectName] = currentChallenges
    }

    fun undoStoreChallenge(projectName: String, challenge: Challenge) {
        storedChallenges[projectName]!!.remove(challenge)
        currentChallenges[projectName]!!.add(challenge)
    }

    fun removeStoredChallenge(projectName: String, challenge: Challenge) {
        storedChallenges[projectName]!!.remove(challenge)
    }

    fun addStoredChallenge(projectName: String, challenge: Challenge) {
        storedChallenges[projectName]!!.add(challenge)
    }

}

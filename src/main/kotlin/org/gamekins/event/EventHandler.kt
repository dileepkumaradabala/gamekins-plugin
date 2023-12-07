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

package org.gamekins.event

import hudson.model.Run
import hudson.model.User
import kotlinx.coroutines.runBlocking
import org.gamekins.WebSocketServer
import org.gamekins.event.user.*
import org.gamekins.util.MailUtil
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Handler that stores all events happening in the context of Gamekins. Runs the event after adding it to the list.
 *
 * @author Philipp Straubinger
 * @since 0.3
 */
object EventHandler {

    val events: CopyOnWriteArrayList<Event> = CopyOnWriteArrayList()

    /**
     * Deletes old events, adds a new [event] to the list of [events] and runs it.
     */
    @JvmStatic
    fun addEvent(event: Event) {
        events.add(event)
        Thread(event).start()
    }

    /**
     * Generates the text for all [Event]s based on Challenges.
     */
    private fun generateChallengesText(list: ArrayList<UserEvent>): String {
        var text = ""
        if (list.find { it is ChallengeSolvedEvent } != null) {
            text += "Challenge(s) solved:\n"
            for (event in list.filterIsInstance<ChallengeSolvedEvent>()) {
                text += "- ${event.challenge.toEscapedString()}\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("Challenge(s) solved")
            }
        }

        if (list.find { it is ChallengeUnsolvableEvent } != null) {
            text += "New unsolvable Challenge(s):\n"
            for (event in list.filterIsInstance<ChallengeUnsolvableEvent>()) {
                text += "- ${event.challenge.toEscapedString()}\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("New unsolvable Challenge(s)")
            }
        }

        if (list.find { it is ChallengeGeneratedEvent } != null) {
            text += "Challenge(s) generated:\n"
            for (event in list.filterIsInstance<ChallengeGeneratedEvent>()) {
                text += "- ${event.challenge.toEscapedString()}\n"

            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("Challenge(s) generated")
            }
        }

        return text
    }

    /**
     * Generates the mail text based on the current events.
     */
    fun generateMailText(projectName: String, build: Run<*, *>, user: User, list: ArrayList<UserEvent>): String {
        var text = "Hello ${user.fullName},\n\n"
        text += "here are your Gamekins results from run ${build.number} of project $projectName:\n\n"

        text += generateChallengesText(list)

        text += generateQuestsText(list)

        text += generateQuestTasksText(list)

        if (list.find { it is AchievementSolvedEvent } != null) {
            text += "Achievement(s) solved:\n"
            for (event in list.filterIsInstance<AchievementSolvedEvent>()) {
                text += "- ${event.achievement}\n"
            }
            text += "\n"
        }

        text += "View the build on ${build.parent.absoluteUrl}${build.number}/\n"
        text += MailUtil.generateViewLeaderboardText(build.parent)

        text += "View your achievements on ${user.absoluteUrl}/achievements/"

        return text
    }

    /**
     * Generates the text for all [Event]s based on Quests.
     */
    private fun generateQuestsText(list: ArrayList<UserEvent>): String {
        var text = ""
        if (list.find { it is QuestStepSolvedEvent } != null) {
            text += "Quest step(s) solved:\n"
            for (event in list.filterIsInstance<QuestStepSolvedEvent>()) {
                text += "- ${event.quest}: ${event.questStep}\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("Quest step(s) solved")
            }
        }

        if (list.find { it is QuestSolvedEvent } != null) {
            text += "Quest(s) solved:\n"
            for (event in list.filterIsInstance<QuestSolvedEvent>()) {
                text += "- ${event.quest}\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("Quest(s) solved:")
            }
        }

        if (list.find { it is QuestUnsolvableEvent } != null) {
            text += "New unsolvable Quest(s):\n"
            for (event in list.filterIsInstance<QuestUnsolvableEvent>()) {
                text += "- ${event.quest}\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("New unsolvable Quest(s)")
            }
        }

        if (list.find { it is QuestGeneratedEvent } != null) {
            text += "Quest(s) generated:\n"
            for (event in list.filterIsInstance<QuestGeneratedEvent>()) {
                text += "- ${event.quest}\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("Quest(s) generated")
            }
        }

        return text
    }

    /**
     * Generates the text for all [Event]s based on QuestTasks.
     */
    private fun generateQuestTasksText(list: ArrayList<UserEvent>): String {
        var text = ""
        if (list.find { it is QuestTaskProgressEvent } != null) {
            text += "Progress in Quest(s):\n"
            for (event in list.filterIsInstance<QuestTaskProgressEvent>()) {
                text += "- ${event.questTask}: ${event.currentNumber} of ${event.numberGoal} already done\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("Progress in Quest(s)")
            }
        }

        if (list.find { it is QuestTaskSolvedEvent } != null) {
            text += "Quest(s) solved:\n"
            for (event in list.filterIsInstance<QuestTaskSolvedEvent>()) {
                text += "- ${event.questTask}\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("Quest(s) solved")
            }
        }

        if (list.find { it is QuestTaskGeneratedEvent } != null) {
            text += "Quest(s) generated:\n"
            for (event in list.filterIsInstance<QuestTaskGeneratedEvent>()) {
                text += "- ${event.questTask}\n"
            }
            text += "\n"

            runBlocking {
                WebSocketServer.sendMessage("Quest(s) generated")
            }
        }

        return text
    }
}
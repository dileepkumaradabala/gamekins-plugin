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

package org.gamekins.util

import hudson.FilePath
import java.io.File
import java.io.Serializable
import jenkins.model.Jenkins
import java.nio.file.Path

/**
 * Object with constants for Gamekins and [Parameters] for generation.
 *
 * @author Philipp Straubinger
 * @since 0.4
 */
object Constants {

    /**
     * Object with key constants for configuration forms
     *
     * @author Matthias Rainer
     */
    object FormKeys
    {
        const val PROJECT_NAME = "project"

        const val ACTIVATED = "activated"

        const val SHOW_STATISTICS = "showStatistics"

        const val SHOW_LEADERBOARD = "showLeaderboard"

        const val SHOW_FOLDER_LEADERBOARD = "leaderboard"

        const val CHALLENGES_COUNT = "currentChallengesCount"

        const val QUEST_COUNT = "currentQuestsCount"

        const val STORED_CHALLENGES_COUNT = "currentStoredChallengesCount"

        const val CAN_SEND_CHALLENGE = "canSendChallenge"

        const val SEARCH_COMMIT_COUNT = "searchCommitCount"

        const val PIT_CONFIGURATION = "pitConfiguration"

        const val SHOW_PIT_OUTPUT = "showPitOutput"
    }

    /**
     * Object with default values
     *
     * @author Matthias Rainer
     */
    object Default
    {
        const val CURRENT_CHALLENGES = 3

        const val CURRENT_QUESTS = 1

        const val STORED_CHALLENGES = 2

        const val SEARCH_COMMIT_COUNT = 50

        const val PIT_CONFIGURATION =
                        "<plugin>\n" +
                        "   <groupId>org.pitest</groupId>\n" +
                        "   <artifactId>pitest-maven</artifactId>\n" +
                        "   <version>1.9.10</version>\n" +
                        "   <dependencies>\n" +
                        "       <dependency>\n" +
                        "           <groupId>org.pitest</groupId>\n" +
                        "           <artifactId>pitest-junit5-plugin</artifactId>\n" +
                        "           <version>1.1.0</version>\n" +
                        "       </dependency>\n" +
                        "   </dependencies>\n" +
                        "   <configuration>\n" +
                        "       <outputFormats>\n" +
                        "           <outputFormat>XML</outputFormat>\n" +
                        "       </outputFormats>\n" +
                        "       <targetClasses>\n" +
                        "            <param>{package}.{class}*</param>\n" +
                        "       </targetClasses>\n" +
                        "   </configuration>\n" +
                        "</plugin>"

        const val SHOW_PIT_OUTPUT = false

        const val AVATAR = "001-actress.png"
    }

    /**
     * Object with Error messages
     *
     * @author Matthias Rainer
     */
    object Error
    {
        const val UNEXPECTED = "Unexpected Error"

        const val GENERATION = "There was an error with generating a new challenge"

        const val NO_CHALLENGE_EXISTS = "The challenge does not exist"

        const val NO_USER_SIGNED_IN = "There is no user signed in"

        const val PARENT = "$UNEXPECTED: Parent job is null"

        const val RETRIEVING_PROPERTY = "Unexpected error while retrieving the property"

        const val SAVING = "There was an error with saving"

        const val NO_REASON = "Please insert your reason for rejection"

        const val REJECT_DUMMY = "Dummies cannot be rejected - please run another build"

        const val STORE_DUMMY = "Dummies cannot be stored - please run another build"

        const val NO_TEAM_NAME = "Insert a name for the team"

        const val STORAGE_LIMIT = "Storage Limit reached"

        const val RECEIVER_IS_SELF = "Cannot send challenges to yourself"

        const val USER_NOT_FOUND = "User not found"

        const val UNKNOWN_GAME_PROPERTY = "Unknown Game Property"

        const val NO_TEAM = "No team specified"
        
        const val UNKNOWN_TEAM = "The specified team does not exist"
        
        const val USER_NOT_IN_PROJECT = "The user does not participate in the project"

        const val UNKNOWN_USER = "No user with the specified name found"

        const val PARENT_WITHOUT_PROPERTY = "$UNEXPECTED: Parent job has no property"

        const val USER_ALREADY_IN_TEAM = "The user is already participating in a team"

        const val TEAM_NAME_TAKEN = "The team already exists - please use another name for your team"
    }

    object Mutation {

        const val SHIFT_LEFT = "&lt;&lt;"

        const val SHIFT_RIGHT = "&gt;&gt;"

        const val REPORT_PATH = "/target/pit-reports/mutations.xml"

        const val RETURN_TRUE = "return true"

        const val RETURN_FALSE = "return false"

        const val RETURN_ZERO = "return 0"

        val RETURN_REGEX = "return .*[^;]".toRegex()
    }

    const val AND_TYPE = " and type "

    const val EXISTS = " exists "

    const val NO_QUEST = "No quest could be generated. This could mean that none of the prerequisites was met, " +
            "please try again later."

    const val NOT_ACTIVATED = "[Gamekins] Not activated"

    const val NOT_SOLVED = "Not solved"

    const val NOTHING_DEVELOPED = "You haven't developed anything lately"

    const val REJECTED_QUEST = "Previous quest was rejected, please run a new build to generate a new quest"

    const val RUN_TOTAL_COUNT = 200

    val SONAR_JAVA_PLUGIN = pathToSonarJavaPlugin()

    const val TYPE_JSON = "application/json"

    const val TYPE_PLAIN = "text/plain"

    const val TRY_CLASS = "[Gamekins] Try class "

    const val NO_TEAM_TEAM_NAME = "---"

    /**
     * Returns the path to the most recent jar file of the Sonar-Java-Plugin for SonarLint.
     */
    private fun pathToSonarJavaPlugin(): Path {
        val projectPath = System.getProperty("user.dir")
        var libFolder = File("$projectPath/target/lib")
        if (!libFolder.exists()) libFolder =
            File("${Jenkins.getInstanceOrNull()?.root?.absolutePath}/plugins/gamekins/WEB-INF/lib")
        val jars = libFolder.listFiles()!!.filter { it.nameWithoutExtension.contains("sonar-java-plugin") }
        return jars.last().toPath()
    }

    /**
     * The class representation of parameters during challenge generation.
     *
     * @author Philipp Straubinger
     * @since 0.4
     */
    class Parameters(
        var branch: String = "",
        var currentChallengesCount: Int = Default.CURRENT_CHALLENGES,
        var currentQuestsCount: Int = Default.CURRENT_QUESTS,
        var storedChallengesCount: Int = Default.STORED_CHALLENGES,
        var searchCommitCount: Int = Default.SEARCH_COMMIT_COUNT,
        var pitConfiguration: String = Default.PIT_CONFIGURATION,
        var showPitOutput: Boolean = Default.SHOW_PIT_OUTPUT,
        var generated: Int = 0,
        var jacocoCSVPath: String = "",
        var jacocoResultsPath: String = "",
        var projectCoverage: Double = 0.0,
        var projectName: String = "",
        var projectTests: Int = 0,
        var solved: Int = 0,
        workspace: FilePath = FilePath(null, "")
    ) : Serializable {

        var remote: String = workspace.remote

        @Transient var workspace: FilePath = workspace
            set(value) {
                remote = value.remote
                field = value
            }

        /**
         * Called by Jenkins after the object has been created from his XML representation. Used for data migration.
         */
        @Suppress("unused", "SENSELESS_COMPARISON")
        private fun readResolve(): Any {
            if (workspace == null) workspace = FilePath(null, remote)
            if (pitConfiguration == null) pitConfiguration = Default.PIT_CONFIGURATION
            if (showPitOutput ==null) showPitOutput = Default.SHOW_PIT_OUTPUT
            return this
        }
    }
}

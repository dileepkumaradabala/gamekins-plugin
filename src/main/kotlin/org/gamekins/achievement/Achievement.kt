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

package org.gamekins.achievement

import hudson.model.Run
import hudson.model.TaskListener
import org.gamekins.GameUserProperty
import org.gamekins.LeaderboardAction
import org.gamekins.file.FileDetails
import org.gamekins.util.Constants
import org.gamekins.util.Constants.Parameters
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

/**
 * Class for holding an individual [Achievement]. Described in a json file in path /resources/achievements/ and
 * initialized with [AchievementInitializer].
 *
 * @author Philipp Straubinger
 * @since 0.2
 */
class Achievement(var badgePath: String, var unsolvedBadgePath: String, val fullyQualifiedFunctionName: String,
                  val description: String, val title: String, val secret: Boolean,
                  val additionalParameters: HashMap<String, String>) {

    @Transient private lateinit var callClass: KClass<out Any>
    @Transient private lateinit var callFunction: KCallable<*>
    private var solvedTime: Long = 0
    val solvedTimeString: String
    get() {
        if (solvedTime == 0L) {
            return Constants.NOT_SOLVED
        } else {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = solvedTime
            val month = when (calendar.get(Calendar.MONTH)) {
                Calendar.JANUARY -> "Jan"
                Calendar.FEBRUARY -> "Feb"
                Calendar.MARCH -> "Mar"
                Calendar.APRIL -> "Apr"
                Calendar.MAY -> "May"
                Calendar.JUNE -> "Jun"
                Calendar.JULY -> "Jul"
                Calendar.AUGUST -> "Aug"
                Calendar.SEPTEMBER -> "Sep"
                Calendar.OCTOBER -> "Oct"
                Calendar.NOVEMBER -> "Nov"
                Calendar.DECEMBER -> "Dec"
                else -> ""
            }
            var hour = calendar.get(Calendar.HOUR).toString()
            if (hour.toInt() < 10) hour = "0$hour"
            var minute = calendar.get(Calendar.MINUTE).toString()
            if (minute.toInt() < 10) minute = "0$minute"
            val amPm = when (calendar.get(Calendar.AM_PM)) {
                Calendar.AM -> "am"
                Calendar.PM -> "pm"
                else -> ""
            }
            return "Achieved ${calendar.get(Calendar.DAY_OF_MONTH)} $month ${calendar.get(Calendar.YEAR)} " +
                    "@ $hour:$minute $amPm"
        }
    }

    init {
        initCalls()
    }

    fun clone(): Achievement {
        return clone(this)
    }

    fun clone(ach: Achievement): Achievement {
        val achievement = Achievement(badgePath, unsolvedBadgePath, fullyQualifiedFunctionName, description, title,
            secret, additionalParameters)
        achievement.solvedTime = ach.solvedTime
        return achievement
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Achievement) return false
        return other.description == this.description && other.title == this.title
    }

    override fun hashCode(): Int {
        var result = badgePath.hashCode()
        result = 31 * result + fullyQualifiedFunctionName.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + callClass.hashCode()
        result = 31 * result + callFunction.hashCode()
        result = 31 * result + solvedTime.hashCode()
        return result
    }

    /**
     * Initializes the [callClass] and the [callFunction], which are both transient. The reason is that Kotlin classes
     * are not on the white list for serialisation by Jenkins. All of the needed classes could be added manually, but
     * that is not feasible for reflection types.
     */
    private fun initCalls() {
        val reference = fullyQualifiedFunctionName.split("::")
        callClass = Class.forName(reference[0]).kotlin
        callFunction = callClass.members.single { it.name == reference[1] }
    }

    /**
     * Checks whether the [Achievement] is solved. Adds all parameters to an array to be passed into a vararg
     * parameter and executes the [callFunction] of the [callClass].
     */
    fun isSolved(
        files: ArrayList<FileDetails>, parameters: Parameters, run: Run<*, *>,
        property: GameUserProperty, listener: TaskListener = TaskListener.NULL): Boolean {
        val array = arrayOf(callClass.objectInstance, files, parameters, run, property, listener,
            additionalParameters)
        val result: Boolean = callFunction.call(*array) as Boolean
        if (result) solvedTime = System.currentTimeMillis()
        return result
    }

    /**
     * Returns the String representation of the [Achievement] for the [LeaderboardAction].
     */
    fun printToXML(indentation: String): String {
        return "$indentation<Achievement title=\"$title\" description=\"$description\" secret=\"$secret\" " +
                "solved=\"$solvedTime\"/>"
    }

    /**
     * Called by Jenkins after the object has been created from his XML representation. Used for data migration.
     */
    @Suppress("unused")
    private fun readResolve(): Any {
        initCalls()
        return this
    }

    override fun toString(): String {
        return "$title: $description"
    }

    /**
     * Sets a new [badgePath] and/or [unsolvedBadgePath].
     */
    fun updateBadgePaths(badgePath: String = this.badgePath, unsolvedBadgePath: String = this.unsolvedBadgePath) {
        this.badgePath = badgePath
        this.unsolvedBadgePath = unsolvedBadgePath
    }
}
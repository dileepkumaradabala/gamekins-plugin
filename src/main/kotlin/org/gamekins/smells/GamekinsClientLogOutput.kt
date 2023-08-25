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

package org.gamekins.smells

import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput

/**
 * The log output from SonarLint during execution.
 *
 * @author Philipp Straubinger
 * @since 0.5
 */
class GamekinsClientLogOutput: ClientLogOutput {

    val messages = arrayListOf<Triple<Long, ClientLogOutput.Level, String>>()

    override fun log(formattedMessage: String, level: ClientLogOutput.Level) {
        messages.add(Triple(System.currentTimeMillis(), level, formattedMessage))
    }
}
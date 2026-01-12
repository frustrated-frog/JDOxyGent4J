/*
 * Copyright 2025 JD.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this project except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.oxygent.core.oxygent.samples.examples.liveprompt;

import com.jd.oxygent.core.oxygent.oxy.BaseOxy;
import com.jd.oxygent.core.oxygent.oxy.agents.ChatAgent;
import com.jd.oxygent.core.oxygent.oxy.llms.HttpLlm;
import com.jd.oxygent.core.oxygent.samples.server.ServerApp;
import com.jd.oxygent.core.oxygent.samples.server.masprovider.engine.annotation.OxySpaceBean;
import com.jd.oxygent.core.oxygent.samples.server.utils.GlobalDefaultOxySpaceMapping;
import com.jd.oxygent.core.oxygent.utils.EnvUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Demo application showing the use of OxyGent with live prompts
 *
 * @author OxyGent Team
 * @version 1.0.10.4
 * @since 1.0.10.4
 */
public class DemoLivePrompt {

    /**
     * Get default OxySpace configuration
     *
     * <p>Configuration includes:</p>
     * <ul>
     *   <li><strong>HttpLlm</strong>: HTTP LLM service configuration</li>
     *   <li><strong>ChatAgent</strong>: Chat agent configured with input processing function</li>
     * </ul>
     *
     * <p>Note: ChatAgent uses funcProcessInput to process input,
     * allowing test messages to be sent before agent processes requests.</p>
     *
     * @return List of BaseOxy containing LLM and ChatAgent
     */
    @OxySpaceBean(value = "defaultJavaOxySpace", defaultStart = true, query = "hello")
    public static List<BaseOxy> getDefaultOxySpace() {
        var apiKey = EnvUtils.getEnv("OXY_LLM_API_KEY");
        var baseUrl = EnvUtils.getEnv("OXY_LLM_BASE_URL");
        var modelName = EnvUtils.getEnv("OXY_LLM_MODEL_NAME");

        return Arrays.asList(
                // 1. HTTP LLM Configuration
                HttpLlm.builder()
                        .name("default_llm")
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName(modelName)
                        .timeout(30)
                        .build(),
                // 2. Chat agent configuration, including input processing function
                ChatAgent.builder()
                        .name("chat_agent1")
                        .prompt("You are a helpful assistant.")
                        .promptKey("my_prompt")
                        .llmModel("default_llm")
                        .build(),
                ChatAgent.builder()
                        .name("chat_agent2")
                        .prompt("You are a helpful assistant.")
                        .promptKey("my_prompt")
                        .llmModel("default_llm")
                        .build(),
                ChatAgent.builder()
                        .name("chat_agent3")
                        .prompt("You are a helpful assistant.")
                        .llmModel("default_llm")
                        .userLivePrompt(false)
                        .build()
        );
    }

    public static void main(String[] args) throws Exception {
        var currentClassName = Thread.currentThread().getStackTrace()[1].getClassName();
        GlobalDefaultOxySpaceMapping.searchCurrentThreadStackAnnotationOxySpaceName(currentClassName);
        ServerApp.main(args);
    }
}
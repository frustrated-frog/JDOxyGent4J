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
import com.jd.oxygent.core.oxygent.oxy.agents.ReActAgent;
import com.jd.oxygent.core.oxygent.oxy.llms.HttpLlm;
import com.jd.oxygent.core.oxygent.samples.server.ServerApp;
import com.jd.oxygent.core.oxygent.samples.server.masprovider.engine.annotation.OxySpaceBean;
import com.jd.oxygent.core.oxygent.samples.server.utils.GlobalDefaultOxySpaceMapping;
import com.jd.oxygent.core.oxygent.tools.PresetTools;
import com.jd.oxygent.core.oxygent.utils.EnvUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Demo application showing the use of OxyGent with live prompts
 *
 * @author OxyGent Team
 * @version 1.0.10.4
 * @since 1.0.10.4
 */
public class DemoLivePrompt2 {

    /**
     * Get default OxySpace configuration
     * Create complete configuration space containing LLM, tools and agents
     *
     * @return BaseOxy list containing all necessary components
     * @throws IllegalArgumentException when configuration parameters are invalid
     */
    @OxySpaceBean(value = "defaultJavaOxySpace", defaultStart = true, query = "What time is it now Asia/Shanghai? Please save it into time.txt.")
    public static List<BaseOxy> getDefaultOxySpace() {
        // Using JDK17 var keyword to simplify local variable declaration
        var apiKey = EnvUtils.getEnv("OXY_LLM_API_KEY");
        var baseUrl = EnvUtils.getEnv("OXY_LLM_BASE_URL");
        var modelName = EnvUtils.getEnv("OXY_LLM_MODEL_NAME");

        return Arrays.asList(
                // 1. HTTP LLM configuration
                HttpLlm.builder()
                        .name("default_llm")
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName(modelName)
                        .timeout(30)
                        .build(),

                // 2. Time tools
                PresetTools.TIME_TOOLS,

                // use system prompt
                ReActAgent.builder()
                        .name("time_agent")
                        .desc("Tool agent capable of querying time")
                        .prompt("You are a time management assistant. Help users with time-related queries.")
                        .tools(Arrays.asList("time_tools")) // Tool name list
                        .userLivePrompt(false)
                        .build(),

                // 4. File tools
                PresetTools.FILE_TOOLS,

                // use this prompt
                ReActAgent.builder()
                        .name("file_agent")
                        .desc("Tool agent capable of file system operations")
                        .prompt("You are a file system assistant. Help users with file operations safely and efficiently.")
                        .tools(Arrays.asList("file_tools"))
                        .userLivePrompt(false)
                        .build(),

                // 6. Math tools
                PresetTools.MATH_TOOLS,

                // use live prompt
                ReActAgent.builder()
                        .name("math_agent")
                        .desc("Tool agent capable of mathematical calculations")
                        .prompt("You are a math assistant. Help users with mathematical calculations.")
                        .tools(Arrays.asList("math_tools"))
                        .build(),

                // use live prompt
                ReActAgent.builder()
                        .isMaster(true) // Set as master agent
                        .name("master_agent")
                        .llmModel("default_llm")
                        .subAgents(Arrays.asList("time_agent", "file_agent", "math_agent")) // Sub-agent list
                        .prompt("You are the master agent. Coordinate the actions of your sub-agents effectively.")
                        .build()
        );
    }

    public static void main(String[] args) throws Exception {
        var currentClassName = Thread.currentThread().getStackTrace()[1].getClassName();
        GlobalDefaultOxySpaceMapping.searchCurrentThreadStackAnnotationOxySpaceName(currentClassName);
        ServerApp.main(args);
    }
}

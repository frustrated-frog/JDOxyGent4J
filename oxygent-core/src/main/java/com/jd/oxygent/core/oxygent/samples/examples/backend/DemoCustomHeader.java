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
package com.jd.oxygent.core.oxygent.samples.examples.backend;

import com.jd.oxygent.core.oxygent.oxy.BaseOxy;
import com.jd.oxygent.core.oxygent.oxy.agents.ReActAgent;
import com.jd.oxygent.core.oxygent.oxy.llms.HttpLlm;
import com.jd.oxygent.core.oxygent.samples.server.ServerApp;
import com.jd.oxygent.core.oxygent.samples.server.masprovider.engine.annotation.OxySpaceBean;
import com.jd.oxygent.core.oxygent.samples.server.utils.GlobalDefaultOxySpaceMapping;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyRequest;
import com.jd.oxygent.core.oxygent.utils.EnvUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Demonstrates how to configure custom headers send to remote_llm
 *
 * @author OxyGent Team
 * @version 1.0.10.4
 * @since 1.0.10.4
 */
public class DemoCustomHeader {

    /**
     *  Custom headers function
     *  CAUTION: this function will be called every time a request is made to remote_llm, and will override the default headers
     * @param oxyRequest
     * @return
     */
    private static Map customHeaders(OxyRequest oxyRequest) {
        Objects.requireNonNull(oxyRequest, "OxyRequest object cannot be null");
        HashMap<String, Object> headers = new HashMap<>();
        if (oxyRequest.getSharedData() != null && oxyRequest.getSharedData().get("_headers") != null) {
            headers.putAll((Map) oxyRequest.getSharedData().get("_headers"));
        }
        headers.entrySet().removeIf(entry -> entry.getKey().equals("user-agent"));
        return headers;
    };

    /**
     * Get default OxySpace configuration containing ReAct agent
     *
     * @return BaseOxy list containing ReAct agent
     * @throws IllegalArgumentException when configuration parameters are invalid
     */
    @OxySpaceBean(value = "demoCustomHeader", defaultStart = true, query = "hello")
    public static List<BaseOxy> getDefaultOxySpace() {
        // Apply JDK17 var keyword and parameter validation
        var apiKey = EnvUtils.getEnv("OXY_LLM_API_KEY");
        var baseUrl = EnvUtils.getEnv("OXY_LLM_BASE_URL");
        var modelName = EnvUtils.getEnv("OXY_LLM_MODEL_NAME");

        return Arrays.asList(
                HttpLlm.builder()
                        .name("default_llm")
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName(modelName)
                        .headers(Map.of("my_custom_header", "my_custom_value"))
                        .funcHeaders(DemoCustomHeader::customHeaders)
                        .build(),
                ReActAgent.builder()
                        .name("master_agent")
                        .llmModel("default_llm")
                        .additionalPrompt("Please provide the optimal answer based on my question")
                        .build()
        );
    }

    /**
     * Application main entry point
     * Initialize ReAct agent and start Spring Boot application
     *
     * @param args command line arguments
     * @throws Exception when application startup fails
     */
    public static void main(String[] args) throws Exception {
        var currentClassName = Thread.currentThread().getStackTrace()[1].getClassName();
        GlobalDefaultOxySpaceMapping.searchCurrentThreadStackAnnotationOxySpaceName(currentClassName);
        ServerApp.main(args);
    }
}

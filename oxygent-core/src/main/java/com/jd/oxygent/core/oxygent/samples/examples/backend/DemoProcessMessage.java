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

import com.jd.oxygent.core.Mas;
import com.jd.oxygent.core.oxygent.oxy.BaseOxy;
import com.jd.oxygent.core.oxygent.oxy.agents.ReActAgent;
import com.jd.oxygent.core.oxygent.oxy.llms.HttpLlm;
import com.jd.oxygent.core.oxygent.oxy.mcp.StdioMCPClient;
import com.jd.oxygent.core.oxygent.samples.server.ServerApp;
import com.jd.oxygent.core.oxygent.samples.server.masprovider.engine.annotation.OxySpaceBean;
import com.jd.oxygent.core.oxygent.samples.server.utils.GlobalDefaultOxySpaceMapping;
import com.jd.oxygent.core.oxygent.schemas.memory.Memory;
import com.jd.oxygent.core.oxygent.schemas.memory.Message;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyRequest;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyResponse;
import com.jd.oxygent.core.oxygent.utils.EnvUtils;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo class for demonstrating custom message processing in OxyGent MAS (Multi-Agent System)
 *
 * <p>This class demonstrates how to implement and register a custom message processor
 * for a MAS instance, showing how to modify and enhance message handling:</p>
 * <ul>
 *   <li><strong>Manual MAS Creation</strong>: Create and initialize MAS instance programmatically</li>
 *   <li><strong>Custom Message Processor</strong>: Register a custom message processing function using setFuncProcessMessage()</li>
 *   <li><strong>Stream Message Handling</strong>: Process streaming messages by modifying content dynamically</li>
 *   <li><strong>Chat Interface</strong>: Test message processing through chatWithAgent() interaction</li>
 *   <li><strong>Error Handling</strong>: Proper exception handling for MAS operations</li>
 * </ul>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li><strong>Message Transformation</strong>: Modify message content before or after processing</li>
 *   <li><strong>Stream Processing</strong>: Handle streaming messages with dynamic content updates</li>
 *   <li><strong>Flexible Integration</strong>: Works with both manual MAS creation and Spring Boot integration</li>
 *   <li><strong>ReAct Agent</strong>: Uses reasoning and acting agent with tool access</li>
 * </ul>
 *
 * <h3>Message Processing Flow</h3>
 * <ol>
 *   <li>MAS instance is created and initialized</li>
 *   <li>Custom message processor is registered using setFuncProcessMessage()</li>
 *   <li>Chat request is sent to the agent</li>
 *   <li>Messages pass through the custom processor for modification</li>
 *   <li>Processed messages are returned to the caller</li>
 * </ol>
 *
 * @author OxyGent Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Log4j2
public class DemoProcessMessage {

    /**
     * Get default OxySpace configuration for MAS launch demo
     *
     * @return List of BaseOxy containing HTTP LLM, MCP client and ReAct agent
     */
    @OxySpaceBean(value = "launchMasJavaOxySpace", defaultStart = true, query = "hello")
    public static List<BaseOxy> getDefaultOxySpace() {

        return Arrays.asList(
                HttpLlm.builder()
                        .name("default_llm")
                        .apiKey(EnvUtils.getEnv("OXY_LLM_API_KEY"))
                        .baseUrl(EnvUtils.getEnv("OXY_LLM_BASE_URL"))
                        .modelName(EnvUtils.getEnv("OXY_LLM_MODEL_NAME"))
                        .build(),
                ReActAgent.builder()
                        .name("master_agent")
                        .isPermissionRequired(false)
                        .isMaster(true)
                        .build()
        );
    }

    /**
     * Application main entry point - demonstrates manual MAS creation and various interaction modes
     *
     * @param args Command line arguments
     * @throws Exception When MAS operations fail
     */
    public static void main(String[] args) throws Exception {
        Mas mas = new Mas("app", getDefaultOxySpace());
        mas.init();
        mas.setFuncProcessMessage(DemoProcessMessage::processMessage);
        Map<String, Object> payload = new HashMap<>(Map.of("query", "hello"));
        try {
            mas.chatWithAgent(payload);
        } catch (Exception e) {
            log.error("Chat execution failed", e);
        }
    }

    private static Map processMessage(Map body, OxyRequest oxyRequest) {
        if ("stream".equals((String) body.get("type")) && body.get("content") instanceof Map content) {
            content.put("delta", content.get("delta") + "-");
        }
        return body;
    }
}

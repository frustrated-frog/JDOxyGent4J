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

import com.fasterxml.jackson.databind.JsonNode;
import com.jd.oxygent.core.Config;
import com.jd.oxygent.core.oxygent.oxy.BaseOxy;
import com.jd.oxygent.core.oxygent.oxy.agents.ReActAgent;
import com.jd.oxygent.core.oxygent.oxy.agents.WorkflowAgent;
import com.jd.oxygent.core.oxygent.oxy.llms.HttpLlm;
import com.jd.oxygent.core.oxygent.samples.server.ServerApp;
import com.jd.oxygent.core.oxygent.samples.server.masprovider.engine.annotation.OxySpaceBean;
import com.jd.oxygent.core.oxygent.samples.server.utils.GlobalDefaultOxySpaceMapping;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyRequest;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyResponse;
import com.jd.oxygent.core.oxygent.utils.EnvUtils;
import com.jd.oxygent.core.oxygent.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * Demo class for manual send custom feedback, blocking get feedback stream
 *
 * @author OxyGent Team
 * @version 1.0.10.4
 * @since 1.0.10.4
 */
@Slf4j
public class DemoHumanInTheLoop {

    /**
     * Get default OxySpace configuration
     *
     * @return List of BaseOxy containing HTTP LLM and ReAct Agent
     */
    @OxySpaceBean(value = "DemoHumanInTheLoop", defaultStart = true, query = "hello")
    public static List<BaseOxy> getDefaultOxySpace() {
        return Arrays.asList(
                HttpLlm.builder()
                        .name("default_llm")
                        .apiKey(EnvUtils.getEnv("OXY_LLM_API_KEY"))
                        .baseUrl(EnvUtils.getEnv("OXY_LLM_BASE_URL"))
                        .modelName(EnvUtils.getEnv("OXY_LLM_MODEL_NAME"))
                        .build(),
                WorkflowAgent.builder()
                        .name("master_agent")
                        .funcWorkflow(DemoHumanInTheLoop::workflow)
                        .isPermissionRequired(false)
                        .isMaster(true)
                        .build()
        );
    }

    private static String workflow(OxyRequest oxyRequest) {
        oxyRequest.sendMessage(Map.of("type", "msg_type", "content", "msg_content"));

        String channelId = "my_custom_channel_id";
        // blocking get feedback stream
        return oxyRequest.getFeedbackStream(channelId);
    }

    /**
     * Application main entry point
     *
     * <p>Loads configuration from specified path and starts the server application.</p>
     *
     * @param args Command line arguments
     * @throws Exception When configuration loading or application startup fails
     */
    public static void main(String[] args) throws Exception {
        // simulate sending feedback, wait 20 seconds for user operation
        new Thread(() -> {
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String url = "http://localhost:8888/feedback";
            String channelId = "my_custom_channel_id";
            String jsonBody = String.format("""
            {"channel_id": "%s", "data": "my_custom_feedback"}
            """, channelId);
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                Map<String, String> requestHeaders = new HashMap<>();
                requestHeaders.put("Content-Type", "application/json");
                requestHeaders.forEach(requestBuilder::header);
                HttpRequest request = requestBuilder.build();
                HttpResponse response = HttpLlm.getHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
                log.info("response statusCode:{}", response.statusCode());
            } catch (IOException | InterruptedException e) {
                log.error("Error sending HTTP request", e);
            }
        }).start();

        GlobalDefaultOxySpaceMapping.searchCurrentThreadStackAnnotationOxySpaceName(Thread.currentThread().getStackTrace()[1].getClassName());
        ServerApp.main(args);
    }
}

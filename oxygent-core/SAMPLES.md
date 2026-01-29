# JDOxyGent4J Samples Index (English)

This document summarizes examples under [samples/](./src/main/java/com/jd/oxygent/core/oxygent/samples/) .
It only lists sample names and capabilities, grouped by directory for quick scanning.

## agent (basic and multi-agent architecture)

| Sample | Capability |
| - | - |
| [DemoSingleAgent](./src/main/java/com/jd/oxygent/core/oxygent/samples/agent/DemoSingleAgent.java) | Smallest single-agent system; a single ReActAgent calling tools |
| [DemoReactAgent](./src/main/java/com/jd/oxygent/core/oxygent/samples/agent/DemoReactAgent.java) (supported) | ReAct reasoning loop: Think → Act → Observe → Reflect |
| [DemoChatAgentStream](./src/main/java/com/jd/oxygent/core/oxygent/samples/agent/DemoChatAgentStream.java) (supported) | Chat agent with SSE streaming output; ideal for chatbot scenarios |
| [DemoWorkflowAgent](./src/main/java/com/jd/oxygent/core/oxygent/samples/agent/DemoWorkflowAgent.java) | WorkflowAgent executes custom workflows; explicit call order and data flow |
| [DemoHeterogeneousAgents](./src/main/java/com/jd/oxygent/core/oxygent/samples/agent/DemoHeterogeneousAgents.java) | Heterogeneous agents collaboration (ReAct/Chat/MCP) |
| [DemoHierarchicalAgents](./src/main/java/com/jd/oxygent/core/oxygent/samples/agent/DemoHierarchicalAgents.java) | Hierarchical master–sub agents; permissions and call-chain tracing |
| [DemoRagAgent](./src/main/java/com/jd/oxygent/core/oxygent/samples/agent/DemoRagAgent.java) (supported) | Retrieval-Augmented Generation (RAG); vector search to enhance answers |

## tools (Tool Hub and MCP integration)

| Sample | Capability |
| - | - |
| [DemoFunctionhub](./src/main/java/com/jd/oxygent/core/oxygent/samples/tools/DemoFunctionhub.java) (supported) | Register functions as tools via FunctionHub; decorator-style registration; type validation |
| [DemoFunctionhubAnnotation](./src/main/java/com/jd/oxygent/core/oxygent/samples/tools/DemoFunctionhubAnnotation.java) | Annotation-based tool registration; simplified declaration and injection |
| [DemoMcp](./src/main/java/com/jd/oxygent/core/oxygent/samples/tools/DemoMcp.java) | MCP protocol tools via Stdio/SSE/Streamable clients; supports local and remote MCP servers |

## advanced (advanced features)

| Sample | Capability |
| - | - |
| [DemoContinueExec](./src/main/java/com/jd/oxygent/core/oxygent/samples/advanced/DemoContinueExec.java) | Resume from a specific node and regenerate; helpful for debugging and iteration |
| [DemoCustomAgentInputSchema](./src/main/java/com/jd/oxygent/core/oxygent/samples/advanced/DemoCustomAgentInputSchema.java) | Custom agent input schema; structured parameter passing |
| [DemoMultimodal](./src/main/java/com/jd/oxygent/core/oxygent/samples/advanced/DemoMultimodal.java) | Multimodal input; enable `is_multimodal_supported`; attachments via URL/Base64/images/videos |
| [DemoMultimodalTransfer](./src/main/java/com/jd/oxygent/core/oxygent/samples/advanced/DemoMultimodalTransfer.java) | Cross-agent multimodal data transfer; auto-generate accessible web links |
| [DemoSendMessageFromTool](./src/main/java/com/jd/oxygent/core/oxygent/samples/advanced/DemoSendMessageFromTool.java) | Tools push intermediate messages via `send_message()`; real-time progress and observability |
| [DemoTrustMode](./src/main/java/com/jd/oxygent/core/oxygent/samples/advanced/DemoTrustMode.java) | Trust mode returns raw tool output; skip LLM post-processing |
| [DemoSaveMessage](./src/main/java/com/jd/oxygent/core/oxygent/samples/advanced/DemoSaveMessage.java) | Fine-grained message persistence control (e.g., Elasticsearch); optimize storage cost |

## flows (orchestration flows)

| Sample | Capability |
| - | - |
| [PlanAndSolveDemo](./src/main/java/com/jd/oxygent/core/oxygent/samples/flows/PlanAndSolveDemo.java) | Two-phase Plan-and-Solve: planner builds a plan, executor follows it; optional replanning; structured output parsing |
| [ReflexionAgentDemo](./src/main/java/com/jd/oxygent/core/oxygent/samples/flows/ReflexionAgentDemo.java) | Reflexion mechanism: self-evaluation and improvement; feedback when response quality is insufficient |

## backend (routes, attachments, concurrency, config, bootstrap and logging)

| Sample | Capability |
| - | - |
| [DemoAddRouter](./src/main/java/com/jd/oxygent/core/oxygent/samples/backend/DemoAddRouter.java) | Dynamically register routes; extend web service API endpoints |
| [DemoAttachment](./src/main/java/com/jd/oxygent/core/oxygent/samples/backend/DemoAttachment.java) | Attachment handling; file upload and pass-through; images/videos; path and URL conversions |
| [DemoBatchAndSemaphore](./src/main/java/com/jd/oxygent/core/oxygent/samples/backend/DemoBatchAndSemaphore.java) | Batch processing and concurrency control; semaphore-limited parallelism; throughput optimization |
| [DemoConfig](./src/main/java/com/jd/oxygent/core/oxygent/samples/backend/DemoConfig.java) | Config system usage; DB/LLM/env variable management; multi-environment support |
| [DemoDataScope](./src/main/java/com/jd/oxygent/core/oxygent/samples/backend/DemoDataScope.java) | Data scopes: request (`arguments`), session (`shared_data`), group (`group_data`) |
| [DemoGlobalData](./src/main/java/com/jd/oxygent/core/oxygent/samples/backend/DemoGlobalData.java) | Global data shared across agents; `get_global_data()` and `set_global_data()` |
| [DemoLaunchMas](./src/main/java/com/jd/oxygent/core/oxygent/samples/backend/DemoLaunchMas.java) | MAS system bootstrap; component registration, DB initialization, organization graph; CLI/Web/Programmatic modes |
| [DemoLoggerSetup](./src/main/java/com/jd/oxygent/core/oxygent/samples/backend/DemoLoggerSetup.java) | Logging configuration; custom formats and levels; trace `trace_id` and `node_id` for request tracing |

## distributed (Distributed and multi-node collaboration)

| Sample | Capability |
| - | - |
| [DemoDistributedMas](./src/main/java/com/jd/oxygent/core/oxygent/samples/distributed/DemoDistributedMas.java) | Distributed and multi-node collaboration; supports Python↔Java interop calls |

---

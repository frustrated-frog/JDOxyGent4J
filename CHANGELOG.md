# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Added Prompt management interface, supporting online Prompt modification, see [DemoLivePrompt.java](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/liveprompt/DemoLivePrompt.java)
- Create or update conversation evaluations, supporting additional comments
- Added oxy.BaseBank for standardizing agent input information, see [bank](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/banks)
- Added user feedback interface /feedback for implementing human-in-the-loop, see [DemoHumanInTheLoop.java](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/backend/DemoHumanInTheLoop.java)
- Added stream_end message as streaming message end identifier
- Support for batch storage of stream messages
- Standardized SSE message fields id, event, data
- SSEOxyGent passes through headers
- Added front-end streaming output capability
- Added custom function for processing message body in mas method, supporting adding/deleting body fields, see [DemoMasFunction](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/backend/DemoMasFunction.java)

### Changed
- Added oxy.BaseLLM parameter to support custom multimodal base64 prefix
- MAS class added func_process_message method for unified message processing, see [DemoProcessMessage.java](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/backend/DemoProcessMessage.java)
- Modified message table structure, added fields
- When storing in history table, memory's answer field is forcibly converted to str
- Changed LLM parameter stream default value to True
- Added Agent name field to think messages
- Changed Config.getServer().getWelcomeMessage() to Config.getAgent().getWelcomeMessage()
- Optimized ObjectUtils.deepCopy method performance
- Refactored RemoteEs search method to enhance readability
- Added concurrency, timeout, retry, delay and other parameters to large model parameters

### Fixed
- When the send_msg_key parameter is empty in the chat_with_agent function, modified to not send messages

---
# Changelog

所有重要变更将在此文件中记录。

## [Unreleased]

### Added
- 新增Prompt管理界面，支持在线修改Prompt，详见 [DemoLivePrompt.java](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/liveprompt/DemoLivePrompt.java)
- 创建或更新对话评价，支持附加评论
- 新增oxy.BaseBank，用于标准化智能体输入信息，详见 [bank](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/banks)
- 新增用户反馈接口/feedback，用于实现human-in-the-loop，详见 [DemoHumanInTheLoop.java](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/backend/DemoHumanInTheLoop.java)
- 新增流式消息结束标识的stream_end消息
- stream消息支持分批存储
- 标准化sse消息字段id、event、data
- SSEOxyGent透传headers
- 新增前端的流式输出能力
- 在mas方法中增加处理消息body的自定义function,支持增减body的字段，详见 [DemoMasFunction](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/backend/DemoMasFunction.java)

### Changed
- 新增oxy.BaseLLM参数，支持自定义多模态base64前缀
- MAS类新增func_process_message方法，用于统一处理消息，详见 [DemoProcessMessage.java](./oxygent-core/src/main/java/com/jd/oxygent/core/oxygent/samples/examples/backend/DemoProcessMessage.java)
- 修改message表结构，新增字段
- history表存储时，memory的answer字段强转str
- LLM参数 stream 默认值修改为 True
- think消息 增加 Agent 名称字段
- Config.getServer().getWelcomeMessage()改为Config.getAgent().getWelcomeMessage()
- ObjectUtils.deepCopy方法性能优化
- RemoteEs的search方法重构，增强可读性
- 大模型参数增加并发,超时,重试,延迟等参数

### Fixed
- chat_with_agent函数入参send_msg_key参数为空时，修改为不发送消息

---

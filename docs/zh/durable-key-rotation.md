# Durable 外层密钥生命周期

CompileFlow Durable 内核不持有请求身份 HMAC 根密钥、分页 token keyring、payload 加密密钥身份或应用 codec 密钥。
请求去重和 opaque page token 由定义相应协议的 HTTP、RPC 或 MQ adapter 负责；按 adapter 的生命周期轮换密钥，
不会改变 Durable Store 的身份。

## Wait Token

Wait Token 是随机 Bearer Capability，Store 只持久化 SHA-256 Digest，不存在 Durable Wait-Token Keyring。Plaintext
Token 只能通过认证 Outbox Channel 传递，Integration 长期映射必须按凭据保护。禁止写入日志、metric label、普通 URL、第三方
metadata 或 Workbench view。发生泄露时尽可能阻断投递，检查其提交状态，并且不得为同一 Wait Boundary 签发另一个结果。

参见 [运维手册](durable-operations-runbook.md)。

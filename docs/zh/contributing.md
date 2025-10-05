# 如何为 CompileFlow 贡献

非常欢迎您对 CompileFlow 项目做出贡献！我们珍视来自社区的每一个想法和贡献。

本文档将为您提供一份贡献指南，希望能帮助您更顺畅地参与到项目建设中来。

## 行为准则

在参与社区交流和贡献时，请遵守我们的[行为准则](../CODE_OF_CONDUCT.md)。我们致力于建设一个开放、友好、互相尊重的社区环境。

## 报告问题 (Issues)

如果您在使用中发现了 Bug，或者有任何功能建议，欢迎通过 [GitHub Issues](https://github.com/alibaba/compileflow/issues)
提交给我们。

为了让我们能更快地定位和解决问题，请在提交 Issue 时尽量包含以下信息：

* **问题类型**: Bug, 功能建议 (Feature Request), 文档问题, 还是其他？
* **CompileFlow 版本**: 您正在使用的 `compileflow` 的版本号。
* **问题描述**: 清晰、详细地描述您遇到的问题或您的建议。
* **重现步骤**: 如果是 Bug, 请提供一个最小化的、可稳定复现问题的步骤。
* **代码示例**: 提供相关的流程定义文件和触发执行的 Java 代码片段。
* **期望行为 vs 实际行为**: 描述您期望的结果和实际发生的结果。
* **环境信息**: JDK 版本、操作系统、Spring Boot 版本等。

## 贡献代码 (Pull Requests)

我们非常欢迎您通过 Pull Request (PR) 的方式为项目贡献代码。

### 准备工作

1. **Fork 仓库**: 将 `alibaba/compileflow` 仓库 Fork 到您自己的 GitHub 账户下。
2. **Clone 仓库**: 将您 Fork 后的仓库 Clone 到本地：`git clone https://github.com/YOUR_USERNAME/compileflow.git`
3. **创建分支**: 从 `master` 分支创建一个新的特性分支：`git checkout -b feature/your-awesome-feature-name`。请使用有意义的分支名。
4. **编码**: 在新的特性分支上进行您的代码修改。

### 代码风格

* **Java**: 我们基本遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
  ，但在部分细节上可能略有不同。请尽量保持与项目中现有代码的风格一致。
* **Imports**: 请移除多余的 `import` 语句。
* **Javadoc**: 请为所有新增的 `public` 方法和类添加清晰的 Javadoc 注释。

### 提交 PR

1. **提交代码**: `git commit -m "feat: Add some awesome feature"`
   。我们推荐使用 [Angular 提交信息规范](https://github.com/angular/angular/blob/main/CONTRIBUTING.md#commit)。
    * `feat`: 新功能
    * `fix`: Bug 修复
    * `docs`: 文档变更
    * `style`: 代码风格修改（不影响代码逻辑）
    * `refactor`: 代码重构
    * `test`: 添加或修改测试
    * `chore`: 项目工程配置、构建流程等变更
2. **保持同步**: 在准备提交 PR 前，请先拉取上游 `master` 分支的最新代码并变基（rebase），确保您的分支是基于最新代码的：
   ```bash
   git remote add upstream https://github.com/alibaba/compileflow.git
   git fetch upstream
   git rebase upstream/master
   ```
3. **推送分支**: 将您的特性分支推送到您自己 Fork 的仓库：`git push origin feature/your-awesome-feature-name`
4. **创建 PR**: 在 GitHub 上，从您 Fork 的仓库的特性分支向 `alibaba/compileflow` 的 `master` 分支发起一个 Pull Request。
5. **描述 PR**: 在 PR 的描述中，请清晰地说明这个 PR 的目的、解决了什么问题、以及您的实现方案。如果关联了某个 Issue，请使用
   `Closes #123` 这样的关键词。

### PR 评审

提交 PR 后，项目的维护者会尽快对您的代码进行评审 (Review)。我们可能会提出一些修改建议，请关注 PR 下的评论并参与讨论。

一旦您的 PR 通过了评审并合并到了主干，您的贡献就正式成为 CompileFlow 的一部分了！感谢您的辛勤付出！


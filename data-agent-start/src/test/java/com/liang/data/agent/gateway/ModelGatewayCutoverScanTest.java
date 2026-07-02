package com.liang.data.agent.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型网关切换扫描测试。
 *
 * <p>用于约束生产模型调用点必须通过显式场景编码进入模型网关，避免业务代码绕过网关或继续使用旧的
 * LlmService 兼容重载。</p>
 */
class ModelGatewayCutoverScanTest {

    private static final List<String> PRODUCTION_SOURCE_ROOTS = List.of(
            "data-agent-ai-core/src/main/java",
            "data-agent-workflow/src/main/java",
            "data-agent-service/src/main/java",
            "data-agent-start/src/main/java");
    private static final Set<String> DIRECT_MODEL_INFRASTRUCTURE_FILES = Set.of(
            "OpenAiCompatibleGatewayProvider.java",
            "DynamicModelFactory.java",
            "AiModelRegistry.java",
            "BlockLlmService.java",
            "StreamLlmService.java");
    private static final Set<String> LLM_SERVICE_INFRASTRUCTURE_FILES = Set.of(
            "ResourceGatedLlmService.java");
    private static final Pattern DIRECT_MODEL_ACCESS_PATTERN = Pattern.compile(
            "\\.prompt\\s*\\(|ChatClient\\.builder\\s*\\(|\\bChatModel\\s+");
    private static final Pattern LLM_SERVICE_VARIABLE_PATTERN = Pattern.compile(
            "\\bLlmService\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern LLM_SERVICE_CALL_METHOD_PATTERN = Pattern.compile(
            "(callUser|callSystem|call)\\s*\\(");

    @Test
    void shouldPreventBusinessCodeFromDirectlyUsingChatClient() throws IOException {
        // 1. 扫描生产 Java 文件，排除模型基础设施适配类。
        List<Violation> violations = scanProductionJavaFiles().stream()
                .filter(path -> !DIRECT_MODEL_INFRASTRUCTURE_FILES.contains(path.getFileName().toString()))
                .flatMap(path -> findDirectModelAccessViolations(path).stream())
                .toList();

        // 2. 断言业务代码没有直接使用 ChatClient 或 ChatModel。
        assertThat(violations)
                .as("业务生产代码禁止直接调用 ChatClient/ChatModel，请改为通过模型网关和 LlmService 显式场景重载调用：%s",
                        violations)
                .isEmpty();
    }

    @Test
    void shouldRequireExplicitModelGatewaySceneForBusinessLlmServiceCalls() throws IOException {
        // 1. 扫描生产 Java 文件中的 LlmService 调用点。
        List<Violation> violations = scanProductionJavaFiles().stream()
                .filter(path -> !LLM_SERVICE_INFRASTRUCTURE_FILES.contains(path.getFileName().toString()))
                .flatMap(path -> findLlmServiceSceneViolations(path).stream())
                .toList();

        // 2. 断言业务调用必须把 ModelGatewayScenes 常量作为第一个参数。
        assertThat(violations)
                .as("业务生产代码调用 LlmService 时必须使用 ModelGatewayScenes.* 作为第一个参数：%s", violations)
                .isEmpty();
    }

    @Test
    void shouldDetectLlmServiceAliasAndWhitespaceCallWithoutScene() throws IOException {
        // 1. 构造包含别名变量和换行链式调用的 LlmService 调用样本。
        Path sourceFile = writeTemporaryJavaSource("""
                class Demo {
                    private final LlmService modelClient;
                    void run(String prompt) {
                        modelClient.callUser(prompt);
                        llmService
                                .callUser(prompt);
                    }
                }
                """);

        // 2. 断言两个未传入 ModelGatewayScenes 的调用都能被扫描出来。
        assertThat(findLlmServiceSceneViolations(sourceFile))
                .hasSize(2);
    }

    @Test
    void shouldAllowLlmServiceAliasCallWithScene() throws IOException {
        // 1. 构造使用 LlmService 别名变量且显式传入场景编码的调用样本。
        Path sourceFile = writeTemporaryJavaSource("""
                class Demo {
                    void run(LlmService modelClient, String prompt) {
                        modelClient.callUser(ModelGatewayScenes.REPORT_GENERATE, prompt);
                    }
                }
                """);

        // 2. 断言首个参数为 ModelGatewayScenes 常量时不记录违规。
        assertThat(findLlmServiceSceneViolations(sourceFile))
                .isEmpty();
    }

    /**
     * 扫描生产源码目录下的 Java 文件。
     *
     * @return 生产 Java 文件列表
     * @throws IOException 文件扫描失败时抛出
     */
    private static List<Path> scanProductionJavaFiles() throws IOException {
        // 1. 定位项目根目录，兼容从根模块或子模块执行 Maven 测试。
        Path projectRoot = locateProjectRoot();
        List<Path> javaFiles = new ArrayList<>();

        // 2. 逐个扫描生产源码根目录。
        for (String sourceRoot : PRODUCTION_SOURCE_ROOTS) {
            Path root = projectRoot.resolve(sourceRoot);
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(javaFiles::add);
            }
        }
        return javaFiles;
    }

    /**
     * 查找直接访问 ChatClient 或 ChatModel 的违规位置。
     *
     * @param path Java 文件路径
     * @return 违规位置列表
     */
    private static List<Violation> findDirectModelAccessViolations(Path path) {
        // 1. 读取文件内容并匹配直接模型访问模式。
        String content = readJavaFile(path);
        Matcher matcher = DIRECT_MODEL_ACCESS_PATTERN.matcher(content);
        List<Violation> violations = new ArrayList<>();

        // 2. 记录每个违规匹配所在行号。
        while (matcher.find()) {
            violations.add(new Violation(path, lineNumber(content, matcher.start()), matcher.group()));
        }
        return violations;
    }

    /**
     * 查找未显式传入模型网关场景编码的 LlmService 业务调用。
     *
     * @param path Java 文件路径
     * @return 违规位置列表
     */
    private static List<Violation> findLlmServiceSceneViolations(Path path) {
        // 1. 读取文件内容并识别 LlmService 类型变量名。
        String content = readJavaFile(path);
        Set<String> variableNames = findLlmServiceVariableNames(content);
        List<Violation> violations = new ArrayList<>();

        // 2. 针对每个变量名匹配调用，并允许变量名、点和方法名之间存在空白或换行。
        for (String variableName : variableNames) {
            Pattern callPattern = Pattern.compile(Pattern.quote(variableName)
                    + "\\s*\\.\\s*"
                    + LLM_SERVICE_CALL_METHOD_PATTERN.pattern());
            Matcher matcher = callPattern.matcher(content);

            // 3. 检查每个调用的第一个实参是否为 ModelGatewayScenes 常量。
            while (matcher.find()) {
                String firstArgument = content.substring(matcher.end()).stripLeading();
                if (!firstArgument.startsWith("ModelGatewayScenes.")) {
                    violations.add(new Violation(path, lineNumber(content, matcher.start()), matcher.group()));
                }
            }
        }
        return violations;
    }

    /**
     * 提取 Java 源码中声明为 LlmService 类型的变量名。
     *
     * @param content Java 源码内容
     * @return LlmService 变量名集合
     */
    private static Set<String> findLlmServiceVariableNames(String content) {
        // 1. 默认纳入常见变量名，兼容 Lombok、父类继承等正则无法看到声明的场景。
        Set<String> variableNames = new LinkedHashSet<>();
        variableNames.add("llmService");

        // 2. 识别字段、局部变量和构造参数中的 LlmService 类型变量名。
        Matcher matcher = LLM_SERVICE_VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            variableNames.add(matcher.group(1));
        }
        return variableNames;
    }

    /**
     * 定位项目根目录。
     *
     * @return 项目根目录
     */
    private static Path locateProjectRoot() {
        // 1. 从当前运行目录开始向上查找聚合工程根目录。
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("data-agent-ai-core"))) {
                return current;
            }
            current = current.getParent();
        }

        // 2. 找不到根目录时直接失败，避免扫描范围不明确。
        throw new IllegalStateException("未找到项目根目录，无法执行模型网关切换扫描测试");
    }

    /**
     * 读取 Java 文件内容。
     *
     * @param path Java 文件路径
     * @return 文件内容
     */
    private static String readJavaFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 Java 文件失败：" + path, e);
        }
    }

    /**
     * 写入用于扫描自测的临时 Java 源码。
     *
     * @param content Java 源码内容
     * @return 临时 Java 源码文件路径
     * @throws IOException 写入临时文件失败时抛出
     */
    private static Path writeTemporaryJavaSource(String content) throws IOException {
        // 1. 创建临时 Java 文件承载扫描样本。
        Path sourceFile = Files.createTempFile("model-gateway-cutover-scan-", ".java");

        // 2. 写入 UTF-8 源码内容，保证扫描逻辑读取一致。
        Files.writeString(sourceFile, content, StandardCharsets.UTF_8);
        return sourceFile;
    }

    /**
     * 计算字符偏移对应的行号。
     *
     * @param content 文件内容
     * @param offset 字符偏移
     * @return 一基行号
     */
    private static int lineNumber(String content, int offset) {
        // 1. 统计偏移位置之前的换行符数量。
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }

        // 2. 返回一基行号。
        return line;
    }

    /**
     * 扫描违规信息。
     */
    private record Violation(Path path, int line, String snippet) {

        @Override
        public String toString() {
            return path + ":" + line + " -> " + snippet;
        }
    }
}

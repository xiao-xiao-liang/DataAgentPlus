package com.liang.data.agent.service.storage;

import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.enums.FileStorageType;
import com.liang.data.agent.service.storage.impl.LocalFileStorageStrategy;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用文件存储服务单元测试。
 */
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadShouldUseConfiguredStorageStrategy() {
        DataAgentProperties properties = new DataAgentProperties();
        properties.getFileStorage().setType(FileStorageType.LOCAL);
        MemoryFileStorageStrategy strategy = new MemoryFileStorageStrategy();
        FileStorageService service = new FileStorageService(properties, List.of(strategy));

        StoredFile storedFile = service.upload(new ByteArrayInputStream("hello".getBytes()), 5, "knowledge/1/demo.txt", "text/plain");

        assertThat(storedFile.storageType()).isEqualTo("LOCAL");
        assertThat(storedFile.objectName()).isEqualTo("knowledge/1/demo.txt");
        assertThat(strategy.uploadedObjectName).isEqualTo("knowledge/1/demo.txt");
        assertThat(strategy.uploadedContentType).isEqualTo("text/plain");
    }

    @Test
    void defaultStorageShouldBeLocalAndStartWithoutMinioCredentials() {
        DataAgentProperties properties = new DataAgentProperties();

        assertThat(properties.getFileStorage().getType()).isEqualTo(FileStorageType.LOCAL);
    }

    @Test
    void localStorageShouldRejectPathTraversalObjectName() throws Exception {
        DataAgentProperties properties = new DataAgentProperties();
        properties.getFileStorage().setType(FileStorageType.LOCAL);
        properties.getFileStorage().getLocal().setRootPath(tempDir.toString());
        LocalFileStorageStrategy strategy = new LocalFileStorageStrategy(properties);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        strategy.upload(new ByteArrayInputStream("danger".getBytes()), 6, "../escape.txt", "text/plain"))
                .hasMessageContaining("对象名称非法");

        assertThat(Files.exists(tempDir.resolve("../escape.txt").normalize())).isFalse();
    }

    /**
     * 测试用内存文件存储策略。
     */
    private static class MemoryFileStorageStrategy implements FileStorageStrategy {

        private String uploadedObjectName;
        private String uploadedContentType;

        @Override
        public FileStorageType supportType() {
            return FileStorageType.LOCAL;
        }

        @Override
        public StoredFile upload(InputStream inputStream, long contentLength, String objectName, String contentType) {
            this.uploadedObjectName = objectName;
            this.uploadedContentType = contentType;
            return new StoredFile(supportType().name(), objectName, "memory://" + objectName, contentLength);
        }

        @Override
        public InputStream openStream(String objectName) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(String objectName) {
        }
    }
}

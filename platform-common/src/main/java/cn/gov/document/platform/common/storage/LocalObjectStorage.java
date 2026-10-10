package cn.gov.document.platform.common.storage;

import cn.gov.document.platform.common.error.BusinessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalObjectStorage implements ObjectStorage {
    private final Path root;

    public LocalObjectStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw BusinessException.unprocessable("文件写入失败");
        }
    }

    @Override
    public byte[] read(String key) {
        Path target = resolve(key);
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw BusinessException.unprocessable("文件读取失败");
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw BusinessException.unprocessable("非法存储路径");
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw BusinessException.unprocessable("非法存储路径");
        }
        return target;
    }
}

package cn.gov.document.platform.common.storage;

public interface ObjectStorage {
    void put(String key, byte[] content, String contentType);

    byte[] read(String key);
}

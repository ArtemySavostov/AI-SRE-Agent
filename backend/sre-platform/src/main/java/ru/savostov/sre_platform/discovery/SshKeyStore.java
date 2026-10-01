package ru.savostov.sre_platform.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class SshKeyStore {
    private final Path directory;
    public SshKeyStore(@Value("${sre.ssh.keys-directory:.local/ssh}") String directory) {
        this.directory = Path.of(directory);
    }
    public Path resolve(String credentialId) {
        if (credentialId == null || !credentialId.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Некорректный идентификатор SSH-ключа");
        }
        try {
            Path root = directory.toRealPath();
            Path key = root.resolve(credentialId).toRealPath();
            if (!key.startsWith(root) || !Files.isRegularFile(key) || !Files.isReadable(key)) {
                throw new IOException("Key unavailable");
            }
            return key;
        } catch (IOException exception) {
            throw new DiscoveryException("KEY_UNAVAILABLE", "SSH-ключ недоступен в каталоге ключей backend");
        }
    }
}

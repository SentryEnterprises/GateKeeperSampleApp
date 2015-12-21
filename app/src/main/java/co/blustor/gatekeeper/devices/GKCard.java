package co.blustor.gatekeeper.devices;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import co.blustor.gatekeeper.bftp.CardClient;
import co.blustor.gatekeeper.data.GKFile;

public interface GKCard {
    CardClient.Response retrieve(String cardPath) throws IOException;
    byte[] list(String cardPath) throws IOException;
    CardClient.Response delete(String cardPath) throws IOException;
    File downloadFile(GKFile cardFile, File localFile) throws IOException;
    CardClient.Response store(String cardPath, InputStream inputStream);
    boolean deleteFile(String cardPath) throws IOException;
    boolean removeDirectory(String cardPath) throws IOException;
    boolean makeDirectory(String cardPath) throws IOException;
    String getRootPath();
    void connect() throws IOException;
    void disconnect() throws IOException;
}

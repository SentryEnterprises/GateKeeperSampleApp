package co.blustor.gatekeeper.net;

import android.content.res.Resources;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import co.blustor.gatekeeper.Application;
import co.blustor.gatekeeper.R;
import co.blustor.gatekeeper.data.AbstractFile;
import co.blustor.gatekeeper.data.AbstractFile.Type;
import co.blustor.gatekeeper.data.IOConnection;
import co.blustor.gatekeeper.data.RemoteFilestoreClient;

public class FTPFilestoreClient implements RemoteFilestoreClient {
    private final FTPClient mFTP;

    public FTPFilestoreClient() {
        mFTP = new FTPClient();
    }

    @Override
    public List<AbstractFile> listFiles(String targetPath) throws IOException {
        org.apache.commons.net.ftp.FTPFile[] files = mFTP.listFiles(targetPath);
        ArrayList<AbstractFile> result = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            if (files[i] != null) {
                result.add(new FTPFile(files[i]));
            }
        }
        return result;
    }

    @Override
    public File downloadFile(String remotePath, File targetFile) throws IOException {
        mFTP.setFileType(FTP.BINARY_FILE_TYPE);
        mFTP.enterLocalPassiveMode();
        FileOutputStream outputStream = new FileOutputStream(targetFile);
        mFTP.retrieveFile(remotePath, outputStream);
        return targetFile;
    }

    @Override
    public String getRootPath() {
        return "/";
    }

    @Override
    public void open() throws IOException {
        if (!mFTP.isConnected()) {
            Resources resources = Application.getAppContext().getResources();
            String hostAddress = resources.getString(R.string.ftp_host_address);
            String username = resources.getString(R.string.ftp_username);
            String password = resources.getString(R.string.ftp_password);
            mFTP.connect(hostAddress);
            mFTP.login(username, password);
        }
    }

    @Override
    public void close() throws IOException {
        if (mFTP.isConnected()) {
            mFTP.disconnect();
        }
    }

    private class FTPFile extends AbstractFile {
        public FTPFile(org.apache.commons.net.ftp.FTPFile file) {
            super(file.getName(), getFileType(file));
        }
    }

    private Type getFileType(org.apache.commons.net.ftp.FTPFile file) {
        return file.isDirectory() ? Type.DIRECTORY : Type.FILE;
    }
}
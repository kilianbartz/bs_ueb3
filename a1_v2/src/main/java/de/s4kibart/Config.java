package de.s4kibart;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.io.Serializable;

public class Config implements Serializable {

    private String zfsFilesystem = "";
    private String fileRoot = "";

    public String getZfsFilesystem() {
        return zfsFilesystem;
    }

    public String getFileRoot() {
        return fileRoot;
    }

    public Config(String path){
        File file = new File(path);
        Toml toml = new Toml().read(file);
        this.zfsFilesystem = toml.getString("zfs_filesystem");
        this.fileRoot = toml.getString("file_root");
    }
}

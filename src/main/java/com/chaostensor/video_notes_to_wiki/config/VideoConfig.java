package com.chaostensor.video_notes_to_wiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.video")
public class VideoConfig {

    private String dropDirectory = "./video-drop";
    private String transcribedDirectory = "./transcribed";

    public String getDropDirectory() {
        return dropDirectory;
    }

    public void setDropDirectory(String dropDirectory) {
        this.dropDirectory = dropDirectory;
    }

    public String getTranscribedDirectory() {
        return transcribedDirectory;
    }

    public void setTranscribedDirectory(String transcribedDirectory) {
        this.transcribedDirectory = transcribedDirectory;
    }
}
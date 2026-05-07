package com.chaostensor.video_notes_to_wiki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VideoNotesToWikiApplication {

	public static void main(final String[] args) {
		SpringApplication.run(VideoNotesToWikiApplication.class, args);
	}

}

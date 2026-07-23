package com.mteam.rebuildengine;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RebuildEngineApplication {

    // 설정 파일에서 읽어온 비밀번호를 주입받습니다.
    @Value("${spring.datasource.url}")
    private String dbUrl;
    @Value("${spring.datasource.username}")
    private String dbUsername;


    public static void main(String[] args) {
        SpringApplication.run(RebuildEngineApplication.class, args);
    }

    // 애플리케이션 시작 직후 실행되어 값을 출력합니다.
    @PostConstruct
    public void checkConfig() {
        System.out.println("========================================");
//        System.out.println("현재 로드된 DB 비밀번호: [" + dbPassword + "]");
        System.out.println("현재 로드된 DB dbUrl: [" + dbUrl + "]");
        System.out.println("현재 로드된 DB dbUsername: [" + dbUsername + "]");
        System.out.println("========================================");
    }

}


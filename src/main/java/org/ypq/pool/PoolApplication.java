package org.ypq.pool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class PoolApplication {

    public static void main(String[] args) throws IOException {
//        SpringApplication.run(PoolApplication.class, args);
        TomcatClient.main(args);
    }

}

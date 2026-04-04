package com.example.demo.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticSearchClientConfig {

    @Value("${search.elasticsearch.host}")
    private String host;

    @Value("${search.elasticsearch.port}")
    private int port;

    @Value("${search.elasticsearch.scheme}")
    private String scheme;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        // ES 连接信息统一来自配置文件，避免切环境时继续改代码。
        return new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, scheme)));
    }
}

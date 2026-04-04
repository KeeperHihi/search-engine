package com.example.demo.utils;

import java.io.IOException;
import java.util.Properties;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

/**
 * 给独立的 ES 演示类复用统一的连接配置。
 */
public final class StandaloneElasticsearchClientFactory {

    private static final String ES_HOST_KEY = "search.elasticsearch.host";
    private static final String ES_PORT_KEY = "search.elasticsearch.port";
    private static final String ES_SCHEME_KEY = "search.elasticsearch.scheme";

    private StandaloneElasticsearchClientFactory() {
        // 工具类不需要实例化。
    }

    public static RestHighLevelClient createClient() throws IOException {
        Properties properties = ApplicationPropertiesUtil.loadApplicationProperties();
        String host = ApplicationPropertiesUtil.getRequiredProperty(properties, ES_HOST_KEY);
        int port = ApplicationPropertiesUtil.getRequiredIntProperty(properties, ES_PORT_KEY);
        String scheme = ApplicationPropertiesUtil.getRequiredProperty(properties, ES_SCHEME_KEY);

        return new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, scheme)));
    }
}

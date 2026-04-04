package com.example.demo.constants;

/**
 * Elasticsearch 索引名常量。
 * 统一维护索引契约，避免同一业务在不同类里写出不同索引名。
 */
public final class EsIndexNames {

    public static final String JD_DATA = "jddata";
    public static final String INSURANCE_QUESTION = "insurance_question";
    public static final String INSURANCE_ANSWER = "insurance_answer";

    private EsIndexNames() {
        // 工具类不需要实例化。
    }
}

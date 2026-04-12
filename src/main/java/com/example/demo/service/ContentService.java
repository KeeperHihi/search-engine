package com.example.demo.service;

import com.alibaba.fastjson.JSON;
import com.example.demo.constants.EsIndexNames;
import com.example.demo.pojo.Content;
import com.example.demo.utils.ContentDocumentIdUtil;
import com.example.demo.utils.GoodsDisplayUtil;
import com.example.demo.utils.HtmlParseUtil;
import com.example.demo.utils.JsonParseUtil;
import com.example.demo.utils.PriceTextUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ContentService {

    private final RestHighLevelClient client;
    private final HtmlParseUtil htmlParseUtil;
    private final JsonParseUtil jsonParseUtil;
    private final String jdFilePath;

    public ContentService(
            @Qualifier("restHighLevelClient") RestHighLevelClient client,
            HtmlParseUtil htmlParseUtil,
            JsonParseUtil jsonParseUtil,
            @org.springframework.beans.factory.annotation.Value("${search.data.jd-file-path}")
                    String jdFilePath) {
        this.client = client;
        this.htmlParseUtil = htmlParseUtil;
        this.jsonParseUtil = jsonParseUtil;
        this.jdFilePath = jdFilePath;
    }

    public boolean parseContent(String keyword) throws IOException {
        List<Content> goods = htmlParseUtil.parseJD(keyword);
        return writeGoodsToEs(goods);
    }

    // 手动把本地商品文件导入 ES，便于离线样例快速重建数据。
    public boolean writeJDContent() throws IOException {
        List<Content> goods = jsonParseUtil.parseJDJson(jdFilePath);
        return writeGoodsToEs(goods);
    }

    public List<Map<String, Object>> searchPage(
            String keyword, int pageNo, int pageSize, String sortBy, String priceOrder)
            throws IOException {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }

        SearchRequest searchRequest = new SearchRequest(EsIndexNames.JD_DATA);
        SearchSourceBuilder sourceBuilder =
                buildGoodsSearchSourceBuilder(normalizedKeyword, pageNo, pageSize);

        searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        List<Map<String, Object>> goodsList = extractUniqueGoods(searchResponse);
        for (Map<String, Object> goods : goodsList) {
            GoodsDisplayUtil.enrichGoods(goods);
        }

        GoodsDisplayUtil.sortGoods(goodsList, sortBy, priceOrder);
        return paginateGoods(goodsList, pageNo, pageSize);
    }

    private SearchSourceBuilder buildGoodsSearchSourceBuilder(
            String keyword, int pageNo, int pageSize) {
        int normalizedPageNo = normalizePageNo(pageNo);
        int normalizedPageSize = normalizePageSize(pageSize);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from(0);
        // 销量、人气等字段是运行时模拟出来的，所以先放大 ES 召回窗口，再在内存中排序分页。
        sourceBuilder.size(buildGoodsFetchSize(normalizedPageNo, normalizedPageSize));
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.query(QueryBuilders.matchQuery("title", keyword));
        return sourceBuilder;
    }

    private SearchSourceBuilder buildPagedSourceBuilder(int pageNo, int pageSize) {
        int normalizedPageNo = normalizePageNo(pageNo);
        int normalizedPageSize = normalizePageSize(pageSize);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from(buildFrom(normalizedPageNo, normalizedPageSize));
        sourceBuilder.size(normalizedPageSize);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        return sourceBuilder;
    }

    private int normalizePageNo(int pageNo) {
        return pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(int pageSize) {
        return pageSize < 1 ? 20 : pageSize;
    }

    private int buildFrom(int pageNo, int pageSize) {
        return (pageNo - 1) * pageSize;
    }

    private int buildGoodsFetchSize(int pageNo, int pageSize) {
        int expectedSize = pageNo * pageSize * 4;
        if (expectedSize < 40) {
            return 40;
        }
        return Math.min(expectedSize, 200);
    }

    private boolean writeGoodsToEs(List<Content> contents) throws IOException {
        BulkRequest request = buildGoodsBulkRequest(contents);
        if (request.numberOfActions() == 0) {
            return true;
        }

        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);
        return !bulk.hasFailures();
    }

    private BulkRequest buildGoodsBulkRequest(List<Content> contents) {
        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        if (contents == null || contents.isEmpty()) {
            return request;
        }

        Set<String> processedDocumentIds = new HashSet<String>();
        for (Content content : contents) {
            normalizeContentPrice(content);
            String documentId = ContentDocumentIdUtil.buildDocumentId(content);
            if (!processedDocumentIds.add(documentId)) {
                // 同一批次里出现重复商品时直接跳过，避免一次 bulk 内重复覆盖。
                continue;
            }

            request.add(buildIndexRequest(EsIndexNames.JD_DATA, documentId, content));
        }
        return request;
    }

    private IndexRequest buildIndexRequest(String indexName, String documentId, Object document) {
        IndexRequest indexRequest = new IndexRequest(indexName);
        if (documentId != null && !documentId.isEmpty()) {
            indexRequest.id(documentId);
        }
        return indexRequest.source(JSON.toJSONString(document), XContentType.JSON);
    }

    private List<Map<String, Object>> extractUniqueGoods(SearchResponse searchResponse) {
        List<Map<String, Object>> goodsList = new ArrayList<Map<String, Object>>();
        Set<String> documentIds = new HashSet<String>();

        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = searchHit.getSourceAsMap();
            normalizeGoodsSourcePrice(sourceMap);
            String documentId = ContentDocumentIdUtil.buildDocumentId(sourceMap);
            if (documentIds.add(documentId)) {
                goodsList.add(sourceMap);
            }
        }
        return goodsList;
    }

    private List<Map<String, Object>> paginateGoods(
            List<Map<String, Object>> goodsList, int pageNo, int pageSize) {
        int normalizedPageNo = normalizePageNo(pageNo);
        int normalizedPageSize = normalizePageSize(pageSize);
        int fromIndex = buildFrom(normalizedPageNo, normalizedPageSize);
        if (fromIndex >= goodsList.size()) {
            return new ArrayList<Map<String, Object>>();
        }

        int toIndex = Math.min(fromIndex + normalizedPageSize, goodsList.size());
        return new ArrayList<Map<String, Object>>(goodsList.subList(fromIndex, toIndex));
    }

    private void normalizeContentPrice(Content content) {
        if (content == null) {
            return;
        }

        content.setPrice(PriceTextUtil.normalizeDisplayPrice(content.getPrice()));
    }

    private void normalizeGoodsSourcePrice(Map<String, Object> sourceMap) {
        if (sourceMap == null) {
            return;
        }

        sourceMap.put("price", PriceTextUtil.normalizeDisplayPrice(readMapValue(sourceMap, "price")));
    }

    private String readMapValue(Map<String, Object> sourceMap, String fieldName) {
        Object value = sourceMap.get(fieldName);
        return value == null ? "" : String.valueOf(value);
    }
}

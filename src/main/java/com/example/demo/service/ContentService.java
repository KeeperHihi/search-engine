package com.example.demo.service;

import com.alibaba.fastjson.JSON;
import com.example.demo.constants.EsIndexNames;
import com.example.demo.pojo.Content;
import com.example.demo.pojo.CustomQARecord;
import com.example.demo.utils.ContentDocumentIdUtil;
import com.example.demo.utils.GoodsDisplayUtil;
import com.example.demo.utils.HtmlParseUtil;
import com.example.demo.utils.JsonParseUtil;
import com.example.demo.utils.PriceTextUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.rest.RestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    private final RestHighLevelClient client;
    private final HtmlParseUtil htmlParseUtil;
    private final JsonParseUtil jsonParseUtil;

    @Value("${search.data.question-file-path}")
    private String questionFilePath;

    @Value("${search.data.answer-file-path}")
    private String answerFilePath;

    @Value("${search.data.jd-file-path}")
    private String jdFilePath;

    @Value("${search.data.custom-qa-file-path}")
    private String customQaFilePath;

    public ContentService(
            @Qualifier("restHighLevelClient") RestHighLevelClient client,
            HtmlParseUtil htmlParseUtil,
            JsonParseUtil jsonParseUtil) {
        this.client = client;
        this.htmlParseUtil = htmlParseUtil;
        this.jsonParseUtil = jsonParseUtil;
    }

    @PostConstruct
    public void bootstrapCustomQaIndex() {
        try {
            ensureCustomQaDataReady();
        } catch (IOException e) {
            log.warn("custom_qa 初始化失败，首次搜索时会再次尝试加载数据。", e);
        }
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

    public boolean writeQAContent() throws IOException {
        ensureCustomQaIndexExists();
        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        List<CustomQARecord> records = jsonParseUtil.parseCustomQaJson(customQaFilePath);
        for (CustomQARecord record : records) {
            request.add(buildIndexRequest(EsIndexNames.CUSTOM_QA, record.getId(), record));
        }

        if (request.numberOfActions() == 0) {
            return true;
        }

        BulkResponse bulkResponse = client.bulk(request, RequestOptions.DEFAULT);
        return !bulkResponse.hasFailures();
    }

    public List<Map<String, Object>> searchQA(
            String keyword, int pageNo, int pageSize, boolean deduplicate)
            throws IOException {
        ensureCustomQaDataReady();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }

        try {
            return executeCustomQaSearch(normalizedKeyword, pageNo, pageSize, deduplicate);
        } catch (ElasticsearchStatusException e) {
            if (!isMissingCustomQaIndex(e)) {
                throw e;
            }
            log.warn("custom_qa 索引在查询时不存在，正在自动重建并重试一次。");
            ensureCustomQaIndexExists();
            writeQAContent();
            return executeCustomQaSearch(normalizedKeyword, pageNo, pageSize, deduplicate);
        }
    }

    public List<Map<String, Object>> searchAnswer(String qid) throws IOException {
        ensureCustomQaDataReady();
        String normalizedQid = qid == null ? "" : qid.trim();
        if (normalizedQid.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }

        try {
            List<Map<String, Object>> answerList = searchCustomQaAnswers(normalizedQid);
            if (answerList.isEmpty()) {
                return loadCustomQaAnswersFromJson(normalizedQid);
            }

            sortAnswersByQualityDesc(answerList);
            return answerList;
        } catch (ElasticsearchStatusException e) {
            if (!isMissingCustomQaIndex(e)) {
                throw e;
            }
            log.warn("custom_qa 详情索引缺失，正在自动重建并重试一次。");
            ensureCustomQaIndexExists();
            writeQAContent();
            List<Map<String, Object>> answerList = searchCustomQaAnswers(normalizedQid);
            if (answerList.isEmpty()) {
                return loadCustomQaAnswersFromJson(normalizedQid);
            }
            sortAnswersByQualityDesc(answerList);
            return answerList;
        }
    }

    private List<Map<String, Object>> searchCustomQaAnswers(String qid) throws IOException {
        return searchByExactField(EsIndexNames.CUSTOM_QA, "qid", qid);
    }

    private List<Map<String, Object>> loadCustomQaAnswersFromJson(String qid) throws IOException {
        List<CustomQARecord> recordList = jsonParseUtil.parseCustomQaJson(customQaFilePath);
        List<Map<String, Object>> answerList = new ArrayList<Map<String, Object>>();
        for (CustomQARecord record : recordList) {
            if (record == null || !qid.equals(record.getQid())) {
                continue;
            }
            Map<String, Object> answerMap = new HashMap<String, Object>();
            answerMap.put("id", record.getId());
            answerMap.put("qid", record.getQid());
            answerMap.put("topic", record.getTopic());
            answerMap.put("question", record.getQuestion());
            answerMap.put("answer", record.getAnswer());
            answerMap.put("answerQuality", record.getAnswerQuality());
            answerList.add(answerMap);
        }
        sortAnswersByQualityDesc(answerList);
        return answerList;
    }

    private List<Map<String, Object>> executeCustomQaSearch(
            String keyword, int pageNo, int pageSize, boolean deduplicate)
            throws IOException {
        SearchRequest searchRequest = new SearchRequest(EsIndexNames.CUSTOM_QA);
        SearchSourceBuilder sourceBuilder =
                buildCustomQaSearchSourceBuilder(keyword, pageNo, pageSize, deduplicate);

        searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        if (!deduplicate) {
            return extractSourceList(searchResponse);
        }

        List<Map<String, Object>> groupedResultList = extractGroupedCustomQaList(searchResponse);
        return paginateGroupedQa(groupedResultList, pageNo, pageSize);
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

    private void ensureCustomQaIndexExists() throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest(EsIndexNames.CUSTOM_QA);
        boolean exists = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
        if (exists) {
            return;
        }

        CreateIndexRequest createIndexRequest = new CreateIndexRequest(EsIndexNames.CUSTOM_QA);
        client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
    }

    private void ensureCustomQaDataReady() throws IOException {
        ensureCustomQaIndexExists();
        if (hasCustomQaDocuments()) {
            return;
        }
        writeQAContent();
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

    private SearchSourceBuilder buildCustomQaSearchSourceBuilder(
            String keyword, int pageNo, int pageSize, boolean deduplicate) {
        int normalizedPageNo = normalizePageNo(pageNo);
        int normalizedPageSize = normalizePageSize(pageSize);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        if (deduplicate) {
            sourceBuilder.from(0);
            sourceBuilder.size(buildCustomQaFetchSize(normalizedPageNo, normalizedPageSize));
        } else {
            sourceBuilder.from(buildFrom(normalizedPageNo, normalizedPageSize));
            sourceBuilder.size(normalizedPageSize);
        }
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.query(buildCustomQaRankQuery(keyword));
        return sourceBuilder;
    }

    private QueryBuilder buildCustomQaRankQuery(String keyword) {
        QueryBuilder baseQuery = buildCustomQaBaseQuery(keyword);
        String scoreScript =
                "double quality = doc['answerQuality'].size() == 0 ? 0 : doc['answerQuality'].value;"
                        + " return _score + quality * 0.2;";
        Script script =
                new Script(Script.DEFAULT_SCRIPT_TYPE, "painless", scoreScript, new HashMap<String, Object>());
        return QueryBuilders.scriptScoreQuery(baseQuery, script);
    }

    private QueryBuilder buildCustomQaBaseQuery(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return QueryBuilders.matchAllQuery();
        }

        String upperKeyword = normalizedKeyword.toUpperCase(Locale.ROOT);
        if (upperKeyword.contains(" AND ")) {
            return buildAndQuery(normalizedKeyword);
        }
        if (upperKeyword.contains(" OR ")) {
            return buildOrQuery(normalizedKeyword);
        }
        if (upperKeyword.contains(" NOT ")) {
            return buildNotQuery(normalizedKeyword);
        }

        return buildFieldBoostQuery(normalizedKeyword);
    }

    private QueryBuilder buildFieldBoostQuery(String keyword) {
        return QueryBuilders.multiMatchQuery(keyword, "question", "answer", "topic")
                .field("question", 3.0f)
                .field("answer", 2.0f)
                .field("topic", 1.2f)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS);
    }

    private QueryBuilder buildAndQuery(String keyword) {
        String[] parts = keyword.split("(?i)\\s+AND\\s+");
        if (parts.length <= 1) {
            return buildFieldBoostQuery(keyword);
        }

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        for (String part : parts) {
            String normalizedPart = part.trim();
            if (!normalizedPart.isEmpty()) {
                boolQueryBuilder.must(buildFieldBoostQuery(normalizedPart));
            }
        }
        return boolQueryBuilder;
    }

    private QueryBuilder buildOrQuery(String keyword) {
        String[] parts = keyword.split("(?i)\\s+OR\\s+");
        if (parts.length <= 1) {
            return buildFieldBoostQuery(keyword);
        }

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        for (String part : parts) {
            String normalizedPart = part.trim();
            if (!normalizedPart.isEmpty()) {
                boolQueryBuilder.should(buildFieldBoostQuery(normalizedPart));
            }
        }
        boolQueryBuilder.minimumShouldMatch(1);
        return boolQueryBuilder;
    }

    private QueryBuilder buildNotQuery(String keyword) {
        String[] parts = keyword.split("(?i)\\s+NOT\\s+");
        if (parts.length <= 1) {
            return buildFieldBoostQuery(keyword);
        }

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        String positivePart = parts[0].trim();
        if (!positivePart.isEmpty()) {
            boolQueryBuilder.must(buildFieldBoostQuery(positivePart));
        }

        StringBuilder negativePartBuilder = new StringBuilder();
        for (int index = 1; index < parts.length; index++) {
            if (negativePartBuilder.length() > 0) {
                negativePartBuilder.append(" NOT ");
            }
            negativePartBuilder.append(parts[index]);
        }
        String negativePart = negativePartBuilder.toString().trim();
        if (!negativePart.isEmpty()) {
            boolQueryBuilder.mustNot(buildFieldBoostQuery(negativePart));
        }
        return boolQueryBuilder;
    }

    private List<Map<String, Object>> searchByTerm(String indexName, String fieldName, String value)
            throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery(fieldName, value));
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        return extractSourceList(searchResponse);
    }

    private List<Map<String, Object>> searchByExactField(String indexName, String fieldName, String value)
            throws IOException {
        List<Map<String, Object>> resultList = searchByTerm(indexName, fieldName + ".keyword", value);
        if (!resultList.isEmpty()) {
            return resultList;
        }

        resultList = searchByTerm(indexName, fieldName, value);
        if (!resultList.isEmpty()) {
            return resultList;
        }

        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchQuery(fieldName, value));
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        return extractSourceList(searchResponse);
    }

    private void sortAnswersByQualityDesc(List<Map<String, Object>> answerList) {
        answerList.sort(
                (left, right) ->
                        Integer.compare(
                                readIntegerValue(right, "answerQuality"),
                                readIntegerValue(left, "answerQuality")));
    }

    private boolean hasCustomQaDocuments() throws IOException {
        SearchRequest searchRequest = new SearchRequest(EsIndexNames.CUSTOM_QA);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());
        sourceBuilder.size(1);
        searchRequest.source(sourceBuilder);

        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        return response.getHits().getHits().length > 0;
    }

    private boolean isMissingCustomQaIndex(ElasticsearchStatusException exception) {
        return exception != null && exception.status() == RestStatus.NOT_FOUND;
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

    private int buildCustomQaFetchSize(int pageNo, int pageSize) {
        int expectedSize = pageNo * pageSize * 8;
        if (expectedSize < 80) {
            return 80;
        }
        return Math.min(expectedSize, 400);
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

    private List<Map<String, Object>> extractSourceList(SearchResponse searchResponse) {
        List<Map<String, Object>> sourceList = new ArrayList<Map<String, Object>>();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            sourceList.add(searchHit.getSourceAsMap());
        }
        return sourceList;
    }

    private List<Map<String, Object>> extractGroupedCustomQaList(SearchResponse searchResponse) {
        Map<String, Map<String, Object>> groupedSourceMap =
                new LinkedHashMap<String, Map<String, Object>>();

        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = new HashMap<String, Object>(searchHit.getSourceAsMap());
            sourceMap.put("_searchScore", searchHit.getScore());
            String qid = readMapValue(sourceMap, "qid");
            if (qid.isEmpty()) {
                qid = searchHit.getId();
            }

            Map<String, Object> existingSourceMap = groupedSourceMap.get(qid);
            if (existingSourceMap == null || isBetterQaSearchHit(sourceMap, existingSourceMap)) {
                groupedSourceMap.put(qid, sourceMap);
            }
        }

        List<Map<String, Object>> groupedResultList =
                new ArrayList<Map<String, Object>>(groupedSourceMap.values());
        Collections.sort(
                groupedResultList,
                Comparator.comparingDouble(this::readSearchScore)
                        .reversed()
                        .thenComparing(
                                (Map<String, Object> item) -> readIntegerValue(item, "answerQuality"),
                                Comparator.reverseOrder()));

        for (Map<String, Object> item : groupedResultList) {
            item.remove("_searchScore");
        }
        return groupedResultList;
    }

    private boolean isBetterQaSearchHit(
            Map<String, Object> candidateSourceMap, Map<String, Object> existingSourceMap) {
        double candidateScore = readSearchScore(candidateSourceMap);
        double existingScore = readSearchScore(existingSourceMap);
        if (Double.compare(candidateScore, existingScore) != 0) {
            return candidateScore > existingScore;
        }
        return readIntegerValue(candidateSourceMap, "answerQuality")
                > readIntegerValue(existingSourceMap, "answerQuality");
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

    private List<Map<String, Object>> paginateGroupedQa(
            List<Map<String, Object>> groupedResultList, int pageNo, int pageSize) {
        int normalizedPageNo = normalizePageNo(pageNo);
        int normalizedPageSize = normalizePageSize(pageSize);
        int fromIndex = buildFrom(normalizedPageNo, normalizedPageSize);
        if (fromIndex >= groupedResultList.size()) {
            return new ArrayList<Map<String, Object>>();
        }

        int toIndex = Math.min(fromIndex + normalizedPageSize, groupedResultList.size());
        return new ArrayList<Map<String, Object>>(groupedResultList.subList(fromIndex, toIndex));
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

    private int readIntegerValue(Map<String, Object> sourceMap, String fieldName) {
        Object value = sourceMap.get(fieldName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private double readSearchScore(Map<String, Object> sourceMap) {
        Object value = sourceMap.get("_searchScore");
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0.0d;
        }
    }
}

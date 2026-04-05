package com.example.demo.service;

import com.alibaba.fastjson.JSON;
import com.example.demo.constants.EsIndexNames;
import com.example.demo.pojo.Answer;
import com.example.demo.pojo.Content;
import com.example.demo.pojo.Question;
import com.example.demo.utils.ContentDocumentIdUtil;
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
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ContentService {

    private final RestHighLevelClient client;
    private final HtmlParseUtil htmlParseUtil;
    private final JsonParseUtil jsonParseUtil;

    @Value("${search.data.question-file-path}")
    private String questionFilePath;

    @Value("${search.data.answer-file-path}")
    private String answerFilePath;

    @Value("${search.data.jd-file-path}")
    private String jdFilePath;

    public ContentService(
            @Qualifier("restHighLevelClient") RestHighLevelClient client,
            HtmlParseUtil htmlParseUtil,
            JsonParseUtil jsonParseUtil) {
        this.client = client;
        this.htmlParseUtil = htmlParseUtil;
        this.jsonParseUtil = jsonParseUtil;
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

    public List<Map<String, Object>> searchPage(String keyword, int pageNo, int pageSize)
            throws IOException {
        SearchRequest searchRequest = new SearchRequest(EsIndexNames.JD_DATA);
        SearchSourceBuilder sourceBuilder = buildPagedSourceBuilder(keyword, pageNo, pageSize, "title");

        searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        return extractUniqueGoods(searchResponse);
    }

    public List<Map<String, Object>> searchQA(String keyword, int pageNo, int pageSize)
            throws IOException {
        SearchRequest searchRequest = new SearchRequest(EsIndexNames.INSURANCE_QUESTION);
        SearchSourceBuilder sourceBuilder = buildPagedSourceBuilder(pageNo, pageSize);
        sourceBuilder.query(buildQuestionQuery(keyword));

        searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        return extractSourceList(searchResponse);
    }

    public List<Map<String, Object>> searchAnswer(String qid) throws IOException {
        List<Map<String, Object>> questionList =
                searchByTerm(EsIndexNames.INSURANCE_QUESTION, "qid", qid);
        if (questionList.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }

        Map<String, Object> question = questionList.get(0);
        String aid = extractAnswerId((String) question.get("qanswers"));
        if (aid == null || aid.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }

        List<Map<String, Object>> answerList =
                searchByTerm(EsIndexNames.INSURANCE_ANSWER, "aid", aid);
        if (answerList.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("qid", question.get("qid"));
        result.put("qdomain", question.get("qdomain"));
        result.put("qzh", question.get("qzh"));
        result.put("qen", question.get("qen"));
        result.put("aid", answerList.get(0).get("aid"));
        result.put("azh", answerList.get(0).get("azh"));
        result.put("aen", answerList.get(0).get("aen"));

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        results.add(result);
        return results;
    }

    public boolean writeQAContent() throws IOException {
        BulkRequest questionRequest = new BulkRequest();
        questionRequest.timeout("2m");

        List<Question> questionList = jsonParseUtil.parseJson(questionFilePath);
        for (Question question : questionList) {
            // 问答索引同样使用稳定主键，避免重复导入时产生脏数据。
            questionRequest.add(
                    buildIndexRequest(EsIndexNames.INSURANCE_QUESTION, question.getQid(), question));
        }

        BulkRequest answerRequest = new BulkRequest();
        answerRequest.timeout("2m");

        List<Answer> answerList = jsonParseUtil.parseAnJson(answerFilePath);
        for (Answer answer : answerList) {
            answerRequest.add(
                    buildIndexRequest(EsIndexNames.INSURANCE_ANSWER, answer.getAid(), answer));
        }

        boolean questionSuccess =
                questionRequest.numberOfActions() == 0
                        || !client.bulk(questionRequest, RequestOptions.DEFAULT).hasFailures();
        boolean answerSuccess =
                answerRequest.numberOfActions() == 0
                        || !client.bulk(answerRequest, RequestOptions.DEFAULT).hasFailures();
        return questionSuccess && answerSuccess;
    }

    private SearchSourceBuilder buildPagedSourceBuilder(
            String keyword, int pageNo, int pageSize, String fieldName) {
        SearchSourceBuilder sourceBuilder = buildPagedSourceBuilder(pageNo, pageSize);
        sourceBuilder.query(QueryBuilders.matchQuery(fieldName, keyword));
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

    private QueryBuilder buildQuestionQuery(String keyword) {
        String[] keywordParts = keyword.trim().split("\\s+");
        if (keywordParts.length <= 1) {
            return QueryBuilders.matchQuery("qzh", keyword);
        }

        MatchQueryBuilder firstKeywordQuery = QueryBuilders.matchQuery("qzh", keywordParts[0]);
        firstKeywordQuery.boost(2);

        StringBuilder remainingKeywords = new StringBuilder(keywordParts[1]);
        for (int index = 2; index < keywordParts.length; index++) {
            remainingKeywords.append(" ").append(keywordParts[index]);
        }
        MatchQueryBuilder remainingKeywordQuery =
                QueryBuilders.matchQuery("qzh", remainingKeywords.toString());

        String scoreScript =
                "int weight=10;\n"
                        + "def random= randomScore(params.uuidHash);\n"
                        + "return weight*random";
        Map<String, Object> scriptParams = new HashMap<String, Object>();
        scriptParams.put("uuidHash", (int) (Math.random() * 100));

        Script script =
                new Script(Script.DEFAULT_SCRIPT_TYPE, "painless", scoreScript, scriptParams);
        ScriptScoreQueryBuilder scriptScoreQueryBuilder =
                QueryBuilders.scriptScoreQuery(remainingKeywordQuery, script);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.should(firstKeywordQuery);
        boolQueryBuilder.should(scriptScoreQueryBuilder);
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

    private String extractAnswerId(String qanswers) {
        if (qanswers == null || qanswers.isEmpty()) {
            return null;
        }

        String[] parts = qanswers.split("\"");
        return parts.length > 1 ? parts[1] : null;
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

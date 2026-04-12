package com.example.demo.service;

import com.example.demo.constants.EsIndexNames;
import com.example.demo.pojo.courseqa.CourseQaAnswerDetail;
import com.example.demo.pojo.courseqa.CourseQaAnswerItem;
import com.example.demo.pojo.courseqa.CourseQaDataset;
import com.example.demo.pojo.courseqa.CourseQaImportResult;
import com.example.demo.pojo.courseqa.CourseQaQuestionItem;
import com.example.demo.pojo.courseqa.CourseQaSearchResponse;
import com.example.demo.pojo.courseqa.CourseQaSearchResultItem;
import com.example.demo.utils.CourseQaDatasetParser;
import com.example.demo.utils.CourseQaDocumentIdUtil;
import com.example.demo.utils.CourseQaRankingUtil;
import com.example.demo.utils.CourseQaTextUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 新版课程问答全文搜索服务。
 * 关键流程：
 * 1. 导入课程问答测试集并建立问题索引、答案索引
 * 2. 先按 top-k 或 top-p 策略召回相近问题
 * 3. 再召回这些问题对应的候选答案
 * 4. 最后在 Java 里执行答案重排
 */
@Service
public class CourseQaSearchService {

    public static final String RECALL_STRATEGY_TOP_K = "top_k";
    public static final String RECALL_STRATEGY_TOP_P = "top_p";

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;
    private static final double DEFAULT_TOP_P_THRESHOLD = 0.85D;
    private static final int MAX_CANDIDATE_ANSWERS = 10000;
    private static final int TOP_P_FETCH_SIZE = 120;
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("0.0000");

    private final RestHighLevelClient client;
    private final CourseQaDatasetParser datasetParser;
    private final String defaultDatasetFilePath;

    public CourseQaSearchService(
            @Qualifier("restHighLevelClient") RestHighLevelClient client,
            CourseQaDatasetParser datasetParser,
            @Value("${search.data.course-qa-file-path}") String defaultDatasetFilePath) {
        this.client = client;
        this.datasetParser = datasetParser;
        this.defaultDatasetFilePath = defaultDatasetFilePath;
    }

    public CourseQaImportResult importDatasetFromFile(String filePath) throws IOException {
        String resolvedFilePath = resolveFilePath(filePath);
        byte[] datasetBytes = Files.readAllBytes(Paths.get(resolvedFilePath));
        return importDatasetInternal(resolvedFilePath, datasetBytes);
    }

    public CourseQaSearchResponse searchAnswers(
            String keyword,
            int pageNo,
            int pageSize,
            String recallStrategy,
            int topK,
            double topPThreshold)
            throws IOException {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedRecallStrategy = normalizeRecallStrategy(recallStrategy);
        CourseQaSearchResponse response = new CourseQaSearchResponse();
        response.setKeyword(normalizedKeyword);
        response.setRecallStrategy(normalizedRecallStrategy);
        response.setTopK(normalizeTopK(topK));
        response.setTopPThreshold(normalizeTopPThreshold(topPThreshold));
        response.setPageNo(normalizePageNo(pageNo));
        response.setPageSize(normalizePageSize(pageSize));

        if (normalizedKeyword.isEmpty()) {
            response.setItems(new ArrayList<CourseQaSearchResultItem>());
            return response;
        }

        if (!indexExists(EsIndexNames.COURSE_QA_QUESTION)
                || !indexExists(EsIndexNames.COURSE_QA_ANSWER)) {
            response.setItems(new ArrayList<CourseQaSearchResultItem>());
            return response;
        }

        List<QuestionRecallHit> questionHits =
                recallQuestions(
                        normalizedKeyword,
                        response.getRecallStrategy(),
                        response.getTopK(),
                        response.getTopPThreshold());
        response.setRecalledQuestionCount(questionHits.size());
        if (questionHits.isEmpty()) {
            response.setItems(new ArrayList<CourseQaSearchResultItem>());
            return response;
        }

        List<Map<String, Object>> answerCandidates = recallAnswers(questionHits);
        response.setRecalledAnswerCount(answerCandidates.size());
        if (answerCandidates.isEmpty()) {
            response.setItems(new ArrayList<CourseQaSearchResultItem>());
            return response;
        }

        List<CourseQaSearchResultItem> rankedAnswers =
                rerankAnswers(normalizedKeyword, questionHits, answerCandidates);
        List<CourseQaSearchResultItem> pagedItems =
                paginateItems(rankedAnswers, response.getPageNo(), response.getPageSize());
        response.setReturnedAnswerCount(pagedItems.size());
        response.setItems(pagedItems);
        return response;
    }

    public CourseQaAnswerDetail getAnswerDetail(String answerDocumentId) throws IOException {
        if (answerDocumentId == null || answerDocumentId.trim().isEmpty()) {
            return null;
        }
        if (!indexExists(EsIndexNames.COURSE_QA_ANSWER)) {
            return null;
        }

        GetRequest getRequest = new GetRequest(EsIndexNames.COURSE_QA_ANSWER, answerDocumentId.trim());
        GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);
        if (!getResponse.isExists()) {
            return null;
        }

        Map<String, Object> sourceMap = getResponse.getSourceAsMap();
        CourseQaAnswerDetail answerDetail = new CourseQaAnswerDetail();
        answerDetail.setAnswerDocumentId(readString(sourceMap, "answerDocumentId"));
        answerDetail.setCategoryName(readString(sourceMap, "categoryName"));
        answerDetail.setQuestionId(readString(sourceMap, "questionId"));
        answerDetail.setQuestionText(readString(sourceMap, "questionText"));
        answerDetail.setAnswerText(readString(sourceMap, "answerText"));
        answerDetail.setAnswer_quality(readNullableInteger(sourceMap, "answer_quality"));
        return answerDetail;
    }

    private CourseQaImportResult importDatasetInternal(String sourceName, byte[] datasetBytes)
            throws IOException {
        CourseQaDataset dataset = datasetParser.parse(datasetBytes, sourceName);

        rebuildCourseQaIndices();
        ImportBuildResult importBuildResult = buildBulkRequests(dataset);

        if (importBuildResult.questionRequest.numberOfActions() > 0) {
            BulkResponse bulkResponse =
                    client.bulk(importBuildResult.questionRequest, RequestOptions.DEFAULT);
            if (bulkResponse.hasFailures()) {
                throw new IOException("课程问答问题索引写入失败：" + bulkResponse.buildFailureMessage());
            }
        }
        if (importBuildResult.answerRequest.numberOfActions() > 0) {
            BulkResponse bulkResponse =
                    client.bulk(importBuildResult.answerRequest, RequestOptions.DEFAULT);
            if (bulkResponse.hasFailures()) {
                throw new IOException("课程问答答案索引写入失败：" + bulkResponse.buildFailureMessage());
            }
        }

        return new CourseQaImportResult(
                dataset.getSourceName(),
                dataset.getCategoryCount(),
                importBuildResult.importedQuestionCount,
                importBuildResult.importedAnswerCount,
                importBuildResult.duplicateQuestionCount,
                importBuildResult.duplicateAnswerCount);
    }

    private List<QuestionRecallHit> recallQuestions(
            String keyword, String recallStrategy, int topK, double topPThreshold)
            throws IOException {
        SearchRequest searchRequest = new SearchRequest(EsIndexNames.COURSE_QA_QUESTION);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.from(0);
        sourceBuilder.size(resolveQuestionFetchSize(recallStrategy, topK));
        sourceBuilder.query(buildQuestionRecallQuery(keyword));
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        List<QuestionRecallHit> questionHits = new ArrayList<QuestionRecallHit>();
        float maxScore = searchResponse.getHits().getMaxScore();
        float normalizedMaxScore = maxScore <= 0F ? 1F : maxScore;

        int questionRank = 1;
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            double normalizedRecallScore =
                    searchHit.getScore() <= 0F ? 0D : searchHit.getScore() / normalizedMaxScore;
            Map<String, Object> sourceMap = searchHit.getSourceAsMap();
            QuestionRecallHit questionHit = new QuestionRecallHit();
            questionHit.questionDocumentId = searchHit.getId();
            questionHit.categoryName = readString(sourceMap, "categoryName");
            questionHit.questionId = readString(sourceMap, "questionId");
            questionHit.questionText = readString(sourceMap, "questionText");
            questionHit.questionTokens = readString(sourceMap, "questionTokens");
            questionHit.answerCount = readInteger(sourceMap, "answerCount");
            questionHit.questionRank = questionRank++;
            questionHit.normalizedRecallScore = normalizedRecallScore;
            if (RECALL_STRATEGY_TOP_P.equals(recallStrategy)) {
                if (normalizedRecallScore >= topPThreshold) {
                    questionHits.add(questionHit);
                }
                continue;
            }

            questionHits.add(questionHit);
            if (questionHits.size() >= topK) {
                break;
            }
        }
        return questionHits;
    }

    private List<Map<String, Object>> recallAnswers(List<QuestionRecallHit> questionHits)
            throws IOException {
        List<String> questionDocumentIds = new ArrayList<String>();
        int expectedAnswerCount = 0;
        for (QuestionRecallHit questionHit : questionHits) {
            questionDocumentIds.add(questionHit.questionDocumentId);
            expectedAnswerCount += questionHit.answerCount;
        }
        if (questionDocumentIds.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }

        SearchRequest searchRequest = new SearchRequest(EsIndexNames.COURSE_QA_ANSWER);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.from(0);
        sourceBuilder.size(Math.min(Math.max(expectedAnswerCount, 1), MAX_CANDIDATE_ANSWERS));
        sourceBuilder.query(QueryBuilders.termsQuery("questionDocumentId", questionDocumentIds));
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        List<Map<String, Object>> answerList = new ArrayList<Map<String, Object>>();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = new HashMap<String, Object>(searchHit.getSourceAsMap());
            sourceMap.put("documentId", searchHit.getId());
            answerList.add(sourceMap);
        }
        return answerList;
    }

    private List<CourseQaSearchResultItem> rerankAnswers(
            String keyword,
            List<QuestionRecallHit> questionHits,
            List<Map<String, Object>> answerCandidates) {
        Map<String, QuestionRecallHit> questionHitMap = new HashMap<String, QuestionRecallHit>();
        for (QuestionRecallHit questionHit : questionHits) {
            questionHitMap.put(questionHit.questionDocumentId, questionHit);
        }

        List<CourseQaSearchResultItem> rankedItems = new ArrayList<CourseQaSearchResultItem>();
        for (Map<String, Object> answerCandidate : answerCandidates) {
            String questionDocumentId = readString(answerCandidate, "questionDocumentId");
            QuestionRecallHit questionHit = questionHitMap.get(questionDocumentId);
            if (questionHit == null) {
                continue;
            }

            String answerText = readString(answerCandidate, "answerText");
            String answerTokens = readString(answerCandidate, "answerTokens");
            CourseQaRankingUtil.ScoreBreakdown scoreBreakdown =
                    CourseQaRankingUtil.scoreCandidate(
                            keyword,
                            questionHit.questionTokens,
                            answerText,
                            answerTokens,
                            questionHit.normalizedRecallScore);

            CourseQaSearchResultItem resultItem = new CourseQaSearchResultItem();
            resultItem.setAnswerDocumentId(readString(answerCandidate, "answerDocumentId"));
            resultItem.setCategoryName(readString(answerCandidate, "categoryName"));
            resultItem.setQuestionId(readString(answerCandidate, "questionId"));
            resultItem.setQuestionText(readString(answerCandidate, "questionText"));
            resultItem.setAnswerText(answerText);
            resultItem.setAnswer_quality(readNullableInteger(answerCandidate, "answer_quality"));
            resultItem.setQuestionRank(questionHit.questionRank);
            resultItem.setQuestionRecallScore(roundScore(scoreBreakdown.getQuestionRecallScore()));
            resultItem.setAnswerRerankScore(roundScore(scoreBreakdown.getAnswerRerankScore()));
            resultItem.setTotalScore(roundScore(scoreBreakdown.getTotalScore()));
            rankedItems.add(resultItem);
        }

        Collections.sort(
                rankedItems,
                Comparator.comparingDouble(CourseQaSearchResultItem::getTotalScore).reversed()
                        .thenComparingInt(CourseQaSearchResultItem::getQuestionRank)
                        .thenComparing(
                                item ->
                                        item.getAnswerDocumentId() == null
                                                ? ""
                                                : item.getAnswerDocumentId()));
        return rankedItems;
    }

    private List<CourseQaSearchResultItem> paginateItems(
            List<CourseQaSearchResultItem> items, int pageNo, int pageSize) {
        int fromIndex = (pageNo - 1) * pageSize;
        if (fromIndex >= items.size()) {
            return new ArrayList<CourseQaSearchResultItem>();
        }

        int toIndex = Math.min(fromIndex + pageSize, items.size());
        return new ArrayList<CourseQaSearchResultItem>(items.subList(fromIndex, toIndex));
    }

    private BoolQueryBuilder buildQuestionRecallQuery(String keyword) {
        String tokensText = CourseQaTextUtil.buildSearchTokensText(keyword);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.should(QueryBuilders.matchPhraseQuery("questionText", keyword).boost(6F));
        if (!tokensText.isEmpty()) {
            MatchQueryBuilder strictTokenQuery = QueryBuilders.matchQuery("questionTokens", tokensText);
            strictTokenQuery.operator(org.elasticsearch.index.query.Operator.AND);
            strictTokenQuery.boost(5F);

            MatchQueryBuilder looseTokenQuery = QueryBuilders.matchQuery("questionTokens", tokensText);
            looseTokenQuery.minimumShouldMatch("60%");
            looseTokenQuery.boost(2F);

            boolQueryBuilder.should(strictTokenQuery);
            boolQueryBuilder.should(looseTokenQuery);
        } else {
            boolQueryBuilder.should(
                    QueryBuilders.matchQuery("questionText", keyword)
                            .minimumShouldMatch("80%")
                            .boost(2F));
        }
        boolQueryBuilder.minimumShouldMatch(1);
        return boolQueryBuilder;
    }

    private int resolveQuestionFetchSize(String recallStrategy, int topK) {
        if (RECALL_STRATEGY_TOP_P.equals(recallStrategy)) {
            return TOP_P_FETCH_SIZE;
        }
        return Math.min(Math.max(topK * 4, 10), 80);
    }

    private ImportBuildResult buildBulkRequests(CourseQaDataset dataset) {
        BulkRequest questionRequest = new BulkRequest();
        questionRequest.timeout("2m");

        BulkRequest answerRequest = new BulkRequest();
        answerRequest.timeout("2m");

        LinkedHashMap<String, IndexedQuestion> questionMap =
                new LinkedHashMap<String, IndexedQuestion>();
        Set<String> answerDocumentIds = new HashSet<String>();

        int duplicateQuestionCount = 0;
        int duplicateAnswerCount = 0;
        for (CourseQaQuestionItem questionItem : dataset.getQuestions()) {
            String questionDocumentId =
                    CourseQaDocumentIdUtil.buildQuestionDocumentId(
                            questionItem.getCategoryName(),
                            questionItem.getQuestionId(),
                            questionItem.getQuestionText());
            IndexedQuestion indexedQuestion = questionMap.get(questionDocumentId);
            if (indexedQuestion == null) {
                indexedQuestion =
                        new IndexedQuestion(questionDocumentId, questionItem, dataset.getSourceName());
                questionMap.put(questionDocumentId, indexedQuestion);
            } else {
                duplicateQuestionCount++;
            }

            for (CourseQaAnswerItem answerItem : questionItem.getAnswers()) {
                String answerDocumentId =
                        CourseQaDocumentIdUtil.buildAnswerDocumentId(
                                questionDocumentId, answerItem.getAnswerText());
                if (!answerDocumentIds.add(answerDocumentId)) {
                    duplicateAnswerCount++;
                    continue;
                }

                Map<String, Object> answerDocument =
                        buildAnswerDocument(questionDocumentId, answerDocumentId, questionItem, answerItem);
                answerRequest.add(
                        new IndexRequest(EsIndexNames.COURSE_QA_ANSWER)
                                .id(answerDocumentId)
                                .source(answerDocument));
                indexedQuestion.answerCount++;
            }
        }

        int importedQuestionCount = 0;
        for (IndexedQuestion indexedQuestion : questionMap.values()) {
            Map<String, Object> questionDocument = buildQuestionDocument(indexedQuestion);
            questionRequest.add(
                    new IndexRequest(EsIndexNames.COURSE_QA_QUESTION)
                            .id(indexedQuestion.questionDocumentId)
                            .source(questionDocument));
            importedQuestionCount++;
        }

        return new ImportBuildResult(
                questionRequest,
                answerRequest,
                importedQuestionCount,
                answerDocumentIds.size(),
                duplicateQuestionCount,
                duplicateAnswerCount);
    }

    private Map<String, Object> buildQuestionDocument(IndexedQuestion indexedQuestion) {
        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("questionDocumentId", indexedQuestion.questionDocumentId);
        document.put("categoryName", indexedQuestion.questionItem.getCategoryName());
        document.put("questionId", normalizeQuestionId(indexedQuestion.questionItem.getQuestionId()));
        document.put("questionText", indexedQuestion.questionItem.getQuestionText());
        document.put(
                "questionTokens",
                CourseQaTextUtil.buildSearchTokensText(indexedQuestion.questionItem.getQuestionText()));
        document.put("answerCount", indexedQuestion.answerCount);
        document.put("sourceName", indexedQuestion.sourceName);
        return document;
    }

    private Map<String, Object> buildAnswerDocument(
            String questionDocumentId,
            String answerDocumentId,
            CourseQaQuestionItem questionItem,
            CourseQaAnswerItem answerItem) {
        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("answerDocumentId", answerDocumentId);
        document.put("questionDocumentId", questionDocumentId);
        document.put("categoryName", questionItem.getCategoryName());
        document.put("questionId", normalizeQuestionId(questionItem.getQuestionId()));
        document.put("questionText", questionItem.getQuestionText());
        document.put("questionTokens", CourseQaTextUtil.buildSearchTokensText(questionItem.getQuestionText()));
        document.put("answerText", answerItem.getAnswerText());
        document.put("answerTokens", CourseQaTextUtil.buildSearchTokensText(answerItem.getAnswerText()));
        document.put("answerLength", CourseQaTextUtil.effectiveLength(answerItem.getAnswerText()));
        document.put("answer_quality", answerItem.getAnswer_quality());
        return document;
    }

    private void rebuildCourseQaIndices() throws IOException {
        deleteIndexIfExists(EsIndexNames.COURSE_QA_ANSWER);
        deleteIndexIfExists(EsIndexNames.COURSE_QA_QUESTION);
        createQuestionIndex();
        createAnswerIndex();
    }

    private void deleteIndexIfExists(String indexName) throws IOException {
        if (!indexExists(indexName)) {
            return;
        }

        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
    }

    private void createQuestionIndex() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(EsIndexNames.COURSE_QA_QUESTION);
        request.source(
                "{\n"
                        + "  \"mappings\": {\n"
                        + "    \"properties\": {\n"
                        + "      \"questionDocumentId\": {\"type\": \"keyword\"},\n"
                        + "      \"categoryName\": {\"type\": \"keyword\"},\n"
                        + "      \"questionId\": {\"type\": \"keyword\"},\n"
                        + "      \"questionText\": {\"type\": \"text\"},\n"
                        + "      \"questionTokens\": {\"type\": \"text\"},\n"
                        + "      \"answerCount\": {\"type\": \"integer\"},\n"
                        + "      \"sourceName\": {\"type\": \"keyword\"}\n"
                        + "    }\n"
                        + "  }\n"
                        + "}",
                XContentType.JSON);
        client.indices().create(request, RequestOptions.DEFAULT);
    }

    private boolean indexExists(String indexName) throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
        return client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
    }

    private void createAnswerIndex() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(EsIndexNames.COURSE_QA_ANSWER);
        request.source(
                "{\n"
                        + "  \"mappings\": {\n"
                        + "    \"properties\": {\n"
                        + "      \"answerDocumentId\": {\"type\": \"keyword\"},\n"
                        + "      \"questionDocumentId\": {\"type\": \"keyword\"},\n"
                        + "      \"categoryName\": {\"type\": \"keyword\"},\n"
                        + "      \"questionId\": {\"type\": \"keyword\"},\n"
                        + "      \"questionText\": {\"type\": \"text\"},\n"
                        + "      \"questionTokens\": {\"type\": \"text\"},\n"
                        + "      \"answerText\": {\"type\": \"text\"},\n"
                        + "      \"answerTokens\": {\"type\": \"text\"},\n"
                        + "      \"answerLength\": {\"type\": \"integer\"},\n"
                        + "      \"answer_quality\": {\"type\": \"integer\"}\n"
                        + "    }\n"
                        + "  }\n"
                        + "}",
                XContentType.JSON);
        client.indices().create(request, RequestOptions.DEFAULT);
    }

    private String resolveFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return defaultDatasetFilePath;
        }
        return filePath.trim();
    }

    private String normalizeRecallStrategy(String recallStrategy) {
        if (recallStrategy == null) {
            return RECALL_STRATEGY_TOP_K;
        }

        String normalizedStrategy = recallStrategy.trim().toLowerCase();
        if ("top-p".equals(normalizedStrategy) || "topp".equals(normalizedStrategy)) {
            return RECALL_STRATEGY_TOP_P;
        }
        if ("top-k".equals(normalizedStrategy) || "topk".equals(normalizedStrategy)) {
            return RECALL_STRATEGY_TOP_K;
        }
        if (RECALL_STRATEGY_TOP_P.equals(normalizedStrategy)) {
            return RECALL_STRATEGY_TOP_P;
        }
        return RECALL_STRATEGY_TOP_K;
    }

    private int normalizeTopK(int topK) {
        if (topK < 1) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private double normalizeTopPThreshold(double topPThreshold) {
        if (topPThreshold <= 0D) {
            return DEFAULT_TOP_P_THRESHOLD;
        }
        return Math.min(topPThreshold, 1D);
    }

    private int normalizePageNo(int pageNo) {
        return pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(int pageSize) {
        return pageSize < 1 ? 20 : pageSize;
    }

    private String normalizeQuestionId(String questionId) {
        if (questionId == null || questionId.trim().isEmpty()) {
            return "";
        }
        return questionId.trim();
    }

    private String readString(Map<String, Object> sourceMap, String fieldName) {
        Object value = sourceMap.get(fieldName);
        return value == null ? "" : String.valueOf(value);
    }

    private int readInteger(Map<String, Object> sourceMap, String fieldName) {
        Object value = sourceMap.get(fieldName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Integer readNullableInteger(Map<String, Object> sourceMap, String fieldName) {
        Object value = sourceMap.get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private double roundScore(double score) {
        return Double.parseDouble(SCORE_FORMAT.format(score));
    }

    private static final class IndexedQuestion {
        private final String questionDocumentId;
        private final CourseQaQuestionItem questionItem;
        private final String sourceName;
        private int answerCount;

        private IndexedQuestion(
                String questionDocumentId, CourseQaQuestionItem questionItem, String sourceName) {
            this.questionDocumentId = questionDocumentId;
            this.questionItem = questionItem;
            this.sourceName = sourceName;
            this.answerCount = 0;
        }
    }

    private static final class ImportBuildResult {
        private final BulkRequest questionRequest;
        private final BulkRequest answerRequest;
        private final int importedQuestionCount;
        private final int importedAnswerCount;
        private final int duplicateQuestionCount;
        private final int duplicateAnswerCount;

        private ImportBuildResult(
                BulkRequest questionRequest,
                BulkRequest answerRequest,
                int importedQuestionCount,
                int importedAnswerCount,
                int duplicateQuestionCount,
                int duplicateAnswerCount) {
            this.questionRequest = questionRequest;
            this.answerRequest = answerRequest;
            this.importedQuestionCount = importedQuestionCount;
            this.importedAnswerCount = importedAnswerCount;
            this.duplicateQuestionCount = duplicateQuestionCount;
            this.duplicateAnswerCount = duplicateAnswerCount;
        }
    }

    private static final class QuestionRecallHit {
        private String questionDocumentId;
        private String categoryName;
        private String questionId;
        private String questionText;
        private String questionTokens;
        private int answerCount;
        private int questionRank;
        private double normalizedRecallScore;
    }
}

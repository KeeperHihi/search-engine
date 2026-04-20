package com.example.demo.service;

import com.example.demo.constants.EsIndexNames;
import com.example.demo.pojo.courseqa.CourseQaAnswerDetail;
import com.example.demo.pojo.courseqa.CourseQaAnswerItem;
import com.example.demo.pojo.courseqa.CourseQaAnswerMetricsUpdateItem;
import com.example.demo.pojo.courseqa.CourseQaDataset;
import com.example.demo.pojo.courseqa.CourseQaImportResult;
import com.example.demo.pojo.courseqa.CourseQaMetricsClearResponse;
import com.example.demo.pojo.courseqa.CourseQaMetricsUpdateRequest;
import com.example.demo.pojo.courseqa.CourseQaMetricsUpdateResponse;
import com.example.demo.pojo.courseqa.CourseQaQuestionItem;
import com.example.demo.pojo.courseqa.CourseQaSearchResponse;
import com.example.demo.pojo.courseqa.CourseQaSearchResultItem;
import com.example.demo.utils.CourseQaDatasetParser;
import com.example.demo.utils.CourseQaDocumentIdUtil;
import com.example.demo.utils.CourseQaLogicQueryUtil;
import com.example.demo.utils.CourseQaRankingUtil;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.AnalyzeRequest;
import org.elasticsearch.client.indices.AnalyzeResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_CANDIDATE_ANSWERS = 10000;
    private static final int TOP_P_FETCH_SIZE = 120;
    private static final int METRICS_SCROLL_BATCH_SIZE = 500;
    private static final String IK_INDEX_ANALYZER = "ik_max_word";
    private static final String IK_SEARCH_ANALYZER = "ik_smart";
    private static final String IK_TERM_ANALYZER = "ik_smart";
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("0.0000");
    private static final double QUESTION_PHRASE_BOOST = 6D;
    private static final double QUESTION_AND_BOOST = 4D;
    private static final double QUESTION_LOOSE_BOOST = 2D;

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
        CourseQaLogicQueryUtil.ParsedQuery parsedQuery =
                CourseQaLogicQueryUtil.parse(normalizedKeyword);
        String normalizedRecallStrategy = normalizeRecallStrategy(recallStrategy);
        CourseQaSearchResponse response = new CourseQaSearchResponse();
        response.setKeyword(normalizedKeyword);
        response.setRecallStrategy(normalizedRecallStrategy);
        response.setTopK(normalizeTopK(topK));
        response.setTopPThreshold(normalizeTopPThreshold(topPThreshold));
        response.setPageNo(normalizePageNo(pageNo));
        response.setPageSize(normalizePageSize(pageSize));

        if (!parsedQuery.hasRecallKeyword()) {
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
                        parsedQuery,
                        response.getRecallStrategy(),
                        response.getTopK(),
                        response.getTopPThreshold());
        response.setRecalledQuestionCount(questionHits.size());
        if (questionHits.isEmpty()) {
            response.setItems(new ArrayList<CourseQaSearchResultItem>());
            return response;
        }

        List<Map<String, Object>> answerCandidates = recallAnswers(questionHits);
        List<Map<String, Object>> filteredAnswerCandidates =
                filterAnswerCandidatesByLogic(answerCandidates, parsedQuery);
        response.setRecalledAnswerCount(filteredAnswerCandidates.size());
        if (filteredAnswerCandidates.isEmpty()) {
            response.setItems(new ArrayList<CourseQaSearchResultItem>());
            return response;
        }

        List<String> keywordTerms = analyzeTextWithIk(parsedQuery.getRecallKeyword());
        response.setKeywordTerms(keywordTerms);
        List<CourseQaSearchResultItem> rankedAnswers =
                rerankAnswers(parsedQuery.getRecallKeyword(), questionHits, filteredAnswerCandidates);
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
        String questionDocumentId = readString(sourceMap, "questionDocumentId");
        CourseQaAnswerDetail answerDetail = new CourseQaAnswerDetail();
        answerDetail.setAnswerDocumentId(readString(sourceMap, "answerDocumentId"));
        answerDetail.setCategoryName(readString(sourceMap, "categoryName"));
        answerDetail.setQuestionId(readString(sourceMap, "questionId"));
        answerDetail.setQuestionText(readString(sourceMap, "questionText"));
        answerDetail.setAnswerText(readString(sourceMap, "answerText"));
        answerDetail.setAnswer_quality(readNullableInteger(sourceMap, "answer_quality"));
        answerDetail.setLikeCount(readInteger(sourceMap, "likeCount"));
        answerDetail.setClickCount(readInteger(sourceMap, "clickCount"));
        answerDetail.setQuestionTerms(loadQuestionTerms(questionDocumentId));
        answerDetail.setAnswerTerms(readStringList(sourceMap, "answerTerms"));
        return answerDetail;
    }

    public CourseQaMetricsUpdateResponse updateAnswerMetrics(CourseQaMetricsUpdateRequest request)
            throws IOException {
        CourseQaMetricsUpdateResponse response = new CourseQaMetricsUpdateResponse();
        if (request == null) {
            return response;
        }

        response.setQuestionDocumentId(normalizeTextValue(request.getQuestionDocumentId()));
        if (!indexExists(EsIndexNames.COURSE_QA_ANSWER)) {
            return response;
        }

        List<CourseQaAnswerMetricsUpdateItem> normalizedItems =
                normalizeMetricsUpdateItems(request.getAnswers());
        if (normalizedItems.isEmpty()) {
            return response;
        }

        BulkRequest bulkRequest = new BulkRequest();
        // 保存后前端会立刻重新检索，所以这里必须等待 refresh 完成，
        // 否则第一次重查仍可能读到旧值，看起来像“保存后被清零”。
        bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
        for (CourseQaAnswerMetricsUpdateItem item : normalizedItems) {
            bulkRequest.add(buildMetricsUpdateRequest(
                    item.getAnswerDocumentId(), item.getLikeCount(), item.getClickCount()));
        }

        BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
            throw new IOException("课程问答互动指标保存失败：" + bulkResponse.buildFailureMessage());
        }
        response.setUpdatedAnswerCount(normalizedItems.size());
        return response;
    }

    public CourseQaMetricsClearResponse clearQaMetrics() throws IOException {
        if (!indexExists(EsIndexNames.COURSE_QA_ANSWER)) {
            return new CourseQaMetricsClearResponse(0);
        }

        Scroll scroll = new Scroll(TimeValue.timeValueMinutes(1L));
        SearchRequest searchRequest = new SearchRequest(EsIndexNames.COURSE_QA_ANSWER);
        searchRequest.scroll(scroll);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.size(METRICS_SCROLL_BATCH_SIZE);
        sourceBuilder.fetchSource(false);
        sourceBuilder.sort("_doc");
        sourceBuilder.query(QueryBuilders.matchAllQuery());
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();
        int clearedAnswerCount = 0;
        try {
            while (true) {
                SearchHit[] searchHits = searchResponse.getHits().getHits();
                if (searchHits == null || searchHits.length == 0) {
                    break;
                }

                BulkRequest bulkRequest = new BulkRequest();
                // clearQA 调用后也可能立刻触发查询，这里同样等待 refresh。
                bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
                for (SearchHit searchHit : searchHits) {
                    bulkRequest.add(buildMetricsUpdateRequest(searchHit.getId(), 0, 0));
                }

                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                if (bulkResponse.hasFailures()) {
                    throw new IOException("课程问答互动指标清零失败：" + bulkResponse.buildFailureMessage());
                }
                clearedAnswerCount += searchHits.length;

                SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                scrollRequest.scroll(scroll);
                searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
                scrollId = searchResponse.getScrollId();
            }
        } finally {
            clearScrollQuietly(scrollId);
        }
        return new CourseQaMetricsClearResponse(clearedAnswerCount);
    }

    private List<String> loadQuestionTerms(String questionDocumentId) throws IOException {
        if (questionDocumentId == null || questionDocumentId.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        if (!indexExists(EsIndexNames.COURSE_QA_QUESTION)) {
            return new ArrayList<String>();
        }

        GetRequest getRequest = new GetRequest(EsIndexNames.COURSE_QA_QUESTION, questionDocumentId);
        GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);
        if (!getResponse.isExists()) {
            return new ArrayList<String>();
        }
        return readStringList(getResponse.getSourceAsMap(), "questionTerms");
    }

    private CourseQaImportResult importDatasetInternal(String sourceName, byte[] datasetBytes)
            throws IOException {
        CourseQaDataset dataset = datasetParser.parse(datasetBytes, sourceName);

        rebuildCourseQaIndices();
        ImportBuildResult importBuildResult = buildBulkRequests(dataset);

        if (importBuildResult.questionRequest.numberOfActions() > 0) {
            importBuildResult.questionRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
            BulkResponse bulkResponse =
                    client.bulk(importBuildResult.questionRequest, RequestOptions.DEFAULT);
            if (bulkResponse.hasFailures()) {
                throw new IOException("课程问答问题索引写入失败：" + bulkResponse.buildFailureMessage());
            }
        }
        if (importBuildResult.answerRequest.numberOfActions() > 0) {
            importBuildResult.answerRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
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
            CourseQaLogicQueryUtil.ParsedQuery parsedQuery,
            String recallStrategy,
            int topK,
            double topPThreshold)
            throws IOException {
        SearchRequest searchRequest = new SearchRequest(EsIndexNames.COURSE_QA_QUESTION);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.from(0);
        sourceBuilder.size(resolveQuestionFetchSize(recallStrategy, topK));
        sourceBuilder.query(buildQuestionRecallQuery(parsedQuery.getRecallKeyword()));
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        List<QuestionRecallHit> questionHits = new ArrayList<QuestionRecallHit>();
        int originalRank = 1;
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = searchHit.getSourceAsMap();
            QuestionRecallHit questionHit = new QuestionRecallHit();
            questionHit.questionDocumentId = searchHit.getId();
            questionHit.categoryName = readString(sourceMap, "categoryName");
            questionHit.questionId = readString(sourceMap, "questionId");
            questionHit.questionText = readString(sourceMap, "questionText");
            questionHit.questionTerms = readStringList(sourceMap, "questionTerms");
            questionHit.answerCount = readInteger(sourceMap, "answerCount");
            questionHit.originalRank = originalRank++;
            questionHits.add(questionHit);
        }
        if (questionHits.isEmpty()) {
            return questionHits;
        }

        enrichQuestionRecallScores(parsedQuery.getRecallKeyword(), questionHits);
        Collections.sort(
                questionHits,
                Comparator.comparingDouble((QuestionRecallHit hit) -> hit.weightedRecallScore)
                        .reversed()
                        .thenComparingInt(hit -> hit.originalRank));

        List<QuestionRecallHit> selectedHits = new ArrayList<QuestionRecallHit>();
        for (QuestionRecallHit questionHit : questionHits) {
            if (!CourseQaLogicQueryUtil.allowsQuestion(questionHit.questionText, parsedQuery)) {
                continue;
            }
            if (RECALL_STRATEGY_TOP_P.equals(recallStrategy)) {
                if (questionHit.normalizedRecallScore >= topPThreshold) {
                    selectedHits.add(questionHit);
                }
                continue;
            }

            selectedHits.add(questionHit);
            if (selectedHits.size() >= topK) {
                break;
            }
        }

        int questionRank = 1;
        for (QuestionRecallHit selectedHit : selectedHits) {
            selectedHit.questionRank = questionRank++;
        }
        return selectedHits;
    }

    private List<Map<String, Object>> filterAnswerCandidatesByLogic(
            List<Map<String, Object>> answerCandidates,
            CourseQaLogicQueryUtil.ParsedQuery parsedQuery) {
        List<Map<String, Object>> filteredCandidates = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> answerCandidate : answerCandidates) {
            if (!CourseQaLogicQueryUtil.matchesAnswerCandidate(
                    readString(answerCandidate, "questionText"),
                    readString(answerCandidate, "answerText"),
                    parsedQuery)) {
                continue;
            }
            filteredCandidates.add(answerCandidate);
        }
        return filteredCandidates;
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

    private AnswerBm25Stats fetchAnswerBm25Stats(
            String keyword, List<Map<String, Object>> answerCandidates) throws IOException {
        Map<String, Double> answerBm25ScoreMap = new HashMap<String, Double>();
        if (keyword == null || keyword.trim().isEmpty() || answerCandidates == null
                || answerCandidates.isEmpty()) {
            return new AnswerBm25Stats(answerBm25ScoreMap, 0D);
        }

        List<String> answerDocumentIds = new ArrayList<String>();
        for (Map<String, Object> answerCandidate : answerCandidates) {
            String answerDocumentId = readString(answerCandidate, "answerDocumentId");
            if (!answerDocumentId.isEmpty()) {
                answerDocumentIds.add(answerDocumentId);
            }
        }
        if (answerDocumentIds.isEmpty()) {
            return new AnswerBm25Stats(answerBm25ScoreMap, 0D);
        }

        SearchRequest searchRequest = new SearchRequest(EsIndexNames.COURSE_QA_ANSWER);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.from(0);
        sourceBuilder.size(answerDocumentIds.size());
        sourceBuilder.fetchSource(false);

        MatchQueryBuilder bm25Query = QueryBuilders.matchQuery("answerText", keyword);
        bm25Query.analyzer(IK_SEARCH_ANALYZER);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.filter(
                QueryBuilders.idsQuery().addIds(answerDocumentIds.toArray(new String[0])));
        boolQueryBuilder.must(bm25Query);
        sourceBuilder.query(boolQueryBuilder);
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        double maxBm25Score = 0D;
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            double currentBm25Score = searchHit.getScore();
            answerBm25ScoreMap.put(searchHit.getId(), currentBm25Score);
            maxBm25Score = Math.max(maxBm25Score, currentBm25Score);
        }

        return new AnswerBm25Stats(answerBm25ScoreMap, maxBm25Score);
    }

    private List<CourseQaSearchResultItem> rerankAnswers(
            String keyword,
            List<QuestionRecallHit> questionHits,
            List<Map<String, Object>> answerCandidates)
            throws IOException {
        Map<String, QuestionRecallHit> questionHitMap = new HashMap<String, QuestionRecallHit>();
        for (QuestionRecallHit questionHit : questionHits) {
            questionHitMap.put(questionHit.questionDocumentId, questionHit);
        }

        AnswerBm25Stats answerBm25Stats = fetchAnswerBm25Stats(keyword, answerCandidates);
        Map<String, QuestionAnswerMetricStats> questionMetricStatsMap =
                buildQuestionMetricStats(answerCandidates);
        List<CourseQaSearchResultItem> rankedItems = new ArrayList<CourseQaSearchResultItem>();
        for (Map<String, Object> answerCandidate : answerCandidates) {
            String questionDocumentId = readString(answerCandidate, "questionDocumentId");
            QuestionRecallHit questionHit = questionHitMap.get(questionDocumentId);
            if (questionHit == null) {
                continue;
            }

            QuestionAnswerMetricStats questionMetricStats = questionMetricStatsMap.get(questionDocumentId);
            if (questionMetricStats == null) {
                questionMetricStats = new QuestionAnswerMetricStats();
            }

            String answerDocumentId = readString(answerCandidate, "answerDocumentId");
            double answerBm25RawScore =
                    getScoreOrZero(answerBm25Stats.scoreMap, answerDocumentId);
            int likeCount = normalizeNonNegativeMetricCount(readInteger(answerCandidate, "likeCount"));
            int clickCount = normalizeNonNegativeMetricCount(readInteger(answerCandidate, "clickCount"));
            CourseQaRankingUtil.ScoreBreakdown scoreBreakdown =
                    CourseQaRankingUtil.scoreCandidate(
                            questionHit.weightedRecallScore,
                            questionHit.maxWeightedRecallScore,
                            answerBm25RawScore,
                            answerBm25Stats.normalizationBase,
                            likeCount,
                            questionMetricStats.maxLikeCount,
                            clickCount,
                            questionMetricStats.maxClickCount);

            CourseQaSearchResultItem resultItem = new CourseQaSearchResultItem();
            resultItem.setAnswerDocumentId(answerDocumentId);
            resultItem.setQuestionDocumentId(questionDocumentId);
            resultItem.setCategoryName(readString(answerCandidate, "categoryName"));
            resultItem.setQuestionId(readString(answerCandidate, "questionId"));
            resultItem.setQuestionText(readString(answerCandidate, "questionText"));
            resultItem.setAnswerText(readString(answerCandidate, "answerText"));
            resultItem.setAnswer_quality(readNullableInteger(answerCandidate, "answer_quality"));
            resultItem.setQuestionRank(questionHit.questionRank);
            resultItem.setQuestionRecallRawScore(
                    roundScore(scoreBreakdown.getQuestionRecallRawScore()));
            resultItem.setQuestionRecallNormalizationBase(
                    roundScore(scoreBreakdown.getQuestionRecallNormalizationBase()));
            resultItem.setQuestionRecallScore(roundScore(scoreBreakdown.getQuestionRecallScore()));
            resultItem.setAnswerBm25RawScore(roundScore(scoreBreakdown.getAnswerBm25RawScore()));
            resultItem.setAnswerBm25NormalizationBase(
                    roundScore(scoreBreakdown.getAnswerBm25NormalizationBase()));
            resultItem.setAnswerBm25Score(roundScore(scoreBreakdown.getAnswerBm25Score()));
            resultItem.setLikeCount(scoreBreakdown.getLikeCount());
            resultItem.setLikeNormalizationBase(scoreBreakdown.getLikeNormalizationBase());
            resultItem.setLikeScore(roundScore(scoreBreakdown.getLikeScore()));
            resultItem.setClickCount(scoreBreakdown.getClickCount());
            resultItem.setClickNormalizationBase(scoreBreakdown.getClickNormalizationBase());
            resultItem.setClickScore(roundScore(scoreBreakdown.getClickScore()));
            resultItem.setQuestionRecallContribution(
                    roundScore(
                            scoreBreakdown.getQuestionRecallScore()
                                    * CourseQaRankingUtil.TOTAL_QUESTION_RECALL_WEIGHT));
            resultItem.setAnswerBm25Contribution(
                    roundScore(
                            scoreBreakdown.getAnswerBm25Score()
                                    * CourseQaRankingUtil.TOTAL_ANSWER_BM25_WEIGHT));
            resultItem.setLikeContribution(
                    roundScore(
                            scoreBreakdown.getLikeScore()
                                    * CourseQaRankingUtil.TOTAL_LIKE_WEIGHT));
            resultItem.setClickContribution(
                    roundScore(
                            scoreBreakdown.getClickScore()
                                    * CourseQaRankingUtil.TOTAL_CLICK_WEIGHT));
            resultItem.setTotalQuestionRecallWeight(
                    CourseQaRankingUtil.TOTAL_QUESTION_RECALL_WEIGHT);
            resultItem.setTotalAnswerBm25Weight(
                    CourseQaRankingUtil.TOTAL_ANSWER_BM25_WEIGHT);
            resultItem.setTotalLikeWeight(CourseQaRankingUtil.TOTAL_LIKE_WEIGHT);
            resultItem.setTotalClickWeight(CourseQaRankingUtil.TOTAL_CLICK_WEIGHT);
            resultItem.setTotalScore(roundScore(scoreBreakdown.getTotalScore()));
            rankedItems.add(resultItem);
        }

        Collections.sort(
            rankedItems,
            Comparator.comparingDouble(CourseQaSearchResultItem::getTotalScore).reversed()
                .thenComparingInt(CourseQaSearchResultItem::getQuestionRank)
                .thenComparing(
                    item -> item.getAnswerDocumentId() == null ? "" : item.getAnswerDocumentId()
                )
            );
        return rankedItems;
    }

    private void enrichQuestionRecallScores(String keyword, List<QuestionRecallHit> questionHits)
            throws IOException {
        Map<String, Double> phraseScoreMap =
                fetchQuestionComponentScores(
                        questionHits, buildPhraseQuestionRecallQuery(keyword, 1F));
        Map<String, Double> andScoreMap =
                fetchQuestionComponentScores(questionHits, buildAndQuestionRecallQuery(keyword, 1F));
        Map<String, Double> looseScoreMap =
                fetchQuestionComponentScores(
                        questionHits, buildLooseQuestionRecallQuery(keyword, 1F));

        double maxWeightedRecallScore = 0D;
        for (QuestionRecallHit questionHit : questionHits) {
            questionHit.phraseScore = getScoreOrZero(phraseScoreMap, questionHit.questionDocumentId);
            questionHit.andScore = getScoreOrZero(andScoreMap, questionHit.questionDocumentId);
            questionHit.looseScore = getScoreOrZero(looseScoreMap, questionHit.questionDocumentId);
            questionHit.weightedRecallScore =
                    questionHit.phraseScore * QUESTION_PHRASE_BOOST
                            + questionHit.andScore * QUESTION_AND_BOOST
                            + questionHit.looseScore * QUESTION_LOOSE_BOOST;
            maxWeightedRecallScore = Math.max(maxWeightedRecallScore, questionHit.weightedRecallScore);
        }

        for (QuestionRecallHit questionHit : questionHits) {
            questionHit.maxWeightedRecallScore = maxWeightedRecallScore;
            questionHit.normalizedRecallScore =
                    questionHit.weightedRecallScore <= 0D || maxWeightedRecallScore <= 0D
                            ? 0D
                            : questionHit.weightedRecallScore / maxWeightedRecallScore;
        }
    }

    private Map<String, Double> fetchQuestionComponentScores(
            List<QuestionRecallHit> questionHits, QueryBuilder componentQuery) throws IOException {
        Map<String, Double> componentScoreMap = new HashMap<String, Double>();
        if (questionHits == null || questionHits.isEmpty()) {
            return componentScoreMap;
        }

        List<String> questionDocumentIds = new ArrayList<String>();
        for (QuestionRecallHit questionHit : questionHits) {
            questionDocumentIds.add(questionHit.questionDocumentId);
        }

        SearchRequest searchRequest = new SearchRequest(EsIndexNames.COURSE_QA_QUESTION);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        sourceBuilder.from(0);
        sourceBuilder.size(questionDocumentIds.size());
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.filter(
                QueryBuilders.idsQuery().addIds(questionDocumentIds.toArray(new String[0])));
        boolQueryBuilder.must(componentQuery);
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.fetchSource(false);
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            componentScoreMap.put(searchHit.getId(), (double) searchHit.getScore());
        }
        return componentScoreMap;
    }

    private double getScoreOrZero(Map<String, Double> scoreMap, String documentId) {
        Double score = scoreMap.get(documentId);
        return score == null ? 0D : score;
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
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.should(
                buildPhraseQuestionRecallQuery(keyword, (float) QUESTION_PHRASE_BOOST));
        // keyword 必须作为 Question 的连续子串

        boolQueryBuilder.should(
                buildAndQuestionRecallQuery(keyword, (float) QUESTION_AND_BOOST));
        // keyword 中每个分词都必须出现在 Question 中

        boolQueryBuilder.should(
                buildLooseQuestionRecallQuery(keyword, (float) QUESTION_LOOSE_BOOST));
        // keyword 中 60% 的分词出现在 Question 中

        boolQueryBuilder.minimumShouldMatch(1);
        return boolQueryBuilder;
    }

    private QueryBuilder buildPhraseQuestionRecallQuery(String keyword, float boost) {
        return QueryBuilders.matchPhraseQuery("questionText", keyword).boost(boost);
    }

    private QueryBuilder buildAndQuestionRecallQuery(String keyword, float boost) {
        MatchQueryBuilder strictQuestionQuery = QueryBuilders.matchQuery("questionText", keyword);
        strictQuestionQuery.operator(org.elasticsearch.index.query.Operator.AND);
        strictQuestionQuery.boost(boost);
        return strictQuestionQuery;
    }

    private QueryBuilder buildLooseQuestionRecallQuery(String keyword, float boost) {
        MatchQueryBuilder looseQuestionQuery = QueryBuilders.matchQuery("questionText", keyword);
        looseQuestionQuery.minimumShouldMatch("60%");
        looseQuestionQuery.boost(boost);
        return looseQuestionQuery;
    }

    private int resolveQuestionFetchSize(String recallStrategy, int topK) {
        if (RECALL_STRATEGY_TOP_P.equals(recallStrategy)) {
            return TOP_P_FETCH_SIZE;
        }
        return Math.min(Math.max(topK * 4, 10), 80);
    }

    private ImportBuildResult buildBulkRequests(CourseQaDataset dataset) throws IOException {
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
                        new IndexedQuestion(
                                questionDocumentId,
                                questionItem,
                                dataset.getSourceName(),
                                analyzeTextWithIk(questionItem.getQuestionText()));
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
                        buildAnswerDocument(
                                questionDocumentId,
                                answerDocumentId,
                                questionItem,
                                answerItem);
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
        document.put("questionTerms", indexedQuestion.questionTerms);
        document.put("answerCount", indexedQuestion.answerCount);
        document.put("sourceName", indexedQuestion.sourceName);
        return document;
    }

    private Map<String, Object> buildAnswerDocument(
            String questionDocumentId,
            String answerDocumentId,
            CourseQaQuestionItem questionItem,
            CourseQaAnswerItem answerItem)
            throws IOException {
        Map<String, Object> document = new LinkedHashMap<String, Object>();
        document.put("answerDocumentId", answerDocumentId);
        document.put("questionDocumentId", questionDocumentId);
        document.put("categoryName", questionItem.getCategoryName());
        document.put("questionId", normalizeQuestionId(questionItem.getQuestionId()));
        document.put("questionText", questionItem.getQuestionText());
        document.put("answerText", answerItem.getAnswerText());
        document.put("answerTerms", analyzeTextWithIk(answerItem.getAnswerText()));
        document.put("answer_quality", answerItem.getAnswer_quality());
        document.put("likeCount", 0);
        document.put("clickCount", 0);
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
                        + "      \"questionText\": {\n"
                        + "        \"type\": \"text\",\n"
                        + "        \"analyzer\": \"" + IK_INDEX_ANALYZER + "\",\n"
                        + "        \"search_analyzer\": \"" + IK_SEARCH_ANALYZER + "\"\n"
                        + "      },\n"
                        + "      \"questionTerms\": {\"type\": \"keyword\"},\n"
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
                        + "      \"questionText\": {\n"
                        + "        \"type\": \"text\",\n"
                        + "        \"analyzer\": \"" + IK_INDEX_ANALYZER + "\",\n"
                        + "        \"search_analyzer\": \"" + IK_SEARCH_ANALYZER + "\"\n"
                        + "      },\n"
                        + "      \"answerText\": {\n"
                        + "        \"type\": \"text\",\n"
                        + "        \"analyzer\": \"" + IK_INDEX_ANALYZER + "\",\n"
                        + "        \"search_analyzer\": \"" + IK_SEARCH_ANALYZER + "\"\n"
                        + "      },\n"
                        + "      \"answerTerms\": {\"type\": \"keyword\"},\n"
                        + "      \"answer_quality\": {\"type\": \"integer\"},\n"
                        + "      \"likeCount\": {\"type\": \"integer\"},\n"
                        + "      \"clickCount\": {\"type\": \"integer\"}\n"
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
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeQuestionId(String questionId) {
        if (questionId == null || questionId.trim().isEmpty()) {
            return "";
        }
        return questionId.trim();
    }

    private String normalizeTextValue(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }

    private String readString(Map<String, Object> sourceMap, String fieldName) {
        Object value = sourceMap.get(fieldName);
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> readStringList(Map<String, Object> sourceMap, String fieldName) {
        Object value = sourceMap.get(fieldName);
        if (!(value instanceof List)) {
            return new ArrayList<String>();
        }

        List<?> rawList = (List<?>) value;
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<String>();
        for (Object rawValue : rawList) {
            if (rawValue == null) {
                continue;
            }
            String normalizedValue = String.valueOf(rawValue).trim();
            if (!normalizedValue.isEmpty()) {
                normalizedValues.add(normalizedValue);
            }
        }
        return new ArrayList<String>(normalizedValues);
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

    private int normalizeNonNegativeMetricCount(int count) {
        return Math.max(count, 0);
    }

    private double roundScore(double score) {
        return Double.parseDouble(SCORE_FORMAT.format(score));
    }

    private UpdateRequest buildMetricsUpdateRequest(
            String answerDocumentId, int likeCount, int clickCount) {
        Map<String, Object> metricsDocument = new LinkedHashMap<String, Object>();
        metricsDocument.put("likeCount", normalizeNonNegativeMetricCount(likeCount));
        metricsDocument.put("clickCount", normalizeNonNegativeMetricCount(clickCount));
        return new UpdateRequest(EsIndexNames.COURSE_QA_ANSWER, answerDocumentId)
                .doc(metricsDocument);
    }

    private List<CourseQaAnswerMetricsUpdateItem> normalizeMetricsUpdateItems(
            List<CourseQaAnswerMetricsUpdateItem> rawItems) {
        LinkedHashMap<String, CourseQaAnswerMetricsUpdateItem> normalizedItemMap =
                new LinkedHashMap<String, CourseQaAnswerMetricsUpdateItem>();
        if (rawItems == null) {
            return new ArrayList<CourseQaAnswerMetricsUpdateItem>();
        }

        for (CourseQaAnswerMetricsUpdateItem rawItem : rawItems) {
            if (rawItem == null) {
                continue;
            }
            String answerDocumentId = normalizeTextValue(rawItem.getAnswerDocumentId());
            if (answerDocumentId.isEmpty()) {
                continue;
            }
            normalizedItemMap.put(
                    answerDocumentId,
                    new CourseQaAnswerMetricsUpdateItem(
                            answerDocumentId,
                            normalizeNonNegativeMetricCount(rawItem.getLikeCount()),
                            normalizeNonNegativeMetricCount(rawItem.getClickCount())));
        }
        return new ArrayList<CourseQaAnswerMetricsUpdateItem>(normalizedItemMap.values());
    }

    private Map<String, QuestionAnswerMetricStats> buildQuestionMetricStats(
            List<Map<String, Object>> answerCandidates) {
        Map<String, QuestionAnswerMetricStats> questionMetricStatsMap =
                new HashMap<String, QuestionAnswerMetricStats>();
        // 点赞量/点击量得分都要求按“同一个问题下的答案集合”做最大值归一化，
        // 所以这里先按 questionDocumentId 聚合出当前候选答案里的最大值基准。
        for (Map<String, Object> answerCandidate : answerCandidates) {
            String questionDocumentId = readString(answerCandidate, "questionDocumentId");
            QuestionAnswerMetricStats questionMetricStats = questionMetricStatsMap.get(questionDocumentId);
            if (questionMetricStats == null) {
                questionMetricStats = new QuestionAnswerMetricStats();
                questionMetricStatsMap.put(questionDocumentId, questionMetricStats);
            }
            questionMetricStats.maxLikeCount = Math.max(
                    questionMetricStats.maxLikeCount,
                    normalizeNonNegativeMetricCount(readInteger(answerCandidate, "likeCount")));
            questionMetricStats.maxClickCount = Math.max(
                    questionMetricStats.maxClickCount,
                    normalizeNonNegativeMetricCount(readInteger(answerCandidate, "clickCount")));
        }
        return questionMetricStatsMap;
    }

    private void clearScrollQuietly(String scrollId) {
        if (scrollId == null || scrollId.trim().isEmpty()) {
            return;
        }
        try {
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);
            client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
        } catch (IOException exception) {
            // 清理 scroll 失败不会影响主流程结果，但要避免 finally 中再次抛错覆盖原异常。
        }
    }

    private List<String> analyzeTextWithIk(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<String>();
        }

        AnalyzeRequest analyzeRequest = AnalyzeRequest.withGlobalAnalyzer(IK_TERM_ANALYZER, text);
        AnalyzeResponse analyzeResponse = client.indices().analyze(analyzeRequest, RequestOptions.DEFAULT);
        LinkedHashSet<String> terms = new LinkedHashSet<String>();
        for (AnalyzeResponse.AnalyzeToken token : analyzeResponse.getTokens()) {
            if (token == null || token.getTerm() == null) {
                continue;
            }
            String normalizedTerm = token.getTerm().trim().toLowerCase();
            if (!normalizedTerm.isEmpty()) {
                terms.add(normalizedTerm);
            }
        }
        return new ArrayList<String>(terms);
    }

    private static final class IndexedQuestion {
        private final String questionDocumentId;
        private final CourseQaQuestionItem questionItem;
        private final String sourceName;
        private final List<String> questionTerms;
        private int answerCount;

        private IndexedQuestion(
                String questionDocumentId,
                CourseQaQuestionItem questionItem,
                String sourceName,
                List<String> questionTerms) {
            this.questionDocumentId = questionDocumentId;
            this.questionItem = questionItem;
            this.sourceName = sourceName;
            this.questionTerms = questionTerms;
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

    private static final class AnswerBm25Stats {
        private final Map<String, Double> scoreMap;
        private final double normalizationBase;

        private AnswerBm25Stats(Map<String, Double> scoreMap, double normalizationBase) {
            this.scoreMap = scoreMap;
            this.normalizationBase = normalizationBase;
        }
    }

    private static final class QuestionAnswerMetricStats {
        private int maxLikeCount;
        private int maxClickCount;
    }

    private static final class QuestionRecallHit {
        private String questionDocumentId;
        private String categoryName;
        private String questionId;
        private String questionText;
        private List<String> questionTerms;
        private int answerCount;
        private int originalRank;
        private int questionRank;
        private double normalizedRecallScore;
        private double phraseScore;
        private double andScore;
        private double looseScore;
        private double weightedRecallScore;
        private double maxWeightedRecallScore;
    }
}

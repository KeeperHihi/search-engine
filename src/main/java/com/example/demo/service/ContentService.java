package com.example.demo.service;

import com.alibaba.fastjson.JSON;
import com.example.demo.pojo.Answer;
import com.example.demo.pojo.Content;
import com.example.demo.pojo.Question;
import com.example.demo.utils.HtmlParseUtil;
import com.example.demo.utils.JsonParseUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ContentService {

    // 将客户端注入
    @Autowired
    @Qualifier("restHighLevelClient")
    private RestHighLevelClient client;

    // 1、解析数据放到 es 中
    public boolean parseContent(String keyword) throws IOException {
        List<Content> contents = new HtmlParseUtil().parseJD(keyword);

        // 把查询的数据放入 es 中
        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < contents.size(); i++) {
            request.add(
                    new IndexRequest("jd_goods")
                            .source(JSON.toJSONString(contents.get(i)), XContentType.JSON));
        }

        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);
        return !bulk.hasFailures();
    }

    // 2、获取这些数据实现基本的搜索功能
    public List<Map<String, Object>> searchPage(String keyword, int pageNo, int pageSize)
            throws IOException {
        // keyword="机器学习";
        // keyword=keyword.getBytes("UTF-8").toString();
        if (pageNo <= 1) {
            pageNo = 1;
        }
        if (pageSize <= 1) {
            pageSize = 1;
        }

        // 条件搜索
        // SearchRequest searchRequest = new SearchRequest("jd_goods");
        SearchRequest searchRequest = new SearchRequest("jddata");

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 分页
        sourceBuilder.from(pageNo).size(pageSize);

        // 精准匹配
        // TermQueryBuilder termQuery = QueryBuilders.termQuery("title", keyword);
        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("title", keyword);

        // sourceBuilder.query(termQuery);
        sourceBuilder.query(matchQuery);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

        // 执行搜索
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        // 解析结果
        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            list.add(documentFields.getSourceAsMap());
        }
        return list;
    }

    public List<Map<String, Object>> searchQA(String keyword, int pageNo, int pageSize)
            throws IOException {
        // keyword="机器学习";
        // keyword=keyword.getBytes("UTF-8").toString();
        if (pageNo <= 1) {
            pageNo = 1;
        }
        if (pageSize <= 1) {
            pageSize = 1;
        }

        // 条件搜索
        SearchRequest searchRequest = new SearchRequest("insurance_question");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 分页
        sourceBuilder.from(pageNo).size(pageSize);

        // // 精准匹配 --- 不调整排序算法
        // TermQueryBuilder termQuery = QueryBuilders.termQuery("qzh", keyword);
        // sourceBuilder.query(termQuery);

        // MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
        // sourceBuilder.query(matchQuery);

        // // 调整排序算法 ---boost
        // String[] keyword_buff = keyword.trim().split(" ");
        // if(keyword_buff.length<=1){
        //     MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
        //     sourceBuilder.query(matchQuery);
        // }
        // else{
        //     MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh", keyword_buff[0]);
        //     matchQuery1.boost(2);

        //     String keyword_left=keyword_buff[1];
        //     for(int i=2;i<keyword_buff.length;i++){
        //         keyword_left=" "+keyword_buff[i];
        //     }
        //     MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh", keyword_left);
        //     BoolQueryBuilder boolQueryBuilder=QueryBuilders.boolQuery();
        //     boolQueryBuilder.should(matchQuery1);
        //     boolQueryBuilder.should(matchQuery2);
        //     sourceBuilder.query(boolQueryBuilder);
        // }

        // // 调整排序算法 ---boost positive and negative
        // String[] keyword_buff = keyword.trim().split(" ");
        // if(keyword_buff.length<=1){
        //     MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
        //     sourceBuilder.query(matchQuery);
        // }
        // else{
        //     MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh", keyword_buff[0]);
        //     matchQuery1.boost(2);

        //     String keyword_left=keyword_buff[1];
        //     for(int i=2;i<keyword_buff.length;i++){
        //         keyword_left=" "+keyword_buff[i];
        //     }
        //     MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh", keyword_left);
        //     BoostingQueryBuilder boosting=QueryBuilders.boostingQuery(matchQuery1,matchQuery2);
        //     boosting.negativeBoost(0.2f);
        //     sourceBuilder.query(boosting);
        // }

        // 调整排序算法 ---使用script score
        String[] keyword_buff = keyword.trim().split(" ");
        if (keyword_buff.length <= 1) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
            sourceBuilder.query(matchQuery);
        } else {
            MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh", keyword_buff[0]);
            matchQuery1.boost(2);

            String keyword_left = keyword_buff[1];
            for (int i = 2; i < keyword_buff.length; i++) {
                keyword_left = " " + keyword_buff[i];
            }
            MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh", keyword_left);
            String scoreScript =
                    "int weight=10;\n"
                            + "def random= randomScore(params.uuidHash);\n"
                            + "return weight*random";
            Map paraMap = new HashMap();

            int randint = (int) (Math.random() * 100);
            System.out.println(randint);
            paraMap.put("uuidHash", randint);

            Script script = new Script(Script.DEFAULT_SCRIPT_TYPE, "painless", scoreScript, paraMap);
            ScriptScoreQueryBuilder scriptScoreQueryBuilder =
                    QueryBuilders.scriptScoreQuery(matchQuery2, script);
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

            boolQueryBuilder.should(matchQuery1);
            boolQueryBuilder.should(scriptScoreQueryBuilder);
            sourceBuilder.query(boolQueryBuilder);
        }

        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        // 执行搜索
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        // 解析结果

        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            list.add(documentFields.getSourceAsMap());
        }
        return list;
    }

    public List<Map<String, Object>> searchAnswer(String qid) throws IOException {
        // 条件搜索insurance_question
        SearchRequest searchRequest = new SearchRequest("insurance_question");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 精准匹配
        TermQueryBuilder termQuery = QueryBuilders.termQuery("qid", qid);
        // TermQueryBuilder matchQuery = QueryBuilders.termQuery("qid", qid);

        sourceBuilder.query(termQuery);
        // sourceBuilder.query(matchQuery);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        // 执行搜索
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        // 解析结果

        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            list.add(documentFields.getSourceAsMap());
        }

        List<Map<String, Object>> list2 = new ArrayList<>();
        String qdomain = "";
        String qzh = "";
        String qen = "";
        String qanswers = "";
        String aid = "";
        if (!list.isEmpty()) {
            // 条件搜索insurance_answer
            searchRequest = new SearchRequest("insurance_answer");
            qdomain = (String) list.get(0).get("qdomain");
            qzh = (String) list.get(0).get("qzh");
            qen = (String) list.get(0).get("qen");
            qanswers = (String) list.get(0).get("qanswers");
            String[] temp;
            temp = qanswers.split("\"");
            aid = temp[1];
            // 精准匹配
            termQuery = QueryBuilders.termQuery("aid", aid);
            // TermQueryBuilder matchQuery = QueryBuilders.termQuery("qid", qid);

            sourceBuilder.query(termQuery);
            // sourceBuilder.query(matchQuery);
            sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
            // 执行搜索
            source = searchRequest.source(sourceBuilder);
            searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            // 解析结果

            for (SearchHit documentFields : searchResponse.getHits().getHits()) {
                list2.add(documentFields.getSourceAsMap());
            }
        }
        ;
        List<Map<String, Object>> list3 = new ArrayList<>();

        if (!list2.isEmpty()) {
            Map<String, Object> map1 = new HashMap<String, Object>();
            map1.put("qid", qid);
            map1.put("qdomain", qdomain);
            map1.put("qzh", qzh);
            map1.put("qen", qen);
            map1.put("aid", (String) list2.get(0).get("aid"));
            map1.put("azh", (String) list2.get(0).get("azh"));
            map1.put("aen", (String) list2.get(0).get("aen"));
            list3.add(map1);
        }

        return list3;
    }

    public boolean writeQAContent() throws IOException {

        // write quesitons into ES
        String file_path = "D:/insuranceqa_data/corpus/pool/trainnew.json";
        List<Question> questionList = new JsonParseUtil().parseJson(file_path);

        // 把查询的数据放入 es 中
        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < questionList.size(); i++) {
            request.add(
                    new IndexRequest("insurance_question")
                            .source(JSON.toJSONString(questionList.get(i)), XContentType.JSON));
        }
        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);

        // write answers into ES
        file_path = "D:/insuranceqa_data/corpus/pool/answersnew.json";
        List<Answer> answerList = new JsonParseUtil().parseAnJson(file_path);

        // 把查询的数据放入 es 中
        request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < answerList.size(); i++) {
            request.add(
                    new IndexRequest("insurance_answer")
                            .source(JSON.toJSONString(answerList.get(i)), XContentType.JSON));
        }
        bulk = client.bulk(request, RequestOptions.DEFAULT);

        return !bulk.hasFailures();
    }
}

# 全文搜索 Q&A 改造说明

## 1. 改造目标

本次改造的目标不是继续沿用项目里原来的 `trainnew.json / answersnew.json` demo 问答链路，而是把全文搜索统一升级为“课程问答测试集检索系统”。

老师现场验收时，重点会看下面三件事：

1. 能否导入新的测试数据集。
2. 输入一个问题后，是否能召回相关答案。
3. 候选答案的排序是否合理，而且排序过程不能偷看 `answer_quality` 标签。

因此，这次实现把全文搜索改造成了一条新的、独立的课程问答检索 pipeline。

---

## 2. 当前支持的数据格式

当前默认数据文件是：

```text
data/course_qa.json
```

数据结构是：

```json
{
  "某个课程类别": [
    {
      "id": 1,
      "question": "什么是自然语言处理？",
      "answers": [
        {
          "answer_quality": 0,
          "answer": "..."
        }
      ]
    }
  ]
}
```

说明：

- 顶层是“类别名 -> 问题列表”。
- 每个问题有 `id`、`question`、`answers`。
- 每个答案里带有 `answer_quality`。
- 系统会读取这个字段，但只把它当作外部评估标签理解。
- 导入索引时会保留这个字段，便于前端展示评估参考。
- 检索排序不会使用这个字段参与打分。

---

## 3. 新的全文搜索 Pipeline

用户在全文搜索页输入一个问题后，系统会执行下面这条链路：

```text
用户输入问题
-> /queryse
-> 先按 top-k 或 top-p 策略到问题索引里召回相近问题
-> 收集这些问题对应的候选答案
-> 最多召回 10000 个候选答案
-> 在 Java 里执行答案重排
-> 按总分降序返回前端
-> 前端展示答案列表并可点进详情页
```

这里严格遵守了老师给出的要求：

1. 先匹配问题库。
2. 再召回答案库。
3. 再在候选答案集合里做排序。

---

## 4. 为什么不再使用旧的 demo 问答逻辑

原项目里的全文搜索逻辑是基于：

- `trainnew.json`
- `answersnew.json`
- `insurance_question`
- `insurance_answer`

这套链路是 demo，用来帮助熟悉代码，不再适合老师新的测试集。

因此本次改造做了统一升级：

- 旧的 demo 问答 POJO 和旧导入逻辑已移除。
- `ContentService` 只保留商品搜索相关逻辑。
- 新增独立的 `CourseQaSearchService` 和 `CourseQaController` 负责课程问答全文搜索。
- 全文搜索现在只使用新的课程问答索引：
  - `course_qa_question`
  - `course_qa_answer`

这样做的好处是：

- 商品搜索和全文搜索职责清晰分离。
- 数据契约只有一套权威版本。
- 后续老师再给新的课程问答集时，不会和旧 demo 数据混在一起。

---

## 5. 导入接口设计

### 5.1 接口

```text
GET /writeQA
```

支持从服务端本地路径读取数据集：

```text
filePath=data/course_qa.json
```

如果不传 `filePath`，系统默认读取：

```text
./data/course_qa.json
```

这样更符合老师现场验收方式：

- 先把测试集放到本地
- 再调用 `/writeQA`
- 由后端统一读取本地文件并导入 ES

### 5.2 去重策略

导入阶段做了两层防重：

#### 第一层：重建索引

每次导入前，会先删除并重建：

- `course_qa_question`
- `course_qa_answer`

这样可以避免上一次测试集的数据残留，保证老师现场导入新的测试集后，搜索结果只来自当前这一次导入。

#### 第二层：稳定文档 ID

即使在单次导入文件内部存在重复记录，也会继续去重。

问题文档 ID 生成规则：

```text
categoryName + questionId
```

若 `questionId` 缺失，则回退为：

```text
categoryName + questionText
```

答案文档 ID 生成规则：

```text
questionDocumentId + answerText
```

最终都会做 `SHA-256` 哈希，保证：

- 同一问题重复导入不会新增重复文档
- 同一问题下相同答案文本重复出现也只保留一份

核心代码位置：

- `src/main/java/com/example/demo/utils/CourseQaDocumentIdUtil.java`

---

## 6. 索引设计

### 6.1 问题索引 `course_qa_question`

主要字段：

- `questionDocumentId`
- `categoryName`
- `questionId`
- `questionText`
- `questionTerms`
- `answerCount`
- `sourceName`

说明：

- `questionText` 使用 ES 的 IK 中文分词器建索引。
- `questionTerms` 是导入时调用 `ik_smart` 切出来的词项列表，主要给前端详情页展示和调试使用。

### 6.2 答案索引 `course_qa_answer`

主要字段：

- `answerDocumentId`
- `questionDocumentId`
- `categoryName`
- `questionId`
- `questionText`
- `answerText`
- `answerTerms`

注意：

- `answer_quality` 会保留在答案索引里，供前端展示评估标签。
- 排序阶段的打分逻辑不会读取这个字段。

核心代码位置：

- `src/main/java/com/example/demo/service/CourseQaSearchService.java`

---

## 7. 为什么现在直接使用 IK 中文分词插件

这次已经确认老师提供的 Elasticsearch 环境里预装了中文分词插件，项目目录里也能直接看到：

- `./elastic-search/elasticsearch-7.6.1/plugins/ik`
- `./elastic-search/elasticsearch-7.6.1/plugins/hanlp`

因此当前实现不再使用字符级 n-gram，而是直接使用 IK 分词。

具体做法是：

- `questionText` / `answerText` 在索引 mapping 里直接声明 `ik_max_word` 分词器
- 搜索时使用 `ik_smart` 作为 `search_analyzer`
- 导入阶段再额外调用一次 `ik_smart`，把问题词项和答案词项保存到 `questionTerms / answerTerms`

这样做的好处是：

- 更符合中文检索的真实使用方式
- 召回逻辑更容易向老师解释
- 第二阶段可以直接复用 ES 默认的 BM25 评分，而不需要手写很多经验规则

核心代码位置：

- `src/main/java/com/example/demo/utils/CourseQaTextUtil.java`
- `src/main/java/com/example/demo/service/CourseQaSearchService.java`

---

## 8. 问题召回阶段

搜索时，第一步不是直接搜答案，而是先召回问题。

接口：

```text
POST /queryse
```

请求参数：

- `keyword`：用户输入的问题
- `pageNo`：结果页码
- `pageSize`：返回答案数
- `recallStrategy`：召回策略，支持 `top_k` / `top_p`
- `topK`：当策略为 `top_k` 时，取分数最高的 `k` 个问题
- `topP`：当策略为 `top_p` 时，取归一化召回分大于等于 `p` 的问题

默认：

- `recallStrategy=top_k`
- `topK=5`
- `topP=0.85`
- `pageSize=20`

两种策略的含义如下：

1. `top_k`
   - 只保留得分最高的 `k` 个问题
2. `top_p`
   - 先把问题召回分归一化到 `0~1`
   - 只保留分数大于等于阈值 `p` 的问题

这里的 `top_p` 是“得分阈值策略”，不是“召回数量”。

问题召回查询由以下几部分组成：

1. `questionText` 的 `match_phrase`
2. `questionText` 的 `AND match`
3. `questionText` 的 `minimum_should_match=60%`
4. ES 返回 `_score` 后，再在 Java 中归一化成 `questionRecallScore`
5. 最后再套 `top_k` 或 `top_p` 策略裁剪问题集合

这样做的目的：

- 既照顾原问题的整句匹配
- 又兼顾近似问法下的关键词命中
- 并且利用 IK 分词让“神经网络 / 自然语言处理 / 监督学习”这类中文术语直接按词匹配

核心代码位置：

- `CourseQaSearchService.buildQuestionRecallQuery(...)`
- `CourseQaSearchService.recallQuestions(...)`

---

## 9. 候选答案召回阶段

得到问题召回结果后，系统会：

1. 取出这些问题的 `questionDocumentId`
2. 到答案索引里用 `termsQuery(questionDocumentId)` 一次性召回答案
3. 最多保留 `10000` 个候选答案

这样严格符合老师要求的：

```text
先匹配若干个问题
-> 再召回这些问题对应的 10k 个候选答案
```

核心代码位置：

- `CourseQaSearchService.recallAnswers(...)`

---

## 10. 答案重排算法

### 10.1 原则

排序阶段完全不读取 `answer_quality`。

当前排序只使用：

1. 查询问题本身
2. 召回到的源问题文本
3. 候选答案文本

### 10.2 排序特征

当前最终排序只保留两类真正参与总分的特征：

#### A. 问题召回分

- 这是 ES 在“问题召回阶段”给出的相对得分
- 用来保证答案主要来自真正相关的问题

#### B. 答案重排分

- 直接使用答案文本相对于当前查询的 BM25 分数
- 不再手写“答案覆盖率”“答案长度分”之类的经验规则
- 先在候选答案集合内取 BM25 原始分
- 再用“当前答案 BM25 / 候选集合最高 BM25”做归一化

### 10.3 总分公式

代码里现在采用的是“问题召回分 + BM25 归一化答案分”的两段式：

```text
总分 = 0.40 * 问题召回分 + 0.60 * 答案重排分
```

其中：

```text
答案重排分 = 答案 BM25 原始分 / 候选答案集合中的最高 BM25 分
```

这套设计的目的很明确：

- 先保证答案来自相对正确的问题簇
- 再在同一批候选答案内，用标准 BM25 把更贴近查询表达的答案排到前面
- 评分过程更接近真实搜索系统，也更容易向老师解释

核心代码位置：

- `src/main/java/com/example/demo/utils/CourseQaRankingUtil.java`

---

## 11. 前端页面改造

老师要求“全文搜索的前端样式不做改动，但允许添加新的组件”。

因此前端处理方式是：

- 保留原有搜索页的头部结构、蓝白配色和基本布局
- 在原页面上新增：
  - 检索提示文案
  - `top_k / top_p` 策略切换
  - 参数输入框
  - 召回统计信息
  - 答案结果卡片列表

搜索结果现在展示的是“答案列表”，而不是旧版的问题列表。

每条结果会展示：

- 来源问题
- 题号
- 类别
- 来源问题排名
- 总分
- 问题召回分
- 答案重排分
- 答案文本

并支持点击进入详情页：

```text
/answer/{answerDocumentId}
```

核心文件：

- `src/main/resources/templates/se.html`
- `src/main/resources/templates/answer.html`
- `src/main/resources/static/css/stylese.css`

---

## 12. 代码结构对应关系

### 12.1 控制器

- `ContentController`
  - 现在只负责商品搜索
- `CourseQaController`
  - `GET /writeQA`
  - `POST /queryse`
- `HelloController`
  - `GET /contentse`
  - `GET /answer/{answerId}`

### 12.2 服务层

- `ContentService`
  - 商品搜索
- `CourseQaSearchService`
  - 导入测试集
  - 创建索引
  - 问题召回
  - 答案召回
  - 答案重排
  - 详情查询

### 12.3 工具类

- `CourseQaDatasetParser`
  - 解析 `course_qa.json`
- `CourseQaDocumentIdUtil`
  - 生成稳定文档 ID
- `CourseQaTextUtil`
  - 中文分词结果处理
- `CourseQaRankingUtil`
  - 问题召回分与 BM25 归一化答案分的总分组合

### 12.4 DTO

位于：

```text
src/main/java/com/example/demo/pojo/courseqa
```

主要包括：

- `CourseQaDataset`
- `CourseQaQuestionItem`
- `CourseQaAnswerItem`
- `CourseQaImportResult`
- `CourseQaSearchResponse`
- `CourseQaSearchResultItem`
- `CourseQaAnswerDetail`

---

## 13. 单元测试

本次新增的单元测试包括：

1. `CourseQaDatasetParserTest`
   - 验证样例数据集能被正确解析
2. `CourseQaDocumentIdUtilTest`
   - 验证问题/答案文档 ID 稳定且可区分
3. `CourseQaRankingUtilTest`
   - 验证在同题候选答案中，更完整的答案会排得更高
   - 验证更贴题且更完整的答案会比泛化短答案得分更高

这些测试的目的不是模拟完整 ES 环境，而是先把最容易出问题的核心基础件固定住。

---

## 14. 现场演示建议流程

老师现场可以按下面顺序演示：

1. 启动项目

```bash
mvn -q spring-boot:run
```

2. 打开全文搜索页

```text
http://localhost:8080/contentse
```

3. 导入测试数据

```text
http://localhost:8080/writeQA
```

如果老师给的是其他本地文件，也可以：

```text
http://localhost:8080/writeQA?filePath=/你的/本地/路径.json
```

4. 输入一个问题进行检索

例如：

- 什么是自然语言处理
- 中文分词为什么重要
- 词袋模型有什么特点

5. 查看结果页

展示重点：

- 先召回了多少个问题
- 再召回了多少个答案
- 当前排序后的前若干条答案

6. 点击“查看详情”

进入答案详情页，展示：

- 类别
- 题号
- 来源问题
- 最终答案文本

---

## 15. 本次实现最重要的答辩点

如果老师问“你们怎么保证没有偷看 answer_quality”，可以直接回答：

1. 解析数据时虽然会读到这个字段，也会保存在答案索引里供前端展示，但排序逻辑不会读取它。
2. 排序服务只接触查询文本、问题文本和答案文本。
3. 重排公式完全基于问题召回分和答案 BM25 分，不依赖任何人工写死的长度奖励规则。
4. 接口返回给前端的 `answer_quality` 只用于人工评估展示，不参与排序。

如果老师问“为什么不用现成的中文插件或预训练模型”，可以回答：

1. 现场验收优先保证稳定可运行。
2. 老师提供的 ES 环境已经预装 IK，所以中文分词能力是现成可用的。
3. 当前方案直接使用 IK 做中文切词，不需要联网下载额外模型。
4. 在课程问答这种文本相对规整的场景里，问题召回 + 答案重排已经能给出比较稳定、可解释的结果。

---

## 16. 仍可继续优化的方向

如果后面你还想继续提高效果，可以继续演进：

1. 引入本地可离线运行的中文向量模型，做语义召回或语义重排。
2. 增加拼写纠错、同义词扩展、问句改写。
3. 在前端增加分页、排序解释面板、调试面板。
4. 如果老师后续允许，也可以把 `answer_quality` 进一步用于离线评估，计算 `MRR / NDCG` 等排序指标。

当前版本的优势是：

- 结构清晰
- 依赖稳定
- 现场可演示
- 代码讲解成本低

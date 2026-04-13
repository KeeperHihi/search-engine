# 全文搜索算法说明

## 1. 目标

当前全文搜索链路的目标是：

1. 用户输入一个问题。
2. 先从问题库中召回相近问题。
3. 再取这些问题对应的全部候选答案。
4. 在候选答案集合内做二次排序。
5. 返回排序后的答案列表给前端展示。

整个流程严格不使用 `answer_quality` 参与任何打分。它只在前端作为人工评估标签展示。

---

## 2. 当前依赖的中文分词能力

这版实现直接使用老师提供的 Elasticsearch 中文分词插件。

项目内可以看到：

- `./elastic-search/elasticsearch-7.6.1/plugins/ik`
- `./elastic-search/elasticsearch-7.6.1/plugins/hanlp`

当前代码实际使用的是 IK：

- 建索引分词器：`ik_max_word`
- 搜索分词器：`ik_smart`
- 导入时抽取词项：`ik_smart`

这样做的原因是：

1. `ik_max_word` 建索引时切得更细，召回更充分。
2. `ik_smart` 搜索时切得更稳，更适合做问题匹配和词项覆盖率计算。
3. 整套逻辑都可以直接在老师给的 ES 环境里运行，不需要额外下载模型。

---

## 3. 索引结构

### 3.1 问题索引 `course_qa_question`

核心字段：

- `questionDocumentId`
- `categoryName`
- `questionId`
- `questionText`
- `questionTerms`
- `answerCount`
- `sourceName`

其中：

- `questionText` 是真正用于 ES 问题召回的文本字段。
- `questionTerms` 是导入时调用 `ik_smart` 切出来的词项数组，供 Java 重排阶段计算覆盖率。

`questionText` 的 mapping 逻辑是：

```json
{
  "type": "text",
  "analyzer": "ik_max_word",
  "search_analyzer": "ik_smart"
}
```

### 3.2 答案索引 `course_qa_answer`

核心字段：

- `answerDocumentId`
- `questionDocumentId`
- `categoryName`
- `questionId`
- `questionText`
- `answerText`
- `answerTerms`
- `answerLength`
- `answer_quality`

其中：

- `answerText` 用于详情展示。
- `answerTerms` 是导入时调用 `ik_smart` 切出来的词项数组，供重排计算答案覆盖率。
- `answerLength` 是答案有效长度，供重排时做轻量长度偏好。
- `answer_quality` 只展示，不参与排序。

---

## 4. 数据导入时做了什么

接口：

```text
GET /writeQA
```

默认读取：

```text
./data/course_qa.json
```

导入流程如下：

1. 解析 JSON 数据集。
2. 为每个问题生成稳定的 `questionDocumentId`。
3. 为每个答案生成稳定的 `answerDocumentId`。
4. 删除并重建 `course_qa_question` 与 `course_qa_answer`。
5. 对每个问题调用一次 `ik_smart`，得到 `questionTerms`。
6. 对每个答案调用一次 `ik_smart`，得到 `answerTerms`。
7. 把问题、答案分别写入 ES。

这里的去重分两层：

1. 每次导入前先重建索引，避免历史测试集残留。
2. 同一批数据内部再通过稳定文档 ID 去重，避免重复写入。

---

## 5. 问题召回算法

### 5.1 查询入口

前端搜索会调用：

```text
POST /queryse
```

关键参数：

- `keyword`
- `recallStrategy`
- `topK`
- `topP`

其中：

- `top_k`：保留得分最高的 `k` 个问题。
- `top_p`：保留归一化召回分 `>= p` 的问题。

注意，这里的 `top_p` 不是累计概率，而是“归一化得分阈值”。

### 5.2 问题召回查询

问题召回直接对 `questionText` 做检索，不再依赖字符级 n-gram 字段。

当前 query 由三部分组成：

1. `match_phrase(questionText, keyword)`，权重 `6`
2. `match(questionText, keyword, operator=AND)`，权重 `4`
3. `match(questionText, keyword, minimum_should_match=60%)`，权重 `2`

并且：

- 三个条件放在同一个 `bool should` 中
- `minimum_should_match = 1`

含义很直接：

1. 如果用户问题和题库问题几乎一模一样，`match_phrase` 会拿到最高权重。
2. 如果不是完全一样，但关键词都命中了，`AND match` 仍然会给高分。
3. 如果只是近似问法，`60%` 关键词命中也能保留，避免漏召回。

### 5.3 问题召回分

ES 会给每个问题一个原始 `_score`，但原始 `_score` 只能在同一次查询内比较，不适合直接拿来做最终排序。

所以当前实现先做一次归一化：

```text
questionRecallScore = 当前问题的 _score / 本次查询第一名问题的 _score
```

归一化后：

- 第一名问题的 `questionRecallScore = 1.0`
- 其他问题落在 `0 ~ 1` 之间

这样有两个好处：

1. `top_p` 可以直接按阈值裁剪。
2. 后续总分计算更稳定，便于讲解。

### 5.4 top-k 和 top-p 的实际含义

#### top-k

```text
按 questionRecallScore 从高到低排序
只保留前 k 个问题
```

#### top-p

```text
按 questionRecallScore 从高到低排序
只保留 questionRecallScore >= p 的问题
```

举例：

- 如果 `top_p = 0.90`
- 第一名问题归一化分数是 `1.00`
- 第二名问题是 `0.94`
- 第三名问题是 `0.87`

那么只会保留前两个问题。

---

## 6. 候选答案召回算法

问题召回结束后，系统不会立刻把问题返回给前端，而是继续做答案召回。

流程是：

1. 取出保留下来的 `questionDocumentId`
2. 到 `course_qa_answer` 索引里执行 `termsQuery(questionDocumentId)`
3. 召回这些问题对应的全部答案
4. 最多保留 `10000` 个候选答案

这样严格符合老师要求的 pipeline：

```text
先召回问题
-> 再召回这些问题的候选答案
-> 再在候选答案集合里排序
```

---

## 7. 答案重排算法

### 7.1 先准备三组词项

在 Java 重排阶段，会准备三组词项：

1. `queryTerms`
   - 对用户输入的 `keyword` 调用一次 `ik_smart`
2. `questionTerms`
   - 导入时保存的问题词项
3. `answerTerms`
   - 导入时保存的答案词项

例如用户输入：

```text
什么是神经网络？
```

可能得到的 `queryTerms` 类似：

```text
[神经网络]
```

如果某条题库问题是：

```text
什么是神经网络？
```

它的 `questionTerms` 可能类似：

```text
[什么, 神经网络]
```

如果某条候选答案是：

```text
神经网络是一类由多层神经元连接而成的函数逼近模型。
```

它的 `answerTerms` 可能类似：

```text
[神经网络, 一类, 多层, 神经元, 连接, 函数, 逼近, 模型]
```

### 7.2 问题覆盖率

定义：

```text
questionCoverage = |queryTerms ∩ questionTerms| / |queryTerms|
```

含义：

- 查询词有多少比例在来源问题里出现了

例如：

- `queryTerms = [神经网络]`
- `questionTerms = [什么, 神经网络]`

则：

```text
questionCoverage = 1 / 1 = 1.0
```

### 7.3 答案覆盖率

定义：

```text
answerCoverage = |queryTerms ∩ answerTerms| / |queryTerms|
```

含义：

- 查询词有多少比例在候选答案里出现了

例如：

- `queryTerms = [神经网络]`
- `answerTerms = [神经网络, 一类, 多层, 神经元, 模型]`

则：

```text
answerCoverage = 1 / 1 = 1.0
```

### 7.4 答案长度分

定义：

```text
answerLengthScore = min(answerLength, 120) / 120
```

其中：

- `answerLength` 是清洗后文本的有效长度
- 120 字作为上限，超过后不再继续加分

设计原因：

1. 太短的答案通常信息不完整。
2. 但长度不能无限加分，否则会鼓励冗长废话。

例如：

- 如果答案有效长度是 `72`

则：

```text
answerLengthScore = 72 / 120 = 0.60
```

### 7.5 答案重排分

定义：

```text
answerRerankScore = 0.70 * answerCoverage + 0.30 * answerLengthScore
```

解释：

1. 先看答案是否真正覆盖了用户问题里的关键词。
2. 再用少量长度分去区分“过短答案”和“相对完整答案”。

### 7.6 最终总分

定义：

```text
totalScore = 0.70 * questionRecallScore
           + 0.30 * answerRerankScore
```

这两个部分的职责分别是：

1. `questionRecallScore`
   - 这条答案来自哪个问题，那个问题本身和查询有多像
2. `answerRerankScore`
   - 这条答案自己是否贴题、是否足够完整

补充说明：

- `questionCoverage` 仍然会保留为调试指标，方便答辩时解释“查询和来源问题到底重合了多少”
- 但它不再重复参与总分，避免和 `questionRecallScore` 在“问题相关性”这个维度上重复计分

---

## 8. 现场可讲的算分示例

假设用户查询：

```text
什么是神经网络？
```

某条候选答案对应的来源问题召回情况如下：

- `questionRecallScore = 0.95`
- `questionCoverage = 1.00`

候选答案自身情况如下：

- `answerCoverage = 1.00`
- `answerLength = 72`
- `answerLengthScore = 72 / 120 = 0.60`

则：

```text
answerRerankScore = 0.70 * 1.00 + 0.30 * 0.60
                  = 0.70 + 0.18
                  = 0.88
```

最终总分：

```text
totalScore = 0.70 * 0.95 + 0.30 * 0.88
           = 0.665 + 0.264
           = 0.929
```

如果另一条答案同样命中了“神经网络”，但正文很短，例如：

```text
神经网络是一种模型。
```

那么它可能仍然有：

- `answerCoverage = 1.00`

但长度分会显著更低，例如：

- `answerLengthScore = 0.15`

于是：

```text
answerRerankScore = 0.70 * 1.00 + 0.30 * 0.15
                  = 0.745
```

最终总分也会更低，所以会排在更完整的答案后面。

---

## 9. 为什么这个算法适合现场答辩

这套算法的优点是：

1. 结构清楚，严格符合“先问题召回，再答案重排”。
2. 每个分数都能解释，老师现场可以直接手算。
3. `top_k` 和 `top_p` 都有明确含义，且和代码实现一致。
4. 直接利用老师提供的 IK 分词环境，不再用字符 n-gram 这种临时近似方案。
5. 完全不使用 `answer_quality` 参与排序，便于现场说明评测公平性。

---

## 10. 对应代码位置

- 问题召回与答案召回：
  - `src/main/java/com/example/demo/service/CourseQaSearchService.java`
- 答案重排：
  - `src/main/java/com/example/demo/utils/CourseQaRankingUtil.java`
- 词项集合与长度工具：
  - `src/main/java/com/example/demo/utils/CourseQaTextUtil.java`
- 数据导入解析：
  - `src/main/java/com/example/demo/utils/CourseQaDatasetParser.java`
- 稳定文档 ID：
  - `src/main/java/com/example/demo/utils/CourseQaDocumentIdUtil.java`

---

## 11. 一句话汇报版本

如果你要现场快速汇报，可以直接说：

```text
我们的全文搜索分三步：先用 IK 分词在问题索引里召回相近问题，再取这些问题的全部候选答案，最后根据“问题召回分 + 问题词覆盖率 + 答案词覆盖率和长度”计算总分做重排，answer_quality 只用于展示评估，不参与排序。
```

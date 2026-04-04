# 项目上手说明

## 1. 先用一句话理解这个项目

这是一个基于 `Spring Boot` 的搜索演示项目，后端用 `Java + Elasticsearch` 提供搜索接口，前端不是现代工程化的 `Vue CLI / React` 项目，而是 `Thymeleaf + 原生 HTML + 直接引入 Vue2/axios` 的方式来渲染页面和发请求。

你可以把它理解成两个功能放在同一个项目里：

1. 一个“仿京东/商品搜索”的页面。
2. 一个“保险问答检索”的页面。

其中真正能跑出完整交互的主流程，重点在下面两个地址：

1. `http://localhost:8080/jdsearch`
2. `http://localhost:8080/contentse`

根路径 `/` 对应的首页模板并不是完整可用的主页面，这一点后面会专门解释。

---

## 2. 技术栈和整体结构

### 2.1 技术栈

从 `pom.xml` 可以看出，这个项目主要使用了：

- `Spring Boot 2.3.1.RELEASE`
- `Spring MVC`
- `Thymeleaf`
- `Elasticsearch 7.6.1`
- `Jsoup`
- `Fastjson`
- `Lombok`

前端没有打包构建流程，直接在页面里引入：

- `Vue 2`
- `axios`
- `jQuery`

### 2.2 项目结构

最重要的目录如下：

```text
src/main/java/com/example/demo
├── DemoApplication.java              # Spring Boot 启动入口
├── controller
│   ├── HelloController.java          # 页面路由
│   └── ContentController.java        # 接口路由
├── service
│   └── ContentService.java           # 核心业务逻辑
├── config
│   ├── ElasticSearchClientConfig.java# ES 客户端配置
│   ├── CorConfig.java                # CORS 配置
│   └── MyWebMvcConfig.java           # MVC/静态资源配置
├── utils
│   ├── HtmlParseUtil.java            # 爬取京东页面
│   └── JsonParseUtil.java            # 读取本地 JSON 数据
├── pojo
│   ├── Content.java                  # 商品/搜索结果模型
│   ├── Question.java                 # 问题模型
│   └── Answer.java                   # 答案模型
├── EsDoc.java / EsIndex.java / EsJDDoc.java / HtmlParseExample.java
│                                      # 偏演示/测试性质的辅助代码
└── User.java                         # ES 演示类使用的简单实体

src/main/resources
├── application.properties           # Spring Boot 配置
├── templates                        # Thymeleaf 模板页面
│   ├── index.html
│   ├── jdsearch.html
│   ├── se.html
│   ├── answer.html
│   └── test*.html
└── static
    ├── css
    ├── images
    └── js
```

---

## 3. 程序是怎么启动起来的

### 3.1 启动入口

启动类是：

- `src/main/java/com/example/demo/DemoApplication.java`

它只有一件事：

```java
SpringApplication.run(DemoApplication.class, args);
```

也就是说，Spring Boot 启动后会：

1. 扫描当前包及子包下的 `Controller`、`Service`、`Configuration` 等组件。
2. 初始化 Web 服务。
3. 按照配置把页面模板、静态资源、接口路由都挂起来。

### 3.2 端口配置

`application.properties` 指定了：

```properties
server.port=8080
```

所以默认访问地址就是：

```text
http://localhost:8080
```

### 3.3 Thymeleaf 的作用

配置里启用了 Thymeleaf：

- 模板目录：`classpath:/templates/`
- 后缀：`.html`

这意味着：

1. Java 控制器返回字符串 `"jdsearch"`。
2. Spring 会去找 `src/main/resources/templates/jdsearch.html`。
3. 再把这个页面返回给浏览器。

---

## 4. 整个项目最重要的代码流向

如果你第一次看 Java Web 项目，可以先记住这个总路线：

```text
浏览器页面
-> 访问某个 URL
-> Controller 接收请求
-> Service 处理业务
-> 调 Elasticsearch / 读 JSON / 爬网页
-> Service 返回结果
-> Controller 返回 JSON 或页面
-> 前端把结果渲染出来
```

这个项目里有两条主线：

1. 商品搜索主线
2. 问答搜索主线

下面分别讲。

---

## 5. 主线一：商品搜索页面是怎么跑起来的

### 5.1 页面入口

页面路由在：

- `src/main/java/com/example/demo/controller/HelloController.java`

其中：

```java
@GetMapping("/jdsearch")
public String hello2(Model model){
    return "jdsearch";
}
```

意思是：

1. 浏览器访问 `/jdsearch`
2. 进入 `HelloController.hello2()`
3. 返回模板名 `"jdsearch"`
4. Spring 渲染 `templates/jdsearch.html`

### 5.2 前端页面做了什么

商品搜索页面在：

- `src/main/resources/templates/jdsearch.html`

这个页面做了两件核心事：

1. 用 `Vue` 维护输入框和结果列表。
2. 点击搜索时，用 `axios` 发请求到后端。

前端关键逻辑是：

```javascript
axios.request({
    method: "POST",
    url:"http://localhost:8080/query",
    params:{
      keyword:this.keyword,
      pageNo:1,
      pageSize:20
    }
}).then(response=>{
  this.results=response.data;
})
```

意思是：

1. 用户在输入框输入关键词。
2. 点击“搜索”。
3. 浏览器发一个 `POST /query` 请求。
4. 后端返回 JSON 数组。
5. 前端把这个数组赋值给 `results`。
6. 页面通过 `v-for="item in results"` 渲染商品卡片。

### 5.3 后端接口怎么接住这个请求

接口在：

- `src/main/java/com/example/demo/controller/ContentController.java`

对应方法：

```java
@PostMapping("/query")
public List<Map<String, Object>> query(String keyword, int pageNo, int pageSize) throws IOException {
    List<Map<String, Object>> list = contentService.searchPage(keyword, pageNo, pageSize);
    return list;
}
```

这一步非常重要：

1. Controller 不负责真正搜索。
2. Controller 只是接参数，然后把工作转交给 `ContentService`。

### 5.4 Service 里真正做搜索

核心方法在：

- `src/main/java/com/example/demo/service/ContentService.java`

方法：

```java
public List<Map<String, Object>> searchPage(String keyword, int pageNo, int pageSize)
```

它的执行流程如下：

```text
接收 keyword/pageNo/pageSize
-> 构造 Elasticsearch 查询请求
-> 查询索引 jddata
-> 在 title 字段上做 matchQuery
-> 拿到命中的文档列表
-> 转成 List<Map<String, Object>>
-> 返回给 Controller
```

关键代码可以概括成：

```java
SearchRequest searchRequest = new SearchRequest("jddata");
MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("title", keyword);
sourceBuilder.query(matchQuery);
SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
```

### 5.5 返回浏览器之后发生了什么

Controller 把 `List<Map<String, Object>>` 直接返回。

因为 `ContentController` 上有 `@RestController`，所以 Spring 会自动把 Java 对象转成 JSON。

浏览器收到的每条数据，大致长这样：

```json
{
  "title": "...",
  "img": "...",
  "price": "..."
}
```

前端页面里：

- `item.img` 用来展示图片
- `item.price` 用来展示价格
- `item.title` 用来展示标题

所以商品搜索的完整链路是：

```text
/jdsearch
-> jdsearch.html
-> 点击搜索
-> POST /query
-> ContentController.query()
-> ContentService.searchPage()
-> Elasticsearch 索引 jddata
-> 返回 JSON
-> Vue 渲染 results
```

---

## 6. 主线二：问答搜索页面是怎么跑起来的

### 6.1 页面入口

还是在 `HelloController.java` 里：

```java
@GetMapping("/contentse")
public String se(Model model){
    return "se";
}
```

浏览器访问 `/contentse` 时，会打开：

- `src/main/resources/templates/se.html`

### 6.2 前端页面逻辑

`se.html` 的前端逻辑和 `jdsearch.html` 很像，也是：

1. Vue 维护关键词和结果列表
2. axios 发请求

搜索按钮触发：

```javascript
axios.request({
    method: "POST",
    url:"http://localhost:8080/queryse",
    params:{
      keyword:this.keyword,
      pageNo:1,
      pageSize:100
    }
}).then(response=>{
  this.results=response.data;
})
```

所以它调用的是：

```text
POST /queryse
```

### 6.3 后端接口

在 `ContentController.java`：

```java
@PostMapping("/queryse")
public List<Map<String, Object>> queryse(String keyword, int pageNo, int pageSize) throws IOException {
    List<Map<String, Object>> list = contentService.searchQA(keyword, pageNo, pageSize);
    return list;
}
```

### 6.4 真正的问答搜索逻辑

在 `ContentService.java`：

```java
public List<Map<String, Object>> searchQA(String keyword, int pageNo, int pageSize)
```

它会查询 ES 中的：

```text
insurance_question
```

这个方法的核心不是简单查询，而是带了一点“排序调节”思路。

当前实际启用的是“按空格拆词后，做不同权重查询”的版本：

1. 如果关键词只有一个词：
   - 直接对 `qzh` 做 `matchQuery`
2. 如果关键词有多个词：
   - 第一个词做 `matchQuery`，并 `boost(2)`
   - 剩余部分做 `matchQuery`
   - 再配合 `scriptScore` 引入一个随机分数
   - 最后通过 `boolQuery.should(...)` 合并

通俗理解：

1. 题目里如果命中了第一个词，会更重要。
2. 其余词也参与匹配。
3. 结果里还混入了一点随机性。

所以问答搜索的链路是：

```text
/contentse
-> se.html
-> 点击搜索
-> POST /queryse
-> ContentController.queryse()
-> ContentService.searchQA()
-> Elasticsearch 索引 insurance_question
-> 返回问题列表
-> Vue 渲染问题结果
```

### 6.5 点击某个问题后，答案页如何打开

在 `se.html` 中，每条搜索结果都绑定了：

```javascript
searchId(qid){
  window.location.href = "http://localhost:8080/searchAn/"+qid;
}
```

也就是说，点击问题标题后，浏览器会跳到：

```text
/searchAn/{qid}
```

### 6.6 详情页的 Controller

在 `HelloController.java`：

```java
@GetMapping("/searchAn/{aid}")
public String parsese(Model model, @PathVariable("aid") String aid) throws IOException
```

这里有一个命名上的小混乱：

1. 路由写的是 `/searchAn/{aid}`
2. 但页面传进去的其实是 `qid`
3. 方法参数变量名叫 `aid`

实际上它收到的是“问题 id”，只是变量名没改干净。

### 6.7 详情页查询逻辑

真正查询逻辑在：

```java
public List<Map<String, Object>> searchAnswer(String qid)
```

这段逻辑分两步：

#### 第一步：先查问题

去 ES 的 `insurance_question` 索引里，根据 `qid` 查对应问题。

取出的字段包括：

- `qdomain`
- `qzh`
- `qen`
- `qanswers`

#### 第二步：再查答案

问题里有个字段 `qanswers`，代码是这样取答案 id 的：

```java
temp=qanswers.split("\"");
aid=temp[1];
```

也就是说，它是假设 `qanswers` 这个字段里有一段带双引号的字符串，然后粗暴地切出来第一个答案 id。

拿到这个 `aid` 以后，再去另一个索引：

```text
insurance_answer
```

查对应答案。

最后把问题和答案的字段重新拼成一个 `Map`，再放进列表返回。

### 6.8 Thymeleaf 如何显示详情页

`HelloController.parsese()` 把返回结果中的字段放进 `Model`：

```java
model.addAttribute("qid", ...)
model.addAttribute("qzh", ...)
model.addAttribute("qen", ...)
model.addAttribute("qdomain", ...)
model.addAttribute("aid", ...)
model.addAttribute("azh", ...)
model.addAttribute("aen", ...)
```

然后返回：

```java
return "answer";
```

于是 `templates/answer.html` 用 `th:text` 把这些值展示出来。

答案页完整链路如下：

```text
点击 se.html 中的问题
-> 跳转 /searchAn/{qid}
-> HelloController.parsese()
-> ContentService.searchAnswer(qid)
-> 先查 insurance_question
-> 再查 insurance_answer
-> 拼装结果
-> 返回 answer.html
-> Thymeleaf 渲染页面
```

---

## 7. 数据是从哪里来的

这个项目里，数据来源不止一种。

### 7.1 商品数据来源：网页抓取或本地 JSON

#### 路径 A：抓京东页面

`ContentService.parseContent(String keyword)` 会调用：

- `HtmlParseUtil.parseJD(keyword)`

`HtmlParseUtil` 做的事情是：

1. 拼出京东搜索 URL
2. 用 `Jsoup.parse(new URL(url), 30000)` 抓取页面
3. 找到页面里的 `J_goodsList`
4. 从每个 `li.gl-item` 中抽取：
   - 图片 `img`
   - 价格 `price`
   - 标题 `title`
5. 封装成 `Content`
6. 批量写入 Elasticsearch

对应接口是：

```text
GET /parse/{keyword}
```

#### 路径 B：从本地 JSON 导入

`EsJDDoc.writeJDdata()` 会从固定文件路径读取：

```text
./data/jddata.json
```

然后写入 ES 索引：

```text
jddata
```

这里的核心点是：

- 当前代码里，`parseContent()` 和 `searchPage()` 都统一使用 `jddata`
- `EsJDDoc.writeJDdata()` 也会把本地 JSON 导入到 `jddata`

也就是说，商品抓取、商品搜索、商品离线导入这三条链路现在已经使用同一个索引。

历史上这里曾经出现过：

1. `/parse/{keyword}` 写入 `jd_goods`
2. `/query` 查询 `jddata`

这会导致“抓到了数据，但页面搜不到”的错觉。当前代码已经修正为统一使用 `jddata`。

### 7.2 问答数据来源：本地 JSON

`ContentService.writeQAContent()` 会从两个固定路径读取数据：

```text
D:/insuranceqa_data/corpus/pool/trainnew.json
D:/insuranceqa_data/corpus/pool/answersnew.json
```

然后分别写入：

```text
insurance_question
insurance_answer
```

这里用到的解析工具是：

- `JsonParseUtil.parseJson()`：读取问题 JSON
- `JsonParseUtil.parseAnJson()`：读取答案 JSON

对应接口是：

```text
GET /writeQA
```

---

## 8. 主要类的职责怎么理解

### 8.1 `DemoApplication`

作用：启动 Spring Boot。

你可以把它理解成“项目总开关”。

### 8.2 `HelloController`

作用：返回页面模板。

它主要负责：

- `/` -> `index.html`
- `/jdsearch` -> `jdsearch.html`
- `/contentse` -> `se.html`
- `/searchAn/{qid}` -> `answer.html`

所以它更像“页面导航控制器”。

### 8.3 `ContentController`

作用：提供接口，返回 JSON。

它主要负责：

- `/query`：商品搜索
- `/queryse`：问答搜索
- `/parse/{keyword}`：抓取京东并写入 ES
- `/writeQA`：导入问答数据

所以它更像“数据接口控制器”。

### 8.4 `ContentService`

作用：真正的业务核心。

如果你要改搜索逻辑，大概率改这里。

它负责：

- 商品抓取入库
- 商品搜索
- 问答搜索
- 问题详情和答案拼装
- JSON 数据写入 ES

### 8.5 `ElasticSearchClientConfig`

作用：创建 ES 客户端。

现在写死的是：

```java
new HttpHost("127.0.0.1", 9200, "http")
```

也就是说：

1. ES 默认必须在本机
2. 端口必须是 `9200`

### 8.6 `MyWebMvcConfig`

作用：自定义 MVC 配置。

它主要做了：

1. 指定字符串响应编码为 `UTF-8`
2. 手动配置静态资源目录
3. 保证在继承 `WebMvcConfigurationSupport` 后静态资源还能访问

这个类不是业务核心，但和页面能否正常加载 CSS/JS 有关系。

### 8.7 `CorConfig`

作用：全局允许跨域。

它配置了：

- 允许任意来源
- 允许任意请求头
- 允许任意方法

页面里很多 `axios` 请求把地址直接写成 `http://localhost:8080/...`，所以这个配置能减少跨域问题。

### 8.8 `HtmlParseUtil`

作用：网页抓取工具。

如果你后面想改抓取字段、改抓取网站、或者加更多商品属性，就改它。

### 8.9 `JsonParseUtil`

作用：把本地 JSON 文件读成 Java 对象列表。

如果你后面换 JSON 数据格式，就改它。

### 8.10 `Content / Question / Answer`

作用：承载数据字段的普通 Java 类。

可以把它们理解为：

- `Content`：商品一条记录
- `Question`：问题一条记录
- `Answer`：答案一条记录

---

## 9. 前端代码应该怎么看

如果你没写过前端，这个项目前端部分可以按下面方法看：

### 9.1 这不是“前后端完全分离”

页面文件放在：

- `src/main/resources/templates`

这说明：

1. 页面是由 Spring 返回的
2. 不是独立部署的前端项目
3. 没有 `npm run dev` 这种前端开发服务器主流程

### 9.2 Vue 在这里只是“页面小助手”

页面里直接这样写：

```html
<script src="./js/vue.min.js"></script>
<script src="./js/axios.min.js"></script>
```

然后：

```javascript
new Vue({
  el:'#app',
  data:{ ... },
  methods:{ ... }
})
```

这不是现代单文件组件写法，而是最传统、最直接的用法。

所以你看前端时，只要抓 3 个点：

1. `data` 里有哪些状态变量
2. `methods` 里点击按钮会调用哪个函数
3. 函数里调用了哪个后端接口

### 9.3 页面渲染的几个常见语法

你会看到：

```html
v-model="keyword"
```

意思是：输入框内容和 `keyword` 变量双向绑定。

```html
v-for="item in results"
```

意思是：遍历 `results` 数组，把每一项都渲染出来。

```html
{{item.price}}
```

意思是：把变量值显示出来。

```html
v-html="item.title"
```

意思是：把字符串按 HTML 方式插入页面。

### 9.4 `templates` 和 `static` 的区别

- `templates`：要经过 Spring + Thymeleaf 渲染
- `static`：静态资源，浏览器直接访问

本项目里：

- HTML 主页面在 `templates`
- CSS、JS、图片在 `static`

---

## 10. 初学者建议按什么顺序读代码

我建议你按这个顺序读：

1. `DemoApplication.java`
2. `HelloController.java`
3. `ContentController.java`
4. `ContentService.java`
5. `templates/jdsearch.html`
6. `templates/se.html`
7. `templates/answer.html`
8. `utils/HtmlParseUtil.java`
9. `utils/JsonParseUtil.java`
10. `pojo/*.java`

原因很简单：

1. 先搞清入口和路由
2. 再搞清接口
3. 再搞清业务
4. 最后再看数据来源和数据结构

这样不会一上来就淹没在细节里。

---

## 11. 你后面如果想修改功能，应该从哪里下手

### 11.1 想改页面样式

改这些文件：

- `src/main/resources/templates/jdsearch.html`
- `src/main/resources/templates/se.html`
- `src/main/resources/templates/answer.html`
- `src/main/resources/static/css/style.css`
- `src/main/resources/static/css/stylese.css`

### 11.2 想改商品搜索逻辑

优先改：

- `src/main/java/com/example/demo/service/ContentService.java`

重点看：

- `searchPage()`

你可以改：

- 查询的索引名
- 查询字段
- 排序方式
- 分页方式

### 11.3 想改问答搜索排序逻辑

优先改：

- `ContentService.searchQA()`

这里现在已经放了多种思路的注释版本：

- 直接精准匹配
- boost
- boosting query
- script score

你可以把它当成实验区。

### 11.4 想改答案详情页展示内容

优先改：

1. `ContentService.searchAnswer()`
2. `HelloController.parsese()`
3. `templates/answer.html`

因为详情页展示的数据是：

```text
Service 查出来
-> Controller 放入 Model
-> Thymeleaf 渲染
```

这三处必须配套看。

### 11.5 想改数据导入方式

优先改：

- `ContentService.writeQAContent()`
- `JsonParseUtil.java`
- `HtmlParseUtil.java`
- `EsJDDoc.java`

如果你要把硬编码路径改成配置文件读取，也建议从这里入手。

---

## 12. 这个项目里哪些代码是“主流程”，哪些是“辅助/演示”

### 12.1 主流程代码

优先关注：

- `DemoApplication`
- `HelloController`
- `ContentController`
- `ContentService`
- `ElasticSearchClientConfig`
- `templates/jdsearch.html`
- `templates/se.html`
- `templates/answer.html`
- `HtmlParseUtil`
- `JsonParseUtil`
- `pojo/*.java`

### 12.2 可以先忽略的代码

这些更像实验、演示、历史残留：

- `EsDoc.java`
- `EsIndex.java`
- `EsJDDoc.java`
- `HtmlParseExample.java`
- `templates/test.html`
- `templates/test1_old.html`
- `templates/test2.html`
- `static/index.html`
- `static/css/style1.css`

不是说它们完全没用，而是它们不在当前主流程的核心调用链上。

---

## 13. 这个项目里最容易踩的坑

这一节非常重要。

### 13.1 根路径 `/` 对应的首页并不是完整成品

`HelloController` 中：

```java
@GetMapping({"/","index"})
public String index(){
    return "index";
}
```

它返回的是 `templates/index.html`。

但是这个 `templates/index.html` 只有页面壳子，没有真正初始化 Vue，也没有引入搜索逻辑对应的脚本。

所以：

1. 你看到首页有输入框和按钮
2. 但它不是完整可交互的页面
3. 真正能工作的页面反而是 `/jdsearch` 和 `/contentse`

同时项目里还有一个 `static/index.html`，它比 `templates/index.html` 更完整，但由于有 `HelloController` 显式接管了 `/`，所以它并不是当前主入口。

### 13.2 商品索引历史上曾不一致，当前已统一

这个项目原先最关键的业务坑是：

- `/parse/{keyword}` -> 写入 `jd_goods`
- `/query` -> 查询 `jddata`

现在这两条链路都已经统一到了 `jddata`，但理解这个历史问题依然有价值，因为它解释了为什么仓库里会同时出现过两个商品索引名。

### 13.3 `pageNo` 不是标准分页写法

在 `searchPage()` 和 `searchQA()` 里：

```java
sourceBuilder.from(pageNo).size(pageSize);
```

通常 Elasticsearch 分页应该是：

```java
from = (pageNo - 1) * pageSize
```

但这里直接把 `pageNo` 当成了 `from` 偏移量。

所以如果你后面要做真正分页，这里必须改。

### 13.4 问答导入路径写死在 Windows 盘符

`writeQAContent()` 使用的是：

```text
D:/insuranceqa_data/corpus/pool/trainnew.json
D:/insuranceqa_data/corpus/pool/answersnew.json
```

这说明：

1. 代码最初作者大概率在 Windows 上开发
2. 换机器后很可能直接失效
3. 这类路径最好改成配置文件或相对路径

### 13.5 页面里把后端地址写死成 `http://localhost:8080`

例如：

```javascript
url:"http://localhost:8080/query"
window.location.href = "http://localhost:8080/searchAn/"+qid;
```

问题是：

1. 如果端口改了，会失效
2. 如果部署到服务器域名上，也会失效
3. 更推荐改成相对路径，如 `/query`、`/searchAn/xxx`

### 13.6 `searchAnswer()` 解析答案 id 的方式比较脆弱

它通过：

```java
temp=qanswers.split("\"");
aid=temp[1];
```

来拿答案 id。

这非常依赖 `qanswers` 字段的字符串格式。

如果 JSON 格式稍有变化，这段代码就可能报错或取错值。

### 13.7 `WebMvcConfigurationSupport` 会覆盖默认 MVC 配置

`MyWebMvcConfig` 继承了 `WebMvcConfigurationSupport`。

这类写法本身没有错，但要知道：

1. 它会覆盖 Spring Boot 的一部分默认配置
2. 所以作者不得不手动补 `addResourceHandlers()`
3. 如果你以后发现静态资源 404，要先看这里

### 13.8 CORS 配置有重复

项目里既有：

- `@CrossOrigin`

又有：

- `CorConfig` 全局 `CorsFilter`

能用，但有点重复。

---

## 14. 运行这个项目前，你至少要满足什么条件

### 14.1 Java 版本

`pom.xml` 里写的是：

```xml
<java.version>1.8</java.version>
```

也就是说，这个项目设计时默认是跑在 `JDK 8` 上的。

我在当前环境里尝试执行：

```text
mvn -q -DskipTests compile
```

结果失败，报错核心信息是：

```text
Unable to make field ... JavacProcessingEnvironment.discoveredProcs accessible
```

当前环境的 Java 版本是：

```text
OpenJDK 21.0.10
```

这基本可以判断为：

1. 这个旧项目和当前 JDK 21 兼容性不好
2. 问题大概率与旧版 `Lombok 1.18.4` / 老项目编译配置有关
3. 最稳妥的做法是先切到 `JDK 8`

所以如果你要本地运行，建议优先准备：

```text
JDK 8 + Maven + Elasticsearch 7.x
```

### 14.2 Elasticsearch

ES 客户端配置写死为：

```text
127.0.0.1:9200
```

所以本地需要有一个可访问的 ES 实例。

### 14.3 数据

你至少要准备好相应索引中的数据：

- `jddata`
- `insurance_question`
- `insurance_answer`

否则页面即使能打开，也可能查不到内容。

---

## 15. 如果你现在要快速上手，我建议你这么做

### 第一步：先别急着全看懂

先只看这 4 个文件：

1. `HelloController.java`
2. `ContentController.java`
3. `ContentService.java`
4. `templates/se.html` 或 `templates/jdsearch.html`

这 4 个文件足够你抓住“页面 -> 接口 -> 业务 -> 返回页面”的最短链路。

### 第二步：选一条线改一个小功能

比如你可以先做这种很小的修改：

1. 改 `answer.html` 的显示文字
2. 在 `jdsearch.html` 增加一个字段展示
3. 把 `localhost:8080` 改成相对路径
4. 把硬编码文件路径提到配置文件里

这样你会最快建立信心。

### 第三步：再动搜索逻辑

等你熟悉后，再去改：

- `searchPage()`
- `searchQA()`
- `searchAnswer()`

这是项目真正的“核心算法区”。

---

## 16. 给你一个“脑内地图”

以后你看这个项目时，可以始终带着下面这张图：

```text
页面入口：
HelloController
  /jdsearch   -> jdsearch.html
  /contentse  -> se.html
  /searchAn   -> answer.html

接口入口：
ContentController
  /query      -> 商品搜索
  /queryse    -> 问答搜索
  /parse      -> 抓取京东并入库
  /writeQA    -> 导入问答 JSON

业务核心：
ContentService
  searchPage()
  searchQA()
  searchAnswer()
  parseContent()
  writeQAContent()

数据工具：
HtmlParseUtil
JsonParseUtil

数据存储：
Elasticsearch
  jddata
  insurance_question
  insurance_answer
```

只要这张图不丢，你就不会迷路。

---

## 17. 最后给你的结论

如果把这个项目压缩成最核心的理解，就是下面 4 句话：

1. 这是一个 `Spring Boot + Thymeleaf + Elasticsearch` 的搜索演示项目。
2. 页面层很薄，真正核心逻辑主要都在 `ContentService`。
3. 商品搜索和问答搜索是两条并行主线，问答搜索这一条更完整。
4. 这个项目存在一些明显的历史遗留问题，尤其是首页不完整、索引名不一致、路径硬编码、分页不标准。

所以你后续修改时，建议优先级是：

1. 先统一“数据写到哪个索引、页面查哪个索引”
2. 再把硬编码路径和硬编码 URL 清掉
3. 最后再优化搜索逻辑和页面展示

这样改起来最稳。

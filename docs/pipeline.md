# 项目启动方式与代码流动说明

本文的目标不是只告诉你“怎么跑起来”，而是让你在第一次接触 `Java + Spring Boot + Thymeleaf + Elasticsearch` 的情况下，也能看清楚这个项目从启动到处理请求的整条链路。这样你后面要加新功能时，能知道该改哪一层、为什么改那里。

---

## 1. 先用一句话理解这个项目

这是一个基于 `Spring Boot` 的搜索演示项目：

- 后端负责启动 Web 服务、接收 HTTP 请求、调用 Elasticsearch、返回页面或 JSON。
- 前端不是独立打包项目，而是直接放在 Spring Boot 里的 `Thymeleaf 模板 + 静态资源`。
- 项目里实际上混合了两套业务：
  - 仿京东商品搜索
  - 保险问答检索

所以它不是一个只有“京东页面”的纯前端练习，而是一个“Java Web 搜索实验项目”。

---

## 2. 我已经帮你核对过的启动前提

你给出的 Java 8 环境切换命令是可用的，我已经在本机验证过：

```bash
export JAVA_HOME=/home/jin/.jdks/corretto-1.8.0_482
export PATH="$JAVA_HOME/bin:$PATH"
```

验证结果：

- `java -version` 显示 `1.8.0_482`
- `mvn -version` 显示 Maven 正在使用这套 Java 8
- `mvn -q -DskipTests compile` 编译通过
- `mvn -q spring-boot:run` 可以正常把 Spring Boot 启起来

也就是说，这个项目在你提供的 Java 8 环境下，至少“编译”和“应用启动”这两步是通的。

---

## 3. 启动前你需要知道的外部依赖

### 3.1 Java

这个项目的 `pom.xml` 明确写了：

- `java.version = 1.8`

所以你本机默认的 `Java 21` 不适合直接跑这个项目。你已经准备好了 `Java 8`，这是正确的。

### 3.2 Maven

项目是 `Maven` 项目，因为根目录有：

- `pom.xml`

### 3.3 Elasticsearch

项目现在通过 `application.properties` 配置 Elasticsearch 连接：

```properties
search.elasticsearch.host=127.0.0.1
search.elasticsearch.port=9200
search.elasticsearch.scheme=http
```

也就是说：

- 默认 Elasticsearch 需要运行在本机 `127.0.0.1:9200`
- 如果 ES 没开，应用本身通常还能启动
- 但一旦访问搜索接口，调用 ES 时就会报错

### 3.4 数据索引

项目里的搜索依赖这些索引名：

- `jddata`
- `insurance_question`
- `insurance_answer`

其中商品搜索现在统一使用：

- `jddata`

历史上抓取链路曾写入 `jd_goods`、页面查询读取 `jddata`，这是此前最关键的逻辑分叉；当前代码已经统一到 `jddata`。

---

## 4. 最常用的启动命令

在项目根目录执行：

```bash
export JAVA_HOME=/home/jin/.jdks/corretto-1.8.0_482
export PATH="$JAVA_HOME/bin:$PATH"
mvn -q spring-boot:run
```

启动成功后默认访问：

```text
http://localhost:8080
```

但这个项目真正建议直接访问的入口不是根路径 `/`，而是：

- `http://localhost:8080/jdsearch`
- `http://localhost:8080/contentse`

原因是：

- `/` 对应的页面不完整，只有页面头部，没有完整搜索结果交互链路
- `/jdsearch` 才是仿京东商品搜索主页面
- `/contentse` 是问答检索页面

---

## 5. 你可以怎样判断“项目启动成功”

当你执行：

```bash
mvn -q spring-boot:run
```

启动日志里出现类似内容，就说明 Web 服务已经起来了：

```text
Tomcat initialized with port(s): 8080 (http)
Tomcat started on port(s): 8080 (http)
Started DemoApplication
```

这表示：

1. Spring Boot 已启动。
2. 内嵌 Tomcat 已启动。
3. 当前项目正在监听 `8080` 端口。
4. 浏览器已经可以访问页面和接口。

---

## 6. 项目结构总览

最重要的目录和职责如下：

```text
src/main/java/com/example/demo
├── DemoApplication.java
├── controller
│   ├── HelloController.java
│   └── ContentController.java
├── service
│   └── ContentService.java
├── config
│   ├── ElasticSearchClientConfig.java
│   ├── CorConfig.java
│   └── MyWebMvcConfig.java
├── utils
│   ├── HtmlParseUtil.java
│   └── JsonParseUtil.java
├── pojo
│   ├── Content.java
│   ├── Question.java
│   └── Answer.java
└── EsDoc.java / EsIndex.java / EsJDDoc.java

src/main/resources
├── application.properties
├── templates
│   ├── index.html
│   ├── jdsearch.html
│   ├── se.html
│   └── answer.html
└── static
    ├── css
    ├── images
    └── js
```

如果你以后要加功能，可以先用这张心智图：

- 页面展示相关：`templates`、`static`
- 路由入口：`controller`
- 业务逻辑：`service`
- 数据模型：`pojo`
- 外部系统接入：`config`
- 抓取/读文件工具：`utils`

---

## 7. Spring Boot 启动时，代码到底做了什么

下面按时间顺序讲。

### 7.1 进入主函数

程序入口是：

- `src/main/java/com/example/demo/DemoApplication.java`

核心代码只有一行：

```java
SpringApplication.run(DemoApplication.class, args);
```

这行代码做的事情可以理解为：

1. 启动 Spring Boot 容器。
2. 扫描 `com.example.demo` 包及其子包。
3. 把 `@Controller`、`@RestController`、`@Service`、`@Configuration` 这些类注册到 Spring 容器里。
4. 创建内嵌 Tomcat。
5. 让浏览器请求能够被路由到对应的 Java 方法。

### 7.2 读取配置文件

接着会读取：

- `src/main/resources/application.properties`

里面最重要的配置是：

```properties
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
server.port=8080
```

它们的含义是：

- 页面模板在 `templates` 目录
- 控制器返回 `"jdsearch"` 时，会去找 `templates/jdsearch.html`
- Web 服务端口是 `8080`

### 7.3 注册配置类

Spring 会把 `config` 包里的配置类加载进来。

#### 7.3.1 `ElasticSearchClientConfig`

职责：

- 创建 `RestHighLevelClient`
- 供业务层注入使用

作用：

- 后续 `ContentService` 里可以直接 `@Autowired` 一个 ES 客户端

#### 7.3.2 `CorConfig`

职责：

- 开启跨域访问

作用：

- 即使未来前端单独部署，也能通过全局 CORS 配置放行跨域请求

当前主页面已经改成使用相对路径如 `/query`、`/queryse` 发请求，所以在同源部署下并不依赖这个配置。

#### 7.3.3 `MyWebMvcConfig`

职责：

- 自定义消息转换器
- 自定义静态资源路径

它比较重要，因为它继承了 `WebMvcConfigurationSupport`。这意味着 Spring Boot 默认的一些 MVC 自动配置会被覆盖，所以作者专门手动加了静态资源映射。

如果没有这层配置，像下面这些资源可能访问不到：

- `/css/style.css`
- `/js/vue.min.js`
- `/images/jdlogo.png`

---

## 8. 启动成功后，浏览器请求是如何被处理的

你可以把这个项目理解成两类请求：

1. 页面请求
2. 接口请求

### 8.1 页面请求

页面请求的特点：

- 浏览器地址栏直接访问某个 URL
- 后端返回一个 HTML 页面

例如：

- `GET /jdsearch`
- `GET /contentse`

### 8.2 接口请求

接口请求的特点：

- 页面上的 JS 代码通过 `axios` 发请求
- 后端返回 JSON 数据

例如：

- `POST /query`
- `POST /queryse`
- `GET /parse/{keyword}`

所以运行时经常是“两段式”的：

1. 先请求页面
2. 页面加载后再请求接口拿数据

---

## 9. 模块职责逐个讲解

这一节最重要，因为你以后加功能基本就是在这些地方动手。

### 9.1 `HelloController`：页面控制器

这个类的职责是：

- 把某个 URL 映射到某个 HTML 模板
- 少量页面跳转时也会顺便塞一点模板变量

它主要负责这些路由：

- `/` 和 `/index` -> `index.html`
- `/jdsearch` -> `jdsearch.html`
- `/contentse` -> `se.html`
- `/searchAn/{aid}` -> `answer.html`

也就是说，`HelloController` 更像“页面导航员”。

当它返回：

```java
return "jdsearch";
```

Spring 不会把这个字符串直接显示给浏览器，而是会去找：

```text
src/main/resources/templates/jdsearch.html
```

### 9.2 `ContentController`：接口控制器

这个类的职责是：

- 接收前端传来的参数
- 调用 `ContentService`
- 把结果直接返回给前端

因为它是：

```java
@RestController
```

所以它返回的不是模板页面，而是：

- `Boolean`
- `List<Map<String, Object>>`
- 也就是 JSON 风格的数据

它主要负责这些接口：

- `GET /parse/{keyword}`：抓京东并写入 ES
- `GET /search/{keyword}/{pageNo}/{pageSize}`：商品搜索接口
- `POST /query`：商品搜索接口，供页面调用
- `POST /queryse`：问答搜索接口
- `GET /writeQA`：把问答 JSON 数据写入 ES

### 9.3 `ContentService`：核心业务层

这是整个项目最核心的类。

它承担了真正的业务逻辑：

- 爬取京东页面
- 批量写入 Elasticsearch
- 执行商品搜索
- 执行问答搜索
- 根据问题查答案

你可以把控制器和服务层理解为：

- `Controller` 负责“接电话”
- `Service` 负责“真正干活”

### 9.4 `pojo`：数据模型

这些类本质上是“数据结构”。

#### `Content`

表示一个商品结果，字段有：

- `title`
- `img`
- `price`

#### `Question`

表示一个问题，字段有：

- `qid`
- `qzh`
- `qen`
- `qdomain`
- `qanswers`
- `qnegatives`

#### `Answer`

表示一个答案，字段有：

- `aid`
- `azh`
- `aen`

以后你如果要给结果页多展示一个字段，往往就要从这里开始考虑要不要扩字段。

### 9.5 `HtmlParseUtil`：抓取京东搜索页

职责：

- 用 `Jsoup` 请求京东搜索页面
- 解析 HTML
- 提取商品图片、价格、标题

这不是浏览器端代码，而是 Java 后端直接去访问网页并解析 HTML。

### 9.6 `JsonParseUtil`：读取本地 JSON 文件

职责：

- 从磁盘读 JSON 文件
- 反序列化为 `Question`、`Answer`、`Content` 的列表

这个工具主要服务于“离线导入数据到 ES”。

---

## 10. 运行主线一：仿京东商品搜索是怎么流动的

这是你最应该先理解的一条链路。

### 10.1 第一步：浏览器访问页面

你在浏览器打开：

```text
http://localhost:8080/jdsearch
```

### 10.2 第二步：进入 `HelloController`

Spring 根据 `@GetMapping("/jdsearch")` 找到对应方法，然后返回：

```java
return "jdsearch";
```

### 10.3 第三步：Spring 渲染模板

Spring 去 `templates` 目录找到：

- `jdsearch.html`

然后把这个 HTML 发给浏览器。

### 10.4 第四步：浏览器加载 CSS/JS/图片

页面里会继续加载：

- `css/style.css`
- `js/vue.min.js`
- `js/axios.min.js`
- `images/jdlogo.png`

这些资源由 `MyWebMvcConfig` 的静态资源映射负责提供。

### 10.5 第五步：用户输入关键词并点击搜索

在 `jdsearch.html` 里，输入框和按钮是由 Vue 管理的。

点击“搜索”时，会调用前端方法：

```javascript
searchPage()
```

里面通过 `axios` 发请求到：

```text
POST /query
```

提交参数：

- `keyword`
- `pageNo`
- `pageSize`

### 10.6 第六步：进入 `ContentController.query`

后端拿到请求后，会进入：

```java
public List<Map<String, Object>> query(String keyword, int pageNo, int pageSize)
```

这个方法本身不做搜索，只做一件事：

- 调 `contentService.searchPage(...)`

### 10.7 第七步：进入 `ContentService.searchPage`

这是商品搜索真正执行的地方。

里面做了这些事：

1. 处理分页参数。
2. 指定要查询的索引：

```java
new SearchRequest("jddata")
```

3. 构建查询条件：

```java
MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("title", keyword);
```

4. 调用 ES：

```java
client.search(searchRequest, RequestOptions.DEFAULT);
```

5. 把返回结果转成 `List<Map<String, Object>>`

### 10.8 第八步：结果返回前端

控制器把这个列表直接返回给浏览器，浏览器拿到 JSON 后执行：

```javascript
this.results = response.data;
```

### 10.9 第九步：Vue 重新渲染页面

模板里有：

```html
<div class="product" v-for="item in results">
```

所以一旦 `results` 被赋值，Vue 会自动重新渲染商品列表。

### 10.10 商品搜索主线图

```text
浏览器访问 /jdsearch
-> HelloController 返回模板 jdsearch
-> 浏览器加载 jdsearch.html
-> 用户点击搜索
-> axios POST /query
-> ContentController.query()
-> ContentService.searchPage()
-> Elasticsearch 索引 jddata
-> 返回 JSON
-> Vue 渲染 results
```

---

## 11. 运行主线二：保险问答检索是怎么流动的

这条链路和商品搜索很像，只是查的数据不一样。

### 11.1 搜索问题列表

浏览器访问：

```text
http://localhost:8080/contentse
```

流程如下：

```text
/contentse
-> HelloController 返回 se.html
-> 页面点击搜索
-> axios POST /queryse
-> ContentController.queryse()
-> ContentService.searchQA()
-> Elasticsearch 索引 insurance_question
-> 返回问题列表 JSON
-> Vue 渲染问题列表
```

### 11.2 点击某个问题查看答案

在 `se.html` 里，点击某条问题会跳转：

```text
/searchAn/{qid}
```

然后流程如下：

```text
/searchAn/{qid}
-> HelloController.parsese()
-> ContentService.searchAnswer(qid)
-> 先查 insurance_question
-> 从 qanswers 字段里拆出 aid
-> 再查 insurance_answer
-> 把问题和答案塞进 Model
-> 返回 answer.html
-> 浏览器显示问题与答案页面
```

这个流程说明了一个非常典型的 Spring MVC 模式：

- 页面跳转类请求返回模板
- 模板变量通过 `Model` 传给 HTML 页面

---

## 12. 抓取京东并写入 ES 的流程

接口：

```text
GET /parse/{keyword}
```

流程如下：

```text
/parse/{keyword}
-> ContentController.parse()
-> ContentService.parseContent(keyword)
-> HtmlParseUtil.parseJD(keyword)
-> Jsoup 请求京东搜索页
-> 解析 HTML 提取商品
-> 批量写入 Elasticsearch 索引 jddata
-> 返回 true/false
```

这里要注意：

- 这是后端抓取网页，不是浏览器抓
- 当前抓取结果会直接写入 `jddata`
- 商品页 `/jdsearch` 和接口 `/query` 也查询 `jddata`

所以现在这条链路已经闭合：

1. 你调用 `/parse/java` 抓到了数据。
2. 数据直接进入 `jddata`。
3. 页面 `/jdsearch` 也是去 `jddata` 查。
4. 只要 ES 可用且查询条件命中，页面就能看到刚抓到的数据。

历史上“抓到了但页面看不到”的问题，就出在这里曾经一边写 `jd_goods`、一边查 `jddata`。

---

## 13. 离线导入商品和问答数据的流程

### 13.1 商品数据导入

`EsJDDoc.writeJDdata()` 的作用是：

- 先从 JSON 文件读商品数据
- 再批量写入 ES 的 `jddata`

它读的是硬编码路径：

```text
./data/jddata.json
```

也就是仓库里的商品示例数据文件。

这意味着：

- 导入商品数据时默认读仓库内的 `data/jddata.json`
- 不依赖仓库外的额外商品 JSON 路径

### 13.2 问答数据导入

`ContentService.writeQAContent()` 现在从配置文件读取问答数据路径：

- `search.data.question-file-path`
- `search.data.answer-file-path`

默认值是：

- `./data/trainnew.json`
- `./data/answersnew.json`

然后写入：

- `insurance_question`
- `insurance_answer`

这说明问答导入逻辑是按作者自己电脑的路径写的，不是一个可移植的方案。

---

## 14. 为什么根路径 `/` 看起来不像完整页面

因为：

- `/` 返回的是 `templates/index.html`
- 这个模板只有顶部搜索区域
- 没有完整的结果区域和脚本逻辑

而真正可用的完整商品搜索页在：

- `templates/jdsearch.html`

另外项目里还存在：

- `src/main/resources/static/index.html`

这个静态版 `index.html` 比模板版完整一些，但由于根路径已经被 `HelloController` 映射到模板版页面，所以浏览器访问 `/` 时拿到的是模板版，不是静态版。

这也是一个容易让初学者困惑的点：

- 同名文件不一定真的会被访问到
- 最终访问哪个页面，要看“控制器路由”优先还是“静态资源映射”优先

在当前项目中，根路径是控制器接管的。

---

## 15. 你以后要加新功能，应该从哪里下手

这里按最常见的几类需求来讲。

### 15.1 新增一个页面

比如你要加一个“商品详情页”或“搜索统计页”。

通常做法：

1. 在 `templates/` 里新建一个 HTML 模板。
2. 在 `HelloController` 里新增一个 `@GetMapping`。
3. 返回模板名。

示例思路：

```java
@GetMapping("/detail")
public String detail() {
    return "detail";
}
```

### 15.2 新增一个 AJAX 接口

比如你想做“搜索联想词”。

通常做法：

1. 在 `ContentController` 里加接口。
2. 在 `ContentService` 里加对应业务方法。
3. 前端页面用 `axios` 调这个接口。

这类需求最适合沿用当前项目结构。

### 15.3 修改搜索逻辑

比如你想：

- 按价格排序
- 高亮关键词
- 支持分页
- 同时搜索标题和店铺

主要改：

- `ContentService.searchPage()`
- `ContentService.searchQA()`

因为查询条件、排序方式、分页方式都在这里构建。

### 15.4 新增数据字段

比如你要让商品多显示：

- 店铺名
- 评论数
- 链接地址

通常要同时改这几层：

1. `pojo/Content.java` 增字段
2. `HtmlParseUtil` 或 JSON 导入逻辑补采集/补解析
3. ES 里写入的数据带上新字段
4. 前端模板 `jdsearch.html` 里显示这个字段

也就是说，字段新增通常是“全链路改动”。

### 15.5 新增一个全新的业务模块

比如你想再加一个“新闻搜索”或“课程搜索”。

最合理的做法是沿用现有分层：

1. 新建新的 `POJO`
2. 新建新的 `Service` 方法
3. 新建新的 `Controller` 接口或页面路由
4. 新建新的模板页面
5. 新建 ES 索引或导入逻辑

不要把所有功能都继续塞进 `ContentService` 一个类里，否则后面会越来越难维护。

---

## 16. 当前代码里最重要的几个坑

这一节对你以后改功能很重要。

### 16.1 商品数据索引历史上不一致，现已统一

这部分原先分成两套：

- `parseContent()` 写 `jd_goods`
- `searchPage()` 查 `jddata`

现在已经统一成同一个索引：

- `parseContent()` 写 `jddata`
- `searchPage()` 查 `jddata`

如果你后面继续扩展商品搜索，应该继续复用同一个索引常量，不要重新引入第二个商品索引。

### 16.2 分页逻辑写法不标准

历史上这里的代码是：

```java
sourceBuilder.from(pageNo).size(pageSize);
```

这里 `from` 实际上表示“起始偏移量”，不是“页码”。

标准写法通常应该是：

```java
from = (pageNo - 1) * pageSize
```

现在已经改成：

```java
sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);
```

也就是说：

- `pageNo=1` 会从第 0 条开始
- `pageNo=2` 会从第 `pageSize` 条开始
- 分页语义已经与 Elasticsearch 的 `from/size` 保持一致

### 16.3 硬编码路径很多

历史上这里的问题主要是：

- 代码里直接写死数据文件路径
- 页面里直接写死 `localhost:8080`

现在已经收口为两种方式：

- 数据文件路径走 `application.properties`
- 页面请求走相对路径，如 `/query`、`/queryse`
- 环境变量

### 16.4 问答详情路由参数命名混乱

`/searchAn/{aid}` 这个名字看起来像传入的是答案 ID。

但实际上页面调用时传的是：

- `qid`

也就是问题 ID。

所以这段命名会误导阅读者，后面最好统一。

### 16.5 根页面和静态页面重复

项目里同时存在：

- `templates/index.html`
- `static/index.html`

这会增加理解成本，后面最好保留一个明确主入口。

### 16.6 `ContentService` 责任过重

当前它同时负责：

- 商品搜索
- 问答搜索
- 问答详情
- 京东抓取
- 数据写入

如果后面功能继续增长，建议拆成多个 service：

- `ProductSearchService`
- `QaSearchService`
- `ImportService`

---

## 17. 一个完整请求在代码里是怎么穿过去的

为了让你以后读代码更快，这里给你一个统一模板。

当你看到一个功能时，按下面顺序找：

### 17.1 先找页面入口

看浏览器访问哪个 URL。

例如：

- `/jdsearch`

### 17.2 再找哪个 Controller 接住它

找 `@GetMapping("/jdsearch")` 或 `@PostMapping(...)`

### 17.3 判断它返回的是页面还是 JSON

- `@Controller` + 返回字符串模板名 -> 页面
- `@RestController` + 返回对象/列表 -> JSON

### 17.4 再找它调用了哪个 Service

控制器通常只负责转发工作，不是真正的业务中心。

### 17.5 再看 Service 最终访问了哪里

可能是：

- Elasticsearch
- HTML 抓取工具
- 本地 JSON 文件

### 17.6 最后回到前端看结果怎么显示

对于模板页面：

- 看 `th:text`
- 看 `Model.addAttribute`

对于 Vue 页面：

- 看 `axios`
- 看 `data`
- 看 `v-for`
- 看 `v-model`

这个阅读顺序非常适合你之后自己排查问题。

---

## 18. 推荐你后续按这个顺序改造项目

如果你准备在这份代码上继续做功能，我建议先做下面几件事。

### 第一步：保持商品索引单一

当前商品索引已经统一为：

- `jddata`

后续新增能力时，也要继续让下面几条链路保持同一个索引：

- 抓取写入
- 搜索读取
- JSON 导入

### 第二步：把硬编码路径移到配置文件

例如放进 `application.properties`：

- ES 地址
- 数据文件路径
- 是否启用抓取

### 第三步：修正分页

把 `pageNo` 和 `from` 的语义改正确。

### 第四步：拆分 service

让商品、问答、导入逻辑各归各类。

### 第五步：明确主入口页面

建议只保留一个真正的首页入口，减少模板和静态页重复。

---

## 19. 你现在最应该记住的“全局地图”

如果你只想先记住最核心的部分，可以记这张图：

```text
启动应用
-> DemoApplication
-> Spring Boot 扫描 Controller / Service / Config
-> Tomcat 监听 8080

页面请求
-> HelloController
-> templates/*.html

接口请求
-> ContentController
-> ContentService
-> Elasticsearch / Jsoup / JSON 文件
-> 返回 JSON 或 Model

前端展示
-> Thymeleaf 或 Vue
```

再具体一点：

```text
/jdsearch
-> jdsearch.html
-> axios POST /query
-> ContentService.searchPage()
-> ES: jddata

/contentse
-> se.html
-> axios POST /queryse
-> ContentService.searchQA()
-> ES: insurance_question

/searchAn/{qid}
-> ContentService.searchAnswer()
-> ES: insurance_question + insurance_answer

/parse/{keyword}
-> HtmlParseUtil.parseJD()
-> ES: jddata
```

---

## 20. 结论

这个项目的本质不是“一个单纯前端页面”，而是一个典型的小型 Spring Boot 搜索项目：

- `HelloController` 管页面入口
- `ContentController` 管接口入口
- `ContentService` 管核心业务
- `HtmlParseUtil` 和 `JsonParseUtil` 负责数据来源
- `Elasticsearch` 是最终搜索的数据承载层
- `Thymeleaf + Vue + axios` 负责把数据显示给浏览器

如果你想在这个项目上加功能，最重要的不是先会写 Java 语法，而是先养成“按层找代码”的习惯：

1. 这个功能的入口 URL 是什么
2. 是哪个 Controller 接住
3. 调了哪个 Service
4. Service 最终访问了哪里
5. 返回结果后前端怎么渲染

一旦你掌握了这条链路，这个项目后面无论是加页面、加接口、改搜索逻辑、改数据结构，都会清楚很多。

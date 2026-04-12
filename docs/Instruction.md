# Search Engine CustomQA Part操作说明

```powershell
mvn clean spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

这份说明对应当前 `search-engine` 项目的自定义问答流程。主链路是：

1. 把 `data/example.json` 导入 Elasticsearch。
2. 打开问答搜索页。
3. 输入关键词检索。
4. 点击某条结果查看该问题下的全部答案。

## 1. 启动前准备

先确保 Elasticsearch 已经启动，并且 `application.properties` 里的地址可访问。

- ES 地址：`127.0.0.1:9200`
- 应用端口：`8081` 或你启动时指定的端口

如果你本地还没有 `custom_qa` 索引，不用手动建，导入接口会自动创建。

## 2. 启动项目

在 `search-engine` 目录下执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

启动成功后，终端会继续输出 Spring Boot 的启动日志，不会立刻退出。

## 3. 导入自定义问答数据

浏览器访问：

```text
GET /writeCustomQA
```

或者直接访问对应地址：

```text
http://localhost:8081/writeCustomQA
```

### 预期输出

- 成功时：返回 `true`
- 失败时：返回 `false`，或者终端打印 ES 连接/写入异常

这个接口会读取 `data/example.json`，把一个问题下的多个答案展开成多条 ES 文档，写入 `custom_qa` 索引。

## 4. 打开问答搜索页

浏览器访问：

```text
http://localhost:8081/contentse
```

### 页面表现

- 页面顶部会显示搜索框
- 输入关键词后，按回车或点“搜索”都会触发查询
- 搜索支持示例：
  - [Java AND 接口](#5-搜索接口输出)
  - [线程 OR 并发](#5-搜索接口输出)
  - [集合 NOT List](#5-搜索接口输出)

## 5. 搜索接口输出

前端实际调用的是：

```text
POST /queryse
```

请求参数：

- `keyword`
- `pageNo`
- `pageSize`

### 预期输出

接口返回 `JSON 数组`，每一项是一条答案候选记录，常见字段包括：

- `qid`
- `topic`
- `question`
- `answer`
- `answerQuality`

如果没有命中，会返回空数组 `[]`。

## 6. 点击结果看详情

在搜索结果页点击某一条问题后，会跳转到：

```text
/answer/{qid}
```

例如：

```text
http://localhost:8081/answer/java零基础到入门-q1
```

### 预期输出

- 返回一个 HTML 页面
- 页面顶部展示问题 ID、主题、问题文本
- 页面下方按 `answerQuality` 从高到低展示该问题的全部答案

## 7. 常见输出含义

### `true`

说明导入接口调用成功，ES 写入成功或至少没有明显失败。

### `[]`

说明这次搜索没有命中结果，或者当前 `custom_qa` 索引里还没有数据。

### 404 `no such index [custom_qa]`

说明 `custom_qa` 索引不存在，通常是因为还没有执行 `/writeCustomQA`。

### 页面点搜索没反应

通常是以下两个原因之一：

- ES 没启动
- `custom_qa` 还没导入数据

## 8. 推荐操作顺序

1. 启动 Elasticsearch。
2. 启动 `search-engine`。
3. 访问 `/writeCustomQA` 导入数据。
4. 访问 `/contentse` 开始搜索。
5. 点击结果进入 `/answer/{qid}` 看详情。

## 9. 备注

当前项目里保留了旧的 `/writeQA`、`/query`、`/answer/{qid}` 等接口风格，但现在更推荐使用自定义问答这条链路：

- `/writeCustomQA`
- `/contentse`
- `/queryse`
- `/answer/{qid}`

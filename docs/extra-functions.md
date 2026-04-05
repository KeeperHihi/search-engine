# 新增功能说明

本次改动遵循了现有项目在 `docs/guidance.md` 和 `docs/pipeline.md` 里描述的主链路：

- 前端仍然是 `Thymeleaf + Vue2 + axios`
- 后端仍然是 `Spring Boot + Elasticsearch`
- 商品搜索主流程仍然是 `jdsearch.html -> /query -> ContentService -> Elasticsearch`
- 没有改动当前京东风格页面的整体布局，只在现有区域内补轻量交互和展示信息

## 1. 新增了哪些功能

### 1.1 热词点击后可快速填充到搜索栏

页面顶部原本就有 `Java / Python / 机器问答 / 信息检索 / 人工智能` 这些提示词，但之前只是静态文本。

现在改成了：

- 点击任意提示词，会把该词写入搜索框
- 点击后会自动聚焦回输入框，便于继续编辑

这样用户可以直接拿热词作为搜索起点，不需要手动敲完整关键词。

### 1.2 在搜索栏按回车可以直接搜索

之前只有点击按钮才会触发搜索。

现在把搜索动作绑定到了表单提交：

- 点击按钮可以搜索
- 在输入框里按回车也会直接搜索

这更符合电商搜索页的常见交互习惯。

### 1.3 商品列表支持多种排序方式

原页面里已经有：

- 综合
- 人气
- 新品
- 销量
- 价格

这些排序项，但之前只是静态 UI，没有真实行为。

现在已经接通：

- `综合`：保持搜索结果原始相关性顺序
- `人气`：按模拟的人气分数倒序
- `新品`：按模拟的上新天数升序，越新越靠前
- `销量`：按模拟销量倒序
- `价格`：支持升序 / 降序切换

其中价格排序的交互是：

- 第一次点价格，按价格从低到高
- 再次点价格，切换为价格从高到低

### 1.4 用稳定随机数模拟了商品指标

为了让排序和商品状态展示更像真实电商页，我给每个商品补充了运行时模拟指标：

- 销量
- 人气分
- 评论数
- 好评率
- 上新天数

这里不是每次完全真随机，而是“稳定随机”：

- 同一商品会根据自己的 `sku / itemUrl / title / price` 组合生成稳定种子
- 同一个商品多次搜索时，这些指标保持一致
- 看起来像随机分布，但不会出现一次搜索 3000 销量、下一次又变成 9000 的跳变问题

这样更适合做 demo，也更符合真实商品页的观感。

### 1.5 增加了几个轻量的京东式小巧思

在不改布局的前提下，我补了几处很轻的增强：

- 商品卡片增加了 `自营 / 次日达 / 新品 / 热销 / 高人气` 等标签
- 店铺行增加了配送与好评率文案
- 搜索后会显示“找到了多少个结果、当前按什么规则排序”
- 无结果时会给出简单提示
- 搜索按钮在请求期间会显示 `搜索中...`

这些都属于“应该有，但不复杂”的体验补足。

## 2. 每个功能分别是如何实现的

### 2.1 前端搜索交互

实现位置：

- `src/main/resources/templates/jdsearch.html`

实现方式：

- 给搜索表单增加 `@submit.prevent="searchPage"`，统一接管点击搜索和回车搜索
- 把热词改成 `v-for` 渲染，并绑定 `fillKeyword(hotKeyword)`
- 在 Vue `data` 中增加：
  - `hotKeywords`
  - `sortBy`
  - `priceOrder`
  - `loading`
  - `hasSearched`
  - `searchedKeyword`
- 在 Vue `computed` 中增加：
  - `currentSortLabel`
  - `showEmptyState`
- 在 Vue `methods` 中增加：
  - `fillKeyword`
  - `changeSort`
  - `togglePriceSort`
  - `focusKeywordInput`

### 2.2 前端请求参数扩展

原来的请求参数只有：

- `keyword`
- `pageNo`
- `pageSize`

现在新增：

- `sortBy`
- `priceOrder`

也就是说，前端会把当前排序状态一并发给后端，后端再统一处理。

### 2.3 控制器接收排序参数

实现位置：

- `src/main/java/com/example/demo/controller/ContentController.java`

实现方式：

- 给 `/query` 接口新增两个参数：
  - `sortBy`
  - `priceOrder`
- 设定默认值，避免前端没传时出错
- 再把这两个参数继续传入 `ContentService.searchPage(...)`

这样控制器仍然只负责“收参数并转发”，职责没有变重。

### 2.4 服务层统一补模拟指标、排序并分页

实现位置：

- `src/main/java/com/example/demo/service/ContentService.java`

实现方式：

1. 先对 `keyword` 做 `trim`
2. 空关键词直接返回空列表，避免无意义查询
3. 查询 ES 时，不再只取严格一页，而是适度放大召回窗口
4. 对 ES 返回结果做去重和价格格式归一化
5. 给每个商品补充模拟指标
6. 按前端要求的排序方式排序
7. 最后再做内存分页返回

之所以把排序放在 ES 结果之后做，是因为：

- 销量、人气、好评率这些字段是本次运行时模拟出来的
- 它们不是现有 ES 索引中的真实排序字段
- 所以更合适的做法是在服务层统一补字段并排序

### 2.5 把模拟指标和排序规则抽成独立工具类

实现位置：

- `src/main/java/com/example/demo/utils/GoodsDisplayUtil.java`

这个类主要做两件事：

1. `enrichGoods`

职责：

- 为商品补销量、人气、评论数、好评率、上新天数
- 生成店铺名称、配送文案
- 生成标签列表

2. `sortGoods`

职责：

- 根据 `sortBy` 和 `priceOrder` 对商品列表排序
- 支持综合、人气、新品、销量、价格

这样做的好处是：

- `ContentService` 不需要塞太多展示细节
- 后续如果要新增“评论数排序”“好评率排序”，可以直接继续扩展这个工具类
- 也更容易单测

### 2.6 新增单元测试

实现位置：

- `src/test/java/com/example/demo/utils/GoodsDisplayUtilTest.java`

当前覆盖了三类验证：

- 同一商品多次生成指标时结果稳定
- 销量排序是否正确
- 价格降序排序是否正确

这和已有的 `PriceTextUtilTest` 一起构成了这次改动的基础回归验证。

## 3. 代码的流动方向

用户操作到页面渲染的完整链路如下：

1. 用户点击热词、按回车、点击搜索按钮，或者点击排序条
2. `jdsearch.html` 里的 Vue 方法被触发
3. Vue 调用 `axios` 发起 `POST /query`
4. 请求携带：
   - `keyword`
   - `pageNo`
   - `pageSize`
   - `sortBy`
   - `priceOrder`
5. `ContentController.query(...)` 接收参数
6. `ContentController` 调用 `ContentService.searchPage(...)`
7. `ContentService` 向 Elasticsearch 查询商品标题
8. 查询结果先经过：
   - 去重
   - 价格归一化
   - 模拟指标补充
   - 排序
   - 分页
9. 最终返回商品 JSON 数组给前端
10. Vue 把结果赋值给 `results`
11. 页面渲染：
    - 商品价格
    - 商品标题
    - 标签
    - 店铺与配送文案
    - 销量 / 人气
    - 搜索结果摘要

可以把它概括为：

`用户交互 -> Vue 状态更新 -> axios 请求 -> Controller 收参 -> Service 查询 ES -> GoodsDisplayUtil 补展示数据并排序 -> 返回前端 -> 商品卡片渲染`

## 4. 本次涉及的主要文件

- `src/main/resources/templates/jdsearch.html`
- `src/main/resources/static/css/style.css`
- `src/main/java/com/example/demo/controller/ContentController.java`
- `src/main/java/com/example/demo/service/ContentService.java`
- `src/main/java/com/example/demo/utils/GoodsDisplayUtil.java`
- `src/test/java/com/example/demo/utils/GoodsDisplayUtilTest.java`

## 5. 验证结果

已执行：

- `mvn test`

结果：

- `BUILD SUCCESS`
- 共 7 个测试全部通过

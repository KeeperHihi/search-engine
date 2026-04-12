# 京东搜索额外功能说明

本次补充的是京东搜索页搜索框附近的两类交互增强，目标是让推荐搜索词真正可用，而不只是静态提示。

## 1. 新增能力

### 1.1 搜索框内部增加滚动推荐词

实现位置：

- `src/main/resources/templates/jdsearch.html`
- `src/main/resources/static/css/style.css`

当前页面会把推荐搜索词放到搜索框内部作为动态提示层，并固定显示前缀 `推荐搜索：`，后面的热词循环展示现有这组内容：

- `Java`
- `Python`
- `机器问答`
- `信息检索`
- `人工智能`

前端通过 `setInterval` 定时切换当前推荐词，并在输入框内部用向上翻动过渡切换推荐词。

### 1.2 空搜索时自动使用当前推荐词

之前如果用户没有输入任何内容就点击搜索，前端会直接把空串传给 `/query`，而服务层会返回空结果。

现在改成：

1. 用户点击搜索按钮或按回车时，先在前端判断输入框是否为空。
2. 如果为空，就回退到当前正在搜索框内部展示的推荐词。
3. 再把这个推荐词写回输入框，并发起真实搜索。

这样用户即使不输入，也能直接进入一次有效搜索。

### 1.3 搜索框下方热词改为点击即搜

之前搜索框下方的推荐词只有“填充输入框”效果，用户还得再点一次搜索。

现在改成：

1. 鼠标移到推荐词上会显示手型光标。
2. 点击推荐词后，会立刻把该词写入搜索框。
3. 同一个点击动作会直接触发搜索请求。

这个行为比原来少一步，更符合电商搜索页里的热词入口习惯。

## 2. 具体实现方式

### 2.1 前端状态

在 `jdsearch.html` 的 Vue `data` 里新增了：

- `activeHotKeywordIndex`
- `rotationTimer`

并新增了两个计算属性：

- `activeHotKeyword`
- `showSearchSuggestion`
- `currentSearchPlaceholder`

其中：

- `activeHotKeyword` 表示当前正在轮播的推荐词
- `showSearchSuggestion` 用来控制输入框内部推荐提示层何时显示
- `currentSearchPlaceholder` 只在没有推荐提示层时回退为普通占位语

### 2.2 搜索触发逻辑

`searchPage(...)` 现在不再只依赖 `this.keyword`，而是先调用 `resolveSearchKeyword(...)`：

1. 有用户输入时，优先使用用户输入。
2. 没有输入时，回退到 `activeHotKeyword`。
3. 如果最终仍为空，才不发请求。

这样可以保证：

- 搜索按钮
- 回车提交
- 点击底部热词

这三种入口最终都走同一套搜索流程。

### 2.3 样式调整

样式层面新增了：

- 搜索框内部推荐提示层
- 推荐词向上翻动动画
- 热词可点击光标样式

特别是：

- `.relKeyTop li a` 现在显式设置了 `cursor: pointer`
- 点击态入口统一使用 `searchByHotKeyword(...)`

这样可以避免浏览器把没有 `href` 的 `<a>` 当成普通文本处理，导致手型光标不出现。

## 3. 这次改动没有影响的部分

本次没有修改后端接口签名：

- `/query`
- `ContentController`
- `ContentService`

原因很简单：

- 空关键词回退推荐词属于前端交互语义
- 直接在页面层处理，影响范围最小
- 不会改动现有商品检索链路

因此京东搜索主流程仍然是：

`jdsearch.html -> /query -> ContentService -> Elasticsearch`

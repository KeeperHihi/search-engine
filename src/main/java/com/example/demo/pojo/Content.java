package com.example.demo.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Content {
    private String title;
    private String img;
    private String price;

    // 京东商品的稳定标识，优先用于去重与幂等写入。
    private String sku;

    // 商品详情链接，供去重和后续扩展详情页时使用。
    private String itemUrl;
}

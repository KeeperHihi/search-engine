package com.example.demo.utils;

import com.example.demo.pojo.Content;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class HtmlParseUtil {

    public List<Content> parseJD(String keywords) throws IOException {
        String url = "https://search.jd.com/Search?keyword=" + keywords + "&enc=utf-8";
        Document document = Jsoup.parse(new URL(url), 30000);

        // 京东结果列表不存在时直接返回空集合，避免空指针把整个导入流程打断。
        Element goodsListElement = document.getElementById("J_goodsList");
        List<Content> goods = new ArrayList<Content>();
        if (goodsListElement == null) {
            return goods;
        }

        Elements elements = goodsListElement.getElementsByTag("li");
        for (Element element : elements) {
            if (!"gl-item".equalsIgnoreCase(element.attr("class"))) {
                continue;
            }

            Element titleElement = element.getElementsByClass("p-name").first();
            if (titleElement == null) {
                continue;
            }

            Content content = new Content();
            content.setSku(element.attr("data-sku"));
            content.setImg(resolveImage(element));
            content.setPrice(resolvePrice(element));
            content.setTitle(titleElement.text());
            content.setItemUrl(titleElement.getElementsByTag("a").eq(0).attr("href"));
            goods.add(content);
        }
        return goods;
    }

    private String resolveImage(Element element) {
        Elements imageElements = element.getElementsByTag("img");
        if (imageElements.isEmpty()) {
            return "";
        }

        Element imageElement = imageElements.get(0);
        String lazyImage = imageElement.attr("data-lazy-img");
        return lazyImage.isEmpty() ? imageElement.attr("src") : lazyImage;
    }

    private String resolvePrice(Element element) {
        Element priceElement = element.getElementsByClass("p-price").first();
        if (priceElement == null) {
            return "";
        }

        return PriceTextUtil.normalizeDisplayPrice(priceElement.text());
    }
}

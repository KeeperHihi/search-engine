package com.example.demo.utils;

import com.example.demo.pojo.Content;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Component


public class HtmlParseUtil {
    // public static void main(String[] args) throws IOException {
    //     new HtmlParseUtil().parseJD("python").forEach(System.out::println);
    // }

    public List<Content> parseJD(String keywords) throws IOException {
        // 获取请求 https://search.jd.com/Search?keyword=java&enc=utf-8
        // 前提需要连网
        // keywords="人工智能";
        String url = "https://search.jd.com/Search?keyword=" + keywords + "&enc=utf-8";
        System.out.println(url);

        // 解析网页 (Jsoup返回Document就是浏览器Document对象)
        Document document = Jsoup.parse(new URL(url), 30000);

        System.out.println("----------------");
        // 所有在js中能使用的方法,这里都能用
        Element element = document.getElementById("J_goodsList");
        // 获取所有li元素
        Elements elements = element.getElementsByTag("li");

        ArrayList<Content> goodList = new ArrayList<>();

        // 通过元素中的内容,这里el就是每一个li标签了
        for (Element el : elements) {
            // 加if判断是为了 过滤空标签
            if (el.attr("class").equalsIgnoreCase("gl-item")) {
                // 关于这种图片特别多的网页,所有的图片都是延迟加载的
                // 在jd搜索后f12可以看到存放在data-lazy-img中
                String sku = el.attr("data-sku");
                String img = el.getElementsByTag("img").eq(0).attr("data-lazy-img");
                String price = el.getElementsByClass("p-price").eq(0).text();
                Element titleElement = el.getElementsByClass("p-name").first();
                String title = titleElement.text();
                String itemUrl = titleElement.getElementsByTag("a").eq(0).attr("href");

                System.out.println(img+price+title);
                Content content = new Content();
                content.setImg(img);
                content.setPrice(price);
                content.setSku(sku);
                content.setItemUrl(itemUrl);
                content.setTitle(title);
                goodList.add(content);
            }

        }
        return goodList;
    }
}   

// public class HtmlParseUtil {
//    // 测试数据
//    public static void main(String[] args) throws IOException, InterruptedException {
//        // 获取请求
//        String url = "https://search.jd.com/Search?keyword=python";
//        //  解析网页 （Jsou返回的Document就是浏览器的Docuement对象）
//        Document document = Jsoup.parse(new URL(url), 30000);
//        // 获取id，所有在js里面使用的方法在这里都可以使用
//        Element element = document.getElementById("J_goodsList");
//        // 获取所有的li元素
//        Elements elements = element.getElementsByTag("li");
//        // 用来计数
//        int c = 0;
//        // 获取元素中的内容  ，这里的el就是每一个li标签
//        for (Element el : elements) {
//            c++;
//            // 这里有一点要注意，直接attr使用src是爬不出来的，因为京东使用了img懒加载
//            String img = el.getElementsByTag("img").eq(0).attr("data-lazy-img");
//            // 获取商品的价格，并且只获取第一个text文本内容
//            String price = el.getElementsByClass("p-price").eq(0).text();
//            String title = el.getElementsByClass("p-name").eq(0).text();
//            String shopName = el.getElementsByClass("p-shop").eq(0).text();

//            System.out.println("========================================");
//            System.out.println(img);
//            System.out.println(price);
//            System.out.println(title);
//            System.out.println(shopName);
//        }
//        System.out.println(c);
//    }
// }

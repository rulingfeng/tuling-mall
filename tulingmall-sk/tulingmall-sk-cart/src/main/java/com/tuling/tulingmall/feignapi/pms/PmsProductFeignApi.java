package com.tuling.tulingmall.feignapi.pms;

import com.tuling.tulingmall.common.api.CommonResult;
import com.tuling.tulingmall.domain.CartPromotionItem;
import com.tuling.tulingmall.domain.PmsProductParam;
import com.tuling.tulingmall.domain.PromotionProduct;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
* @desc: 类的描述:调用商品服务接口
*/
@FeignClient(name = "tulingmall-product")
public interface PmsProductFeignApi {

//    @RequestMapping(value = "/pms/getPromotionProductList", method = RequestMethod.GET)
//    CommonResult<List<PromotionProduct>> getPromotionProductList(@RequestParam("productIds") List<Long> ids);
//
//    @RequestMapping("/stock/lockStock")
//    CommonResult lockStock(@RequestBody List<CartPromotionItem> cartPromotionItemList);
//
//    @RequestMapping(value = "/pms/productInfo/{id}", method = RequestMethod.GET)
//    @ResponseBody
//    CommonResult<PmsProductParam> getProductInfo(@PathVariable("id") Long id);
//
//    @RequestMapping(value = "/stock/selectStock", method = RequestMethod.GET)
//    @ResponseBody
//    CommonResult<Integer> selectStock(@RequestParam("productId") Long productId,
//                                             @RequestParam(value = "flashPromotionRelationId") Long flashPromotionRelationId);
}

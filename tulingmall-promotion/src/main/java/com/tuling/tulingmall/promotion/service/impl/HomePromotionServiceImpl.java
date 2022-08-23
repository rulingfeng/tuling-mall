package com.tuling.tulingmall.promotion.service.impl;

import com.github.pagehelper.PageHelper;
import com.tuling.tulingmall.model.PmsBrand;
import com.tuling.tulingmall.model.PmsProduct;
import com.tuling.tulingmall.promotion.clientapi.PmsProductClientApi;
import com.tuling.tulingmall.promotion.config.PromotionRedisKey;
import com.tuling.tulingmall.promotion.dao.FlashPromotionProductDao;
import com.tuling.tulingmall.promotion.domain.FlashPromotionParam;
import com.tuling.tulingmall.promotion.domain.FlashPromotionProduct;
import com.tuling.tulingmall.promotion.domain.HomeContentResult;
import com.tuling.tulingmall.promotion.mapper.*;
import com.tuling.tulingmall.promotion.model.*;
import com.tuling.tulingmall.promotion.service.HomePromotionService;
import com.tuling.tulingmall.rediscomm.util.RedisDistrLock;
import com.tuling.tulingmall.rediscomm.util.RedisOpsUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 首页促销内容Service实现类
 */
@Slf4j
@Service
public class HomePromotionServiceImpl implements HomePromotionService {
    @Autowired
    private SmsHomeAdvertiseMapper advertiseMapper;
    @Autowired
    private SmsHomeBrandMapper smsHomeBrandMapper;
    @Autowired
    private SmsHomeNewProductMapper smsHomeNewProductMapper;
    @Autowired
    private SmsHomeRecommendProductMapper smsHomeRecommendProductMapper;
    @Autowired
    private PmsProductClientApi pmsProductClientApi;
    @Autowired
    private FlashPromotionProductDao flashPromotionProductDao;
    @Autowired
    private PromotionRedisKey promotionRedisKey;
    @Autowired
    private RedisOpsUtil redisOpsUtil;
    @Autowired
    private RedisDistrLock redisDistrLock;

    @Value("${secKillServerList}")
    private List<String> secKillServerList;

    @Autowired
    private SmsFlashPromotionMapper smsFlashPromotionMapper;

    @Override
    public HomeContentResult content(int getType) {
        HomeContentResult result = new HomeContentResult();
        if(ConstantPromotion.HOME_GET_TYPE_ALL == getType
                ||ConstantPromotion.HOME_GET_TYPE_BARND == getType){
            //获取推荐品牌
            getRecommendBrand(result);
        }
        if(ConstantPromotion.HOME_GET_TYPE_ALL == getType
                ||ConstantPromotion.HOME_GET_TYPE_NEW == getType){
            getRecommendProducts(result);
        }
        if(ConstantPromotion.HOME_GET_TYPE_ALL == getType
                ||ConstantPromotion.HOME_GET_TYPE_HOT == getType){
            getHotProducts(result);
        }
        if(ConstantPromotion.HOME_GET_TYPE_ALL == getType
                ||ConstantPromotion.HOME_GET_TYPE_AD == getType){
            //获取首页广告
            result.setAdvertiseList(getHomeAdvertiseList());
        }
        return result;
    }

    /*获取秒杀内容*/
    @Override
    public List<FlashPromotionProduct> secKillContent(long secKillId) {
        PageHelper.startPage(1, 8);
        Long secKillIdL = -1 == secKillId ? null : secKillId;
        /*获得秒杀相关的活动信息*/
        FlashPromotionParam flashPromotionParam = flashPromotionProductDao.getFlashPromotion(secKillIdL);
        if (flashPromotionParam == null || CollectionUtils.isEmpty(flashPromotionParam.getRelation())) {
            return null;
        }
        /*获得秒杀相关的商品信息,map用来快速寻找秒杀商品的限购信息*/
        List<Long> productIds = new ArrayList<>();
        Map<Long,SmsFlashPromotionProductRelation> temp = new HashMap<>();
        flashPromotionParam.getRelation().stream().forEach(item -> {
            productIds.add(item.getProductId());
            temp.put(item.getProductId(),item);
        });
        PageHelper.clearPage();
        List<PmsProduct> secKillProducts = pmsProductClientApi.getProductBatch(productIds);
        /*拼接前端需要的内容*/
        List<FlashPromotionProduct> flashPromotionProducts = new ArrayList<>();
        int loop = 0;
        int serverSize = secKillServerList.size();
        for(PmsProduct product : secKillProducts){
            FlashPromotionProduct flashPromotionProduct = new FlashPromotionProduct();
            BeanUtils.copyProperties(product,flashPromotionProduct);
            flashPromotionProduct.setFlashPromotionCount(temp.get(product.getId()).getFlashPromotionCount());
            flashPromotionProduct.setFlashPromotionPrice(temp.get(product.getId()).getFlashPromotionPrice());
            flashPromotionProduct.setFlashPromotionLimit(temp.get(product.getId()).getFlashPromotionLimit());
            flashPromotionProduct.setRelationId(temp.get(product.getId()).getId());
            flashPromotionProduct.setFlashPromotionId(temp.get(product.getId()).getFlashPromotionId());
            flashPromotionProduct.setSecKillServer(secKillServerList.get(loop % serverSize));
            loop++;
        }
        return flashPromotionProducts;
    }

    @Override
    public int turnOnSecKill(long secKillId) {
        SmsFlashPromotion smsFlashPromotion = new SmsFlashPromotion();
        smsFlashPromotion.setId(secKillId);
        smsFlashPromotion.setStatus(1);
        return smsFlashPromotionMapper.updateByPrimaryKeySelective(smsFlashPromotion);
    }

    /*获取推荐品牌*/
    private void getRecommendBrand(HomeContentResult result){
        final String brandKey = promotionRedisKey.getBrandKey();
        List<PmsBrand> recommendBrandList = redisOpsUtil.getListAll(brandKey, PmsBrand.class);
        if(CollectionUtils.isEmpty(recommendBrandList)){
            redisDistrLock.lock(promotionRedisKey.getDlBrandKey(),promotionRedisKey.getDlTimeout());
            try {
                PageHelper.startPage(0,ConstantPromotion.HOME_RECOMMEND_PAGESIZE,"sort desc");
                SmsHomeBrandExample example = new SmsHomeBrandExample();
                example.or().andRecommendStatusEqualTo(ConstantPromotion.HOME_PRODUCT_RECOMMEND_YES);
                List<Long> smsHomeBrandIds = smsHomeBrandMapper.selectBrandIdByExample(example);
//                pmsProductFeignApi.getHomeSecKillProductList();
//                log.info("---------------------------");
                recommendBrandList = pmsProductClientApi.getRecommandBrandList(smsHomeBrandIds);
                redisOpsUtil.putListAllRight(brandKey,recommendBrandList);
            } finally {
                redisDistrLock.unlock(promotionRedisKey.getDlBrandKey());
            }
            result.setBrandList(recommendBrandList);
            log.info("品牌推荐信息存入缓存，键{}" ,brandKey);
        }else{
            log.info("品牌推荐信息已在缓存，键{}" ,brandKey);
            result.setBrandList(recommendBrandList);
        }
    }

    /*获取人气推荐产品*/
    private void getRecommendProducts(HomeContentResult result){
        final String recProductKey = promotionRedisKey.getRecProductKey();
        List<PmsProduct> recommendProducts = redisOpsUtil.getListAll(recProductKey, PmsProduct.class);
        if(CollectionUtils.isEmpty(recommendProducts)){
            redisDistrLock.lock(promotionRedisKey.getDlRecProductKey(),promotionRedisKey.getDlTimeout());
            try {
                PageHelper.startPage(0,ConstantPromotion.HOME_RECOMMEND_PAGESIZE,"sort desc");
                SmsHomeRecommendProductExample example2 = new SmsHomeRecommendProductExample();
                example2.or().andRecommendStatusEqualTo(ConstantPromotion.HOME_PRODUCT_RECOMMEND_YES);
                List<Long> recommendProductIds = smsHomeRecommendProductMapper.selectProductIdByExample(example2);
                recommendProducts = pmsProductClientApi.getProductBatch(recommendProductIds);
                redisOpsUtil.putListAllRight(recProductKey,recommendProducts);
            } finally {
                redisDistrLock.unlock(promotionRedisKey.getDlRecProductKey());
            }
            log.info("人气推荐商品信息存入缓存，键{}" ,recProductKey);
            result.setHotProductList(recommendProducts);
        }else{
            log.info("人气推荐商品信息已在缓存，键{}" ,recProductKey);
            result.setHotProductList(recommendProducts);
        }
    }

    /*获取新品推荐产品*/
    private void getHotProducts(HomeContentResult result){
        final String newProductKey = promotionRedisKey.getNewProductKey();
        List<PmsProduct> newProducts = redisOpsUtil.getListAll(newProductKey, PmsProduct.class);
        if(CollectionUtils.isEmpty(newProducts)){
            redisDistrLock.lock(promotionRedisKey.getDlNewProductKey(),promotionRedisKey.getDlTimeout());
            try {
                PageHelper.startPage(0,ConstantPromotion.HOME_RECOMMEND_PAGESIZE,"sort desc");
                SmsHomeNewProductExample example = new SmsHomeNewProductExample();
                example.or().andRecommendStatusEqualTo(ConstantPromotion.HOME_PRODUCT_RECOMMEND_YES);
                List<Long> newProductIds = smsHomeNewProductMapper.selectProductIdByExample(example);
                newProducts = pmsProductClientApi.getProductBatch(newProductIds);
                redisOpsUtil.putListAllRight(newProductKey,newProducts);
            } finally {
                redisDistrLock.unlock(promotionRedisKey.getDlNewProductKey());
            }
            log.info("新品推荐信息存入缓存，键{}" ,newProductKey);
            result.setNewProductList(newProducts);
        }else{
            log.info("新品推荐信息已在缓存，键{}" ,newProductKey);
            result.setNewProductList(newProducts);
        }
    }

    /*获取轮播广告*/
    private List<SmsHomeAdvertise> getHomeAdvertiseList() {
        final String homeAdvertiseKey = promotionRedisKey.getHomeAdvertiseKey();
        List<SmsHomeAdvertise> smsHomeAdvertises =
                redisOpsUtil.getListAll(homeAdvertiseKey, SmsHomeAdvertise.class);
        if(CollectionUtils.isEmpty(smsHomeAdvertises)){
            redisDistrLock.lock(promotionRedisKey.getDlHomeAdvertiseKey(),promotionRedisKey.getDlTimeout());
            try {
                SmsHomeAdvertiseExample example = new SmsHomeAdvertiseExample();
                Date now = new Date();
                example.createCriteria().andTypeEqualTo(ConstantPromotion.HOME_ADVERTISE_TYPE_APP)
                        .andStatusEqualTo(ConstantPromotion.HOME_ADVERTISE_STATUS_ONLINE)
                        .andStartTimeLessThan(now).andEndTimeGreaterThan(now);
                example.setOrderByClause("sort desc");
                smsHomeAdvertises = advertiseMapper.selectByExample(example);
                redisOpsUtil.putListAllRight(homeAdvertiseKey,smsHomeAdvertises);
            } finally {
                redisDistrLock.unlock(promotionRedisKey.getDlHomeAdvertiseKey());
            }
            log.info("轮播广告存入缓存，键{}" ,homeAdvertiseKey);
            return smsHomeAdvertises;
        }else{
            log.info("轮播广告已在缓存，键{}" ,homeAdvertiseKey);
            return smsHomeAdvertises;
        }
    }
}

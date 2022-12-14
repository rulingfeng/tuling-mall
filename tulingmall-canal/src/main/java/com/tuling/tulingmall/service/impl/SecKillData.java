package com.tuling.tulingmall.service.impl;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.tuling.tulingmall.common.constant.RedisKeyPrefixConst;
import com.tuling.tulingmall.config.PromotionRedisKey;
import com.tuling.tulingmall.feignapi.promotion.PromotionFeignApi;
import com.tuling.tulingmall.promotion.domain.FlashPromotionProduct;
import com.tuling.tulingmall.rediscomm.util.RedisClusterUtil;
import com.tuling.tulingmall.rediscomm.util.RedisSingleUtil;
import com.tuling.tulingmall.service.IProcessCanalData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SecKillData implements IProcessCanalData {

    private final static String SECKILL_STATUS = "status";
    private final static String SECKILL_ID = "id";
    private final static int ON_SECKILL_STATUS = 1;
    private final static int OFF_SECKILL_STATUS = 0;

    @Autowired
    @Qualifier("secKillConnector")
    private CanalConnector connector;

    @Autowired
    private PromotionRedisKey promotionRedisKey;

    @Autowired
    private RedisClusterUtil homeRedisOpsExtUtil;

    @Autowired
    private RedisSingleUtil secKillStockUtil;

    @Value("${canal.seckill.subscribe:server}")
    private String subscribe;

    @Autowired
    private PromotionFeignApi promotionFeignApi;

    @Value("${canal.promotion.batchSize}")
    private int batchSize;



    @PostConstruct
    @Override
    public void connect() {
        connector.connect();
        if("server".equals(subscribe))
            connector.subscribe(null);
        else
            connector.subscribe(subscribe);
        connector.rollback();
    }

    @PreDestroy
    @Override
    public void disConnect() {
        connector.disconnect();
    }

    @Async
    @Scheduled(initialDelayString="${canal.seckill.initialDelay:5000}",fixedDelayString = "${canal.seckill.fixedDelay:5000}")
    @Override
    public void processData() {
        try {
            if(!connector.checkValid()){
                log.warn("???Canal???????????????????????????????????????????????????????????????????????????");
                this.connect();
            }else{
                Message message = connector.getWithoutAck(batchSize);
                long batchId = message.getId();
                int size = message.getEntries().size();
                if (batchId == -1 || size == 0) {
                    log.info("??????[{}]????????????????????????????????????",batchId);
                }else{
                    log.info("??????[{}]????????????????????????[{}]?????????????????????",batchId,size);
                    for(CanalEntry.Entry entry : message.getEntries()){
                        if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN
                                || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                            continue;
                        }
                        CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                        String tableName = entry.getHeader().getTableName();
                        CanalEntry.EventType eventType = rowChange.getEventType();
                        if(log.isDebugEnabled()){
                            log.debug("???????????????????????????binglog[{}.{}]????????????{}.{}???????????????{}",
                                    entry.getHeader().getLogfileName(),entry.getHeader().getLogfileOffset(),
                                    entry.getHeader().getSchemaName(),tableName,eventType);
                        }
                        for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                            List<CanalEntry.Column> columns = rowData.getAfterColumnsList();
                            long secKillId = -1L;
                            int secKillStatus = -1;
                            if (eventType == CanalEntry.EventType.DELETE) {/*?????????????????????*/
                                for (CanalEntry.Column column : columns) {
                                    if(column.getName().equals(SECKILL_ID)) {
                                        secKillId = Long.valueOf(column.getValue());
                                        break;
                                    }
                                }
                                secKillOffRedis(promotionFeignApi.getHomeSecKillProductList(secKillId,STATUS_OFF).getData());
                            } else if (eventType == CanalEntry.EventType.INSERT) { /*??????????????????*/
                                for (CanalEntry.Column column : columns) {
                                    if(column.getName().equals(SECKILL_STATUS)) {
                                        secKillStatus = Integer.valueOf(column.getValue());
                                    }
                                    if(column.getName().equals(SECKILL_ID)) {
                                        secKillId = Long.valueOf(column.getValue());
                                    }
                                }
                                /*??????????????????*/
                                if(ON_SECKILL_STATUS == secKillStatus){
                                    secKillOnRedis(secKillId);
                                }
                            } else {/*??????????????????*/
                                for (CanalEntry.Column column : columns) {
                                    if(column.getName().equals(SECKILL_STATUS)) {
                                        secKillStatus = Integer.valueOf(column.getValue());
                                    }
                                    if(column.getName().equals(SECKILL_ID)) {
                                        secKillId = Long.valueOf(column.getValue());
                                    }
                                }
                                /*??????????????????*/
                                if(ON_SECKILL_STATUS == secKillStatus){
                                    secKillOnRedis(secKillId);
                                }else{/*??????????????????*/
                                    secKillOffRedis(promotionFeignApi.getHomeSecKillProductList(secKillId,STATUS_OFF).getData());
                                }
                            }
                        }
                    }
                    connector.ack(batchId); // ????????????
                    log.info("??????[{}]????????????Canal??????????????????",batchId);
                }
            }
        } catch (Exception e) {
            log.error("????????????Canal?????????????????????????????????",e);
        }

    }

    private static final int STATUS_ON = 1;
    private static final int STATUS_OFF = 0;

    /* PO ??????????????????pipeline??????*/
    private void secKillOnRedis(long secKillId){
        List<FlashPromotionProduct> result =
                promotionFeignApi.getHomeSecKillProductList(secKillId,STATUS_ON).getData();
        if(CollectionUtils.isEmpty(result)){
            log.warn("?????????????????????????????????????????????????????????????????????");
            return;
        }
        final String homeSecKillKey = promotionRedisKey.getSecKillKey();
        homeRedisOpsExtUtil.delete(homeSecKillKey);
        long homeShowDuration = result.get(0).getFlashPromotionEndDate().getTime() - System.currentTimeMillis();
        if(homeShowDuration > 0){
            /*??????????????????*/
            homeRedisOpsExtUtil.putListAllRight(homeSecKillKey,result);
            homeRedisOpsExtUtil.expire(homeSecKillKey,homeShowDuration, TimeUnit.MILLISECONDS);
        }
        /*??????????????????*/
        for(FlashPromotionProduct product : result){
            String productKey = RedisKeyPrefixConst.SECKILL_PRODUCT_PREFIX + product.getFlashPromotionId()
                    + ":" + product.getId();
            String productCountKey = RedisKeyPrefixConst.MIAOSHA_STOCK_CACHE_PREFIX + product.getId();
            secKillStockUtil.delete(productKey);
            secKillStockUtil.delete(productCountKey);
            if(homeShowDuration > 0){
                secKillStockUtil.set(productKey,product,homeShowDuration, TimeUnit.MILLISECONDS);
                secKillStockUtil.set(productCountKey,product.getFlashPromotionCount(),homeShowDuration, TimeUnit.MILLISECONDS);
            }
        }
    }

    /* PO ??????????????????pipeline??????*/
    private void secKillOffRedis(List<FlashPromotionProduct> products){
        if(CollectionUtils.isEmpty(products)){
            log.warn("??????????????????????????????????????????????????????????????????");
            return;
        }
        final String secKillKey = promotionRedisKey.getSecKillKey();
        homeRedisOpsExtUtil.delete(secKillKey);
        /*??????????????????*/
        for(FlashPromotionProduct product : products){
            secKillStockUtil.delete(RedisKeyPrefixConst.SECKILL_PRODUCT_PREFIX + product.getFlashPromotionId()
                    + ":" + product.getId());
            secKillStockUtil.delete(RedisKeyPrefixConst.MIAOSHA_STOCK_CACHE_PREFIX + product.getId());
        }
    }
}

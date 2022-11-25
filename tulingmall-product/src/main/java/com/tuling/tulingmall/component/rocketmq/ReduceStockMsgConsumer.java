package com.tuling.tulingmall.component.rocketmq;

import com.tuling.tulingmall.service.StockManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author Fox
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "${rocketmq.consumer.group}",topic = "${rocketmq.consumer.topic}")
public class ReduceStockMsgConsumer implements RocketMQListener<StockChangeEvent> {

    @Autowired
    private StockManageService stockManageService;
    
    /**
     * 接收消息
     */
    @Override
    public void onMessage(StockChangeEvent stockChangeEvent) {
        log.info("开始消费消息:{}",stockChangeEvent);

        stockManageService.reduceStock(stockChangeEvent);
    }


}

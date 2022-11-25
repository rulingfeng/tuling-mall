package com.tuling.tulingmall.ordercurr.component.rocketmq;

import com.tuling.tulingmall.ordercurr.domain.OmsOrderDetail;
import com.tuling.tulingmall.ordercurr.domain.StockChanges;
import com.tuling.tulingmall.ordercurr.model.OmsOrderItem;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @Author Fox
 */
@Component
public class ReduceStockMsgSender {


    @Autowired
    private ExtRocketMQTemplate extRocketMQTemplate;

    /**
     * 使用事务消息机制发送扣减库存消息
     * @param orderId
     * @param payType
     * @param orderDetail
     * @return
     */
    public boolean sendReduceStockMsg(Long orderId, Integer payType, OmsOrderDetail orderDetail){

        List<StockChanges> stockChangesList = new ArrayList<>();
        for(OmsOrderItem omsOrderItem : orderDetail.getOrderItemList()){
            stockChangesList.add(new StockChanges(omsOrderItem.getProductSkuId(),omsOrderItem.getProductQuantity()));
        }
        String destination = "reduce-stock";

        StockChangeEvent stockChangeEvent = new StockChangeEvent();
        stockChangeEvent.setPayType(payType);
        stockChangeEvent.setOrderId(orderId);
        stockChangeEvent.setStockChangesList(stockChangesList);
        //TODO  全局事务id   可以用于幂等校验
        String transactionId = UUID.randomUUID().toString();

        stockChangeEvent.setTransactionId(transactionId);
        Message<StockChangeEvent> message = MessageBuilder.withPayload(stockChangeEvent)
                .setHeader(RocketMQHeaders.TRANSACTION_ID, transactionId)
                .setHeader("orderId",orderId)
                .setHeader("payType",payType)
                .build();
        //destination：目的地(主题)，这里发送给reduce-stock这个topic
        // message：发送给消费者的消息体，需要使用MessageBuilder.withPayload() 来构建消息
        // arg：参数
        TransactionSendResult sendResult = extRocketMQTemplate.sendMessageInTransaction(destination,message,orderId);
        return SendStatus.SEND_OK == sendResult.getSendStatus();
    }


}

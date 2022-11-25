package com.tuling.tulingmall.component.rocketmq;

import com.tuling.tulingmall.domain.StockChanges;
import lombok.Data;

import java.util.List;

/**
 * @Author Fox
 */
@Data
public class StockChangeEvent {

    /**
     * 事务id
     */
    private String transactionId;

    private List<StockChanges> stockChangesList;

    private Long orderId;
    /**
     * 支付方式：0->未支付；1->支付宝；2->微信
     */
    private Integer payType;
}

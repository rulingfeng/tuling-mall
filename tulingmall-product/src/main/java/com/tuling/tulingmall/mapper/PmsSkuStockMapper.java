package com.tuling.tulingmall.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.tuling.tulingmall.domain.StockChanges;
import com.tuling.tulingmall.model.PmsSkuStock;
import com.tuling.tulingmall.model.PmsSkuStockExample;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
@DS("goods")
public interface PmsSkuStockMapper {
    long countByExample(PmsSkuStockExample example);

    int deleteByExample(PmsSkuStockExample example);

    int deleteByPrimaryKey(Long id);

    int insert(PmsSkuStock record);

    int insertSelective(PmsSkuStock record);

    List<PmsSkuStock> selectByExample(PmsSkuStockExample example);

    PmsSkuStock selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") PmsSkuStock record, @Param("example") PmsSkuStockExample example);

    int lockStockByExample(@Param("lockQuantity") Integer lockQuantity, @Param("example") PmsSkuStockExample example);

    int reduceStockByExample(@Param("reduceQuantity") Integer reduceQuantity, @Param("example") PmsSkuStockExample example);

    int updateSkuStock(@Param("itemList") List<StockChanges> orderItemList);

    int recoverStockByExample(@Param("recoverQuantity") Integer recoverQuantity, @Param("example") PmsSkuStockExample example);

    int updateByExample(@Param("record") PmsSkuStock record, @Param("example") PmsSkuStockExample example);

    int updateByPrimaryKeySelective(PmsSkuStock record);

    int updateByPrimaryKey(PmsSkuStock record);

    @Select("select count(1) from local_transaction_log where tx_no = #{txNo}")
    int isExistTx(String txNo);

    @Insert("insert into local_transaction_log values(#{txNo},now());")
    int addTx(String txNo);
}
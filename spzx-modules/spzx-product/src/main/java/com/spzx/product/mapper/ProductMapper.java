package com.spzx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spzx.product.api.domain.Product;
import com.spzx.product.api.domain.ProductSku;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 商品Mapper接口
 */
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 查询商品列表
     *
     * @param product 商品
     * @return 商品集合
     */
    public List<Product> selectProductList(Product product);

    ProductSku getProductSkuById(Long skuId);

    Integer checkAndLockStock(@Param("skuId") Long skuId, @Param("skuNum") Integer skuNum);
}
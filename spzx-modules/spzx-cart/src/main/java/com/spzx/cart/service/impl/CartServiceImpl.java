package com.spzx.cart.service.impl;

import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.service.ICartService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class CartServiceImpl implements ICartService {
    //购物车存在redis里, hash存储, 以固定字符串+userID分组(key), skuId为HashKey, CartInfo为value

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    RemoteProductService remoteProductService;

    private String getHashKey(){
        return "user:cart:" + SecurityContextHolder.getUserId();
    }

    @Override
    public void addCart(Long skuId, Integer num) {
        String key = getHashKey();
        //查询redis, 现在购物车里有没有该sku
        CartInfo cartInfo = (CartInfo) redisTemplate.opsForHash().get(key, skuId.toString() );
        if ( null != cartInfo){ //如果购物车里已有
            Integer skuNum = cartInfo.getSkuNum();
            cartInfo.setSkuNum( (skuNum += num) > 99 ? 99 : (skuNum + num));
        }else { //如果购物车里没有这个东西, 根据skuId查询CartInfo, 再放到redis里
            R<ProductSku> productSkuR = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
            if (R.FAIL == productSkuR.getCode()){
                throw new ServiceException(productSkuR.getMsg());
            }
            ProductSku productSku = productSkuR.getData();
            cartInfo = new CartInfo();
            cartInfo.setUserId( SecurityContextHolder.getUserId() );
            cartInfo.setSkuId(skuId);
            cartInfo.setCartPrice(productSku.getSalePrice());
            cartInfo.setSkuPrice(productSku.getMarketPrice());
            cartInfo.setSkuNum(1);
            cartInfo.setSkuName(productSku.getSkuName());
            cartInfo.setThumbImg(productSku.getThumbImg());
        }
        redisTemplate.opsForHash().put( key, skuId, cartInfo);
    }

    @Override
    public void deleteSku(Long skuId) {
        String key = getHashKey();
        redisTemplate.opsForHash().delete(key, skuId.toString());
    }


















}
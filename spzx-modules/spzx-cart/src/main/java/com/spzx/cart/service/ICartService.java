package com.spzx.cart.service;

import com.spzx.cart.api.domain.CartInfo;

import java.util.List;

public interface ICartService {

    void addCart(Long skuId, Integer num);

    void deleteSku(Long skuId);

    void updateSingleSkuIsChecked(String skuId, Integer isChecked);

    List<CartInfo> getCart();

    List<CartInfo> getCartCheckedList(Long userId);
}

package com.spzx.cart.service;

public interface ICartService {

    void addCart(Long skuId, Integer num);

    void deleteSku(Long skuId);
}

package com.spzx.cart.controller;

import com.spzx.cart.service.ICartService;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.security.annotation.RequiresLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "购物车接口")
@RestController
@RequestMapping
public class CartController extends BaseController {

    @Autowired
    private ICartService cartService;

    @Operation( summary = "添加")
    @RequiresLogin
    @GetMapping("/addToCart/{skuId}/{num}")
    public AjaxResult addCart(@PathVariable Long skuId, @PathVariable Integer num){
        cartService.addCart(skuId, num);
        return success("添加成功");
    }
















    @Operation( summary = "删除")
    @Operation( summary = "修改")
    @Operation( summary = "查询")


}
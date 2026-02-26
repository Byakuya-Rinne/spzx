package com.spzx.cart.controller;

import com.spzx.cart.service.ICartService;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.security.annotation.RequiresLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "购物车接口")
@RestController
@RequestMapping
public class CartController extends BaseController {

    @Autowired
    private ICartService cartService;

    @Operation( summary = "添加、修改数量")
    @RequiresLogin
    @GetMapping("/addToCart/{skuId}/{num}")
    public AjaxResult addCart(@PathVariable Long skuId, @PathVariable Integer num){
        cartService.addCart(skuId, num);
        return success("添加成功");
    }

    @Operation( summary = "删除单个SKU")
    @RequiresLogin
    @DeleteMapping("/deleteCart/{skuId}")
    public AjaxResult deleteSku(@PathVariable Long skuId){
        cartService.deleteSku(skuId);
        return success("删除成功");
    }



//
//    @Operation( summary = "修改")
//    @Operation( summary = "查询")


}
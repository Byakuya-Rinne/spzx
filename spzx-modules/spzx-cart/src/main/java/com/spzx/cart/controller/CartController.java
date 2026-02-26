package com.spzx.cart.controller;

import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.service.ICartService;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.security.annotation.RequiresLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation( summary = "修改单个Sku选中状态")
    @RequiresLogin
    @GetMapping("/checkCart/{skuId}/{isChecked}")
    public AjaxResult updateSingleSkuIsChecked(@PathVariable String skuId, @PathVariable Integer isChecked){
        cartService.updateSingleSkuIsChecked(skuId, isChecked);
        return success("修改成功");
    }

    @Operation( summary = "查看购物车")
    @RequiresLogin
    @GetMapping("/cartList")
    public AjaxResult getCart(){
        //调用ICartService中查看购物车的方法
        List<CartInfo> cartInfoList = cartService.getCart();
        return success(cartInfoList);
    }




}
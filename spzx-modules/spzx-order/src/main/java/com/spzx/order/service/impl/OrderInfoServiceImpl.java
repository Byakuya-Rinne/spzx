package com.spzx.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.order.domain.vo.TradeVo;
import com.spzx.order.mapper.OrderInfoMapper;
import com.spzx.order.mapper.OrderItemMapper;
import com.spzx.order.mapper.OrderLogMapper;
import com.spzx.order.service.IOrderInfoService;
import com.spzx.product.api.RemoteProductService;
import com.spzx.user.api.RemoteUserAddressService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderLogMapper orderLogMapper;

    @Autowired
    private RemoteCartService remoteCartService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemoteProductService remoteProductService;

    @Autowired
    private RemoteUserAddressService remoteUserAddressService;

    @Autowired
    RabbitService rabbitService; //来自于公共模块：spzx-common-rabbit

    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单
     */
    @Override
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo) {
        return orderInfoMapper.selectOrderInfoList(orderInfo);
    }

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    @Override
    public OrderInfo selectOrderInfoById(Long id) {
        OrderInfo orderInfo = orderInfoMapper.selectById(id);
        List<OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }

    @Override
    public TradeVo trade() {
        //结算总金额 BigDecimal totalAmount, 结算商品列表 List<OrderItem> orderItemList, 交易号 String tradeNo
        //还没下单, 所以OrderItem订单号都为空
        //获取选中的购物项, 生成交易号(产生订单之前的临时记录)

        Long userId = SecurityContextHolder.getUserId();

        R<List<CartInfo>> cartInfoListR = remoteCartService.getCartCheckedList(userId);
        if (R.FAIL == cartInfoListR.getCode() ){
            throw new ServiceException( cartInfoListR.getMsg() );
        }

        List<CartInfo> cartInfoList = cartInfoListR.getData();

        if (cartInfoList.isEmpty()){
            throw new ServiceException("未选中商品");
        }

        List<OrderItem> orderItems = cartInfoList.stream().map(cartInfo -> {
            OrderItem orderItem = new OrderItem();

            BeanUtils.copyProperties(cartInfo, orderItem);
//            orderItem.setSkuId(cartInfo.getSkuId());
//            orderItem.setSkuName(cartInfo.getSkuName());
//            orderItem.setSkuNum(cartInfo.getSkuNum());
//            orderItem.setThumbImg(cartInfo.getThumbImg());
//            orderItem.setSkuPrice(cartInfo.getCartPrice());
            return orderItem;
        }).collect(Collectors.toList());

        //生成交易号并存入redis
        String tradeNo = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set("tradeNo:"+tradeNo, tradeNo, 240, TimeUnit.SECONDS);

        //计算总金额
        BigDecimal totalAmount = new BigDecimal(0);
        for (OrderItem orderItem : orderItems) {
            BigDecimal skuPrice = orderItem.getSkuPrice();
            Integer skuNum = orderItem.getSkuNum();
            BigDecimal skuAmount = skuPrice.multiply( new BigDecimal(skuNum) );
            totalAmount = totalAmount.add(skuAmount);
        }

        TradeVo tradeVo = new TradeVo();
        tradeVo.setTradeNo(tradeNo);
        tradeVo.setOrderItemList(orderItems);
        tradeVo.setTotalAmount(totalAmount);
        return tradeVo;
    }
}
package com.spzx.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.core.utils.StringUtils;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.order.domain.OrderLog;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;
import com.spzx.order.mapper.OrderInfoMapper;
import com.spzx.order.mapper.OrderItemMapper;
import com.spzx.order.mapper.OrderLogMapper;
import com.spzx.order.service.IOrderInfoService;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuLockVo;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.user.api.RemoteUserAddressService;
import com.spzx.user.api.RemoteUserInfoService;
import com.spzx.user.domain.UserAddress;
import com.spzx.user.domain.UserInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private RabbitService rabbitService; //来自于公共模块：spzx-common-rabbit

    @Autowired
    private RemoteUserInfoService remoteUserInfoService;

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
        if (R.FAIL == cartInfoListR.getCode()) {
            throw new ServiceException(cartInfoListR.getMsg());
        }

        List<CartInfo> cartInfoList = cartInfoListR.getData();

        if (cartInfoList.isEmpty()) {
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
        String tradeNo = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("tradeNo:" + tradeNo, tradeNo, 20, TimeUnit.SECONDS);

        //计算总金额
        BigDecimal totalAmount = new BigDecimal(0);
        for (OrderItem orderItem : orderItems) {
            BigDecimal skuPrice = orderItem.getSkuPrice();
            Integer skuNum = orderItem.getSkuNum();
            BigDecimal skuAmount = skuPrice.multiply(new BigDecimal(skuNum));
            totalAmount = totalAmount.add(skuAmount);
        }

        TradeVo tradeVo = new TradeVo();
        tradeVo.setTradeNo(tradeNo);
        tradeVo.setOrderItemList(orderItems);
        tradeVo.setTotalAmount(totalAmount);
        return tradeVo;
    }

    @Override
    public String submitOrder(OrderForm orderForm) {
        //创建订单, 保存orderInfo, orderItem, 记录日志orderLog

        //验证trade号是否已经存在(重复提交订单)
        String tradeNo = orderForm.getTradeNo();
        String redisTradeNo = (String) redisTemplate.opsForValue().get("tradeNo:" + tradeNo);

        if (null == redisTradeNo) {
            //如果tradeNo不在redis中, 则不是第一次下单, 报错
            throw new ServiceException("重复下单, 或请求已过期, 请返回重试");
        } else {
            //如果传入的OrderForm中的tradeNo在redis中, 则为第一次下单, 正常下单并删除redis中的tradeNo
            Boolean deleted = redisTemplate.delete("tradeNo:" + tradeNo);
            if (Boolean.FALSE.equals(deleted)) {
                throw new ServiceException("下单失败, 未清除redis暂存的tradeNo");
            }
        }

        //再次检查sku最新价格, 如果不一样则更新购物车中的价格并以新价格继续
        List<OrderItem> orderItemList = orderForm.getOrderItemList();

        //逐个查询sku价格
//        for (OrderItem orderItem : orderItemList) {
//            BigDecimal orderSkuPrice = orderItem.getSkuPrice();
//            Long orderSkuId = orderItem.getSkuId();
//            R<ProductSku> latestProductSkuR = remoteProductService.getProductSku(orderSkuId, SecurityConstants.INNER);
//            if (R.FAIL == latestProductSkuR.getCode()){
//                throw new ServiceException("远程调用商品服务(查询sku详情)失败");
//            }
//            ProductSku latestProductSku = latestProductSkuR.getData();
//            if (null == latestProductSku){
//                throw new ServiceException("未查询到指定sku");
//            }
//            BigDecimal latestSalePrice = latestProductSku.getSalePrice();
//
//            if (!latestSalePrice.equals(orderSkuPrice)){ //订单中的价格与最新价格不同
////                remoteCartService. //更改购物车中的商品sku价格
//            }
//        }

        List<Long> skuIdList = orderItemList.stream().map(OrderItem::getSkuId).collect(Collectors.toList());
        R<List<SkuPrice>> skuPriceListR = remoteProductService.getSkuPriceList(skuIdList, SecurityConstants.INNER);
        if (R.FAIL == skuPriceListR.getCode()) {
            throw new ServiceException("远程调用商品服务(查询sku详情)失败");
        }

        Map<Long, BigDecimal> skuIdSalePriceMap = skuPriceListR.getData().stream().collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));
        //比较新旧价格
        StringBuffer msg = new StringBuffer();
        BigDecimal totalAmount = new BigDecimal(0);
        for (OrderItem orderItem : orderItemList) {
            Long skuId = orderItem.getSkuId();
            BigDecimal orderSkuPrice = orderItem.getSkuPrice();
            BigDecimal latestPrice = skuIdSalePriceMap.get(skuId);
            if ( !orderSkuPrice.equals(latestPrice) ){
                msg.append("商品").append(orderItem.getSkuName()).append("价格已变化 \n");
                //remoteCartService. //更改购物车中的商品sku价格
            }
            Integer skuNum = orderItem.getSkuNum();
            totalAmount = totalAmount.add(orderSkuPrice.multiply( new BigDecimal(skuNum) ));
        }

        if(StringUtils.hasText(msg.toString())){
            //已经记录过价格变动提示信息, 报错
            throw new ServiceException(msg.toString());
        }

        //校验和锁库存
        //将List<OrderItem>转换为List<SkuLockVo>
        List<SkuLockVo> skuLockVoList = orderItemList.stream().map(orderItem -> {
            //创建SkuLockVo对象
            SkuLockVo skuLockVo = new SkuLockVo();
            //设置属性值
            skuLockVo.setSkuId(orderItem.getSkuId());
            skuLockVo.setSkuNum(orderItem.getSkuNum());
            return skuLockVo;
        }).collect(Collectors.toList());

        R<String> r = remoteProductService.checkAndLockStock(skuLockVoList, SecurityConstants.INNER);
        if (R.FAIL == r.getCode()) {
            throw new ServiceException(r.getMsg());
        }

        String returnMsg = r.getData();
        if(StringUtils.hasText(returnMsg)){
            //库存锁定失败
            throw new ServiceException(returnMsg);
        }

        //1.保存订单
        //创建OrderInfo对象
        OrderInfo orderInfo = new OrderInfo();
        //设置前端提交的数据

        //获取用户收货地址的id
        Long userAddressId = orderForm.getUserAddressId();
        //获取运费
        BigDecimal feightFee = orderForm.getFeightFee();
        //获取备注
        String remark = orderForm.getRemark();

        orderInfo.setFeightFee(feightFee);
        orderInfo.setRemark(remark);

        //获取用户id和用户昵称
        Long userId = SecurityContextHolder.getUserId();
        //获取用户名
        String userName = SecurityContextHolder.getUserName();
        //远程调用用户微服务根据用户名获取用户信息
        R<UserInfo> userInfo = remoteUserInfoService.getUserInfo(userName, SecurityConstants.INNER);
        if(R.FAIL == userInfo.getCode()){
            throw new ServiceException(userInfo.getMsg());
        }
        //获取用户信息
        UserInfo userInfoData = userInfo.getData();
        //获取用户昵称
        String nickName = userInfoData.getNickName();
        //给订单设置用户信息
        orderInfo.setUserId(userId);
        orderInfo.setNickName(nickName);
        orderInfo.setCreateBy(nickName);

        //远程调用用户微服务根据用户地址的id获取用户地址信息
        R<UserAddress> userAddressData = remoteUserAddressService.getUserAddress(userAddressId, SecurityConstants.INNER);
        if(R.FAIL == userAddressData.getCode()){
            throw new ServiceException(userAddressData.getMsg());
        }
        //获取用户地址信息
        UserAddress userAddress = userAddressData.getData();
        //给订单设置用户地址信息
        orderInfo.setReceiverProvince(userAddress.getProvinceCode());
        orderInfo.setReceiverCity(userAddress.getCityCode());
        orderInfo.setReceiverDistrict(userAddress.getDistrictCode());
        orderInfo.setReceiverAddress(userAddress.getFullAddress());
        orderInfo.setReceiverName(userAddress.getName());
        orderInfo.setReceiverPhone(userAddress.getPhone());
        orderInfo.setReceiverTagName(userAddress.getTagName());

        //使用UUID随机生成一个字符串作为订单号
        String orderNo = UUID.randomUUID().toString().replaceAll("-", "");
        //设置订单号
        orderInfo.setOrderNo(orderNo);
        //设置订单状态
        orderInfo.setOrderStatus(0);
        //设置订单总金额
        orderInfo.setTotalAmount(totalAmount);
        //设置订单原价
        orderInfo.setOriginalTotalAmount(totalAmount);
        //保存订单
        orderInfoMapper.insert(orderInfo);
        //获取订单的id
        Long orderId = orderInfo.getId();
        //2.保存订单项
        //给每个订单项设置订单id
        orderItemList.forEach(orderItem -> orderItem.setOrderId(orderId));
        //批量保存订单项
        orderItemService.saveBatch(orderItemList);

        //3.保存订单日志
        OrderLog orderLog = new OrderLog();
        //设置属性值
        orderLog.setOrderId(orderId);
        orderLog.setOperateUser(nickName);
        orderLog.setCreateBy(nickName);
        orderLog.setNote("提交订单");
        orderLog.setProcessStatus(0);
        orderLog.setRemark(remark);
        //保存订单日志
        orderLogMapper.insert(orderLog);
        R<Void> voidR = remoteCartService.deleteCheckedCartInfo(userId, SecurityConstants.INNER);
        if(R.FAIL == voidR.getCode()){
            throw new ServiceException(voidR.getMsg());
        }
        return orderId;
    }
}
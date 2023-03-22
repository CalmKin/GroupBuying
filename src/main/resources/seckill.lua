---
--- Generated by Luanalysis
--- Created by 86158.
--- DateTime: 2023/3/22 11:32
---

-- 因为要判断库存，所以传入优惠券id，判断一人一单，传入用户id
local VoucherID = ARGV[1]
local UsrId = ARGV[2]

-- 查询库存和订单的key
local stock_key = 'seckill:stock:' .. VoucherID

local order_key = 'seckill:order:' .. VoucherID

-- 先判断库存是否充足
if(redis.call("GET",stock_key) =='0') then
    --库存不充足，直接返回1
return 1;
end

if( redis.call("sismember" , order_key,UsrId) == 1) then
    -- 用户已经下过单了
    return 2
end

-- 用户有下单资格
redis.call("incrby",stock_key,-1)
redis.call("sadd",order_key,UsrId)

return 0
package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource
    private IUserService userService;
    @Override
    public Result queryBlog(Long blogId) {
        LambdaQueryWrapper<Blog> lqw = new LambdaQueryWrapper<>();
        lqw.eq(Blog::getId,blogId);
        Blog blog = getOne(lqw);
        this.setUserInfo(blog);
        this.isLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(
                blog -> {
                    this.setUserInfo(blog);
                    this.isLiked(blog);
                }
        );
        return Result.ok(records);
    }

    public void isLiked(Blog blog)
    {
        Long id = blog.getId();
        String key = BLOG_LIKED_KEY + id;
        UserDTO usr = UserHolder.getUser();
        if(usr==null) return;
        Long user = UserHolder.getUser().getId();
        Double score = redisTemplate.opsForZSet().score(key, user.toString());
        blog.setIsLike(score!=null);
    }

    @Override
    public Result likeBlog(Long id) {

        Blog blog = this.getById(id);
        if(blog==null)
        {
            return Result.fail("博客不存在");
        }

        //判断用户是否点过赞
            //1.获取用户的id
        Long usrID = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double exist = redisTemplate.opsForZSet().score(key, usrID.toString());
        //如果用户没有点过赞
        if(exist==null)
        {
            //将用户加入到该博客的集合里面,时间戳作为权重
            redisTemplate.opsForZSet().add(key,usrID.toString(),System.currentTimeMillis());
            //博客点赞数+1
            boolean succ = update().setSql("liked = liked + 1").eq("id", id).update();
            if(succ)
            {
                return Result.ok("点赞成功");
            }
            else
            {
                return Result.fail("点赞失败");
            }
        }
        else
        {
            //否则将用户移出该博客的点赞集合
            redisTemplate.opsForZSet().remove(key,usrID.toString());
            //博客点赞数-1
            boolean succ = update().setSql("liked = liked - 1").eq("id", id).update();
            if(succ)
            {
                return Result.ok("已取消点赞");
            }
            else
            {
                return Result.fail("取消点赞失败");
            }
        }

    }

    @Override
    public Result getLikes(Long id) {

        //根据博客id获取前五的点赞用户id
        String key = BLOG_LIKED_KEY+id;
        Set<String> range = redisTemplate.opsForZSet().range(key, 0, 4);
        if(range ==null || range.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }
        //将用户信息进行处理
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());    //Long::valueOf将所有string类型转化为long类型
        String str = StrUtil.join(",", ids);    //用于拼接sql语句


        List<UserDTO> userDtos = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + str + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDtos);
    }

    private void setUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}

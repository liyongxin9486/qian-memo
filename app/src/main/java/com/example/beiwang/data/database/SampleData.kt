package com.example.beiwang.data.database

import com.example.beiwang.data.dao.MemoDao
import com.example.beiwang.data.dao.ShoppingItemDao
import com.example.beiwang.data.dao.TagDao
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.TagEntity

/**
 * 演示用种子数据：10 条长作文（备忘录）+ 若干购物条目。
 * 备忘录与购物清单共用同一套标签。仅在数据库首次创建（onCreate）时写入。
 */
object SampleData {

    private val defaultTags = listOf(
        TagEntity(id = "tag_work", name = "工作", color = 0xFF1E88E5.toInt()),
        TagEntity(id = "tag_life", name = "生活", color = 0xFF4CAF50.toInt()),
        TagEntity(id = "tag_study", name = "学习", color = 0xFF9C27B0.toInt())
    )

    val sampleMemos: List<MemoEntity> = listOf(
        MemoEntity(
            title = "我的家乡",
            content = "我的家乡是一座被水温柔包裹的小城。清晨，薄雾还萦绕在河面上，乌篷船便轻轻摇橹而出，桨声欸乃，惊起几只白鹭。青石板铺就的巷子里，早点铺腾起袅袅热气，桂花糕的甜香与豆浆的醇厚交织在一起。到了傍晚，夕阳把河水染成蜜色，老人坐在桥头摇着蒲扇，孩子们追着蜻蜓跑过石阶。这里没有都市的喧嚣，时间仿佛被拉得很长很长。无论我走得多远，只要闭上眼，那座小桥、那片流水、那碗温热的酒酿，便会在记忆里缓缓浮现。家乡不是地图上的一个点，而是灵魂始终回望的方向。",
            isFavorite = true,
            tagIds = "tag_life"
        ),
        MemoEntity(
            title = "读书的乐趣",
            content = "有人说，读书是寂寞的事，我却觉得它是世上最热闹的旅行。翻开一本书，我便不再是原来的我：读《红楼梦》，我随大观园里的少女一同落泪；读《三体》，我仰望星河想象文明的渺小与壮丽；读一本闲散文集，又像与老友围炉夜话。读书之乐，在于它让我在一间斗室里拥有了千万种人生。困顿时有智者替我拨开迷雾，骄傲时有哲人轻轻提醒谦卑。久而久之，书成了我的避难所，也是我的望远镜。每当生活令我疲惫，我便躲进文字里喘口气，再出来时，世界似乎也温柔了几分。",
            tagIds = "tag_study"
        ),
        MemoEntity(
            title = "时间都去哪儿了",
            content = "我们总抱怨一天只有二十四小时，却很少追问那些时间究竟流向了何处。刷手机的一个晚上，能轻易吞掉三小时；犹豫要不要开始一件事，又悄悄溜走半天。时间从不是被偷走的，多半是我们亲手交了出去。后来我学着给每一天画三件最重要的事，像往瓶子里先放进大石头，再用碎砂填满缝隙。我开始把'等会儿'换成'现在'，把'差不多'换成'认真做'。奇妙的是，当我不再追赶时间，时间反而宽裕起来。原来管理时间，管理的从来不是时钟，而是自己的心与选择。",
            isFavorite = true,
            tagIds = "tag_work"
        ),
        MemoEntity(
            title = "一次难忘的旅行",
            content = "去年夏天，我独自去了西北。火车穿过黄土高原时，窗外渐渐褪去绿色，只剩苍茫的赭红与灰黄。到达敦煌那晚，我租了辆单车去鸣沙山。风很轻，沙丘在月光下泛着银蓝。我脱了鞋往上爬，细沙从脚趾缝里滑落，凉而细腻。坐在沙脊上，整片沙漠安静得能听见自己的心跳。远处月牙泉如一弯碧玉，倔强地嵌在金色的怀抱里。那一刻我忽然懂了什么叫'大漠孤烟直'。回来后很久，我仍会想起那夜的风——它教会我，最美的风景往往不在计划表里，而在愿意慢下来的脚步中。",
            tagIds = "tag_life"
        ),
        MemoEntity(
            title = "科技改变生活",
            content = "二十年前，一封家书要走上半个月；如今一条语音，眨眼便送到万里之外。科技像一列无声的快车，把不可能变成日常。移动支付让我们忘带钱包也能果腹，导航让路痴也能独行天下，远程医疗把专家请进了县城的诊室。可硬币总有另一面：我们越来越依赖屏幕，却越来越疏于面对面地说一句'最近好吗'。真正的进步，不该只是更快更方便，更该让人与人之间更近，而不是更远的像素。愿我们在拥抱科技时，仍为真实的温度留一扇门。",
            tagIds = "tag_work"
        ),
        MemoEntity(
            title = "保护环境，从我做起",
            content = "地球不需要被拯救，需要被拯救的是我们自己。当冰川消融、物种消失、雾霾遮住星空，受苦的终究是依存自然的人类。环保听来宏大，落点却很小：少用一个塑料袋，多走一段路；点餐时不贪多，纸张双面写。我家阳台摆了三个分类桶，孩子两岁就懂得把瓶子放进蓝色那只。改变习惯起初别扭，久了便成自然。一个人的力量像萤火，可千万点萤火聚起来，便是能照亮前路的光。保护环境，从来不是某个人或某天的壮举，而是每个人每一天的小心翼翼。",
            tagIds = "tag_life"
        ),
        MemoEntity(
            title = "友谊最珍贵",
            content = "人生路上，亲人陪你启程，爱人陪你走一段，而朋友，是那个你没邀请却主动跟上的人。难过时他不说大道理，只默默递来一杯热茶；得意时他比你还开心，又在你飘飘然时轻轻拉你衣角。真正的朋友经得起沉默，也经得起距离——多久不见，一开口仍是旧日的语气。我曾以为朋友越多越好，后来才明白，三两知己，胜过百个点头之交。岁月会筛掉浮萍般的关系，留下来的，都是经了风雨仍愿为你撑伞的人。友谊之贵，正在于此：不喧哗，却长情。",
            tagIds = "tag_life"
        ),
        MemoEntity(
            title = "我的梦想",
            content = "小时候，我的梦想是当宇航员，以为伸手就能摘下星星。长大些，梦想换成考一所好大学、找一份体面的工作。如今再问自己，倒有些答不上来。后来我慢慢清楚：梦想未必是某个具体的终点，更像心里的一团火——它让我在平庸的日子里仍愿多走一步。我想成为那种人：手里有事做，眼里有光，对弱者温柔，对不公敢发声。或许成不了伟人，但能把小日子过出滋味，顺手再照亮旁人一程，便不负此生。梦想不必惊天动地，真诚就好。",
            tagIds = "tag_study"
        ),
        MemoEntity(
            title = "舌尖上的乡愁",
            content = "味觉是记忆最顽固的守门人。离家久了，最想念的从不是山珍海味，而是母亲那一碗酱油拌面：面条要手擀的，筋道；酱油得兑一点糖，咸里回甜；最后撒一把青蒜末，热油'刺啦'一泼，香气能窜上三楼。后来我走南闯北，吃过米其林也尝过路边摊，可舌尖认准的，始终是那碗朴素的面。每次回乡，第一顿必是它，一口下去，所有漂泊的疲惫都被熨平。食物之所以动人，是因为它盛着做菜人的心意与时光。所谓乡愁，很多时候，不过是一道怎么也复刻不像的家常味。",
            tagIds = "tag_life"
        ),
        MemoEntity(
            title = "四季之美",
            content = "春是少年，嫩绿试探着爬上枝头，风里都是蠢蠢欲动的心事；夏是盛年，蝉鸣把白天拉得很长，雷雨来得急去得也快，像不计后果的豪情；秋是智者，枫叶把山染成宣言，稻谷低头感恩土地，连离别都带着成熟的通透；冬是老者，万物收敛，雪覆盖喧嚣，教人在安静里回味来路。四季从不停留，却把每一种活法都演给我们看。若能像春一样敢新生、夏一样去热爱、秋一样懂放手、冬一样会沉淀，这一生，便也算没辜负轮回。",
            isFavorite = true,
            tagIds = "tag_study"
        )
    )

    /**
     * 购物清单示例条目：(名称, 标签, 数量, 单位)
     */
    val sampleShoppingItems: List<ShoppingSeedItem> = listOf(
        ShoppingSeedItem("西红柿", "tag_life", 3, "斤"),
        ShoppingSeedItem("青菜", "tag_life", 2, "把"),
        ShoppingSeedItem("苹果", "tag_life", 5, "个"),
        ShoppingSeedItem("香蕉", "tag_life", 2, "把"),
        ShoppingSeedItem("鸡胸肉", "tag_life", 2, "斤"),
        ShoppingSeedItem("牛排", "tag_life", 3, "块"),
        ShoppingSeedItem("抽纸", "tag_life", 4, "提"),
        ShoppingSeedItem("洗洁精", "tag_life", 1, "瓶"),
        ShoppingSeedItem("纯牛奶", "tag_life", 2, "箱"),
        ShoppingSeedItem("酸奶", "tag_life", 3, "杯"),
        ShoppingSeedItem("可乐", "tag_life", 6, "瓶"),
        ShoppingSeedItem("乌龙茶", "tag_life", 2, "瓶"),
        ShoppingSeedItem("薯片", "tag_life", 3, "袋"),
        ShoppingSeedItem("混合坚果", "tag_life", 1, "罐"),
        ShoppingSeedItem("五号电池", "tag_life", 4, "节"),
        ShoppingSeedItem("笔记本", "tag_work", 3, "本"),
        ShoppingSeedItem("中性笔", "tag_work", 10, "支"),
        ShoppingSeedItem("充电宝", "tag_work", 1, "个"),
        ShoppingSeedItem("数据线", "tag_work", 2, "根"),
        ShoppingSeedItem("猫粮", "tag_life", 1, "袋"),
        ShoppingSeedItem("猫砂", "tag_life", 1, "袋")
    )

    suspend fun seedTags(tagDao: TagDao) {
        defaultTags.forEach { tagDao.insert(it) }
    }

    suspend fun seedMemos(memoDao: MemoDao) {
        sampleMemos.forEach { memoDao.insert(it) }
    }

    suspend fun seedShoppingItems(shoppingItemDao: ShoppingItemDao) {
        sampleShoppingItems.forEach { seed ->
            shoppingItemDao.insert(
                ShoppingItemEntity(
                    name = seed.name,
                    quantity = seed.quantity,
                    unit = seed.unit,
                    tagIds = seed.tagId
                )
            )
        }
    }
}

data class ShoppingSeedItem(
    val name: String,
    val tagId: String,
    val quantity: Int = 1,
    val unit: String = "个"
)

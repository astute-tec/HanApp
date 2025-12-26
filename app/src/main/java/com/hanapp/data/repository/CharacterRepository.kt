package com.hanapp.data.repository

import android.content.Context
import com.hanapp.data.dao.CharacterDao
import com.hanapp.data.model.Character
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CharacterRepository(private val characterDao: CharacterDao) {

    suspend fun importDataFromAssets(context: Context) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("app_data", Context.MODE_PRIVATE)
            val currentVersion = prefs.getInt("char_data_version", 0)
            val targetVersion = 11 // 升级版本以强制刷新数据，导入更多汉字

            if (characterDao.getCount() > 0 && currentVersion >= targetVersion) return@withContext

            try {
                val inputStream = context.assets.open("quiz.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val root = Gson().fromJson(jsonString, JsonObject::class.java)
                val dataArray = root.getAsJsonArray("data")

                // 人教版 1-6 年级生字映射
                val gradeMap = mapOf(
                    1 to "一二三十木禾上下土个八入大天人火文六七儿九无口日中了子门月不开四五目耳头米见白田电也长山出飞马鸟云公车牛羊小少巾牙尺毛卜又心风力手水广升足走方半巴业本平书自已东西回片皮生里果几用鱼今正雨两瓜衣来年左右万丁冬百齐说话朋友春高你们红绿花草爷节岁亲的行古处声多处知忙洗认扫真父母爸全关写完家看着画笑兴会妈奶午合放收女太气早去亮和语千李秀香听唱连远定向以后更主意总先干赶起明净同工专才级队蚂蚁前空房网诗林童黄闭立是朵美我叶机她他送过时让吗吧虫往得很河姐借呢呀哪谁怕跟凉量最园因为脸阳光可石办法找许别到那都吓叫再象像做点照沙海桥竹军苗井乡面忘想念王从边这进道贝原男爱虾跑吹地快乐老师短对冷淡热情拉把给活种吃练习苦学非常问间伙伴共汽分要没位孩选北南江湖秋只星雪帮请就球玩跳桃树刚兰各坐座带急名发成晚动新有在什么变条",
                    2 to "宜实色华谷金尽层丰壮波浪灯作字苹丽劳尤其区巨它安块站已甲豆识纷经如好娃洼于首枝枫记刘胡戏棋钢观弹琴养休伸甜歌院除息您牵困员青宁室樣校切教响班欠元包钟叹哈迟闹及身仔细次外计怦礼加夕与川州台争民族亿洁欢祖旗帜庆曲央交市旁优壇城国图申匹互京泪洋拥抱相扬讲打指接惊故侯奇寸落补拔功助取所信沿拾际蛙错答还言每治棵挂哇怪慢怎思穿弯比服浅漂啦啊夫表示号汗伤吸极串免告诉狐狸猴颗斤折挑根独满容易采背板椅但傍清消由术吐注课铅笔桌景拿坏松扎抓祝福句幸之令布直当第现期轮路丑永饥饱温贫富户亚角周床病始张寻哭良食双体操场份粉昨晴姑娘妹读舟乘音客何汪丛牢拍护保物鸡猫羽领捉理跃蹦灵晨失觉扔掉眼睛纸船久乎至死腰捡粒被并夜喜重味轻刻群卫运宇宙航舰冲晒池浮灾害黑器岸纹洞影倒游圆围杯件住须能飘必事历史灭克化代孙植厂产介农科技纺织脱冻溪棉探摇野躲解未追店枯徐烧荣菜宿冈世界轰笋芽喊呼唤弟哥骨抽拐浇终静躺谢渐微瓦泉然结股脆塔杜鹃冒雷需迈迷迹叔锋滴洒泥泞扑托摸利铃弱末芬芳夏应该岛展建纱环绕胜隐约省茂盛吾季留杏密蜜坡搭摘钉沟够龙恩寿柏泼特敬鲜脚度凤凰束勾府单夺宫扮雄伟烁辉煌色另志题提漫朗哄喝骗刀尔求仍使便英票整式而且丹乌艺显忽丝杆眨涛陈转斜吴含窗炉岭鸣绝银烟泊流柳垂乱沉压逃越阵彩虹蝉蜘蛛册岩宝趴印刨埋陆铁质厚底忠导盏积稠稀针碰慌兄呆商抹挤拱决价钱购批评报玻璃拾破碎滑继续封骄傲拎桶停聪胳膊甸晃荡叭玲狗糟楼梯肯脑筋讶谈派引列峰敲附近守丢焦费望算此桩肥灰讨厌冰蛋壳鸭欺负鹅翅膀勺斗玉组珍珠数钻研睡距离油检查团斥责炎夸奖亡肉耐谜传染类严寒",
                    3 to "晨绒球汉艳服装扮读静停粗影落荒笛舞狂罚假互所够猜扬臂寒径斜霜赠刘盖菊残君橙送挑铺泥晶紧院印排列规则乱棕迟盒颜料票飘争仙淡闻梨勾曲丰冷离等剩斧砍谷柴煤油诉睁接旅咱怜救命拼扫胃管刚流泪算洞准备暴墙壁饿蜘蛛漂撞饱晒搭亲父沙啦响羽翠嘴悄吞哦捕蒲英盛耍喊欠钓而察拢趣喜睡断楚至孤帆饮初镜未磨遥银盘富优浅错岩虾挺鼓数厚宝贵滨灰渔遍躺载靠栽亚夏除踩洁脑袋严实挡视线坛显材软刮库妙演奏琴柔感受激击器滴敲鸣朝雾蒙鼻总抖露湿吸猎翅膀重刺枣颗忽乎暗伸匆沟聪偷追腰司庭登跌众弃持掌班默腿轮回投调摇晃烈勇雀郊养粉粒男或者冻惜肯诚尾招些劲勇仰芝蒜郊低准卧佳丧邮内减具丘丈举划刮矛兵初陶况险卡骂婆猪鹅数破布补锯舌牙齿试买妹背打背饱吵桥棚拾题龟吓房师晴工绳分清龙王神晃象抓容易浪费狼撞敢鹿客波姑娘篮虾烟江舞蹈称将军曹操运杀切冲父沈沿岸斤量鲁班国古匠造需料派砍斧够缺野指被血具忘疼痛省假威森寻食扑狡猾珠命反管兽趟活半平凶恶骗麦挡应该浅淹惊深昨伙伴愿危险关原蛋甘罗茂皇帝官爷香助摸肩叹苦令午内实虽改主跪恭敬替假慌响楚求勇感救穷尚富部川瘦拜佛碗钱岭化缘庙相年辛终于达访惜本志者竟迷确忠导引影阴密稀盏永乱特融积商迎跛紧详较迹至究必须顺继续岳战胜织养校教岁卖纸街簸箕沙插双捧全交母铺而且历史复返注匆躺便场嘀嗒秒或限验似乎使善利获功寸壮努徒悲嗨副严肃鲜嗯临叮咛强结猜鞠躬中希念级赞骄傲劳曾镇市筐瓷器店架歪简系坡哐啷摔粉碎荡桨推映塔环绕夕洒愉尽爽院馆品超厕愚移附妻犹豫堆抢填领邻居参渐智叟嘲笨糊涂团坚持顾仍止仙燕聚增掠稻尖偶沾圈漾倦符演赞咏碧妆裁剪滨紫荷挨莲蓬账仿佛裳翩蹈蜻蜓翠秆腹赤衬衫透泛泡饲翁陡壁欧洲瑞士舒启殊骤涉疲政踏救载森郁葱湛盖犁砍裸扩栋柴喘黎寓则窟窿狼叼街劝悔盘缠硬弓魏射箭猎雁弦悲惨愈痛裂叮嘱排靠幅审肃晌悦熟悉诲赛疼忧慰梭虽狂赢暑益穷将若俱博鸦截伍默局棒羡慕禁席众纠匠替抄墨骂缩承肩扛缘愤毕戒既贺顾迅速复恰犯缓婆议达稚烦享炸医输眉型否垫酒掩咬拳制柔渴罐累竟匆哀舔反递忍凑咽唾沫涌差抵氏庄稼兽存繁殖蔬麻较杀预幕临悬曾奥努登任撒藻旦项估龄络箱迫悟盯鼠唐警眯览敞寄秒恋彤霞陪趁窄脖段漆胆踪镇摊鼻忧换摔竖卖售驮构端掏馆饭辨堆模付标齿乞巧霄渡屏烛晓偷淹官逼姓挣旱徒腾催吊跪渠灌溉隆塌露燃熊挣熄喷缺纯冶炼盆",
                    4 to "蒜郊低准卧佳丧邮内减具丘丈举划刮矛兵初陶况险卡骂婆猪鹅数破布补锯舌牙齿试买妹背打背饱吵桥棚拾题龟吓房师晴工绳分清龙王神晃象抓容易浪费狼撞敢鹿客波姑娘篮虾烟江舞蹈称将军曹操运杀切冲父沈沿岸斤量鲁班国古匠造需料派砍斧够缺野指被血具忘疼痛省假威森寻食扑狡猾珠命反管兽趟活半平凶恶骗麦挡应该浅淹惊深昨伙伴愿危险关原蛋甘罗茂皇帝官爷香助摸肩叹苦令午内实虽改主跪恭敬替假慌响楚求勇感救穷尚富部川瘦拜佛碗钱岭化缘庙相年辛终于达访惜本志者竟迷确忠导引影阴密稀盏永乱特融积商迎跛紧详较迹至究必须顺继续岳战胜织养校教岁卖纸街簸箕沙插双捧全交母铺而且历史复返注匆躺便场嘀嗒秒或限验似乎使善利获功寸壮努徒悲嗨副严肃鲜嗯临叮咛强结猜鞠躬中希念级赞骄傲劳曾镇市筐瓷器店架歪简系坡哐啷摔粉碎荡桨推映塔环绕夕洒愉尽爽院馆品超厕愚移附妻犹豫堆抢填领邻居参渐智叟嘲笨糊涂团坚持顾仍止仙燕聚增掠稻尖偶沾圈漾倦符演赞咏碧妆裁剪滨紫荷挨莲蓬账仿佛裳翩蹈蜻蜓翠秆腹赤衬衫透泛泡饲翁陡壁欧洲瑞士舒启殊骤涉疲政踏救载森郁葱湛盖犁砍裸扩栋柴喘黎寓则窟窿狼叼街劝悔盘缠硬弓魏射箭猎雁弦悲惨愈痛裂叮嘱排靠幅审肃晌悦熟悉诲赛疼忧慰梭虽狂赢暑益穷将若俱博鸦截伍默局棒羡慕禁席众纠匠替抄墨骂缩承肩扛缘愤毕戒既贺顾迅速复恰犯缓婆议达稚烦享炸医输眉型否垫酒掩咬拳制柔渴罐累竟匆哀舔反递忍凑咽唾沫涌差抵氏庄稼兽存繁殖蔬麻较杀预幕临悬曾奥努登任撒藻旦项估龄络箱迫悟盯鼠唐警眯览敞寄秒恋彤霞陪趁窄脖段漆胆踪镇摊鼻忧换摔竖卖售驮构端掏馆饭辨堆模付标齿乞巧霄渡屏烛晓偷淹官逼姓挣旱徒腾催吊跪渠灌溉隆塌露燃熊挣熄喷缺纯冶炼盆",
                    5 to "羞勒偏迂吟拘涩涯渲毯襟貌跤蹄匕凹芝循奂贺戎矗鸵陷伞抚绍晰疆剥遮牧卸傻咀倭啃嘟厘亏妨乃诣禽谊谣役奠咆呻丞延呐郎侄帕莞嫣暇叉哗姆弛彬绅奄唉噎诺诸召凝仅窃炒锅踮哟饿惧充檐皱碗酸撑柜侣娱盒豫趟诵零编某洛榆畔帐魂缕幽葬愁腮甚绸呜谓梳衰绢侨鲸猪腭哺滤肚肺矮判胎盗嫌夹恙藕粘噪废捞饵溅钩翼纵啪鳃皎唇沮诱诫践亩尝吩咐茅榨榴杉矶混昔墟曼疾爆砾砸颤糕迪搂豪誊置司妙版慈祥歧谨慎损皇珑剔杭莱瑤宏宋侵统销瑰烬庙务葛吼腔崎岖尸斩坠雹仇恨眺丸崖岷典副委协宾泽奏诞钮瞻拂骑嗓党毯渲勒吟迂襟蹄貌拘羞涩跤偏涯晰伞抚绍疆陷牧蓑遮醉媚锄剥毡卸咀嚼漠寞袄袍傻胚祸患臂赋淘妨岂痴绞汁厘愧亏梁惠诣乃曰禽侮辱谎敝矩囚嘻臣淮柑橘枳贼赔妮役硝炭谊谣噩耗跺嫂挎篮咆哮疯狞淌肆揪豹瞪呻膛搀祭奠赵璧召诺怯瑟拒诸荆妒忌曹督甘鲁延幔私寨擂呐援丞擞绽扳咚监侄郎皆敛媳骚宗怜帕脊莞锦姹嫣暇颇尼艇叉艄翘翘舱姆祷雇哗",
                    6 to "邀俯瀑峭躯津蕴侠谧巷俏逗庞烘烤韵勤勉吻施挠庸艰毅铲劣惹讥浆岔挚寝频朦胧凄斑篇搁填怨掀唉裹魁梧淋撕霉虑悠仪歉溜嘿割晶莹蔼资矿赐竭滥胁睹嗡鹿骏鹰潺脂婴眷扭胯厨套猬畜窜挽囫囵枣搞恍霜详逝章咳嗽塑饼谱抑挫歇吉营劈寇蕉筒躁革遭泣浴搏碑茵蜡陌盲键粼霎录挪蒸秧萎番锻雅勃旬熬蒜醋饺翡拌榛栗筝鞭麦寺逛籍屉怖瞅魔胖刑哼峻残匪窝啃舅鸿鼎旺炊乖裙兜币哎橱锈摩揉玛蘸毒撇噎搓匣喳吭娜伊搅埃伦藤析碱顽卓效蚀乏誉衔粪捐澡械逆玫域"
                )

                // 准备快速查找表
                val charToGrade = mutableMapOf<Char, Int>()
                gradeMap.forEach { (grade, chars) ->
                    chars.forEach { charToGrade[it] = grade }
                }

                val charactersList = mutableListOf<Character>()

                // 第一步：从 quiz.json 提取全局词组 (用于组词参考)
                val globalWords = mutableListOf<String>()
                try {
                    val qStream = context.assets.open("quiz.json")
                    val qText = qStream.bufferedReader().use { it.readText() }
                    val qRoot = Gson().fromJson(qText, JsonObject::class.java)
                    val qData = qRoot.getAsJsonArray("data")
                    for (i in 0 until qData.size()) {
                        val element = qData.get(i).asJsonObject
                        val content = element.getAsJsonArray("content")
                        for (j in 0 until content.size()) {
                            val text = content.get(j).asString
                            if (text.length > 1) globalWords.add(text)
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                // 第二步：综合数据导入 - 扫描 assets/hanzi-data
                val hanziDataPath = "hanzi-data"
                val assetFiles = context.assets.list(hanziDataPath) ?: emptyArray()
                
                // 仅处理常见的汉字命名文件 (x.json)
                val allCharsFromAssets = assetFiles
                    .filter { it.endsWith(".json") && it.length >= 6 } 
                    .map { it.substring(0, it.lastIndexOf(".")) }
                    .toSet()

                allCharsFromAssets.forEach { s ->
                    val char = s[0]
                    // 确定年级：根据 gradeMap 大纲，不在大纲中的归为 6
                    val finalGrade = charToGrade[char] ?: 6
                    
                    charactersList.add(
                        Character(
                            id = s,
                            pinyin = "", // 稍后由 ViewModel 加载数据填充
                            strokes = "",
                            grade = finalGrade,
                            exampleWords = globalWords.filter { it.contains(s) }.sortedByDescending { it.length }.take(3)
                        )
                    )
                }

                if (charactersList.isNotEmpty()) {
                    characterDao.deleteAll()
                    characterDao.insertAll(charactersList)
                    prefs.edit().putInt("char_data_version", targetVersion).apply()
                    android.util.Log.d("CharacterRepository", "Imported ${charactersList.size} characters")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

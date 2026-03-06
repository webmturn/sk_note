DELETE FROM references_doc;

-- ========== 变量 (var) - #FFEE7D16 橙色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('Number 变量', '变量', 'variable', '存储数值（整数或小数）的变量。', '用于计数、计算、存储分数等场景。', '类型: double', 'double score = 0;\nscore = score + 10;', '#FFEE7D16', 's'),
('String 变量', '变量', 'variable', '存储文本字符串的变量。', '用于存储用户名、消息内容等文本数据。', '类型: String', 'String name = "";\nname = edittext1.getText().toString();', '#FFEE7D16', 's'),
('Boolean 变量', '变量', 'variable', '存储真(true)/假(false)值的变量。', '用于标记状态，如是否登录、是否显示等。', '类型: boolean', 'boolean isLoggedIn = false;\nisLoggedIn = true;', '#FFEE7D16', 's'),
('Map 变量', '变量', 'variable', '存储键值对数据的变量。', '用于存储结构化数据，如用户信息。', '类型: HashMap<String, Object>', 'HashMap map = new HashMap<>();\nmap.put("name", "张三");', '#FFEE7D16', 's'),
('设置变量', '变量', 'block', '将一个值赋给变量。', '基础赋值操作，可以设置数值、字符串、布尔值等。', 'variable: 目标变量\nvalue: 要赋的值', 'score = 100;', '#FFEE7D16', 's'),
('变量自增', '变量', 'block', '将变量的值增加指定数量。', '常用于计数器递增。', 'variable (number): 目标变量\namount (number): 增加的值', 'count = count + 1;', '#FFEE7D16', 's');

-- ========== 列表 (list) - #FFCC5B22 深橙色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('List String', '列表', 'variable', '字符串列表变量。存储多个文本项的有序集合。', '用于存储聊天消息、菜单选项等。', '类型: ArrayList<String>', 'ArrayList<String> items = new ArrayList<>();\nitems.add("Apple");', '#FFCC5B22', 's'),
('List Number', '列表', 'variable', '数值列表变量。存储多个数值的有序集合。', '用于存储分数、价格等数值列表。', '类型: ArrayList<Double>', 'ArrayList<Double> scores = new ArrayList<>();\nscores.add(95.5);', '#FFCC5B22', 's'),
('List Map', '列表', 'variable', 'Map 列表变量。存储多个键值对对象的集合。', '用于存储用户列表、商品列表等结构化数据。', '类型: ArrayList<HashMap<String, Object>>', 'ArrayList<HashMap> users = new ArrayList<>();', '#FFCC5B22', 's'),
('添加到列表', '列表', 'block', '向列表末尾添加一个元素。', '往列表中追加新项。', 'list: 目标列表\nvalue: 要添加的值', 'list.add("新项目");', '#FFCC5B22', 's'),
('插入到列表', '列表', 'block', '在列表的指定位置插入一个元素。', '在列表中间插入新项。', 'list: 目标列表\nindex (number): 位置\nvalue: 要插入的值', 'list.add(0, "第一项");', '#FFCC5B22', 's'),
('从列表删除', '列表', 'block', '删除列表中指定位置的元素。', '移除不需要的列表项。', 'list: 目标列表\nindex (number): 要删除的位置', 'list.remove(0);', '#FFCC5B22', 's'),
('列表长度', '列表', 'block', '获取列表中元素的数量。', '检查列表有多少项，常用于循环。', 'list: 目标列表\n返回: number', 'int size = list.size();', '#FFCC5B22', 'd'),
('获取列表项', '列表', 'block', '获取列表中指定位置的元素。', '读取列表中特定位置的值。', 'list: 目标列表\nindex (number): 位置\n返回: 该位置的值', 'String item = list.get(0);', '#FFCC5B22', 's'),
('清空列表', '列表', 'block', '删除列表中的所有元素。', '重置列表为空。', 'list: 目标列表', 'list.clear();', '#FFCC5B22', 's'),
('列表包含', '列表', 'block', '检查列表是否包含指定元素。', '判断某个值是否在列表中。', 'list: 目标列表\nvalue: 要查找的值\n返回: boolean', 'boolean has = list.contains("Apple");', '#FFCC5B22', 'b'),
('列表索引', '列表', 'block', '获取指定元素在列表中的位置。', '查找元素的索引位置，找不到返回 -1。', 'list: 目标列表\nvalue: 要查找的值\n返回: number', 'int pos = list.indexOf("Apple");', '#FFCC5B22', 'd');

-- ========== 控制 (control) - #FFE1A92A 金黄色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('如果', '控制', 'block', '条件判断积木块。当条件为真时执行内部的积木块。', '用于根据条件执行不同的代码逻辑。将条件放入菱形槽中。', 'condition (boolean): 判断条件', 'if (score > 60) {\n  showMessage("及格");\n}', '#FFE1A92A', 'c'),
('如果 否则', '控制', 'block', '条件分支积木块。条件为真执行第一个分支，否则执行第二个分支。', '用于二选一的逻辑判断。', 'condition (boolean): 判断条件', 'if (age >= 18) {\n  status = "成年";\n} else {\n  status = "未成年";\n}', '#FFE1A92A', 'e'),
('重复', '控制', 'block', '指定次数重复执行内部积木块。', '用于需要重复执行某些操作的场景，如循环添加列表项。', 'count (number): 重复次数', 'for (int i = 0; i < 10; i++) {\n  list.add("Item " + i);\n}', '#FFE1A92A', 'c'),
('永远重复', '控制', 'block', '持续不断地执行内部积木块（死循环）。', '常用于游戏循环或持续监听。需要配合延时或 break 使用。', '无参数', 'while (true) {\n  updateGame();\n  delay(16);\n}', '#FFE1A92A', 'c'),
('等待', '控制', 'block', '暂停执行指定的毫秒数。', '在操作之间添加延时。', 'milliseconds (number): 等待毫秒数', 'Thread.sleep(1000);', '#FFE1A92A', 's'),
('Toast 提示', '控制', 'block', '在屏幕底部显示一条短暂的提示消息。', '给用户简单的反馈信息。', 'message (String): 提示内容', 'Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();', '#FFE1A92A', 's'),
('结束Activity', '控制', 'block', '关闭当前 Activity 页面。', '结束当前页面并返回上一个页面。', '无参数', 'finish();', '#FFE1A92A', 'f'),
('跳转Activity', '控制', 'block', '跳转到另一个 Activity 页面。', '在不同页面之间导航。', 'activity: 目标 Activity', 'startActivity(new Intent(this, SecondActivity.class));', '#FFE1A92A', 's'),
('停止重复', '控制', 'block', '跳出当前循环。', '在循环中满足特定条件时提前结束循环。', '无参数', 'break;', '#FFE1A92A', 'f');

-- ========== 运算 (operator) - #FF5CB722 绿色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('等于', '运算', 'block', '判断两个值是否相等。', '用于条件判断中比较两个值。', 'a: 第一个值\nb: 第二个值\n返回: boolean', 'if (a == b) { ... }', '#FF5CB722', 'b'),
('大于', '运算', 'block', '判断第一个值是否大于第二个值。', '用于数值大小比较。', 'a (number): 第一个值\nb (number): 第二个值\n返回: boolean', 'if (score > 60) { ... }', '#FF5CB722', 'b'),
('小于', '运算', 'block', '判断第一个值是否小于第二个值。', '用于数值大小比较。', 'a (number): 第一个值\nb (number): 第二个值\n返回: boolean', 'if (count < 10) { ... }', '#FF5CB722', 'b'),
('且', '运算', 'block', '逻辑与。两个条件都为真时返回真。', '用于同时满足多个条件的判断。', 'a (boolean): 条件1\nb (boolean): 条件2\n返回: boolean', 'if (age > 18 && hasID) { ... }', '#FF5CB722', 'b'),
('或', '运算', 'block', '逻辑或。任一条件为真时返回真。', '用于满足其中一个条件即可的判断。', 'a (boolean): 条件1\nb (boolean): 条件2\n返回: boolean', 'if (isAdmin || isOwner) { ... }', '#FF5CB722', 'b'),
('非', '运算', 'block', '逻辑非。将真变假，假变真。', '用于取反条件。', 'condition (boolean): 要取反的条件\n返回: boolean', 'if (!isLoggedIn) { ... }', '#FF5CB722', 'b'),
('加', '运算', 'block', '两个数值相加。', '基础加法运算。', 'a (number)\nb (number)\n返回: number', 'result = a + b;', '#FF5CB722', 'd'),
('减', '运算', 'block', '两个数值相减。', '基础减法运算。', 'a (number)\nb (number)\n返回: number', 'result = a - b;', '#FF5CB722', 'd'),
('乘', '运算', 'block', '两个数值相乘。', '基础乘法运算。', 'a (number)\nb (number)\n返回: number', 'result = a * b;', '#FF5CB722', 'd'),
('除', '运算', 'block', '两个数值相除。', '基础除法运算。注意除数不能为 0。', 'a (number)\nb (number)\n返回: number', 'result = a / b;', '#FF5CB722', 'd'),
('取余', '运算', 'block', '取两个数值相除的余数。', '用于判断奇偶、周期性计算等。', 'a (number)\nb (number)\n返回: number', 'remainder = a % b;', '#FF5CB722', 'd'),
('连接字符串', '运算', 'block', '将两个字符串拼接在一起。', '用于组合文本内容。', 'a (String)\nb (String)\n返回: String', 'result = "Hello " + name;', '#FF5CB722', 's'),
('字符串长度', '运算', 'block', '获取字符串的字符数。', '检查文本的长度。', 'text (String)\n返回: number', 'int len = text.length();', '#FF5CB722', 'd'),
('字符串包含', '运算', 'block', '检查字符串是否包含指定文本。', '用于搜索、过滤文本。', 'text (String): 原字符串\nsearch (String): 要查找的文本\n返回: boolean', 'boolean has = text.contains("hello");', '#FF5CB722', 'b'),
('随机数', '运算', 'block', '生成指定范围内的随机整数。', '用于游戏、随机选择等场景。', 'min (number): 最小值\nmax (number): 最大值\n返回: number', 'int r = new Random().nextInt(100);', '#FF5CB722', 'd'),
('大于等于', '运算', 'block', '判断第一个值是否大于或等于第二个值。', '用于数值大小比较。', 'a (number)\nb (number)\n返回: boolean', 'if (score >= 60) { ... }', '#FF5CB722', 'b'),
('小于等于', '运算', 'block', '判断第一个值是否小于或等于第二个值。', '用于数值大小比较。', 'a (number)\nb (number)\n返回: boolean', 'if (count <= 0) { ... }', '#FF5CB722', 'b'),
('不等于', '运算', 'block', '判断两个值是否不相等。', '用于条件判断中比较两个值不同。', 'a: 第一个值\nb: 第二个值\n返回: boolean', 'if (a != b) { ... }', '#FF5CB722', 'b'),
('字符串比较', '运算', 'block', '比较两个字符串是否相等。', '字符串比较应使用 equals 而非 ==。', 'a (String)\nb (String)\n返回: boolean', 'if (name.equals("admin")) { ... }', '#FF5CB722', 'b'),
('截取字符串', '运算', 'block', '截取字符串的一部分。', '从指定位置截取文本。', 'text (String)\nstart (number): 起始位置\nend (number): 结束位置\n返回: String', 'String sub = text.substring(0, 5);', '#FF5CB722', 's'),
('转换为数字', '运算', 'block', '将字符串转换为数值。', '把用户输入的文本转为数字进行计算。', 'text (String)\n返回: number', 'double num = Double.parseDouble(text);', '#FF5CB722', 'd'),
('转换为字符串', '运算', 'block', '将数值转换为字符串。', '把数字转为文本进行显示或拼接。', 'number (number)\n返回: String', 'String s = String.valueOf(num);', '#FF5CB722', 's');

-- ========== 数学 (math) - #FF23B9A9 青绿色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('绝对值', '数学', 'block', '返回数值的绝对值（非负值）。', '去掉数值的负号。', 'number (number)\n返回: number', 'double abs = Math.abs(-5); // 5', '#FF23B9A9', 'd'),
('四舍五入', '数学', 'block', '将数值四舍五入为最接近的整数。', '用于精度处理。', 'number (number)\n返回: number', 'long r = Math.round(3.7); // 4', '#FF23B9A9', 'd'),
('向上取整', '数学', 'block', '将数值向上取整到最近的整数。', '用于计算页数等场景。', 'number (number)\n返回: number', 'double c = Math.ceil(3.1); // 4', '#FF23B9A9', 'd'),
('向下取整', '数学', 'block', '将数值向下取整到最近的整数。', '用于截断小数部分。', 'number (number)\n返回: number', 'double f = Math.floor(3.9); // 3', '#FF23B9A9', 'd'),
('最大值', '数学', 'block', '返回两个数值中较大的一个。', '用于取较大值。', 'a (number)\nb (number)\n返回: number', 'double max = Math.max(a, b);', '#FF23B9A9', 'd'),
('最小值', '数学', 'block', '返回两个数值中较小的一个。', '用于取较小值。', 'a (number)\nb (number)\n返回: number', 'double min = Math.min(a, b);', '#FF23B9A9', 'd'),
('幂运算', '数学', 'block', '计算一个数的指定次幂。', '用于平方、立方等运算。', 'base (number): 底数\nexp (number): 指数\n返回: number', 'double p = Math.pow(2, 10); // 1024', '#FF23B9A9', 'd'),
('平方根', '数学', 'block', '计算数值的平方根。', '用于数学计算。', 'number (number)\n返回: number', 'double s = Math.sqrt(16); // 4', '#FF23B9A9', 'd'),
('正弦', '数学', 'block', '计算角度的正弦值（弧度）。', '用于三角函数计算、动画等。', 'angle (number): 弧度值\n返回: number', 'double s = Math.sin(Math.PI / 2);', '#FF23B9A9', 'd'),
('余弦', '数学', 'block', '计算角度的余弦值（弧度）。', '用于三角函数计算、动画等。', 'angle (number): 弧度值\n返回: number', 'double c = Math.cos(0); // 1', '#FF23B9A9', 'd'),
('正切', '数学', 'block', '计算角度的正切值（弧度）。', '用于三角函数计算。', 'angle (number): 弧度值\n返回: number', 'double t = Math.tan(Math.PI / 4);', '#FF23B9A9', 'd'),
('对数', '数学', 'block', '计算自然对数（以 e 为底）。', '用于数学和科学计算。', 'number (number)\n返回: number', 'double l = Math.log(Math.E); // 1', '#FF23B9A9', 'd'),
('PI', '数学', 'block', '圆周率常量 π ≈ 3.14159。', '用于圆形相关计算。', '返回: 3.141592653589793', 'double area = Math.PI * r * r;', '#FF23B9A9', 'd'),
('E', '数学', 'block', '自然常数 e ≈ 2.71828。', '用于指数和对数计算。', '返回: 2.718281828459045', 'double val = Math.E;', '#FF23B9A9', 'd');

-- ========== 文件 (file) - #FFA1887F 棕色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('读取文件', '文件操作', 'block', '从本地存储读取文件内容为字符串。', '读取文本文件的内容。', 'path (String): 文件路径\n返回: String', 'String content = FileUtil.readFile(path);', '#FFA1887F', 's'),
('写入文件', '文件操作', 'block', '将字符串写入本地文件。', '保存数据到本地文件。', 'path (String): 文件路径\ncontent (String): 要写入的内容', 'FileUtil.writeFile(path, content);', '#FFA1887F', 's'),
('文件是否存在', '文件操作', 'block', '检查指定路径的文件是否存在。', '在读取文件前先检查是否存在。', 'path (String): 文件路径\n返回: boolean', 'boolean exists = new File(path).exists();', '#FFA1887F', 'b'),
('删除文件', '文件操作', 'block', '删除指定路径的文件。', '清理不需要的文件。', 'path (String): 文件路径', 'new File(path).delete();', '#FFA1887F', 's'),
('创建文件夹', '文件操作', 'block', '创建指定路径的目录。', '在保存文件前确保目录存在。', 'path (String): 目录路径', 'new File(path).mkdirs();', '#FFA1887F', 's'),
('获取外部存储路径', '文件操作', 'block', '获取设备外部存储的根路径。', '用于构建文件存储路径。', '返回: String', 'String path = Environment.getExternalStorageDirectory().getPath();', '#FFA1887F', 's');

-- ========== 控件功能 (view) - #FF4A6CD4 蓝色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('设置文本', '控件操作', 'block', '设置 TextView 的文本内容。', '动态更改文本控件显示的内容。', 'view (TextView): 目标控件\ntext (String): 要设置的文本', 'textview1.setText("Hello");', '#FF4A6CD4', 's'),
('获取文本', '控件操作', 'block', '获取 EditText 的文本内容。', '读取用户输入。', 'view (EditText): 目标控件\n返回: String', 'String input = edittext1.getText().toString();', '#FF4A6CD4', 's'),
('设置可见性', '控件操作', 'block', '控制控件的显示和隐藏。', '动态显示/隐藏界面元素。', 'view (View): 目标控件\nvisibility: VISIBLE/INVISIBLE/GONE', 'button1.setVisibility(View.GONE);', '#FF4A6CD4', 's'),
('设置可用性', '控件操作', 'block', '设置控件是否可交互。', '禁用或启用按钮等控件。', 'view (View): 目标控件\nenabled (boolean)', 'button1.setEnabled(false);', '#FF4A6CD4', 's'),
('设置图片', '控件操作', 'block', '设置 ImageView 的图片资源。', '动态更换图片。', 'view (ImageView): 目标控件\nresource: 图片资源', 'imageview1.setImageResource(R.drawable.icon);', '#FF4A6CD4', 's'),
('设置背景颜色', '控件操作', 'block', '设置控件的背景颜色。', '动态更改控件背景色。', 'view (View): 目标控件\ncolor (int): 颜色值', 'view.setBackgroundColor(Color.RED);', '#FF4A6CD4', 's'),
('设置文字颜色', '控件操作', 'block', '设置 TextView 的文字颜色。', '动态更改文字颜色。', 'view (TextView): 目标控件\ncolor (int): 颜色值', 'textview1.setTextColor(Color.BLUE);', '#FF4A6CD4', 's'),
('设置文字大小', '控件操作', 'block', '设置 TextView 的文字大小。', '动态调整文字尺寸。', 'view (TextView): 目标控件\nsize (float): 字号(sp)', 'textview1.setTextSize(18);', '#FF4A6CD4', 's'),
('设置提示文字', '控件操作', 'block', '设置 EditText 的提示文字(hint)。', '提示用户输入什么内容。', 'view (EditText): 目标控件\nhint (String): 提示文字', 'edittext1.setHint("请输入用户名");', '#FF4A6CD4', 's'),
('设置选中状态', '控件操作', 'block', '设置 CheckBox/Switch 的选中状态。', '以代码方式切换选中状态。', 'view (CompoundButton)\nchecked (boolean)', 'checkbox1.setChecked(true);', '#FF4A6CD4', 's'),
('获取选中状态', '控件操作', 'block', '获取 CheckBox/Switch 是否选中。', '检查用户的选择状态。', 'view (CompoundButton)\n返回: boolean', 'boolean checked = checkbox1.isChecked();', '#FF4A6CD4', 'b'),
('设置进度', '控件操作', 'block', '设置 ProgressBar/SeekBar 的进度值。', '更新进度条显示。', 'view (ProgressBar)\nprogress (int): 进度值', 'seekbar1.setProgress(50);', '#FF4A6CD4', 's'),
('滚动到位置', '控件操作', 'block', '将 ListView/ScrollView 滚动到指定位置。', '程序化控制滚动。', 'view: 目标控件\nposition (int): 位置', 'listview1.smoothScrollToPosition(0);', '#FF4A6CD4', 's');

-- ========== 控件 (widget) - #FF4A6CD4 蓝色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('Button', 'UI控件', 'widget', '按钮控件。用户点击触发操作。', '最常用的交互控件，配合 onClick 事件使用。', '常用属性: text, enabled, backgroundTint', 'button1.setText("提交");', '#FF4A6CD4', 's'),
('TextView', 'UI控件', 'widget', '文本显示控件。展示静态或动态文本。', '显示标题、描述、状态信息等。', '常用属性: text, textSize, textColor, gravity', 'textview1.setText("欢迎使用");', '#FF4A6CD4', 's'),
('EditText', 'UI控件', 'widget', '文本输入控件。允许用户输入文本。', '用于表单输入，如用户名、密码等。', '常用属性: hint, text, inputType, maxLines', 'String input = edittext1.getText().toString();', '#FF4A6CD4', 's'),
('ImageView', 'UI控件', 'widget', '图片显示控件。展示图片资源。', '显示本地或网络图片。', '常用属性: src, scaleType', 'imageview1.setImageResource(R.drawable.logo);', '#FF4A6CD4', 's'),
('LinearLayout', '布局', 'widget', '线性布局。子控件按水平或垂直方向排列。', '最基础的布局容器。', '常用属性: orientation, gravity, weightSum', '// orientation="vertical"', '#FF4A6CD4', 's'),
('ScrollView', '布局', 'widget', '滚动视图。内容超出屏幕时可滚动。', '包裹一个子布局使其可垂直滚动。', '常用属性: fillViewport', '// 包裹 LinearLayout 实现长页面', '#FF4A6CD4', 's'),
('ListView', 'UI控件', 'widget', '列表视图。以垂直列表展示数据。', '配合 Adapter 使用展示列表数据。', '常用属性: divider, listSelector', 'listview1.setAdapter(adapter);', '#FF4A6CD4', 's'),
('Spinner', 'UI控件', 'widget', '下拉选择控件。从选项中选择一个。', '用于有限选项的选择场景。', '常用属性: entries, spinnerMode', 'spinner1.setSelection(0);', '#FF4A6CD4', 's'),
('CheckBox', 'UI控件', 'widget', '复选框。可选中或取消。', '用于多选场景。', '常用属性: checked, text', 'if (checkbox1.isChecked()) { ... }', '#FF4A6CD4', 's'),
('Switch', 'UI控件', 'widget', '开关控件。在开/关间切换。', '用于设置页面的功能开关。', '常用属性: checked, text', 'switch1.setChecked(true);', '#FF4A6CD4', 's'),
('SeekBar', 'UI控件', 'widget', '滑动条控件。通过拖动调整数值。', '用于音量、亮度等连续值调节。', '常用属性: max, progress', 'seekbar1.setProgress(50);', '#FF4A6CD4', 's'),
('WebView', 'UI控件', 'widget', '网页视图。在应用中显示网页。', '嵌入网页内容或加载 HTML。', '常用方法: loadUrl, loadData', 'webview1.loadUrl("https://example.com");', '#FF4A6CD4', 's'),
('ProgressBar', 'UI控件', 'widget', '进度条控件。显示加载进度。', '用于下载进度、加载状态等。', '常用属性: max, progress, indeterminate', 'progressbar1.setProgress(75);', '#FF4A6CD4', 's'),
('MapView', 'UI控件', 'widget', '地图视图。显示 Google 地图。', '用于地图展示和定位功能。', '需要 Google Maps API Key', '// 需要配置 Google Maps SDK', '#FF4A6CD4', 's'),
('CalendarView', 'UI控件', 'widget', '日历视图。显示可选择日期的日历。', '用于日期选择场景。', '常用属性: minDate, maxDate', 'calendarview1.setDate(System.currentTimeMillis());', '#FF4A6CD4', 's'),
('AdView', 'UI控件', 'widget', '广告视图。显示 AdMob 广告。', '用于应用内广告展示。', '需要 AdMob 账号和广告单元 ID', '// 需要配置 AdMob SDK', '#FF4A6CD4', 's');

-- ========== 组件功能 (component) - #FF2CA5E2 天蓝色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('SharedPreferences', '数据存储', 'component', '轻量级键值对持久化存储。', '保存应用设置、用户偏好等小量数据。', '常用方法: getString, putString, getInt, putInt', 'sp.edit().putString("key", value).apply();', '#FF2CA5E2', 's'),
('Intent', '系统组件', 'component', '意图组件。页面跳转和数据传递。', '在不同 Activity 间导航和传递数据。', '常用方法: putExtra, getStringExtra', 'Intent i = new Intent(this, B.class);\ni.putExtra("id", 1);\nstartActivity(i);', '#FF2CA5E2', 's'),
('Timer', '工具组件', 'component', '定时器。按间隔重复执行任务。', '用于倒计时、定时刷新等。', 'delay (long): 延迟ms\nperiod (long): 间隔ms', 'timer.schedule(task, 0, 1000);', '#FF2CA5E2', 's'),
('Dialog', 'UI组件', 'component', '对话框。显示模态对话框。', '用于确认、输入、选择等交互。', '常用方法: setTitle, setMessage, show', 'new AlertDialog.Builder(this)\n  .setTitle("提示")\n  .setMessage("确定删除?")\n  .show();', '#FF2CA5E2', 's'),
('Calendar', '工具组件', 'component', '日历组件。获取和操作日期时间。', '用于获取当前时间、格式化日期。', '常用方法: get, set, getTimeInMillis', 'int year = Calendar.getInstance().get(Calendar.YEAR);', '#FF2CA5E2', 's'),
('Vibrator', '系统组件', 'component', '振动器。控制设备振动。', '用于触觉反馈。', 'duration (long): 振动毫秒数', 'vibrator.vibrate(200);', '#FF2CA5E2', 's'),
('MediaPlayer', '多媒体', 'component', '媒体播放器。播放音频文件。', '播放背景音乐、音效。', '常用方法: start, pause, stop, release', 'MediaPlayer mp = MediaPlayer.create(this, R.raw.bgm);\nmp.start();', '#FF2CA5E2', 's'),
('SoundPool', '多媒体', 'component', '音效池。同时播放多个短音效。', '适合游戏音效、按钮音效。', '常用方法: load, play', 'soundPool.play(soundId, 1, 1, 1, 0, 1);', '#FF2CA5E2', 's'),
('Camera', '系统组件', 'component', '相机。调用系统相机拍照。', '拍照、扫码等功能。', '常用方法: takePicture', 'Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);', '#FF2CA5E2', 's'),
('FilePicker', '系统组件', 'component', '文件选择器。选择本地文件。', '选择图片、文档等文件。', 'type (String): MIME 类型', 'Intent i = new Intent(Intent.ACTION_GET_CONTENT);\ni.setType("image/*");', '#FF2CA5E2', 's'),
('RequestNetwork', '网络', 'component', '网络请求组件。发送 HTTP 请求。', '请求 API 接口获取数据。', '常用方法: startRequestNetwork, GET/POST', 'requestNetwork.startRequestNetwork("GET", url, tag, listener);', '#FF2CA5E2', 's'),
('FirebaseDB', 'Firebase', 'component', 'Firebase 实时数据库组件。', '云端数据的实时读写和同步。', '常用方法: push, setValue, addListenerForSingleValueEvent', 'firebase.child("users").push().setValue(data);', '#FF2CA5E2', 's'),
('FirebaseAuth', 'Firebase', 'component', 'Firebase 认证组件。', '用户注册、登录、登出。', '常用方法: signIn, createUser, signOut', 'firebaseAuth.signInWithEmailAndPassword(email, pass);', '#FF2CA5E2', 's'),
('FirebaseStorage', 'Firebase', 'component', 'Firebase 云存储组件。', '上传和下载文件。', '常用方法: upload, download, delete', 'storage.child("images/photo.jpg").putFile(uri);', '#FF2CA5E2', 's'),
('ObjectAnimator', '动画', 'component', '属性动画组件。对控件属性做动画。', '实现平移、旋转、缩放、透明度动画。', '常用属性: translationX/Y, rotation, alpha, scaleX/Y', 'ObjectAnimator.ofFloat(view, "alpha", 0, 1).start();', '#FF2CA5E2', 's'),
('Gyroscope', '传感器', 'component', '陀螺仪组件。检测设备旋转。', '用于游戏控制、方向检测。', '返回: x, y, z 轴旋转速率', '// 在 onSensorChanged 中获取数据', '#FF2CA5E2', 's'),
('TextToSpeech', '多媒体', 'component', '文字转语音组件。将文本朗读出来。', '用于语音播报。', '常用方法: speak, stop, setPitch, setSpeechRate', 'tts.speak("你好", TextToSpeech.QUEUE_FLUSH, null);', '#FF2CA5E2', 's'),
('SpeechToText', '多媒体', 'component', '语音转文字组件。识别语音为文本。', '用于语音输入。', '常用方法: startListening', '// 在 onResults 中获取识别结果', '#FF2CA5E2', 's'),
('BluetoothConnect', '系统组件', 'component', '蓝牙连接组件。连接蓝牙设备。', '用于蓝牙数据传输。', '常用方法: connect, send, disconnect', 'bluetooth.connect(deviceAddress);', '#FF2CA5E2', 's'),
('LocationManager', '系统组件', 'component', '位置管理器。获取设备地理位置。', '用于定位、导航。', '常用方法: requestLocationUpdates', '// 在 onLocationChanged 中获取经纬度', '#FF2CA5E2', 's'),
('Notification', '系统组件', 'component', '通知组件。发送系统通知。', '向用户推送提醒消息。', '常用方法: setTitle, setMessage, send', 'notification.setTitle("新消息");\nnotification.send();', '#FF2CA5E2', 's');

-- ========== 事件 (event) - #FF4A6CD4 蓝色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('onClick', '点击事件', 'event', '当用户点击控件时触发。', '最常用的事件，绑定到按钮执行操作。', 'view (View): 被点击的控件', 'button1.setOnClickListener(v -> { });', '#FFE1A92A', 'h'),
('onLongClick', '点击事件', 'event', '当用户长按控件时触发。', '用于弹出菜单或删除操作。', 'view (View): 被长按的控件', 'view.setOnLongClickListener(v -> { return true; });', '#FFE1A92A', 'h'),
('onTextChanged', '文本事件', 'event', '当 EditText 文本变化时触发。', '实时搜索、输入验证、字符计数。', 'text (CharSequence): 当前文本', 'edittext1.addTextChangedListener(...);', '#FFE1A92A', 'h'),
('onItemSelected', '选择事件', 'event', '当 Spinner/ListView 选项被选中时触发。', '响应列表选择操作。', 'position (int): 选中位置', 'spinner1.setOnItemSelectedListener(...);', '#FFE1A92A', 'h'),
('onItemClick', '选择事件', 'event', '当 ListView 的列表项被点击时触发。', '响应列表项点击。', 'position (int): 点击位置', 'listview1.setOnItemClickListener(...);', '#FFE1A92A', 'h'),
('onItemLongClick', '选择事件', 'event', '当 ListView 列表项被长按时触发。', '用于列表项的删除、编辑等操作。', 'position (int): 长按位置', 'listview1.setOnItemLongClickListener(...);', '#FFE1A92A', 'h'),
('onCheckedChanged', '状态事件', 'event', '当 CheckBox/Switch 状态改变时触发。', '响应开关切换。', 'isChecked (boolean): 是否选中', 'switch1.setOnCheckedChangeListener(...);', '#FFE1A92A', 'h'),
('onSeekBarChanged', '状态事件', 'event', '当 SeekBar 滑动时触发。', '响应滑动条数值变化。', 'progress (int): 当前进度', 'seekbar1.setOnSeekBarChangeListener(...);', '#FFE1A92A', 'h'),
('onBackPressed', '生命周期', 'event', '用户按下返回键时触发。', '自定义返回行为。', '无参数', 'onBackPressed() { showExitDialog(); }', '#FFE1A92A', 'h'),
('onCreate', '生命周期', 'event', 'Activity 创建时触发。', '初始化界面、数据、事件。', 'savedInstanceState (Bundle)', 'onCreate() { initViews(); }', '#FFE1A92A', 'h'),
('onResume', '生命周期', 'event', 'Activity 恢复到前台时触发。', '刷新数据、恢复动画。', '无参数', 'onResume() { refreshData(); }', '#FFE1A92A', 'h'),
('onPause', '生命周期', 'event', 'Activity 进入后台时触发。', '保存数据、暂停动画。', '无参数', 'onPause() { saveData(); }', '#FFE1A92A', 'h'),
('onPageSelected', '页面事件', 'event', 'ViewPager 页面切换时触发。', '响应标签页切换。', 'position (int): 当前页位置', 'viewPager.addOnPageChangeListener(...);', '#FFE1A92A', 'h'),
('onScrollChanged', '滚动事件', 'event', 'ScrollView 滚动时触发。', '实现滚动监听、懒加载。', 'scrollX, scrollY (int): 滚动位置', '// 检测滚动到底部加载更多', '#FFE1A92A', 'h');

-- ========== 自定义块 (moreblock) - #FF8A55D7 紫色 ==========
INSERT INTO references_doc (name, category, type, description, usage, parameters, example, color, shape) VALUES
('自定义积木块', '自定义', 'block', '用户自定义的可复用积木块（函数）。', '将常用逻辑封装为可复用的积木块，减少重复代码。可以定义参数。', '自定义参数: 支持 String, Number, Boolean 类型', 'void myBlock(String name) {\n  toast("Hello " + name);\n}', '#FF8A55D7', 'h');

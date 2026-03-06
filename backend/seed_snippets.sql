INSERT INTO snippets (title, description, code, language, category, tags, author_id, author_name) VALUES
('Toast 显示消息', '使用 showMessage 块显示 Toast 消息', 'showMessage("Hello World!");', 'java', 'general', 'toast,消息,基础', 1, 'admin'),

('SharedPreferences 保存数据', '使用 SharedPreferences 组件保存和读取数据', 'sp.setData("key", "value");\n// 读取数据\nString data = sp.getData("key", "默认值");', 'java', 'database', 'SharedPreferences,存储,数据', 1, 'admin'),

('Intent 跳转页面', '使用 Intent 组件跳转到另一个 Activity', 'intent.setScreen(SecondActivity.class);\n// 传递数据\nintent.putExtra("key", "value");\nstartActivity(intent);', 'java', 'intent', 'intent,跳转,页面', 1, 'admin'),

('Timer 定时任务', '使用 Timer 组件创建定时执行的任务', '// 在 Timer 的 onTimerTick 事件中写逻辑\ntimer.setRepeating(true);\ntimer.setInterval(1000); // 1秒\ntimer.start();', 'java', 'control', 'timer,定时,计时器', 1, 'admin'),

('RequestNetwork GET 请求', '使用 RequestNetwork 组件发送 HTTP GET 请求', 'requestNetwork.setUrl("https://api.example.com/data");\nrequestNetwork.setMethod("GET");\nrequestNetwork.startRequestNetwork(\n    RequestNetworkController.GET,\n    url,\n    "response_tag",\n    requestNetworkListener\n);', 'java', 'network', 'HTTP,GET,网络请求,API', 1, 'admin'),

('RequestNetwork POST 请求', '使用 RequestNetwork 组件发送带 JSON 的 POST 请求', 'HashMap<String, Object> map = new HashMap<>();\nmap.put("username", "test");\nmap.put("password", "123456");\n\nrequestNetwork.setUrl("https://api.example.com/login");\nrequestNetwork.setMethod("POST");\nrequestNetwork.setRequestBody(new Gson().toJson(map));', 'java', 'network', 'HTTP,POST,JSON,网络请求', 1, 'admin'),

('Firebase 读取数据', '使用 Firebase 组件读取实时数据库中的数据', '// firebase 组件绑定路径\nfirebase.setPath("users/" + uid);\n\n// 在 onChildAdded 事件中接收数据\n// dataSnapshot.getValue() 获取值\n// dataSnapshot.getKey() 获取键', 'java', 'firebase', 'Firebase,数据库,读取', 1, 'admin'),

('Firebase 写入数据', '向 Firebase 实时数据库写入数据', 'HashMap<String, Object> data = new HashMap<>();\ndata.put("name", "张三");\ndata.put("age", 18);\ndata.put("timestamp", ServerValue.TIMESTAMP);\n\nfirebase.setPath("users/" + uid);\nfirebase.setValue(data);', 'java', 'firebase', 'Firebase,数据库,写入', 1, 'admin'),

('Firebase 用户注册', '使用 FirebaseAuth 组件实现邮箱注册', '// 注册\nfirebaseAuth.createUserWithEmailAndPassword(\n    email, password\n);\n\n// 在 onCreateUserComplete 事件中\nif (success) {\n    showMessage("注册成功");\n} else {\n    showMessage("注册失败: " + errorMessage);\n}', 'java', 'firebase', 'Firebase,Auth,注册,认证', 1, 'admin'),

('Firebase 用户登录', '使用 FirebaseAuth 组件实现邮箱登录', '// 登录\nfirebaseAuth.signInWithEmailAndPassword(\n    email, password\n);\n\n// 在 onSignInComplete 事件中\nif (success) {\n    String uid = FirebaseAuth.getInstance()\n        .getCurrentUser().getUid();\n    showMessage("登录成功");\n}', 'java', 'firebase', 'Firebase,Auth,登录,认证', 1, 'admin'),

('Dialog 对话框', '创建一个带确认和取消按钮的对话框', 'dialog.setTitle("提示");\ndialog.setMessage("确定要执行此操作吗？");\ndialog.setPositiveButton("确定");\ndialog.setNegativeButton("取消");\ndialog.show();\n\n// 在 onDialogPositiveButton 事件中\n// 处理确认逻辑', 'java', 'dialog', '对话框,弹窗,Dialog', 1, 'admin'),

('ListView 加载数据', '使用 ListView 和 CustomAdapter 显示列表数据', 'ArrayList<HashMap<String, Object>> listData = new ArrayList<>();\n\nHashMap<String, Object> item = new HashMap<>();\nitem.put("title", "标题1");\nitem.put("desc", "描述1");\nlistData.add(item);\n\nlistview.setAdapter(new ArrayAdapter<String>(\n    this, android.R.layout.simple_list_item_1,\n    titles\n));', 'java', 'list', 'ListView,列表,适配器', 1, 'admin'),

('MediaPlayer 播放音频', '使用 MediaPlayer 组件播放音频文件', '// 播放 assets 中的音频\nmediaPlayer.setDataSource("audio.mp3");\nmediaPlayer.prepare();\nmediaPlayer.start();\n\n// 暂停\nmediaPlayer.pause();\n\n// 停止\nmediaPlayer.stop();\nmediaPlayer.release();', 'java', 'media', '音频,播放,MediaPlayer', 1, 'admin'),

('Camera 拍照', '使用 Camera 组件拍照并获取图片', '// 打开相机\ncamera.takePicture();\n\n// 在 onPictureTaken 事件中\n// filePath 是拍摄照片的路径\nimageView.setImageBitmap(\n    BitmapFactory.decodeFile(filePath)\n);', 'java', 'media', '拍照,相机,Camera,图片', 1, 'admin'),

('FilePicker 选择文件', '使用 FilePicker 组件选择文件', '// 打开文件选择器\nfilePicker.launch("image/*");\n\n// 在 onFileSelected 事件中\n// filePath 是选择的文件路径\nshowMessage("选中: " + filePath);', 'java', 'file', '文件选择,FilePicker', 1, 'admin'),

('文件读写操作', '使用 FileUtil 进行文件读写', '// 写入文件\nFileUtil.writeFile(\n    "/storage/emulated/0/myapp/data.txt",\n    "Hello World"\n);\n\n// 读取文件\nString content = FileUtil.readFile(\n    "/storage/emulated/0/myapp/data.txt"\n);', 'java', 'file', '文件,读写,FileUtil', 1, 'admin'),

('ObjectAnimator 动画', '使用 ObjectAnimator 创建视图动画', '// 平移动画\nobjectAnimator.setTarget(view);\nobjectAnimator.setPropertyName("translationX");\nobjectAnimator.setFloatValues(0, 300);\nobjectAnimator.setDuration(500);\nobjectAnimator.start();\n\n// 旋转动画\nobjectAnimator.setPropertyName("rotation");\nobjectAnimator.setFloatValues(0, 360);', 'java', 'animation', '动画,ObjectAnimator,平移,旋转', 1, 'admin'),

('Vibrator 振动反馈', '使用 Vibrator 组件产生振动效果', '// 振动 500 毫秒\nvibrator.vibrate(500);\n\n// 振动模式: 等待100ms, 振动200ms, 等待100ms, 振动300ms\nlong[] pattern = {100, 200, 100, 300};\nvibrator.vibrate(pattern, -1);', 'java', 'sensor', '振动,Vibrator,反馈', 1, 'admin'),

('权限动态申请', '运行时动态申请危险权限', 'if (ContextCompat.checkSelfPermission(this,\n    Manifest.permission.CAMERA)\n    != PackageManager.PERMISSION_GRANTED) {\n    ActivityCompat.requestPermissions(this,\n        new String[]{\n            Manifest.permission.CAMERA\n        }, 1001);\n} else {\n    // 已有权限，直接执行\n    openCamera();\n}', 'java', 'permission', '权限,运行时权限,Camera', 1, 'admin'),

('字符串操作常用方法', 'Java 字符串处理常用技巧', '// 截取\nString sub = str.substring(0, 5);\n\n// 替换\nString replaced = str.replace("old", "new");\n\n// 分割\nString[] parts = str.split(",");\n\n// 包含检测\nboolean has = str.contains("keyword");\n\n// 大小写转换\nString upper = str.toUpperCase();\nString lower = str.toLowerCase();\n\n// 去除空格\nString trimmed = str.trim();', 'java', 'string', '字符串,String,截取,替换', 1, 'admin');

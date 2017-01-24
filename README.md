# react-native-upgrade-android

React Native的版本升级插件（仅是android）, react-native版本需要0.17.0及以上
## 如何安装

### 1.首先安装npm包

```bash
npm install react-native-upgrade-android --save
```

### 2.link
#### 自动link方法~ npm requires node version 4.1 or higher

```bash
npm link
```
link成功命令行会提示

```bash
npm info Linking react-native-upgrade-android android dependency
```

#### 手动link~（如果不能够自动link）
#####Android

```
// file: android/settings.gradle
...

include ':react-native-upgrade-android'
project(':react-native-upgrade-android').projectDir = new File(settingsDir, '../node_modules/react-native-upgrade-android/android')
```

```
// file: android/app/build.gradle
...

dependencies {
    ...
    compile project(':react-native-upgrade-android')
}
```

`android/app/src/main/java/<你的包名>/MainActivity.java`中，`public class MainActivity`之前增加：

```java
import com.lenny.modules.upgrade.UpgradeModule;
```

如果react-native-版本 <0.18.0
`.addPackage(new MainReactPackage())`之后增加：

```java
.addPackage(new UpgradPackage())
```
如果react-native-版本 >=0.18.0
在`new MainReactPackage()`之后增加
```java
,new UpgradePackage()
```

## 如何使用

### 引入包

```
import Upgrade from 'react-native-upgrade-android';
```

### API

#### Upgrade.init()

```javascript
// 使用前必须初始化
```

类似如下：

```javascript
componentDidMount() {
  const {
    isSet,
  } = this.props;
  if (Platform.OS !== 'ios') {
    Upgrade.init();
  }
}
```

#### WeiboAPI.startDownLoad(downloadUrl, version, fileName)

开始下载

```javascript
// 参数信息
  downloadUrl: 下载apk地址（绝对地址）String
  version: 要下载的版本号 （防止重复下载）String
  fileName: 保存的文件名 String
```

#### 添加监听
  类似如下:

  ```javascript
  componentDidMount() {
    const {
      isSet,
    } = this.props;
    if (Platform.OS !== 'ios') {
      Upgrade.init();
      DeviceEventEmitter.addListener('progress', (e) => {
        if (e.code === '0000') { // 开始下载
          this.setState({
            isLoading: true,
          });
        } else if (e.code === '0001') {  // 下载中，更新进度条
          this.setState({
            fileSize: e.fileSize,
            downSize: e.downSize,
          });
        } else if (e.code === '0002') { // 下载完成
          this.setState({
            fileSize: e.fileSize,
            downSize: e.downSize,
          });
        }
      });
    }
  }
  ```
